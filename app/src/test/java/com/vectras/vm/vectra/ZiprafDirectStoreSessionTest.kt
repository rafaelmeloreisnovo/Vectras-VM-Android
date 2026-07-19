package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZiprafDirectStoreSessionTest {
    @Test
    fun scan_visitsBoundedWindows_andCyclesAllEightLanes() {
        val file = File.createTempFile("zipraf-session", ".zip")
        try {
            val payload = ByteArray(1000) { it.toByte() }
            writeStoredArchive(file, "runtime/session.bin", payload)
            val plan = ZiprafRuntimePlan(256, 64, 512, 8)
            var checksum = 0L

            ZiprafDirectStoreSession.open(file, "runtime/session.bin", plan).use { session ->
                val result = session.scan(
                    stage = ZiprafMemoryStage.L1_HOT,
                    routeSeed = 0,
                    startOffset = 0,
                    maxBytes = 1000
                ) { window ->
                    assertTrue(window.bytes.isDirect)
                    while (window.bytes.hasRemaining()) {
                        checksum += window.bytes.get().toInt() and 0xff
                    }
                }

                assertEquals(1000L, result.bytesVisited)
                assertEquals(16, result.windowCount)
                assertEquals(0xff, result.laneMask)
                assertEquals(payload.sumOf { it.toInt() and 0xff }.toLong(), checksum)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun scan_respectsStartAndMaxBytes_withoutExposingExtraBytes() {
        val file = File.createTempFile("zipraf-session-range", ".zip")
        try {
            val payload = ByteArray(300) { (it * 3).toByte() }
            writeStoredArchive(file, "runtime/range.bin", payload)
            val observed = ArrayList<Byte>()

            ZiprafDirectStoreSession.open(
                file,
                "runtime/range.bin",
                ZiprafRuntimePlan(128, 64, 256, 4)
            ).use { session ->
                val result = session.scan(
                    stage = ZiprafMemoryStage.L1_HOT,
                    startOffset = 17,
                    maxBytes = 101
                ) { window ->
                    while (window.bytes.hasRemaining()) observed += window.bytes.get()
                }
                assertEquals(101L, result.bytesVisited)
                assertEquals(2, result.windowCount)
            }

            assertTrue(payload.copyOfRange(17, 118).contentEquals(observed.toByteArray()))
        } finally {
            file.delete()
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
}
