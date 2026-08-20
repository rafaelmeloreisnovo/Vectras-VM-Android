package com.vectras.vm.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.UUID

/**
 * Discovers the external Termux RAFCODE-Φ runtime without trusting or exposing
 * another application's private sandbox paths.
 *
 * Protocol v2 returns capability names plus privacy-safe material identity
 * digests. Actual QEMU dispatch is performed by [VectrasTermuxBridge] through
 * the permission-gated RunCommandService.
 */
object CrossRepoIntegrationManager {

    private const val TAG = "CrossRepoIntegration"
    private const val QUERY_TIMEOUT_MS = 3_000L
    private const val PROVIDER_PREFS = "vectras_termux_provider_identity_v1"
    private const val KEY_PROVIDER_APK_PREF = "provider_apk_sha256"
    private const val KEY_PROVIDER_QEMU_PREF = "qemu_binary_sha256_json"
    private const val KEY_PROVIDER_VERSION_PREF = "termux_version"
    private const val KEY_PROVIDER_OBSERVED_AT_PREF = "observed_at_epoch_ms"

    const val TERMUX_PACKAGE = "com.termux.rafacodephi"
    const val TERMUX_RUN_COMMAND_PERMISSION =
        "com.termux.rafacodephi.permission.RUN_COMMAND"

    private const val ACTION_QUERY = "com.vectras.vm.ACTION_QUERY_INTEGRATION"
    private const val ACTION_RESPONSE = "com.vectras.vm.ACTION_INTEGRATION_RESPONSE"
    private const val KEY_NONCE = "nonce"

    data class IntegrationStatus(
        val termuxInstalled: Boolean,
        val bootstrapReady: Boolean,
        val protocolVersion: Int,
        val qemuBinaryNames: List<String>,
        val qemuBinarySha256: Map<String, String>,
        val providerApkSha256: String?,
        val executionMode: String?,
        val runCommandPermission: String?,
        val privatePathsExposed: Boolean,
        val termuxVersion: String?,
    ) {
        val isFullyReady: Boolean
            get() = termuxInstalled &&
                bootstrapReady &&
                protocolVersion >= 2 &&
                qemuBinaryNames.isNotEmpty() &&
                executionMode == VectrasTermuxBridge.EXECUTION_MODE &&
                runCommandPermission == TERMUX_RUN_COMMAND_PERMISSION &&
                !privatePathsExposed

        val provenanceReady: Boolean
            get() = providerApkSha256 != null &&
                qemuBinaryNames.isNotEmpty() &&
                qemuBinaryNames.all { qemuBinarySha256[it] != null }
    }

    data class CachedProviderIdentity(
        val providerApkSha256: String?,
        val providerBinarySha256Discovery: String?,
        val termuxVersion: String?,
        val observedAtEpochMs: Long,
    )

    fun queryIntegration(context: Context, onResult: (IntegrationStatus) -> Unit) {
        if (!isTermuxInstalled(context)) {
            onResult(emptyStatus(termuxInstalled = false))
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val nonce = UUID.randomUUID().toString()
        var responded = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_RESPONSE || responded) return
                if (intent.getStringExtra(KEY_NONCE) != nonce) return
                responded = true
                runCatching { ctx.unregisterReceiver(this) }

                val binaryNames = intent.getStringArrayExtra("qemu_binary_names")
                    ?.toList().orEmpty()
                val binaryDigests = parseBinaryDigests(
                    intent.getStringArrayExtra("qemu_binary_sha256"),
                    binaryNames,
                )
                val status = IntegrationStatus(
                    termuxInstalled = true,
                    bootstrapReady = intent.getBooleanExtra("bootstrap_ready", false),
                    protocolVersion = intent.getIntExtra("protocol_version", 0),
                    qemuBinaryNames = binaryNames,
                    qemuBinarySha256 = binaryDigests,
                    providerApkSha256 = normalizeSha256(
                        intent.getStringExtra("provider_apk_sha256"),
                    ),
                    executionMode = intent.getStringExtra("execution_mode"),
                    runCommandPermission = intent.getStringExtra("run_command_permission"),
                    privatePathsExposed =
                        intent.getBooleanExtra("private_paths_exposed", true),
                    termuxVersion = intent.getStringExtra("termux_version"),
                )
                persistProviderIdentity(ctx, status)
                mainHandler.post { onResult(status) }
            }
        }

        val filter = IntentFilter(ACTION_RESPONSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                filter,
                TERMUX_RUN_COMMAND_PERMISSION,
                mainHandler,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                filter,
                TERMUX_RUN_COMMAND_PERMISSION,
                mainHandler,
            )
        }

        mainHandler.postDelayed({
            if (!responded) {
                responded = true
                runCatching { context.unregisterReceiver(receiver) }
                Log.w(TAG, "Termux did not answer the v2 discovery request")
                onResult(emptyStatus(termuxInstalled = true))
            }
        }, QUERY_TIMEOUT_MS)

        // The query is package-targeted and contains only a nonce. The response
        // is permission-gated and contains no private paths.
        context.sendBroadcast(
            Intent(ACTION_QUERY)
                .setPackage(TERMUX_PACKAGE)
                .putExtra(KEY_NONCE, nonce),
        )
    }

    fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun logStatus(status: IntegrationStatus) {
        Log.i(
            TAG,
            "termux_installed=${status.termuxInstalled} " +
                "bootstrap_ready=${status.bootstrapReady} " +
                "protocol=${status.protocolVersion} " +
                "qemu_count=${status.qemuBinaryNames.size} " +
                "provider_identity=${status.provenanceReady} " +
                "private_paths_exposed=${status.privatePathsExposed} " +
                "version=${status.termuxVersion}",
        )
        status.qemuBinaryNames.forEach { Log.i(TAG, "qemu_name=$it") }
    }

    internal fun loadProviderIdentity(
        context: Context,
        binaryName: String,
    ): CachedProviderIdentity {
        val prefs = context.getSharedPreferences(PROVIDER_PREFS, Context.MODE_PRIVATE)
        val providerApk = normalizeSha256(prefs.getString(KEY_PROVIDER_APK_PREF, null))
        val binaryDigest = runCatching {
            val json = JSONObject(prefs.getString(KEY_PROVIDER_QEMU_PREF, "{}") ?: "{}")
            if (json.has(binaryName)) normalizeSha256(json.optString(binaryName)) else null
        }.getOrNull()
        return CachedProviderIdentity(
            providerApkSha256 = providerApk,
            providerBinarySha256Discovery = binaryDigest,
            termuxVersion = prefs.getString(KEY_PROVIDER_VERSION_PREF, null),
            observedAtEpochMs = prefs.getLong(KEY_PROVIDER_OBSERVED_AT_PREF, -1L),
        )
    }

    private fun persistProviderIdentity(context: Context, status: IntegrationStatus) {
        val digestJson = JSONObject()
        status.qemuBinarySha256.toSortedMap().forEach { (name, digest) ->
            digestJson.put(name, digest)
        }
        context.getSharedPreferences(PROVIDER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_APK_PREF, status.providerApkSha256)
            .putString(KEY_PROVIDER_QEMU_PREF, digestJson.toString())
            .putString(KEY_PROVIDER_VERSION_PREF, status.termuxVersion)
            .putLong(KEY_PROVIDER_OBSERVED_AT_PREF, System.currentTimeMillis())
            .apply()
    }

    private fun parseBinaryDigests(
        values: Array<String>?,
        allowedNames: List<String>,
    ): Map<String, String> {
        val allowed = allowedNames.toSet()
        return values.orEmpty().mapNotNull { encoded ->
            val separator = encoded.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = encoded.substring(0, separator)
            val digest = normalizeSha256(encoded.substring(separator + 1))
            if (name !in allowed || digest == null) null else name to digest
        }.toMap()
    }

    private fun normalizeSha256(value: String?): String? = value
        ?.lowercase()
        ?.takeIf { SHA256_PATTERN.matches(it) }

    private fun emptyStatus(termuxInstalled: Boolean) = IntegrationStatus(
        termuxInstalled = termuxInstalled,
        bootstrapReady = false,
        protocolVersion = 0,
        qemuBinaryNames = emptyList(),
        qemuBinarySha256 = emptyMap(),
        providerApkSha256 = null,
        executionMode = null,
        runCommandPermission = null,
        privatePathsExposed = false,
        termuxVersion = null,
    )

    private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
}
