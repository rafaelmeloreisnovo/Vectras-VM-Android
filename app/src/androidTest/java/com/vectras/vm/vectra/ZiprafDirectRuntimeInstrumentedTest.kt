package com.vectras.vm.vectra

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ZiprafDirectRuntimeInstrumentedTest {
    @Test
    fun validatedStore_roundTripsOnAndroidDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.cacheDir, "zipraf-device-${System.nanoTime()}.zip")
        try {
            val payload = ByteArray(128 * 1024) { (it * 17).toByte() }
            writeStoredArchive(archive, "runtime/device.bin", payload)

            val parsed = ZiprafArchiveValidator.parseStoredEntry(
                archive,
                expectedName = "runtime/device.bin"
            )
            assertEquals(ZiprafValidationLevel.CENTRAL_DIRECTORY, parsed.validationLevel)

            ZiprafDirectRuntime.openValidated(
                file = archive,
                entryName = "runtime/device.bin",
                plan = ZiprafRuntimePlan(
                    bufferBytes = 4096,
                    l1WindowBytes = 1024,
                    l2WindowBytes = 8192,
                    coreCount = 8
                )
            ).use { runtime ->
                val first = runtime.window(0, ZiprafMemoryStage.BUFFER, routeId = 0)
                val middle = runtime.window(65_535, ZiprafMemoryStage.L1_HOT, routeId = 17)
                val last = runtime.window(
                    payload.size.toLong() - 128,
                    ZiprafMemoryStage.L2_SHARED,
                    routeId = 7
                )

                assertEquals(payload[0].toInt() and 0xff, first.bytes.get(0).toInt() and 0xff)
                assertEquals(payload[65_535].toInt() and 0xff, middle.bytes.get(0).toInt() and 0xff)
                assertEquals(1, middle.coreLane)
                assertEquals(128, last.length)
                assertTrue(runtime.verifyCrc32())
            }
        } finally {
            archive.delete()
        }
    }

    @Test
    fun benchmarkHarness_recordsEvidence_withoutPerformanceClaim() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.cacheDir, "zipraf-bench-${System.nanoTime()}.zip")
        val report = File(context.cacheDir, "zipraf-bench-result.json")
        try {
            val payload = ByteArray(2 * 1024 * 1024) { (it xor (it ushr 8)).toByte() }
            writeStoredArchive(archive, "runtime/bench.bin", payload)
            val plan = ZiprafRuntimePlan(
                bufferBytes = 64 * 1024,
                l1WindowBytes = 4 * 1024,
                l2WindowBytes = 256 * 1024,
                coreCount = 8
            )

            var checksum = 0L
            val iterations = 256
            val started = System.nanoTime()
            ZiprafDirectRuntime.openValidated(archive, "runtime/bench.bin", plan).use { runtime ->
                repeat(iterations) { iteration ->
                    val offset = (iteration.toLong() * 7919L) % payload.size
                    val window = runtime.window(offset, ZiprafMemoryStage.L1_HOT, iteration.toLong())
                    checksum += window.bytes.get(0).toInt() and 0xff
                }
            }
            val elapsedNanos = System.nanoTime() - started

            val json = """
                {
                  "schema": "zipraf.android.benchmark.v1",
                  "claim_allowed": false,
                  "iterations": $iterations,
                  "payload_bytes": ${payload.size},
                  "elapsed_nanos": $elapsedNanos,
                  "checksum": $checksum,
                  "device_abi": "${android.os.Build.SUPPORTED_ABIS.joinToString(",")}",
                  "sdk_int": ${android.os.Build.VERSION.SDK_INT}
                }
            """.trimIndent()
            report.writeText(json)
            Log.i(TAG, json)

            assertTrue(elapsedNanos > 0)
            assertTrue(checksum > 0)
            assertTrue(report.isFile)
        } finally {
            archive.delete()
        }
    }

    private fun writeStoredArchive(file: File, name: String, payload: ByteArray) {
        val crc = CRC32().apply { update(payload) }.value
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
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

    companion object {
        private const val TAG = "ZIPRAF_DEVICE_KAT"
    }
}
