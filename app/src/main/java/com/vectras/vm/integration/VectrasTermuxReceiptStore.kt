package com.vectras.vm.integration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Internal append-only request/receipt store for the Termux bridge. */
object VectrasTermuxReceiptStore {

    private const val REQUEST_SCHEMA = "raf.vectras-termux-request.v4"
    private const val REQUEST_DIRECTORY = "rafaelia-runtime-requests"
    private const val RECEIPT_DIRECTORY = "rafaelia-runtime-receipts"

    data class PendingRequest(
        val transactionId: String,
        val binaryName: String,
        val requestSha256: String,
        val argumentCount: Int,
        val argumentsSha256: String,
        val producerApkSha256: String?,
        val providerApkSha256Discovery: String?,
        val providerBinarySha256Discovery: String?,
        val providerIdentityObservedAtEpochMs: Long,
        val providerVersionDiscovery: String?,
        val createdAtEpochMs: Long,
    )

    fun writePending(
        context: Context,
        transactionId: String,
        binaryName: String,
        arguments: List<String>,
        requestSha256: String,
        producerApkSha256: String?,
        providerApkSha256Discovery: String?,
        providerBinarySha256Discovery: String?,
        providerIdentityObservedAtEpochMs: Long,
        providerVersionDiscovery: String?,
    ): Boolean {
        val directory = requestDirectory(context) ?: return false
        val target = File(directory, "$transactionId.json")
        if (target.exists()) return false

        val argumentsSha256 = VectrasTermuxIpcContract.sha256(
            arguments.joinToString(separator = "\n") { argument ->
                "${argument.toByteArray(Charsets.UTF_8).size}:$argument"
            },
        )
        val request = JSONObject().apply {
            put("schema", REQUEST_SCHEMA)
            put("protocol", VectrasTermuxIpcContract.PROTOCOL)
            put("protocol_version", VectrasTermuxIpcContract.PROTOCOL_VERSION)
            put("transaction_id", transactionId)
            put("producer", "rafaelmeloreisnovo/Vectras-VM-Android")
            put("producer_commit", "TOKEN_VAZIO")
            put("producer_apk_sha256", producerApkSha256 ?: "TOKEN_VAZIO")
            put("target_package", VectrasTermuxIpcContract.TERMUX_PACKAGE)
            put("service_class", VectrasTermuxIpcContract.SERVICE_CLASS)
            put("action", VectrasTermuxIpcContract.ACTION_RUN_COMMAND)
            put("permission", VectrasTermuxIpcContract.RUN_COMMAND_PERMISSION)
            put("binary_name", binaryName)
            put("command_path", VectrasTermuxIpcContract.commandPath(binaryName))
            put("provider_apk_sha256_discovery", providerApkSha256Discovery ?: "TOKEN_VAZIO")
            put("provider_binary_sha256_discovery", providerBinarySha256Discovery ?: "TOKEN_VAZIO")
            put("provider_identity_observed_at_epoch_ms", providerIdentityObservedAtEpochMs)
            put("provider_version_discovery", providerVersionDiscovery ?: "TOKEN_VAZIO")
            put("provider_identity_scope", "DISCOVERY_NOT_EXECUTION")
            put("executable_sha256_at_execution", "TOKEN_VAZIO")
            put("workdir", VectrasTermuxIpcContract.WORKDIR)
            put("runner", VectrasTermuxIpcContract.RUNNER_APP_SHELL)
            put("argument_count", arguments.size)
            put("arguments_sha256", argumentsSha256)
            put("arguments", JSONArray(arguments))
            put("request_sha256", requestSha256)
            put("created_at_epoch_ms", System.currentTimeMillis())
            put("state", "PENDING_DISPATCH_RESULT")
            put("claim_allowed", false)
        }
        return writeAtomic(target, request.toString(2) + "\n")
    }

    fun loadPending(context: Context, transactionId: String): PendingRequest? {
        if (!safeTransactionId(transactionId)) return null
        val file = File(File(context.filesDir, REQUEST_DIRECTORY), "$transactionId.json")
        if (!file.isFile) return null
        return try {
            val value = JSONObject(file.readText(Charsets.UTF_8))
            if (value.optString("schema") != REQUEST_SCHEMA) return null
            if (value.optString("transaction_id") != transactionId) return null
            val requestSha256 = value.optString("request_sha256")
            val argumentsSha256 = value.optString("arguments_sha256")
            if (!SHA256_PATTERN.matches(requestSha256)) return null
            if (!SHA256_PATTERN.matches(argumentsSha256)) return null
            PendingRequest(
                transactionId = transactionId,
                binaryName = value.optString("binary_name"),
                requestSha256 = requestSha256,
                argumentCount = value.optInt("argument_count", -1),
                argumentsSha256 = argumentsSha256,
                producerApkSha256 = optionalSha256(value.optString("producer_apk_sha256")),
                providerApkSha256Discovery = optionalSha256(
                    value.optString("provider_apk_sha256_discovery"),
                ),
                providerBinarySha256Discovery = optionalSha256(
                    value.optString("provider_binary_sha256_discovery"),
                ),
                providerIdentityObservedAtEpochMs = value.optLong(
                    "provider_identity_observed_at_epoch_ms",
                    -1L,
                ),
                providerVersionDiscovery = value.optString("provider_version_discovery")
                    .takeUnless { it.isBlank() || it == "TOKEN_VAZIO" },
                createdAtEpochMs = value.optLong("created_at_epoch_ms", -1L),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun writeReceipt(context: Context, transactionId: String, receipt: JSONObject): Boolean {
        if (!safeTransactionId(transactionId)) return false
        val directory = receiptDirectory(context) ?: return false
        val target = File(directory, "$transactionId.json")
        if (target.exists()) return false
        return writeAtomic(target, receipt.toString(2) + "\n")
    }

    private fun requestDirectory(context: Context): File? =
        ensureDirectory(File(context.filesDir, REQUEST_DIRECTORY))

    private fun receiptDirectory(context: Context): File? =
        ensureDirectory(File(context.filesDir, RECEIPT_DIRECTORY))

    private fun ensureDirectory(directory: File): File? {
        if (directory.isDirectory) return directory
        if (directory.exists()) return null
        return if (directory.mkdirs()) directory else null
    }

    private fun writeAtomic(target: File, text: String): Boolean {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        return try {
            if (temporary.exists() && !temporary.delete()) return false
            temporary.writeText(text, Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                false
            } else {
                true
            }
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    private fun optionalSha256(value: String?): String? = value
        ?.lowercase()
        ?.takeIf { SHA256_PATTERN.matches(it) }

    private fun safeTransactionId(value: String): Boolean = TRANSACTION_PATTERN.matches(value)

    private val TRANSACTION_PATTERN = Regex("^[A-Za-z0-9._:-]{8,128}$")
    private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
}
