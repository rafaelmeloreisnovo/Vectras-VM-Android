package com.vectras.vm.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Receives the PendingIntent result emitted by Termux RunCommandService and
 * materializes a privacy-minimized receipt. Raw stdout/stderr are not stored;
 * only byte lengths and SHA-256 digests are retained.
 */
class VectrasTermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXECUTION_RESULT) return

        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
            ?.takeIf { TRANSACTION_PATTERN.matches(it) } ?: return
        val binaryName = intent.getStringExtra(EXTRA_BINARY_NAME)
            ?.takeIf { it in VectrasTermuxBridge.allowedBinaryNames() } ?: return
        val result = intent.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: Bundle.EMPTY

        val stdout = result.getString(EXTRA_STDOUT).orEmpty()
        val stderr = result.getString(EXTRA_STDERR).orEmpty()
        val exitCode = if (result.containsKey(EXTRA_EXIT_CODE)) {
            result.getInt(EXTRA_EXIT_CODE)
        } else {
            null
        }

        val receipt = JSONObject().apply {
            put("schema", "raf.android-runtime-receipt.v1")
            put("transaction_id", transactionId)
            put("producer", "rafaelmeloreisnovo/Vectras-VM-Android")
            put("producer_commit", "TOKEN_VAZIO")
            put("termux_commit", "TOKEN_VAZIO")
            put("protocol_version", 2)
            put("binary_name", binaryName)
            put("dispatch_state", "DISPATCHED")
            put("execution_exit_code", exitCode ?: JSONObject.NULL)
            put("stdout_bytes", stdout.toByteArray(Charsets.UTF_8).size)
            put("stdout_sha256", sha256(stdout))
            put("stderr_bytes", stderr.toByteArray(Charsets.UTF_8).size)
            put("stderr_sha256", sha256(stderr))
            put("guest_boot_artifact_sha256", JSONObject.NULL)
            put("private_paths_exposed", false)
            put("effects_observed", JSONArray().put("DISPATCH").put("EXECUTION_RECEIPT"))
            put("safe_state", "vm-stopped-no-image-mutation")
            put("rollback_anchor", "TOKEN_VAZIO")
            put("claim_allowed", false)
        }

        val directory = File(context.filesDir, "rafaelia-runtime-receipts")
        if (!directory.exists() && !directory.mkdirs()) return
        val target = File(directory, "$transactionId.json")
        val temporary = File(directory, "$transactionId.json.tmp")
        temporary.writeText(receipt.toString(2) + "\n", Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.delete()
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val ACTION_EXECUTION_RESULT =
            "com.rafacodephi.app.ACTION_TERMUX_EXECUTION_RESULT"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_BINARY_NAME = "binary_name"

        private const val EXTRA_RESULT_BUNDLE = "result"
        private const val EXTRA_STDOUT = "stdout"
        private const val EXTRA_STDERR = "stderr"
        private const val EXTRA_EXIT_CODE = "exitCode"

        private val TRANSACTION_PATTERN = Regex("^[A-Za-z0-9._:-]{8,128}$")
    }
}
