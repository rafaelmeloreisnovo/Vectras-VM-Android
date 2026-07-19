package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class ZiprafDirectRuntimeTest {
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

    private fun writeStoredEntry(
        file: File,
        name: String,
        payload: ByteArray,
        flags: Int = FLAG_UTF8
    ) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(payload) }.value
        val output = ByteBuffer.allocate(30 + nameBytes.size + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)

        output.putInt(LOCAL_FILE_HEADER_SIGNATURE)
        output.putShort(20.toShort())
        output.putShort(flags.toShort())
        output.putShort(ZiprafStoredExtent.STORE_METHOD.toShort())
        output.putShort(0)
        output.putShort(0)
        output.putInt(crc.toInt())
        output.putInt(payload.size)
        output.putInt(payload.size)
        output.putShort(nameBytes.size.toShort())
        output.putShort(0)
        output.put(nameBytes)
        output.put(payload)
        file.writeBytes(output.array())
    }

    companion object {
        private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        private const val FLAG_DATA_DESCRIPTOR = 1 shl 3
        private const val FLAG_UTF8 = 1 shl 11
    }
}
