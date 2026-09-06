package com.vectras.vm.integration

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.vectras.vm.R
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receipt-driven maintenance state machine for an installed RAFCODEPHI Termux.
 *
 * Fixed sequence, no free-form commands:
 * BOOTSTRAP -> REFRESH -> VECTRAS_QEMU -> PROBE -> PROOT_VERIFY -> NINJA_VERIFY -> QEMU_VERIFY.
 * A stage advances only after the local PendingIntent result is bound to the
 * persisted request and reports Termux errorCode=0 plus process exitCode=0.
 */
object VectrasTermuxMaintenanceCoordinator {

    private const val TAG = "VectrasTermuxMaint"
    private const val PREFS = "vectras_termux_maintenance_v1"
    private const val KEY_RUN_ID = "run_id"
    private const val KEY_STATE = "state"
    private const val KEY_EXPECTED_STAGE = "expected_stage"
    private const val KEY_EXPECTED_TRANSACTION = "expected_transaction"
    private const val KEY_LAST_REASON = "last_reason"
    private const val KEY_UPDATED_AT = "updated_at_epoch_ms"
    private const val MAX_IN_FLIGHT_AGE_MS = 15L * 60L * 1000L

    private val lock = Any()
    private val dialogVisible = AtomicBoolean(false)

    enum class State {
        IDLE,
        BOOTSTRAP_PENDING,
        REFRESH_PENDING,
        VECTRAS_QEMU_PENDING,
        PROBE_PENDING,
        PROOT_VERIFY_PENDING,
        NINJA_VERIFY_PENDING,
        QEMU_VERIFY_PENDING,
        COMPLETE,
        FAILED,
    }

    data class Snapshot(
        val runId: String?,
        val state: State,
        val expectedStage: VectrasTermuxIpcContract.MaintenanceStage?,
        val expectedTransaction: String?,
        val lastReason: String?,
        val updatedAtEpochMs: Long,
    ) {
        val inProgress: Boolean
            get() = state in setOf(
                State.BOOTSTRAP_PENDING,
                State.REFRESH_PENDING,
                State.VECTRAS_QEMU_PENDING,
                State.PROBE_PENDING,
                State.PROOT_VERIFY_PENDING,
                State.NINJA_VERIFY_PENDING,
                State.QEMU_VERIFY_PENDING,
            )
    }

    @JvmStatic
    fun offerRepairIfNeeded(activity: Activity) {
        if (activity.isFinishing || !CrossRepoIntegrationManager.isTermuxInstalled(activity)) return
        CrossRepoIntegrationManager.queryIntegration(activity.applicationContext) { status ->
            if (!status.termuxInstalled || status.isFullyReady || activity.isFinishing) return@queryIntegration
            if (!dialogVisible.compareAndSet(false, true)) return@queryIntegration
            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing) {
                    dialogVisible.set(false)
                    return@post
                }
                val snapshot = snapshot(activity)
                val message = if (snapshot.inProgress) {
                    "RAFCODEPHI runtime repair is already in progress at ${snapshot.expectedStage ?: snapshot.state}."
                } else {
                    "The external RAFCODEPHI Termux runtime is installed but not fully ready. " +
                        "Repair will bootstrap the toolchain, refresh package metadata, install Vectras QEMU, " +
                        "then execute PRoot, Ninja and QEMU smoke checks before completion."
                }
                AlertDialog.Builder(activity, R.style.MainDialogTheme)
                    .setTitle("Repair RAFCODEPHI runtime")
                    .setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton(if (snapshot.inProgress) "OK" else "Repair") { dialog, _ ->
                        if (!snapshot.inProgress) {
                            val result = startRepair(activity.applicationContext)
                            if (result.state != VectrasTermuxBridge.State.DISPATCHED) {
                                Log.w(TAG, "repair dispatch rejected state=${result.state} reason=${result.reason}")
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .setOnDismissListener { dialogVisible.set(false) }
                    .show()
            }
        }
    }

    @JvmStatic
    fun startRepair(context: Context): VectrasTermuxBridge.DispatchResult = synchronized(lock) {
        val appContext = context.applicationContext
        val current = snapshot(appContext)
        if (current.inProgress && !isStale(current)) {
            return@synchronized VectrasTermuxBridge.DispatchResult(
                state = VectrasTermuxBridge.State.ERROR,
                transactionId = current.expectedTransaction,
                binaryName = current.expectedStage?.targetName,
                component = null,
                reason = "maintenance_already_in_progress",
            )
        }

        val runId = "maint-${UUID.randomUUID()}"
        dispatchStageLocked(
            context = appContext,
            runId = runId,
            stage = VectrasTermuxIpcContract.MaintenanceStage.BOOTSTRAP,
        )
    }

    internal fun onExecutionResult(
        context: Context,
        pending: VectrasTermuxReceiptStore.PendingRequest,
        resultBundlePresent: Boolean,
        exitCode: Int?,
        errorCode: Int?,
    ) {
        if (pending.requestKind != "maintenance") return
        val stage = VectrasTermuxIpcContract.maintenanceStageFrom(
            targetName = pending.binaryName,
            commandPath = pending.commandPath,
            arguments = pending.arguments,
        ) ?: run {
            Log.w(TAG, "ignoring unrecognized maintenance request tx=${pending.transactionId}")
            return
        }

        synchronized(lock) {
            val current = snapshot(context)
            if (!current.inProgress ||
                current.expectedStage != stage ||
                current.expectedTransaction != pending.transactionId
            ) {
                Log.w(
                    TAG,
                    "ignoring stale/out-of-order maintenance receipt tx=${pending.transactionId} stage=$stage " +
                        "expectedTx=${current.expectedTransaction} expectedStage=${current.expectedStage}",
                )
                return
            }

            val succeeded = resultBundlePresent && errorCode == 0 && exitCode == 0
            if (!succeeded) {
                persistOrLog(
                    context = context,
                    runId = current.runId,
                    state = State.FAILED,
                    expectedStage = null,
                    expectedTransaction = null,
                    reason = "stage_${stage.name.lowercase()}_failed:" +
                        "bundle=$resultBundlePresent:error=${errorCode ?: "TOKEN_VAZIO"}:" +
                        "exit=${exitCode ?: "TOKEN_VAZIO"}",
                )
                return
            }

            val next = nextStage(stage)
            if (next == null) {
                persistOrLog(
                    context = context,
                    runId = current.runId,
                    state = State.COMPLETE,
                    expectedStage = null,
                    expectedTransaction = null,
                    reason = "bounded_maintenance_and_runtime_smokes_complete",
                )
                CrossRepoIntegrationManager.queryIntegration(context.applicationContext) { status ->
                    CrossRepoIntegrationManager.logStatus(status)
                }
                return
            }

            val runId = current.runId ?: "maint-recovered-${UUID.randomUUID()}"
            dispatchStageLocked(context.applicationContext, runId, next)
        }
    }

    @JvmStatic
    fun snapshot(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = runCatching { State.valueOf(prefs.getString(KEY_STATE, State.IDLE.name) ?: State.IDLE.name) }
            .getOrDefault(State.IDLE)
        val stage = prefs.getString(KEY_EXPECTED_STAGE, null)?.let { name ->
            runCatching { VectrasTermuxIpcContract.MaintenanceStage.valueOf(name) }.getOrNull()
        }
        return Snapshot(
            runId = prefs.getString(KEY_RUN_ID, null),
            state = state,
            expectedStage = stage,
            expectedTransaction = prefs.getString(KEY_EXPECTED_TRANSACTION, null),
            lastReason = prefs.getString(KEY_LAST_REASON, null),
            updatedAtEpochMs = prefs.getLong(KEY_UPDATED_AT, -1L),
        )
    }

    private fun dispatchStageLocked(
        context: Context,
        runId: String,
        stage: VectrasTermuxIpcContract.MaintenanceStage,
    ): VectrasTermuxBridge.DispatchResult {
        val transactionId = VectrasTermuxBridge.newMaintenanceTransactionId()
        val statePersisted = persist(
            context = context,
            runId = runId,
            state = pendingState(stage),
            expectedStage = stage,
            expectedTransaction = transactionId,
            reason = "dispatch_pending",
        )
        if (!statePersisted) {
            return VectrasTermuxBridge.DispatchResult(
                state = VectrasTermuxBridge.State.REQUEST_PERSISTENCE_FAILED,
                transactionId = transactionId,
                binaryName = stage.targetName,
                component = null,
                reason = "maintenance_state_not_persisted",
            )
        }

        val result = VectrasTermuxBridge.dispatchMaintenance(
            context = context,
            stage = stage,
            transactionId = transactionId,
        )
        if (result.state != VectrasTermuxBridge.State.DISPATCHED) {
            persistOrLog(
                context = context,
                runId = runId,
                state = State.FAILED,
                expectedStage = null,
                expectedTransaction = null,
                reason = "dispatch_${stage.name.lowercase()}_${result.state.name.lowercase()}:" +
                    (result.reason ?: "TOKEN_VAZIO"),
            )
        }
        return result
    }

    private fun nextStage(
        stage: VectrasTermuxIpcContract.MaintenanceStage,
    ): VectrasTermuxIpcContract.MaintenanceStage? = when (stage) {
        VectrasTermuxIpcContract.MaintenanceStage.BOOTSTRAP ->
            VectrasTermuxIpcContract.MaintenanceStage.REFRESH
        VectrasTermuxIpcContract.MaintenanceStage.REFRESH ->
            VectrasTermuxIpcContract.MaintenanceStage.VECTRAS_QEMU
        VectrasTermuxIpcContract.MaintenanceStage.VECTRAS_QEMU ->
            VectrasTermuxIpcContract.MaintenanceStage.PROBE
        VectrasTermuxIpcContract.MaintenanceStage.PROBE ->
            VectrasTermuxIpcContract.MaintenanceStage.PROOT_VERIFY
        VectrasTermuxIpcContract.MaintenanceStage.PROOT_VERIFY ->
            VectrasTermuxIpcContract.MaintenanceStage.NINJA_VERIFY
        VectrasTermuxIpcContract.MaintenanceStage.NINJA_VERIFY ->
            VectrasTermuxIpcContract.MaintenanceStage.QEMU_VERIFY
        VectrasTermuxIpcContract.MaintenanceStage.QEMU_VERIFY -> null
    }

    private fun pendingState(stage: VectrasTermuxIpcContract.MaintenanceStage): State = when (stage) {
        VectrasTermuxIpcContract.MaintenanceStage.BOOTSTRAP -> State.BOOTSTRAP_PENDING
        VectrasTermuxIpcContract.MaintenanceStage.REFRESH -> State.REFRESH_PENDING
        VectrasTermuxIpcContract.MaintenanceStage.VECTRAS_QEMU -> State.VECTRAS_QEMU_PENDING
        VectrasTermuxIpcContract.MaintenanceStage.PROBE -> State.PROBE_PENDING
        VectrasTermuxIpcContract.MaintenanceStage.PROOT_VERIFY -> State.PROOT_VERIFY_PENDING
        VectrasTermuxIpcContract.MaintenanceStage.NINJA_VERIFY -> State.NINJA_VERIFY_PENDING
        VectrasTermuxIpcContract.MaintenanceStage.QEMU_VERIFY -> State.QEMU_VERIFY_PENDING
    }

    private fun isStale(snapshot: Snapshot): Boolean {
        if (!snapshot.inProgress || snapshot.updatedAtEpochMs <= 0L) return false
        return System.currentTimeMillis() - snapshot.updatedAtEpochMs > MAX_IN_FLIGHT_AGE_MS
    }

    private fun persistOrLog(
        context: Context,
        runId: String?,
        state: State,
        expectedStage: VectrasTermuxIpcContract.MaintenanceStage?,
        expectedTransaction: String?,
        reason: String?,
    ) {
        if (!persist(context, runId, state, expectedStage, expectedTransaction, reason)) {
            Log.e(TAG, "failed to persist terminal maintenance state=$state run=$runId reason=$reason")
        }
    }

    private fun persist(
        context: Context,
        runId: String?,
        state: State,
        expectedStage: VectrasTermuxIpcContract.MaintenanceStage?,
        expectedTransaction: String?,
        reason: String?,
    ): Boolean {
        val committed = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RUN_ID, runId)
            .putString(KEY_STATE, state.name)
            .putString(KEY_EXPECTED_STAGE, expectedStage?.name)
            .putString(KEY_EXPECTED_TRANSACTION, expectedTransaction)
            .putString(KEY_LAST_REASON, reason)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()
        Log.i(
            TAG,
            "run=$runId state=$state stage=${expectedStage ?: "none"} tx=${expectedTransaction ?: "none"} " +
                "reason=$reason persisted=$committed",
        )
        return committed
    }
}
