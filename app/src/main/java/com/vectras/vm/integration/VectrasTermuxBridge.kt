package com.vectras.vm.integration

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID

/**
 * Bounded dispatcher from Vectras to the external Termux RAFCODE-Phi runtime.
 *
 * A successful return means Android accepted the service dispatch. It does not
 * prove QEMU execution, exit code, guest boot or VM correctness.
 */
object VectrasTermuxBridge {

    const val EXECUTION_MODE = "run_command_service"

    private val allowedBinaries = setOf(
        "qemu-system-x86_64",
        "qemu-system-x86_64-rafaelia",
        "qemu-system-x86_64-rafacodephi",
        "qemu-system-aarch64",
        "qemu-system-aarch64-rafaelia",
        "qemu-system-i386",
        "qemu-system-arm",
    )

    enum class State {
        DISPATCHED,
        VM_NOT_REQUIRED,
        TERMUX_NOT_INSTALLED,
        PERMISSION_REQUIRED,
        INVALID_BINARY,
        INVALID_ARGUMENTS,
        SERVICE_UNAVAILABLE,
        REQUEST_PERSISTENCE_FAILED,
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
        val requestSha256: String? = null,
        val argumentCount: Int? = null,
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
        if (!background) {
            return DispatchResult(
                State.INVALID_ARGUMENTS,
                null,
                binaryName,
                null,
                reason = "app_shell_runner_required",
            )
        }
        if (binaryName !in allowedBinaries) {
            return DispatchResult(State.INVALID_BINARY, null, binaryName, null)
        }

        val boundedArguments = VectrasTermuxIpcContract.boundedArguments(arguments)
            ?: return DispatchResult(
                State.INVALID_ARGUMENTS,
                null,
                binaryName,
                null,
                reason = "arguments_outside_ipc_v3_contract",
            )

        if (!CrossRepoIntegrationManager.isTermuxInstalled(context)) {
            return DispatchResult(State.TERMUX_NOT_INSTALLED, null, binaryName, null)
        }
        if (!hasRunCommandPermission(context)) {
            return DispatchResult(
                State.PERMISSION_REQUIRED,
                null,
                binaryName,
                null,
                reason = VectrasTermuxIpcContract.RUN_COMMAND_PERMISSION,
            )
        }

        val transactionId = "tx-vectras-termux-${UUID.randomUUID()}"
        val requestSha256 = VectrasTermuxIpcContract.sha256(
            VectrasTermuxIpcContract.canonicalRequest(
                transactionId = transactionId,
                binaryName = binaryName,
                arguments = boundedArguments,
            ),
        )
        if (!VectrasTermuxReceiptStore.writePending(
                context = context,
                transactionId = transactionId,
                binaryName = binaryName,
                arguments = boundedArguments,
                requestSha256 = requestSha256,
            )
        ) {
            return DispatchResult(
                State.REQUEST_PERSISTENCE_FAILED,
                transactionId,
                binaryName,
                null,
                reason = "pending_request_not_persisted",
                requestSha256 = requestSha256,
                argumentCount = boundedArguments.size,
            )
        }

        val receiptIntent = Intent(context, VectrasTermuxResultReceiver::class.java).apply {
            action = VectrasTermuxResultReceiver.ACTION_EXECUTION_RESULT
            putExtra(VectrasTermuxResultReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(VectrasTermuxResultReceiver.EXTRA_BINARY_NAME, binaryName)
            putExtra(VectrasTermuxResultReceiver.EXTRA_REQUEST_SHA256, requestSha256)
        }
        // Termux fills the result Bundle into this explicit PendingIntent. On
        // Android 12+ this narrowly scoped token must therefore be mutable.
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
            component = ComponentName(
                VectrasTermuxIpcContract.TERMUX_PACKAGE,
                VectrasTermuxIpcContract.SERVICE_CLASS,
            )
            action = VectrasTermuxIpcContract.ACTION_RUN_COMMAND
            putExtra(
                VectrasTermuxIpcContract.EXTRA_COMMAND_PATH,
                VectrasTermuxIpcContract.commandPath(binaryName),
            )
            putExtra(
                VectrasTermuxIpcContract.EXTRA_ARGUMENTS,
                boundedArguments.toTypedArray(),
            )
            putExtra(
                VectrasTermuxIpcContract.EXTRA_WORKDIR,
                VectrasTermuxIpcContract.WORKDIR,
            )
            putExtra(
                VectrasTermuxIpcContract.EXTRA_RUNNER,
                VectrasTermuxIpcContract.RUNNER_APP_SHELL,
            )
            putExtra(
                VectrasTermuxIpcContract.EXTRA_PENDING_INTENT,
                resultPendingIntent,
            )
        }

        return try {
            val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            if (component == null) {
                DispatchResult(
                    State.SERVICE_UNAVAILABLE,
                    transactionId,
                    binaryName,
                    null,
                    reason = "service_component_null",
                    requestSha256 = requestSha256,
                    argumentCount = boundedArguments.size,
                )
            } else {
                DispatchResult(
                    state = State.DISPATCHED,
                    transactionId = transactionId,
                    binaryName = binaryName,
                    component = component.flattenToShortString(),
                    executionProven = false,
                    claimAllowed = false,
                    reason = "dispatch_accepted_execution_receipt_pending",
                    requestSha256 = requestSha256,
                    argumentCount = boundedArguments.size,
                )
            }
        } catch (exc: SecurityException) {
            DispatchResult(
                State.PERMISSION_REQUIRED,
                transactionId,
                binaryName,
                null,
                reason = exc.javaClass.simpleName,
                requestSha256 = requestSha256,
                argumentCount = boundedArguments.size,
            )
        } catch (exc: RuntimeException) {
            DispatchResult(
                State.ERROR,
                transactionId,
                binaryName,
                null,
                reason = exc.javaClass.simpleName,
                requestSha256 = requestSha256,
                argumentCount = boundedArguments.size,
            )
        }
    }

    fun allowedBinaryNames(): Set<String> = allowedBinaries.toSet()

    private fun hasRunCommandPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return context.checkSelfPermission(VectrasTermuxIpcContract.RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }
}
