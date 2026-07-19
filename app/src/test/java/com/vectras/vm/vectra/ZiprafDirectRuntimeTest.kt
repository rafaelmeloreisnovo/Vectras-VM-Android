package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZiprafDirectRuntimeTest {

    // --- existing tests (preserved) ---

    @Test
    fun mappedStore_usesThreeStages_andEightLanes() {
        val file = File.createTempFile("zipraf-direct", ".bin")
        try {
            file.writeBytes(ByteArray(512) { it.toByte() })
            val plan = ZiprafRuntimePlan(64, 16, 128, 8)
            ZiprafDirectRuntime(file, ZiprafStoredExtent(32, 256), plan).use { runtime ->
                val l1 = runtime.window(31, ZiprafMemoryStage.L1_HOT, 17)
                val l2 = runtime.window(240, ZiprafMemoryStage.L2_SHARED, 17)
                assertEquals(16, l1.length)
                assertEquals(1, l1.coreLane)
                assertEquals(63, l1.bytes.get(0).toInt() and 0xff)
                assertEquals(16, l2.length)
                assertEquals(1, l2.coreLane)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localHeaderParser_derivesExtent_andVerifiesCrc32() {
        val file = File.createTempFile("zipraf-entry", ".zip")
        try {
            val payload = "RAFAELIA-ZIPRAF".toByteArray()
            writeStoredEntry(file, "runtime/core.bin", payload)

            val entry = ZiprafStoredEntryParser.parse(file, expectedName = "runtime/core.bin")
            assertEquals("runtime/core.bin", entry.name)
            assertEquals(payload.size.toLong(), entry.extent.payloadSize)
            assertEquals(
                30L + "runtime/core.bin".toByteArray().size,
                entry.extent.payloadOffset
            )

            ZiprafDirectRuntime(file, entry.extent).use { runtime ->
                assertTrue(runtime.verifyCrc32())
                val window = runtime.window(0, ZiprafMemoryStage.BUFFER)
                val actual = ByteArray(window.length)
                window.bytes.get(actual)
                assertTrue(payload.contentEquals(actual))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun crcMismatch_isDetected_afterPayloadMutation() {
        val file = File.createTempFile("zipraf-crc", ".zip")
        try {
            writeStoredEntry(file, "payload.bin", byteArrayOf(1, 2, 3, 4))
            val entry = ZiprafStoredEntryParser.parse(file)

            RandomAccessFile(file, "rw").use { randomAccess ->
                randomAccess.seek(entry.extent.payloadOffset)
                randomAccess.write(9)
            }

            ZiprafDirectRuntime(file, entry.extent).use { runtime ->
                assertFalse(runtime.verifyCrc32())
            }
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun dataDescriptorEntry_isRejected() {
        val file = File.createTempFile("zipraf-descriptor", ".zip")
        try {
            writeStoredEntry(
                file = file,
                name = "payload.bin",
                payload = byteArrayOf(1),
                flags = FLAG_UTF8 or FLAG_DATA_DESCRIPTOR
            )
            ZiprafStoredEntryParser.parse(file)
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun traversalEntryName_isRejected() {
        val file = File.createTempFile("zipraf-traversal", ".zip")
        try {
            writeStoredEntry(file, "../payload.bin", byteArrayOf(1))
            ZiprafStoredEntryParser.parse(file)
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedPayload_isRejected() {
        val file = File.createTempFile("zipraf-truncated", ".zip")
        try {
            writeStoredEntry(file, "payload.bin", byteArrayOf(1, 2, 3, 4))
            RandomAccessFile(file, "rw").use { randomAccess ->
                randomAccess.setLength(randomAccess.length() - 1)
            }
            ZiprafStoredEntryParser.parse(file)
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLengthExtent_isRejected() {
        val file = File.createTempFile("zipraf-empty", ".bin")
        try {
            file.writeBytes(byteArrayOf(1))
            ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 0)).close()
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonStoreMethod_isRejected() {
        val file = File.createTempFile("zipraf-deflate", ".bin")
        try {
            file.writeBytes(ByteArray(64))
            ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 64, 8)).close()
        } finally {
            file.delete()
        }
    }

    @Test
    fun fixedBits_neverMove() {
        val mask = 0xF00000000000000FuL.toLong()
        val fixed = 0xA000000000000005uL.toLong()
        val result = ZiprafDirectRuntime.preserveFixedBits(-1L, mask, fixed)
        assertTrue((result and mask) == (fixed and mask))
    }

    // --- new tests: extent-based mmap ---

    @Test
    fun extentMmap_mapsOnlyPayload_notWholeFile() {
        val file = File.createTempFile("zipraf-extent", ".bin")
        try {
            // File: 1024 bytes; extent covers bytes [256, 256+128)
            val content = ByteArray(1024) { (it % 256).toByte() }
            file.writeBytes(content)
            val extent = ZiprafStoredExtent(256L, 128L)
            ZiprafDirectRuntime(file, extent).use { rt ->
                val w = rt.window(0, ZiprafMemoryStage.BUFFER)
                // First byte in mapping corresponds to file[256] = 0 (256 % 256 == 0)
                assertEquals(0, w.bytes.get(0).toInt() and 0xff)
                val wEnd = rt.window(127, ZiprafMemoryStage.BUFFER)
                // byte at logicalOffset=127 → file[256+127=383] = 383 % 256 = 127
                assertEquals(127, wEnd.bytes.get(0).toInt() and 0xff)
            }
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun extentExceeding2GiB_isRejected() {
        val file = File.createTempFile("zipraf-overflow", ".bin")
        try {
            file.writeBytes(ByteArray(64))
            // payloadOffset + payloadSize > Int.MAX_VALUE
            ZiprafDirectRuntime(file, ZiprafStoredExtent(Int.MAX_VALUE.toLong(), 1L)).close()
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun windowBeyondExtent_isRejected() {
        val file = File.createTempFile("zipraf-oob", ".bin")
        try {
            file.writeBytes(ByteArray(256))
            ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 128)).use { rt ->
                rt.window(128, ZiprafMemoryStage.BUFFER) // logicalOffset == payloadSize, out of bounds
            }
        } finally { file.delete() }
    }

    // --- new tests: parseStoredExtent ---

    private fun createStoredZip(entryName: String, payload: ByteArray): File {
        val file = File.createTempFile("zipraf-parse", ".zip")
        ZipOutputStream(file.outputStream()).use { zos ->
            val entry = ZipEntry(entryName)
            entry.method = ZipEntry.STORED
            entry.size = payload.size.toLong()
            entry.compressedSize = payload.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(payload)
            entry.crc = crc.value
            zos.putNextEntry(entry)
            zos.write(payload)
            zos.closeEntry()
        }
        return file
    }

    @Test
    fun parseStoredExtent_returnsCorrectPayload() {
        val payload = ByteArray(200) { it.toByte() }
        val file = createStoredZip("data.bin", payload)
        try {
            val extent = ZiprafDirectRuntime.parseStoredExtent(file, "data.bin")
            ZiprafDirectRuntime(file, extent).use { rt ->
                val w = rt.window(0, ZiprafMemoryStage.BUFFER)
                // Verify payload is accessible through the extent
                assertEquals(0, w.bytes.get(0).toInt() and 0xff)
                assertEquals(1, w.bytes.get(1).toInt() and 0xff)
            }
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseStoredExtent_deflate_isRejected() {
        val file = File.createTempFile("zipraf-deflate", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zos ->
                zos.setMethod(ZipOutputStream.DEFLATED)
                val entry = ZipEntry("data.bin")
                zos.putNextEntry(entry)
                zos.write(ByteArray(100) { 0 })
                zos.closeEntry()
            }
            ZiprafDirectRuntime.parseStoredExtent(file, "data.bin")
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseStoredExtent_missingEntry_throws() {
        val payload = ByteArray(50) { 1 }
        val file = createStoredZip("actual.bin", payload)
        try {
            ZiprafDirectRuntime.parseStoredExtent(file, "nonexistent.bin")
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseStoredExtent_pathTraversal_isRejected() {
        val payload = ByteArray(50) { 1 }
        val file = createStoredZip("safe.bin", payload)
        try {
            ZiprafDirectRuntime.parseStoredExtent(file, "../etc/passwd")
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseStoredExtent_absolutePath_isRejected() {
        val payload = ByteArray(50) { 1 }
        val file = createStoredZip("safe.bin", payload)
        try {
            ZiprafDirectRuntime.parseStoredExtent(file, "/etc/passwd")
        } finally { file.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseStoredExtent_notAZip_throws() {
        val file = File.createTempFile("zipraf-notzip", ".bin")
        try {
            file.writeBytes(ByteArray(128) { 0xFF.toByte() })
            ZiprafDirectRuntime.parseStoredExtent(file, "data.bin")
        } finally { file.delete() }
    }

    // --- new tests: multi-window and close ---

    @Test
    fun multipleWindows_sameExtent_independentPositions() {
        val file = File.createTempFile("zipraf-multi", ".bin")
        try {
            file.writeBytes(ByteArray(512) { (it % 256).toByte() })
            ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 512)).use { rt ->
                val w0 = rt.window(0, ZiprafMemoryStage.BUFFER)
                val w100 = rt.window(100, ZiprafMemoryStage.L1_HOT)
                // Windows are independent slices
                assertEquals(0, w0.bytes.get(0).toInt() and 0xff)
                assertEquals(100, w100.bytes.get(0).toInt() and 0xff)
            }
        } finally { file.delete() }
    }

    @Test
    fun reopenAfterClose_succeedsWithNewInstance() {
        val file = File.createTempFile("zipraf-reopen", ".bin")
        try {
            file.writeBytes(ByteArray(128) { 42 })
            val extent = ZiprafStoredExtent(0, 128)
            ZiprafDirectRuntime(file, extent).close()
            ZiprafDirectRuntime(file, extent).use { rt ->
                assertEquals(42, rt.window(0, ZiprafMemoryStage.BUFFER).bytes.get(0).toInt() and 0xff)
            }
        } finally { file.delete() }
    }

    @Test
    fun preserveFixedBits_allPatterns() {
        assertEquals(0x05L, ZiprafDirectRuntime.preserveFixedBits(0L, 0x0FL, 0x05L))
        assertEquals(-1L and 0x0FL.inv() or 0x05L, ZiprafDirectRuntime.preserveFixedBits(-1L, 0x0FL, 0x05L))
    }
}
