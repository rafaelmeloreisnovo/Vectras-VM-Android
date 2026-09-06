package com.vectras.vm.integration

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Bounded dispatcher from Vectras to the external Termux RAFCODE-Phi runtime.
 *
 * A successful return means Android accepted the service dispatch. It does not
 * prove QEMU execution, exit code, package installation or guest correctness.
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
        val provenanceBound: Boolean = false,
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

        return dispatchBoundedCommand(
            context = context,
            targetName = binaryName,
            commandPath = VectrasTermuxIpcContract.commandPath(binaryName),
            arguments = boundedArguments,
            requestKind = "qemu",
            transactionPrefix = "tx-vectras-termux",
            transactionIdOverride = null,
        ) { transactionId ->
            VectrasTermuxIpcContract.canonicalRequest(
                transactionId = transactionId,
                binaryName = binaryName,
                arguments = boundedArguments,
            )
        }
    }

    fun dispatchMaintenance(
        context: Context,
        stage: VectrasTermuxIpcContract.MaintenanceStage,
        transactionId: String = newMaintenanceTransactionId(),
    ): DispatchResult {
        if (!TRANSACTION_PATTERN.matches(transactionId)) {
            return DispatchResult(
                State.INVALID_ARGUMENTS,
                transactionId,
                stage.targetName,
                null,
                reason = "invalid_maintenance_transaction_id",
            )
        }
        val boundedArguments = VectrasTermuxIpcContract.boundedMaintenanceArguments(stage)
            ?: return DispatchResult(
                State.INVALID_ARGUMENTS,
                transactionId,
                stage.targetName,
                null,
                reason = "maintenance_arguments_outside_ipc_v3_contract",
            )

        return dispatchBoundedCommand(
            context = context,
            targetName = stage.targetName,
            commandPath = stage.commandPath,
            arguments = boundedArguments,
            requestKind = "maintenance",
            transactionPrefix = "tx-vectras-maint",
            transactionIdOverride = transactionId,
        ) { resolvedTransactionId ->
            VectrasTermuxIpcContract.canonicalMaintenanceRequest(
                transactionId = resolvedTransactionId,
                stage = stage,
                arguments = boundedArguments,
            )
        }
    }

    fun newMaintenanceTransactionId(): String = "tx-vectras-maint-${UUID.randomUUID()}"

    private fun dispatchBoundedCommand(
        context: Context,
        targetName: String,
        commandPath: String,
        arguments: List<String>,
        requestKind: String,
        transactionPrefix: String,
        transactionIdOverride: String?,
        canonicalRequest: (String) -> String,
    ): DispatchResult {
        if (!CrossRepoIntegrationManager.isTermuxInstalled(context)) {
            return DispatchResult(State.TERMUX_NOT_INSTALLED, null, targetName, null)
        }
        if (!hasRunCommandPermission(context)) {
            return DispatchResult(
                State.PERMISSION_REQUIRED,
                null,
                targetName,
                null,
                reason = VectrasTermuxIpcContract.RUN_COMMAND_PERMISSION,
            )
        }

        val transactionId = transactionIdOverride ?: "$transactionPrefix-${UUID.randomUUID()}"
        val providerIdentity = CrossRepoIntegrationManager.loadProviderIdentity(context, targetName)
        val producerApkSha256 = context.applicationInfo.sourceDir
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.let(::sha256File)
        val provenanceBound = producerApkSha256 != null &&
            providerIdentity.providerApkSha256 != null &&
            providerIdentity.providerBinarySha256Discovery != null

        val requestSha256 = VectrasTermuxIpcContract.sha256(canonicalRequest(transactionId))
        if (!VectrasTermuxReceiptStore.writePending(
                context = context,
                transactionId = transactionId,
                binaryName = targetName,
                commandPath = commandPath,
                requestKind = requestKind,
                arguments = arguments,
                requestSha256 = requestSha256,
                producerApkSha256 = producerApkSha256,
                providerApkSha256Discovery = providerIdentity.providerApkSha256,
                providerBinarySha256Discovery = providerIdentity.providerBinarySha256Discovery,
                providerIdentityObservedAtEpochMs = providerIdentity.observedAtEpochMs,
                providerVersionDiscovery = providerIdentity.termuxVersion,
            )
        ) {
            return DispatchResult(
                State.REQUEST_PERSISTENCE_FAILED,
                transactionId,
                targetName,
                null,
                reason = "pending_request_not_persisted",
                requestSha256 = requestSha256,
                argumentCount = arguments.size,
                provenanceBound = provenanceBound,
            )
        }

        val receiptIntent = Intent(context, VectrasTermuxResultReceiver::class.java).apply {
            action = VectrasTermuxResultReceiver.ACTION_EXECUTION_RESULT
            putExtra(VectrasTermuxResultReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(VectrasTermuxResultReceiver.EXTRA_BINARY_NAME, targetName)
            putExtra(VectrasTermuxResultReceiver.EXTRA_REQUEST_SHA256, requestSha256)
        }
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
            putExtra(VectrasTermuxIpcContract.EXTRA_COMMAND_PATH, commandPath)
            putExtra(VectrasTermuxIpcContract.EXTRA_ARGUMENTS, arguments.toTypedArray())
            putExtra(VectrasTermuxIpcContract.EXTRA_WORKDIR, VectrasTermuxIpcContract.WORKDIR)
            putExtra(VectrasTermuxIpcContract.EXTRA_RUNNER, VectrasTermuxIpcContract.RUNNER_APP_SHELL)
            putExtra(VectrasTermuxIpcContract.EXTRA_PENDING_INTENT, resultPendingIntent)
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
                    targetName,
                    null,
                    reason = "service_component_null",
                    requestSha256 = requestSha256,
                    argumentCount = arguments.size,
                    provenanceBound = provenanceBound,
                )
            } else {
                DispatchResult(
                    state = State.DISPATCHED,
                    transactionId = transactionId,
                    binaryName = targetName,
                    component = component.flattenToShortString(),
                    executionProven = false,
                    claimAllowed = false,
                    reason = if (provenanceBound) {
                        "dispatch_accepted_discovery_identity_bound_execution_receipt_pending"
                    } else {
                        "dispatch_accepted_provenance_partial_execution_receipt_pending"
                    },
                    requestSha256 = requestSha256,
                    argumentCount = arguments.size,
                    provenanceBound = provenanceBound,
                )
            }
        } catch (exc: SecurityException) {
            DispatchResult(
                State.PERMISSION_REQUIRED,
                transactionId,
                targetName,
                null,
                reason = exc.javaClass.simpleName,
                requestSha256 = requestSha256,
                argumentCount = arguments.size,
                provenanceBound = provenanceBound,
            )
        } catch (exc: RuntimeException) {
            DispatchResult(
                State.ERROR,
                transactionId,
                targetName,
                null,
                reason = exc.javaClass.simpleName,
                requestSha256 = requestSha256,
                argumentCount = arguments.size,
                provenanceBound = provenanceBound,
            )
        }
    }

    fun allowedBinaryNames(): Set<String> = allowedBinaries.toSet()

    fun allowedReceiptTargets(): Set<String> = allowedBinaries + setOf(
        VectrasTermuxIpcContract.MAINTENANCE_RAFPROOT_TARGET,
        VectrasTermuxIpcContract.MAINTENANCE_PKG_TARGET,
    )

    private fun hasRunCommandPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return context.checkSelfPermission(VectrasTermuxIpcContract.RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun sha256File(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private val TRANSACTION_PATTERN = Regex("^[A-Za-z0-9._:-]{8,128}$")
}
