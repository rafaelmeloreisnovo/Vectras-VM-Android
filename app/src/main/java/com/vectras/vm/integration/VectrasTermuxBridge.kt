package com.vectras.vm.integration

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

/**
 * Bounded dispatcher from Vectras to the external Termux RAFCODE-Φ runtime.
 *
 * A successful return means Android accepted the service dispatch. It does not
 * prove QEMU execution, exit code, guest boot or VM correctness.
 */
object VectrasTermuxBridge {

    const val EXECUTION_MODE = "run_command_service"

    private const val TERMUX_PACKAGE = "com.termux.rafacodephi"
    private const val SERVICE_CLASS = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "$TERMUX_PACKAGE.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "$TERMUX_PACKAGE.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "$TERMUX_PACKAGE.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "$TERMUX_PACKAGE.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "$TERMUX_PACKAGE.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_PENDING_INTENT = "$TERMUX_PACKAGE.RUN_COMMAND_PENDING_INTENT"
    private const val RUN_COMMAND_PERMISSION =
        "$TERMUX_PACKAGE.permission.RUN_COMMAND"

    private const val MAX_ARGUMENTS = 128
    private const val MAX_ARGUMENT_LENGTH = 4096

    private val allowedBinaries = setOf(
        "qemu-system-x86_64",
        "qemu-system-x86_64-rafaelia",
        "qemu-system-x86_64-rafacodephi",
        "qemu-system-aarch64",
        "qemu-system-aarch64-rafaelia",
        "qemu-system-i386",
    )

    enum class State {
        DISPATCHED,
        VM_NOT_REQUIRED,
        TERMUX_NOT_INSTALLED,
        PERMISSION_REQUIRED,
        INVALID_BINARY,
        INVALID_ARGUMENTS,
        SERVICE_UNAVAILABLE,
        ERROR,
    }

    data class DispatchResult(
        val state: State,
        val transactionId: String?,
        val binaryName: String?,
        val component: String?,
        val executionProven: Boolean = false,
        val claimAllowed: Boolean = false,
        val safeState: String = "vm-stopped-no-image-mutation",
        val reason: String? = null,
    )

    fun dispatchQemu(
        context: Context,
        binaryName: String,
        arguments: List<String>,
        vmRequired: Boolean,
        background: Boolean = true,
    ): DispatchResult {
        if (!vmRequired) {
            return DispatchResult(State.VM_NOT_REQUIRED, null, binaryName, null)
        }
        if (binaryName !in allowedBinaries) {
            return DispatchResult(State.INVALID_BINARY, null, binaryName, null)
        }
        if (!argumentsAreSafe(arguments)) {
            return DispatchResult(State.INVALID_ARGUMENTS, null, binaryName, null)
        }
        if (!CrossRepoIntegrationManager.isTermuxInstalled(context)) {
            return DispatchResult(State.TERMUX_NOT_INSTALLED, null, binaryName, null)
        }
        if (!hasRunCommandPermission(context)) {
            return DispatchResult(
                State.PERMISSION_REQUIRED,
                null,
                binaryName,
                null,
                reason = RUN_COMMAND_PERMISSION,
            )
        }

        val transactionId = "tx-vectras-termux-${UUID.randomUUID()}"
        val requestSha256 = sha256Request(binaryName, arguments)
        val receiptIntent = Intent(context, VectrasTermuxResultReceiver::class.java).apply {
            action = VectrasTermuxResultReceiver.ACTION_EXECUTION_RESULT
            putExtra(VectrasTermuxResultReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(VectrasTermuxResultReceiver.EXTRA_BINARY_NAME, binaryName)
            putExtra(VectrasTermuxResultReceiver.EXTRA_REQUEST_SHA256, requestSha256)
        }
        // Termux must fill the result Bundle into this explicit PendingIntent.
        // On Android 12+ this narrowly-scoped token must therefore be mutable.
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val resultPendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.hashCode(),
            receiptIntent,
            pendingFlags,
        )

        val intent = Intent().apply {
            component = ComponentName(TERMUX_PACKAGE, SERVICE_CLASS)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, "\$PREFIX/bin/$binaryName")
            putExtra(EXTRA_ARGUMENTS, arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, "~/")
            putExtra(EXTRA_BACKGROUND, background)
            putExtra(EXTRA_PENDING_INTENT, resultPendingIntent)
        }

        return try {
            val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            if (component == null) {
                DispatchResult(State.SERVICE_UNAVAILABLE, transactionId, binaryName, null)
            } else {
                DispatchResult(
                    state = State.DISPATCHED,
                    transactionId = transactionId,
                    binaryName = binaryName,
                    component = component.flattenToShortString(),
                    executionProven = false,
                    claimAllowed = false,
                    reason = "dispatch_accepted_execution_receipt_pending",
                )
            }
        } catch (exc: SecurityException) {
            DispatchResult(
                State.PERMISSION_REQUIRED,
                transactionId,
                binaryName,
                null,
                reason = exc.javaClass.simpleName,
            )
        } catch (exc: RuntimeException) {
            DispatchResult(
                State.ERROR,
                transactionId,
                binaryName,
                null,
                reason = exc.javaClass.simpleName,
            )
        }
    }

    fun allowedBinaryNames(): Set<String> = allowedBinaries.toSet()

    private fun sha256Request(binaryName: String, arguments: List<String>): String {
        val canonical = buildString {
            append(binaryName.length).append(':').append(binaryName).append('\n')
            arguments.forEach { argument ->
                append(argument.length).append(':').append(argument).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun hasRunCommandPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return context.checkSelfPermission(RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun argumentsAreSafe(arguments: List<String>): Boolean {
        if (arguments.size > MAX_ARGUMENTS) return false
        return arguments.all { argument ->
            argument.length <= MAX_ARGUMENT_LENGTH &&
                '\u0000' !in argument &&
                '\n' !in argument &&
                '\r' !in argument
        }
    }
}
