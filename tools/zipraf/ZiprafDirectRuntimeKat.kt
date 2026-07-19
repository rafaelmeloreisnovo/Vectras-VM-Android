package com.vectras.vm.vectra

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main() {
    val checks = linkedMapOf<String, Boolean>()
    val archive = File.createTempFile("zipraf-kat", ".zip")
    try {
        val first = ByteArray(4096) { (it * 31).toByte() }
        val second = "RAFAELIA-DIRECT-RUNTIME".toByteArray()
        writeStoredArchive(
            archive,
            linkedMapOf(
                "runtime/core.bin" to first,
                "runtime/meta.txt" to second
            )
        )

        val parsed = ZiprafArchiveValidator.parseStoredEntry(archive, "runtime/core.bin")
        checks["central_directory"] =
            parsed.validationLevel == ZiprafValidationLevel.CENTRAL_DIRECTORY
        checks["payload_size"] = parsed.extent.payloadSize == first.size.toLong()

        ZiprafDirectRuntime.openValidated(
            file = archive,
            entryName = "runtime/core.bin",
            plan = ZiprafRuntimePlan(
                bufferBytes = 512,
                l1WindowBytes = 64,
                l2WindowBytes = 1024,
                coreCount = 8
            )
        ).use { runtime ->
            val l1 = runtime.window(63, ZiprafMemoryStage.L1_HOT, routeId = 17)
            checks["window_length"] = l1.length == 64
            checks["lane"] = l1.coreLane == 1
            checks["window_content"] =
                (l1.bytes.get(0).toInt() and 0xff) == (first[63].toInt() and 0xff)
            checks["crc"] = runtime.verifyCrc32()
        }

        var scanChecksum = 0L
        ZiprafDirectStoreSession.open(
            archive,
            "runtime/core.bin",
            ZiprafRuntimePlan(512, 64, 1024, 8)
        ).use { session ->
            val scan = session.scan(
                stage = ZiprafMemoryStage.L1_HOT,
                routeSeed = 0,
                startOffset = 17,
                maxBytes = 1000
            ) { window ->
                while (window.bytes.hasRemaining()) {
                    scanChecksum += window.bytes.get().toInt() and 0xff
                }
            }
            checks["scan_bytes"] = scan.bytesVisited == 1000L
            checks["scan_windows"] = scan.windowCount == 16
            checks["scan_lanes"] = scan.laneMask == 0xff
            checks["scan_checksum"] = scanChecksum == first.copyOfRange(17, 1017)
                .sumOf { it.toInt() and 0xff }.toLong()
        }

        checks["missing_entry_rejected"] = runCatching {
            ZiprafArchiveValidator.parseStoredEntry(archive, "missing.bin")
        }.isFailure

        val passed = checks.values.all { it }
        println(buildJson(checks, passed))
        check(passed) { "ZIPRAF standalone KAT failed" }
    } finally {
        archive.delete()
    }
}

private fun writeStoredArchive(file: File, entries: LinkedHashMap<String, ByteArray>) {
    ZipOutputStream(file.outputStream().buffered()).use { zip ->
        entries.forEach { (name, payload) ->
            val crc = CRC32().apply { update(payload) }.value
            val entry = ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = payload.size.toLong()
                compressedSize = payload.size.toLong()
                this.crc = crc
            }
            zip.putNextEntry(entry)
            zip.write(payload)
            zip.closeEntry()
        }
    }
}

private fun buildJson(checks: Map<String, Boolean>, passed: Boolean): String = buildString {
    append("{\n  \"schema\": \"zipraf.kat.v1\",\n  \"status\": \"")
    append(if (passed) "PASS" else "FAIL")
    append("\",\n  \"checks\": {\n")
    checks.entries.forEachIndexed { index, (name, value) ->
        append("    \"").append(name).append("\": ").append(value)
        if (index + 1 < checks.size) append(',')
        append('\n')
    }
    append("  }\n}")
}
