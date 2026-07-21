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

/**
 * CrossRepoIntegrationManager — discovers Vectras ecosystem siblings at runtime.
 *
 * Queries [TERMUX_PACKAGE] for bootstrap readiness and available QEMU binary paths.
 * If termux-app-rafacodephi is installed and its bootstrap is ready, the returned
 * [IntegrationStatus.qemuBinaryPaths] can be fed directly into QemuBinaryResolver
 * as additional search paths.
 *
 * Closes X5 (cross-repo Vectras↔qemu_rafaelia↔termux integration consumer).
 */
object CrossRepoIntegrationManager {

    private const val TAG = "CrossRepoIntegration"
    private const val QUERY_TIMEOUT_MS = 3_000L

    const val TERMUX_PACKAGE = "com.termux.rafacodephi"
    private const val ACTION_QUERY = "com.vectras.vm.ACTION_QUERY_INTEGRATION"
    private const val ACTION_RESPONSE = "com.vectras.vm.ACTION_INTEGRATION_RESPONSE"

    data class IntegrationStatus(
        val termuxInstalled: Boolean,
        val bootstrapReady: Boolean,
        val prefixPath: String?,
        val qemuBinaryPaths: List<String>,
        val termuxVersion: String?
    ) {
        val isFullyReady: Boolean
            get() = termuxInstalled && bootstrapReady && qemuBinaryPaths.isNotEmpty()
    }

    /**
     * Asynchronously queries termux-app-rafacodephi for integration data.
     *
     * [onResult] is called on the main thread, either when the response arrives or after
     * [QUERY_TIMEOUT_MS] ms with whatever data is available.
     */
    fun queryIntegration(context: Context, onResult: (IntegrationStatus) -> Unit) {
        if (!isTermuxInstalled(context)) {
            onResult(IntegrationStatus(false, false, null, emptyList(), null))
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var responded = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_RESPONSE || responded) return
                responded = true
                runCatching { ctx.unregisterReceiver(this) }

                val status = IntegrationStatus(
                    termuxInstalled = true,
                    bootstrapReady = intent.getBooleanExtra("bootstrap_ready", false),
                    prefixPath = intent.getStringExtra("prefix_path"),
                    qemuBinaryPaths = intent.getStringArrayExtra("qemu_binary_paths")
                        ?.toList() ?: emptyList(),
                    termuxVersion = intent.getStringExtra("termux_version")
                )
                mainHandler.post { onResult(status) }
            }
        }

        val filter = IntentFilter(ACTION_RESPONSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        mainHandler.postDelayed({
            if (!responded) {
                responded = true
                runCatching { context.unregisterReceiver(receiver) }
                Log.w(TAG, "termux did not respond within ${QUERY_TIMEOUT_MS}ms; assuming bootstrap not ready")
                onResult(IntegrationStatus(true, false, null, emptyList(), null))
            }
        }, QUERY_TIMEOUT_MS)

        context.sendBroadcast(Intent(ACTION_QUERY).setPackage(TERMUX_PACKAGE))
    }

    fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun logStatus(status: IntegrationStatus) {
        Log.i(TAG, "termux_installed=${status.termuxInstalled} " +
            "bootstrap_ready=${status.bootstrapReady} " +
            "qemu_count=${status.qemuBinaryPaths.size} " +
            "version=${status.termuxVersion}")
        status.qemuBinaryPaths.forEach { Log.i(TAG, "  qemu=$it") }
    }
}
