package com.vectras.vm.rafaelia

import android.content.Context
import com.vectras.vm.core.LowLevelBridge
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RafaeliaCsvTraceExporter {
    private val lock = ReentrantLock()
    private val validEvents = setOf(
        "preflight",
        "qemu_binary_resolved",
        "proot_started",
        "qmp_ready",
        "vnc_ready",
        "x11_ready",
        "boot_ready",
        "stopped",
        "error"
    )

    @JvmStatic
    fun trace(context: Context, engine: String, event: String, payload: JSONObject = JSONObject()) {
        if (event !in validEvents) return
        val outDir = File(RafaeliaSettings.rafaeliaDir(context), "out")
        if (!outDir.exists()) {
            outDir.mkdirs()
        }
        val file = File(outDir, "${engine}_trace.csv")
        lock.withLock {
            ensureHeader(file)
            val ts = System.currentTimeMillis()
            val commandDigest = commandContractDigest(payload.optString("command_contract", ""))
            val phase = payload.optString("phase", "")
            val cMilli = payload.optInt("C_milli", -1)
            val hMilli = payload.optInt("H_milli", -1)
            val phiMilli = payload.optInt("phi_milli", -1)
            val line = buildString {
                append(ts).append(',')
                append(engine).append(',')
                append(event).append(',')
                append(commandDigest).append(',')
                append(escapeCsv(phase)).append(',')
                append(cMilli).append(',')
                append(hMilli).append(',')
                append(phiMilli).append('\n')
            }
            FileWriter(file, true).use { it.append(line) }
        }
    }

    private fun ensureHeader(file: File) {
        if (file.exists()) return
        FileWriter(file, false).use {
            it.append("timestamp_ms,engine,event,command_contract_crc32c,phase,C_milli,H_milli,phi_milli\n")
        }
    }

    private fun commandContractDigest(commandContract: String): String {
        if (commandContract.isEmpty()) return ""
        val data = commandContract.toByteArray(StandardCharsets.UTF_8)
        val crc = LowLevelBridge.crc32cCompat(0, data, 0, data.size)
        return java.lang.Long.toHexString(crc.toLong() and 0xFFFFFFFFL).padStart(8, '0')
    }

    private fun escapeCsv(raw: String): String {
        if (!raw.contains(',') && !raw.contains('"')) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }
}
