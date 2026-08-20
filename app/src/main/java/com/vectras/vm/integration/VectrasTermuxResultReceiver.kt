package com.vectras.vm.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Receives the PendingIntent result emitted by Termux RunCommandService and
 * materializes a privacy-minimized receipt. Raw stdout, stderr and errmsg are
 * not stored; only lengths, truncation state and SHA-256 digests are retained.
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
            ).joinToString("\n", postfix = "\n"),
        )

        val materialIdentityBound = pending.producerApkSha256 != null &&
            pending.providerApkSha256Discovery != null &&
            pending.providerBinarySha256Discovery != null
        val provenanceChainSha256 = VectrasTermuxIpcContract.sha256(
            listOf(
                "schema=raf.provenance-chain.v1",
                "transaction_id=$transactionId",
                "request_sha256=${pending.requestSha256}",
                "producer_apk_sha256=${pending.producerApkSha256 ?: "TOKEN_VAZIO"}",
                "provider_apk_sha256_discovery=${pending.providerApkSha256Discovery ?: "TOKEN_VAZIO"}",
                "provider_binary_sha256_discovery=${pending.providerBinarySha256Discovery ?: "TOKEN_VAZIO"}",
                "provider_identity_observed_at_epoch_ms=${pending.providerIdentityObservedAtEpochMs}",
                "executable_sha256_at_execution=TOKEN_VAZIO",
                "output_sha256=$outputSha256",
            ).joinToString("\n", postfix = "\n"),
        )
        val provenanceChainState = when {
            executionReceiptPresent && materialIdentityBound ->
                "MATERIAL_BOUND_EXECUTION_IDENTITY_REVALIDATION_REQUIRED"
            materialIdentityBound -> "MATERIAL_BOUND_EXECUTION_RECEIPT_TOKEN_VAZIO"
            else -> "PARTIAL_MATERIAL_IDENTITY"
        }

        val fOk = JSONArray()
            .put("request_persisted_before_dispatch")
            .put("result_bound_to_local_transaction")
        val fGap = JSONArray()
            .put("producer_commit:TOKEN_VAZIO")
            .put("termux_commit:TOKEN_VAZIO")
            .put("executable_sha256_at_execution:TOKEN_VAZIO")
            .put("guest_boot:TOKEN_VAZIO")
        val fNext = JSONArray()
            .put("bind provider executable digest at execution-time result boundary")
            .put("capture_guest_boot_artifact")
        if (pending.producerApkSha256 != null) {
            fOk.put("producer_apk_sha256_bound")
        } else {
            fGap.put("producer_apk_sha256:TOKEN_VAZIO")
        }
        if (pending.providerApkSha256Discovery != null) {
            fOk.put("provider_apk_sha256_discovery_bound")
        } else {
            fGap.put("provider_apk_sha256_discovery:TOKEN_VAZIO")
        }
        if (pending.providerBinarySha256Discovery != null) {
            fOk.put("provider_binary_sha256_discovery_bound")
        } else {
            fGap.put("provider_binary_sha256_discovery:TOKEN_VAZIO")
        }
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
        if (stdoutTruncated == true) fGap.put("stdout_truncated")
        if (stderrTruncated == true) fGap.put("stderr_truncated")

        val effectsObserved = JSONArray().put("REQUEST_PERSISTED")
        if (materialIdentityBound) effectsObserved.put("DISCOVERY_MATERIAL_IDENTITY_BOUND")
        if (resultBundlePresent) effectsObserved.put("RESULT_BUNDLE")
        if (executionReceiptPresent) effectsObserved.put("EXECUTION_RECEIPT")

        val receipt = JSONObject().apply {
            put("schema", "raf.android-runtime-receipt.v3")
            put("protocol", VectrasTermuxIpcContract.PROTOCOL)
            put("protocol_version", VectrasTermuxIpcContract.PROTOCOL_VERSION)
            put("transaction_id", transactionId)
            put("producer", "rafaelmeloreisnovo/Vectras-VM-Android")
            put("producer_commit", "TOKEN_VAZIO")
            put("producer_apk_sha256", pending.producerApkSha256 ?: "TOKEN_VAZIO")
            put("termux_repository", "rafaelmeloreisnovo/termux-app-rafacodephi")
            put("termux_commit", "TOKEN_VAZIO")
            put("provider_apk_sha256_discovery", pending.providerApkSha256Discovery ?: "TOKEN_VAZIO")
            put("provider_binary_sha256_discovery", pending.providerBinarySha256Discovery ?: "TOKEN_VAZIO")
            put("provider_identity_observed_at_epoch_ms", pending.providerIdentityObservedAtEpochMs)
            put("provider_version_discovery", pending.providerVersionDiscovery ?: "TOKEN_VAZIO")
            put("provider_identity_scope", "DISCOVERY_NOT_EXECUTION")
            put("executable_sha256_at_execution", "TOKEN_VAZIO")
            put("target_package", VectrasTermuxIpcContract.TERMUX_PACKAGE)
            put("service_class", VectrasTermuxIpcContract.SERVICE_CLASS)
            put("binary_name", pending.binaryName)
            put("argument_count", pending.argumentCount)
            put("arguments_sha256", pending.argumentsSha256)
            put("input_sha256", pending.requestSha256)
            put("output_sha256", outputSha256)
            put("provenance_chain_sha256", provenanceChainSha256)
            put("provenance_chain_state", provenanceChainState)
            put("provenance_chain_complete", false)
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
            put("guest_boot_artifact_sha256", JSONObject.NULL)
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

    private fun intOrNull(bundle: Bundle, key: String): Int? {
        if (!bundle.containsKey(key)) return null
        return when (val value = bundle.get(key)) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    companion object {
        const val ACTION_EXECUTION_RESULT =
            "com.rafacodephi.app.ACTION_TERMUX_EXECUTION_RESULT"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_BINARY_NAME = "binary_name"
        const val EXTRA_REQUEST_SHA256 = "request_sha256"

        private val TRANSACTION_PATTERN = Regex("^[A-Za-z0-9._:-]{8,128}$")
    }
}
