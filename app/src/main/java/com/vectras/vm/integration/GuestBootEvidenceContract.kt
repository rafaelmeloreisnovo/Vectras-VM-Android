package com.vectras.vm.integration

import java.security.SecureRandom

/** Nonce-bound guest serial evidence. Raw serial output is never persisted. */
object GuestBootEvidenceContract {

    const val SCHEMA = "raf.guest-boot-evidence.v1"
    const val BOOT_ARGUMENT_PREFIX = "rafaelia.boot_nonce="

    private val secureRandom = SecureRandom()
    private val noncePattern = Regex("^[0-9a-f]{64}$")

    data class Evidence(
        val state: String,
        val requested: Boolean,
        val complete: Boolean,
        val bootObserved: Boolean,
        val userspaceObserved: Boolean,
        val shutdownObserved: Boolean,
        val markersOrdered: Boolean,
        val outputTruncated: Boolean?,
        val arch: String?,
        val kernel: String?,
        val init: String?,
        val shutdownReason: String?,
        val bootMarkerSha256: String?,
        val userspaceMarkerSha256: String?,
        val shutdownMarkerSha256: String?,
    )

    fun generateNonce(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun validNonce(value: String?): Boolean = value != null && noncePattern.matches(value)

    fun kernelArgument(nonce: String): String {
        require(validNonce(nonce)) { "invalid guest boot nonce" }
        return "$BOOT_ARGUMENT_PREFIX$nonce"
    }

    fun containsNonceArgument(arguments: List<String>, nonce: String): Boolean {
        if (!validNonce(nonce)) return false
        val token = kernelArgument(nonce)
        return arguments.any { argument ->
            argument == token ||
                argument.split(Regex("\\s+")).any { it == token }
        }
    }

    fun analyze(
        stdout: String,
        nonce: String?,
        stdoutTruncated: Boolean?,
        exitCode: Int?,
        termuxErrorCode: Int?,
    ): Evidence {
        if (!validNonce(nonce)) {
            return Evidence(
                state = "NOT_REQUESTED",
                requested = false,
                complete = false,
                bootObserved = false,
                userspaceObserved = false,
                shutdownObserved = false,
                markersOrdered = false,
                outputTruncated = stdoutTruncated,
                arch = null,
                kernel = null,
                init = null,
                shutdownReason = null,
                bootMarkerSha256 = null,
                userspaceMarkerSha256 = null,
                shutdownMarkerSha256 = null,
            )
        }

        val escapedNonce = Regex.escape(nonce!!)
        val bootRegex = Regex(
            "(?m)^RAFAELIA_GUEST_BOOT_V1 nonce=$escapedNonce " +
                "arch=([A-Za-z0-9._-]{1,64}) kernel=([A-Za-z0-9._+:-]{1,128})$",
        )
        val userspaceRegex = Regex(
            "(?m)^RAFAELIA_GUEST_USERSPACE_V1 nonce=$escapedNonce " +
                "init=([A-Za-z0-9._/+:-]{1,160})$",
        )
        val shutdownRegex = Regex(
            "(?m)^RAFAELIA_GUEST_SHUTDOWN_V1 nonce=$escapedNonce " +
                "reason=(poweroff|halt|reboot)$",
        )

        val boot = bootRegex.find(stdout)
        val userspace = userspaceRegex.find(stdout)
        val shutdown = shutdownRegex.find(stdout)
        val ordered = boot != null && userspace != null && shutdown != null &&
            boot.range.first < userspace.range.first &&
            userspace.range.first < shutdown.range.first
        val truncated = stdoutTruncated == true
        val internalError = termuxErrorCode != null && termuxErrorCode != 0
        val complete = ordered && !truncated && !internalError && exitCode == 0

        val state = when {
            internalError -> "TERMUX_INTERNAL_ERROR"
            truncated -> "INCOMPLETE_OUTPUT_TRUNCATED"
            boot == null -> "CHALLENGE_NOT_OBSERVED"
            userspace == null -> "BOOT_MARKER_ONLY"
            shutdown == null -> "USERSPACE_READY_NO_SHUTDOWN"
            !ordered -> "MARKERS_OUT_OF_ORDER"
            exitCode == null -> "COMPLETE_MARKERS_EXIT_TOKEN_VAZIO"
            exitCode != 0 -> "COMPLETE_MARKERS_EXIT_NONZERO"
            else -> "COMPLETE_ORDERED_EXIT_ZERO"
        }

        return Evidence(
            state = state,
            requested = true,
            complete = complete,
            bootObserved = boot != null,
            userspaceObserved = userspace != null,
            shutdownObserved = shutdown != null,
            markersOrdered = ordered,
            outputTruncated = stdoutTruncated,
            arch = boot?.groupValues?.get(1),
            kernel = boot?.groupValues?.get(2),
            init = userspace?.groupValues?.get(1),
            shutdownReason = shutdown?.groupValues?.get(1),
            bootMarkerSha256 = boot?.value?.let(VectrasTermuxIpcContract::sha256),
            userspaceMarkerSha256 = userspace?.value?.let(VectrasTermuxIpcContract::sha256),
            shutdownMarkerSha256 = shutdown?.value?.let(VectrasTermuxIpcContract::sha256),
        )
    }
}
