package com.vectras.vm.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Receives the Termux result and materializes a privacy-minimized receipt.
 * Raw stdout, stderr and errmsg are never persisted.
 */
class VectrasTermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXECUTION_RESULT) return

        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
            ?.takeIf { TRANSACTION_PATTERN.matches(it) } ?: return
        val pending = VectrasTermuxReceiptStore.loadPending(context, transactionId) ?: return

        val intentBinary = intent.getStringExtra(EXTRA_BINARY_NAME)
        val intentRequestSha256 = intent.getStringExtra(EXTRA_REQUEST_SHA256)
        if (intentBinary != null && intentBinary != pending.binaryName) return
        if (intentRequestSha256 != null && intentRequestSha256 != pending.requestSha256) return
        if (pending.binaryName !in VectrasTermuxBridge.allowedBinaryNames()) return

        val result = intent.getBundleExtra(VectrasTermuxIpcContract.RESULT_BUNDLE)
        val resultBundlePresent = result != null
        val safeResult = result ?: Bundle.EMPTY

        val stdout = safeResult.getString(VectrasTermuxIpcContract.RESULT_STDOUT).orEmpty()
        val stderr = safeResult.getString(VectrasTermuxIpcContract.RESULT_STDERR).orEmpty()
        val errorMessage = safeResult.getString(VectrasTermuxIpcContract.RESULT_ERRMSG).orEmpty()
        val exitCode = intOrNull(safeResult, VectrasTermuxIpcContract.RESULT_EXIT_CODE)
        val errorCode = intOrNull(safeResult, VectrasTermuxIpcContract.RESULT_ERR)

        val stdoutBytes = stdout.toByteArray(Charsets.UTF_8).size
        val stderrBytes = stderr.toByteArray(Charsets.UTF_8).size
        val errorMessageBytes = errorMessage.toByteArray(Charsets.UTF_8).size
        val stdoutOriginalLength = intOrNull(
            safeResult,
            VectrasTermuxIpcContract.RESULT_STDOUT_ORIGINAL_LENGTH,
        )
        val stderrOriginalLength = intOrNull(
            safeResult,
            VectrasTermuxIpcContract.RESULT_STDERR_ORIGINAL_LENGTH,
        )
        val stdoutTruncated = stdoutOriginalLength?.let { it > stdoutBytes }
        val stderrTruncated = stderrOriginalLength?.let { it > stderrBytes }

        val stdoutSha256 = VectrasTermuxIpcContract.sha256(stdout)
        val stderrSha256 = VectrasTermuxIpcContract.sha256(stderr)
        val errorMessageSha256 = VectrasTermuxIpcContract.sha256(errorMessage)
        val guestEvidence = GuestBootEvidenceContract.analyze(
            stdout = stdout,
            nonce = pending.guestBootNonce,
            stdoutTruncated = stdoutTruncated,
            exitCode = exitCode,
            termuxErrorCode = errorCode,
        )

        val status = when {
            !resultBundlePresent -> "RESULT_BUNDLE_TOKEN_VAZIO"
            errorCode != null && errorCode != 0 -> "TERMUX_INTERNAL_ERROR"
            exitCode == null -> "EXECUTION_EXIT_TOKEN_VAZIO"
            exitCode == 0 -> "EXECUTED_EXIT_ZERO"
            else -> "EXECUTED_NONZERO"
        }
        val executionReceiptPresent = resultBundlePresent &&
            errorCode != null && errorCode == 0 && exitCode != null
        val outputSha256 = VectrasTermuxIpcContract.sha256(
            listOf(
                "protocol=${VectrasTermuxIpcContract.PROTOCOL}",
                "transaction_id=$transactionId",
                "request_sha256=${pending.requestSha256}",
                "exit_code=${exitCode ?: "TOKEN_VAZIO"}",
                "termux_error_code=${errorCode ?: "TOKEN_VAZIO"}",
                "stdout_sha256=$stdoutSha256",
                "stdout_original_length=${stdoutOriginalLength ?: "TOKEN_VAZIO"}",
                "stderr_sha256=$stderrSha256",
                "stderr_original_length=${stderrOriginalLength ?: "TOKEN_VAZIO"}",
                "errmsg_sha256=$errorMessageSha256",
                "guest_evidence_state=${guestEvidence.state}",
                "guest_nonce=${pending.guestBootNonce ?: "TOKEN_VAZIO"}",
                "boot_marker_sha256=${guestEvidence.bootMarkerSha256 ?: "TOKEN_VAZIO"}",
                "userspace_marker_sha256=${guestEvidence.userspaceMarkerSha256 ?: "TOKEN_VAZIO"}",
                "shutdown_marker_sha256=${guestEvidence.shutdownMarkerSha256 ?: "TOKEN_VAZIO"}",
            ).joinToString("\n", postfix = "\n"),
        )

        val fOk = JSONArray()
            .put("request_persisted_before_dispatch")
            .put("result_bound_to_local_transaction")
        val fGap = JSONArray()
            .put("producer_commit:TOKEN_VAZIO")
            .put("termux_commit:TOKEN_VAZIO")
        val fNext = JSONArray().put("collect_device_manifest_and_apk_hashes")

        if (resultBundlePresent) {
            fOk.put("termux_result_bundle_present")
        } else {
            fGap.put("termux_result_bundle:TOKEN_VAZIO")
        }
        if (executionReceiptPresent) {
            fOk.put("execution_exit_receipt_present")
        } else {
            fGap.put("execution_exit_receipt:TOKEN_VAZIO")
        }
        if (guestEvidence.complete) {
            fOk.put("nonce_bound_guest_markers_complete_ordered_exit_zero")
        } else if (guestEvidence.requested) {
            fGap.put("guest_boot_evidence:${guestEvidence.state}")
        } else {
            fGap.put("guest_boot_evidence:NOT_REQUESTED")
        }
        if (stdoutTruncated == true) fGap.put("stdout_truncated")
        if (stderrTruncated == true) fGap.put("stderr_truncated")

        val effectsObserved = JSONArray().put("REQUEST_PERSISTED")
        if (resultBundlePresent) effectsObserved.put("RESULT_BUNDLE")
        if (executionReceiptPresent) effectsObserved.put("EXECUTION_RECEIPT")
        if (guestEvidence.bootObserved) effectsObserved.put("GUEST_BOOT_MARKER")
        if (guestEvidence.userspaceObserved) effectsObserved.put("GUEST_USERSPACE_MARKER")
        if (guestEvidence.shutdownObserved) effectsObserved.put("GUEST_SHUTDOWN_MARKER")

        val receipt = JSONObject().apply {
            put("schema", "raf.android-runtime-receipt.v2")
            put("protocol", VectrasTermuxIpcContract.PROTOCOL)
            put("protocol_version", VectrasTermuxIpcContract.PROTOCOL_VERSION)
            put("transaction_id", transactionId)
            put("producer", "rafaelmeloreisnovo/Vectras-VM-Android")
            put("producer_commit", "TOKEN_VAZIO")
            put("termux_repository", "rafaelmeloreisnovo/termux-app-rafacodephi")
            put("termux_commit", "TOKEN_VAZIO")
            put("target_package", VectrasTermuxIpcContract.TERMUX_PACKAGE)
            put("service_class", VectrasTermuxIpcContract.SERVICE_CLASS)
            put("binary_name", pending.binaryName)
            put("argument_count", pending.argumentCount)
            put("arguments_sha256", pending.argumentsSha256)
            put("input_sha256", pending.requestSha256)
            put("output_sha256", outputSha256)
            put("status", status)
            put("dispatch_state", "DISPATCH_REQUEST_PERSISTED")
            put("result_bundle_present", resultBundlePresent)
            put("execution_receipt_present", executionReceiptPresent)
            put("execution_exit_code", exitCode ?: JSONObject.NULL)
            put("termux_error_code", errorCode ?: JSONObject.NULL)
            put("termux_error_message_bytes", errorMessageBytes)
            put("termux_error_message_sha256", errorMessageSha256)
            put("stdout_bytes", stdoutBytes)
            put("stdout_original_length", stdoutOriginalLength ?: JSONObject.NULL)
            put("stdout_truncated", stdoutTruncated ?: JSONObject.NULL)
            put("stdout_sha256", stdoutSha256)
            put("stderr_bytes", stderrBytes)
            put("stderr_original_length", stderrOriginalLength ?: JSONObject.NULL)
            put("stderr_truncated", stderrTruncated ?: JSONObject.NULL)
            put("stderr_sha256", stderrSha256)
            put("request_created_at_epoch_ms", pending.createdAtEpochMs)
            put("receipt_created_at_epoch_ms", System.currentTimeMillis())
            put("guest_boot_evidence_schema", GuestBootEvidenceContract.SCHEMA)
            put("guest_boot_nonce", pending.guestBootNonce ?: JSONObject.NULL)
            put("guest_boot_evidence_state", guestEvidence.state)
            put("guest_boot_evidence_requested", guestEvidence.requested)
            put("guest_boot_evidence_complete", guestEvidence.complete)
            put("guest_boot_marker_observed", guestEvidence.bootObserved)
            put("guest_userspace_marker_observed", guestEvidence.userspaceObserved)
            put("guest_shutdown_marker_observed", guestEvidence.shutdownObserved)
            put("guest_markers_ordered", guestEvidence.markersOrdered)
            put("guest_arch", guestEvidence.arch ?: JSONObject.NULL)
            put("guest_kernel", guestEvidence.kernel ?: JSONObject.NULL)
            put("guest_init", guestEvidence.init ?: JSONObject.NULL)
            put("guest_shutdown_reason", guestEvidence.shutdownReason ?: JSONObject.NULL)
            put("guest_boot_marker_sha256", guestEvidence.bootMarkerSha256 ?: JSONObject.NULL)
            put("guest_userspace_marker_sha256", guestEvidence.userspaceMarkerSha256 ?: JSONObject.NULL)
            put("guest_shutdown_marker_sha256", guestEvidence.shutdownMarkerSha256 ?: JSONObject.NULL)
            put("guest_boot_artifact_sha256", outputSha256)
            put("private_paths_exposed", false)
            put("effects_observed", effectsObserved)
            put("safe_state", "vm-stopped-no-image-mutation")
            put("rollback_anchor", "TOKEN_VAZIO")
            put("claim_allowed", false)
            put("F_ok", fOk)
            put("F_gap", fGap)
            put("F_next", fNext)
        }

        VectrasTermuxReceiptStore.writeReceipt(context, transactionId, receipt)
    }

    private fun intOrNull(bundle: Bundle, key: String): Int? =
        if (bundle.containsKey(key)) bundle.getInt(key) else null

    companion object {
        const val ACTION_EXECUTION_RESULT =
            "com.rafacodephi.app.ACTION_TERMUX_EXECUTION_RESULT"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_BINARY_NAME = "binary_name"
        const val EXTRA_REQUEST_SHA256 = "request_sha256"

        private val TRANSACTION_PATTERN = Regex("^[A-Za-z0-9._:-]{8,128}$")
    }
}
