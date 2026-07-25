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
import java.util.UUID

/**
 * Discovers the external Termux RAFCODE-Φ runtime without trusting or exposing
 * another application's private sandbox paths.
 *
 * Protocol v2 returns capability names. Actual QEMU dispatch is performed by
 * [VectrasTermuxBridge] through the permission-gated RunCommandService.
 */
object CrossRepoIntegrationManager {

    private const val TAG = "CrossRepoIntegration"
    private const val QUERY_TIMEOUT_MS = 3_000L

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
    }

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

                val status = IntegrationStatus(
                    termuxInstalled = true,
                    bootstrapReady = intent.getBooleanExtra("bootstrap_ready", false),
                    protocolVersion = intent.getIntExtra("protocol_version", 0),
                    qemuBinaryNames = intent.getStringArrayExtra("qemu_binary_names")
                        ?.toList().orEmpty(),
                    executionMode = intent.getStringExtra("execution_mode"),
                    runCommandPermission = intent.getStringExtra("run_command_permission"),
                    privatePathsExposed =
                        intent.getBooleanExtra("private_paths_exposed", true),
                    termuxVersion = intent.getStringExtra("termux_version"),
                )
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
                "private_paths_exposed=${status.privatePathsExposed} " +
                "version=${status.termuxVersion}",
        )
        status.qemuBinaryNames.forEach { Log.i(TAG, "qemu_name=$it") }
    }

    private fun emptyStatus(termuxInstalled: Boolean) = IntegrationStatus(
        termuxInstalled = termuxInstalled,
        bootstrapReady = false,
        protocolVersion = 0,
        qemuBinaryNames = emptyList(),
        executionMode = null,
        runCommandPermission = null,
        privatePathsExposed = false,
        termuxVersion = null,
    )
}
