package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZiprafDirectEntryPolicyTest {
    @Test
    fun policy_validatesSizeCrcAndSha256() {
        val file = File.createTempFile("zipraf-policy", ".zip")
        try {
            val payload = ByteArray(4097) { (it * 11).toByte() }
            writeStoredArchive(file, "runtime/policy.bin", payload)
            val policy = ZiprafDirectEntryPolicy(
                entryName = "runtime/policy.bin",
                maxPayloadBytes = 8192,
                expectedPayloadBytes = payload.size.toLong(),
                expectedCrc32 = CRC32().apply { update(payload) }.value,
                expectedSha256Hex = sha256Hex(payload)
            )

            ZiprafDirectPolicyVerifier.open(file, policy).use { opened ->
                assertEquals(payload.size.toLong(), opened.evidence.payloadBytes)
                assertEquals(sha256Hex(payload), opened.evidence.sha256Hex)
                assertTrue(opened.evidence.centralDirectoryValidated)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun policy_rejectsWrongSha256() {
        val file = File.createTempFile("zipraf-policy-sha", ".zip")
        try {
            val payload = byteArrayOf(1, 2, 3)
            writeStoredArchive(file, "runtime/policy.bin", payload)
            expectFailure<IllegalArgumentException> {
                ZiprafDirectPolicyVerifier.open(
                    file,
                    ZiprafDirectEntryPolicy(
                        entryName = "runtime/policy.bin",
                        maxPayloadBytes = 100,
                        expectedSha256Hex = "00".repeat(32)
                    )
                ).close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun policy_rejectsPayloadAboveMaximum() {
        val file = File.createTempFile("zipraf-policy-size", ".zip")
        try {
            val payload = ByteArray(101)
            writeStoredArchive(file, "runtime/policy.bin", payload)
            expectFailure<IllegalArgumentException> {
                ZiprafDirectPolicyVerifier.open(
                    file,
                    ZiprafDirectEntryPolicy(
                        entryName = "runtime/policy.bin",
                        maxPayloadBytes = 100
                    )
                ).close()
            }
        } finally {
            file.delete()
        }
    }

    private fun writeStoredArchive(file: File, name: String, payload: ByteArray) {
        val crc = CRC32().apply { update(payload) }.value
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            val entry = ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = payload.size.toLong()
                compressedSize = payload.size.toLong()
                this.crc = crc
            }
            zip.putNextEntry(entry)
            zip.write(payload)
            zip.closeEntry()
        }
    }

    private fun sha256Hex(payload: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(payload)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            assertTrue(failure is T)
        }
    }
}
