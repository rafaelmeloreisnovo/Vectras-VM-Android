package com.vectras.vm.vectra

import java.io.File
import java.security.MessageDigest

data class ZiprafDirectEntryPolicy(
    val entryName: String,
    val maxPayloadBytes: Long,
    val expectedPayloadBytes: Long? = null,
    val expectedCrc32: Long? = null,
    val expectedSha256Hex: String? = null
) {
    init {
        require(entryName.isNotBlank())
        require(maxPayloadBytes > 0)
        require(expectedPayloadBytes == null || expectedPayloadBytes in 1..maxPayloadBytes)
        require(expectedCrc32 == null || expectedCrc32 in 0..0xffff_ffffL)
        expectedSha256Hex?.let {
            require(SHA256_PATTERN.matches(it)) {
                "expectedSha256Hex must contain 64 hexadecimal characters"
            }
        }
    }

    companion object {
        private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    }
}

data class ZiprafDirectPolicyEvidence(
    val entryName: String,
    val payloadBytes: Long,
    val crc32: Long,
    val sha256Hex: String?,
    val centralDirectoryValidated: Boolean
)

data class ZiprafPolicyOpenedSession(
    val session: ZiprafDirectStoreSession,
    val evidence: ZiprafDirectPolicyEvidence
) : AutoCloseable {
    override fun close() = session.close()
}

object ZiprafDirectPolicyVerifier {
    @JvmStatic
    @JvmOverloads
    fun open(
        file: File,
        policy: ZiprafDirectEntryPolicy,
        plan: ZiprafRuntimePlan = ZiprafRuntimePlan()
    ): ZiprafPolicyOpenedSession {
        val session = ZiprafDirectStoreSession.open(
            file = file,
            entryName = policy.entryName,
            plan = plan,
            verifyCrc32 = true
        )
        try {
            val entry = session.entry
            require(entry.validationLevel == ZiprafValidationLevel.CENTRAL_DIRECTORY) {
                "ZIPRAF policy requires central-directory validation"
            }
            require(entry.extent.payloadSize <= policy.maxPayloadBytes) {
                "ZIPRAF payload exceeds policy maxPayloadBytes"
            }
            policy.expectedPayloadBytes?.let { expected ->
                require(entry.extent.payloadSize == expected) {
                    "ZIPRAF payload size differs from policy"
                }
            }
            val archiveCrc = requireNotNull(entry.extent.expectedCrc32) {
                "ZIPRAF validated entry is missing CRC-32"
            }
            policy.expectedCrc32?.let { expected ->
                require(archiveCrc == expected) { "ZIPRAF CRC-32 differs from policy" }
            }

            val sha256Hex = policy.expectedSha256Hex?.let { expected ->
                val actual = computeSha256(session)
                require(MessageDigest.isEqual(hexToBytes(actual), hexToBytes(expected))) {
                    "ZIPRAF SHA-256 differs from policy"
                }
                actual
            }

            return ZiprafPolicyOpenedSession(
                session = session,
                evidence = ZiprafDirectPolicyEvidence(
                    entryName = entry.name,
                    payloadBytes = entry.extent.payloadSize,
                    crc32 = archiveCrc,
                    sha256Hex = sha256Hex,
                    centralDirectoryValidated = true
                )
            )
        } catch (failure: Throwable) {
            session.close()
            throw failure
        }
    }

    private fun computeSha256(session: ZiprafDirectStoreSession): String {
        val digest = MessageDigest.getInstance("SHA-256")
        session.scan(ZiprafMemoryStage.BUFFER) { window ->
            val bytes = window.bytes
            val chunk = ByteArray(bytes.remaining())
            bytes.get(chunk)
            digest.update(chunk)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun hexToBytes(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
