package com.vectras.vm.rafaelia

import android.content.Context
import android.util.Log
import com.vectras.vm.telemetry.TelemetryHub
import com.vectras.vm.telemetry.TelemetryRecord
import com.vectras.vm.vectra.VectraBitStackLog
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RafaeliaEventRecorder {
    private const val TAG = "RafaeliaEvents"
    private val lock = ReentrantLock()
    private var logger: VectraBitStackLog? = null

    private fun ensureLogger(context: Context): VectraBitStackLog? {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return null
        lock.withLock {
            if (logger == null) {
                logger = VectraBitStackLog(RafaeliaSettings.bitStackFile(context))
            }
        }
        return logger
    }

    @JvmStatic
    fun recordStart(context: Context, vmName: String) {
        ensureOptionalCsvExports(context)
        record(context, "start", JSONObject().put("vm", vmName))
    }

    @JvmStatic
    fun recordStop(context: Context, vmName: String) {
        record(context, "stop", JSONObject().put("vm", vmName))
        traceAllEngines(context, "stopped", JSONObject().put("phase", "vm_stop"))
        snapshot(context)
    }

    @JvmStatic
    fun recordCrash(context: Context, reason: String) {
        record(context, "crash", JSONObject().put("reason", reason))
        traceAllEngines(context, "error", JSONObject().put("phase", "vm_crash"))
        snapshot(context)
    }

    @JvmStatic
    fun recordRecoverable(context: Context, category: String, details: String) {
        record(context, "recoverable", JSONObject().put("category", category).put("details", details))
    }

    @JvmStatic
    fun recordBench(context: Context, report: RafaeliaBenchReport, vmName: String) {
        val payload = report.toJson().put("vm", vmName).put("event", "bench")
        record(context, "bench", payload)
        snapshot(context)
    }

    @JvmStatic
    fun snapshot(context: Context): File? {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return null
        val source = RafaeliaSettings.bitStackFile(context)
        if (!source.exists()) return null
        val snapshot = File(RafaeliaSettings.rafaeliaDir(context), "event_snapshot_${timestamp()}.bin")
        source.copyTo(snapshot, overwrite = true)
        exportJsonSnapshot(context)
        return snapshot
    }

    @JvmStatic
    fun exportJsonSnapshot(context: Context): File? {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return null
        val source = RafaeliaSettings.bitStackJsonlFile(context)
        if (!source.exists()) return null
        val snapshot = File(RafaeliaSettings.rafaeliaDir(context), "event_snapshot_${timestamp()}.json")
        source.copyTo(snapshot, overwrite = true)
        return snapshot
    }

    private fun record(context: Context, type: String, payload: JSONObject) {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return
        payload.put("event", type)
        payload.put("timestamp", System.currentTimeMillis())
        val logger = ensureLogger(context)
        val bytes = payload.toString().toByteArray()
        logger?.append(bytes, type.hashCode())
        appendJsonl(context, payload)
        val telemetryPayload = JSONObject(payload.toString())
        telemetryPayload.put("source", "rafaelia")
        val sink = TelemetryHub.get(context)
        sink.publish(TelemetryRecord.event(type, telemetryPayload))
        Log.d(TAG, "recorded event=$type")
    }

    private fun appendJsonl(context: Context, payload: JSONObject) {
        val file = RafaeliaSettings.bitStackJsonlFile(context)
        FileWriter(file, true).use { writer ->
            writer.append(payload.toString()).append("\n")
        }
    }

    private fun traceAllEngines(context: Context, event: String, payload: JSONObject) {
        RafaeliaCsvTraceExporter.trace(context, "geolm", event, payload)
        RafaeliaCsvTraceExporter.trace(context, "geoia", event, payload)
        RafaeliaCsvTraceExporter.trace(context, "uniao", event, payload)
        RafaeliaCsvTraceExporter.trace(context, "vectra_qemu", event, payload)
    }

    private fun ensureOptionalCsvExports(context: Context) {
        val payload = JSONObject().put("phase", "init")
        RafaeliaCsvTraceExporter.trace(context, "geolm", "preflight", payload)
        RafaeliaCsvTraceExporter.trace(context, "geoia", "preflight", payload)
        RafaeliaCsvTraceExporter.trace(context, "uniao", "preflight", payload)
        RafaeliaCsvTraceExporter.trace(context, "vectra_qemu", "preflight", payload)
    }

    private fun timestamp(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return formatter.format(Date())
    }
}
