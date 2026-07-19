package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun archiveValidator_crossChecksCentralDirectory_andOpensRuntime() {
        val file = File.createTempFile("zipraf-archive", ".zip")
        try {
            val payload = "RAFAELIA-ZIPRAF".toByteArray()
            writeArchive(file, listOf(TestEntry("runtime/core.bin", payload)))

            val entry = ZiprafArchiveValidator.parseStoredEntry(file, "runtime/core.bin")
            assertEquals(ZiprafValidationLevel.CENTRAL_DIRECTORY, entry.validationLevel)
            assertEquals(payload.size.toLong(), entry.extent.payloadSize)

            ZiprafDirectRuntime.openValidated(file, "runtime/core.bin").use { runtime ->
                val window = runtime.window(0, ZiprafMemoryStage.BUFFER)
                val actual = ByteArray(window.length)
                window.bytes.get(actual)
                assertTrue(payload.contentEquals(actual))
                assertTrue(runtime.verifyCrc32())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localHeaderParser_remainsAvailable_asLowLevelBoundary() {
        val file = File.createTempFile("zipraf-local", ".bin")
        try {
            val payload = byteArrayOf(7, 8, 9)
            writeLocalRecordOnly(file, TestEntry("payload.bin", payload))
            val entry = ZiprafStoredEntryParser.parse(file, expectedName = "payload.bin")
            assertEquals(ZiprafValidationLevel.LOCAL_HEADER, entry.validationLevel)
            assertEquals(payload.size.toLong(), entry.extent.payloadSize)
        } finally {
            file.delete()
        }
    }

    @Test
    fun crcMismatch_isDetected_afterPayloadMutation() {
        val file = File.createTempFile("zipraf-crc", ".zip")
        try {
            writeArchive(file, listOf(TestEntry("payload.bin", byteArrayOf(1, 2, 3, 4))))
            val entry = ZiprafArchiveValidator.parseStoredEntry(file, "payload.bin")

            RandomAccessFile(file, "rw").use { randomAccess ->
                randomAccess.seek(entry.extent.payloadOffset)
                randomAccess.write(9)
            }

            ZiprafDirectRuntime(file, entry.extent).use { runtime ->
                assertFalse(runtime.verifyCrc32())
            }
            expectFailure<IllegalArgumentException> {
                ZiprafDirectRuntime.openValidated(file, "payload.bin").close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun centralDirectoryCrcMismatch_isRejected() {
        val file = File.createTempFile("zipraf-central-crc", ".zip")
        try {
            val payload = byteArrayOf(1, 2, 3)
            val actualCrc = crc32(payload)
            writeArchive(
                file,
                listOf(TestEntry("payload.bin", payload, centralCrc = actualCrc xor 1L))
            )
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "payload.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun centralDirectorySizeMismatch_isRejected() {
        val file = File.createTempFile("zipraf-central-size", ".zip")
        try {
            writeArchive(
                file,
                listOf(TestEntry("payload.bin", byteArrayOf(1, 2, 3), centralSize = 2))
            )
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "payload.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun duplicateCentralDirectoryName_isRejected() {
        val file = File.createTempFile("zipraf-duplicate", ".zip")
        try {
            writeArchive(
                file,
                listOf(
                    TestEntry("same.bin", byteArrayOf(1)),
                    TestEntry("same.bin", byteArrayOf(2))
                )
            )
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "same.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun expectedName_isRequired_whenArchiveHasMultipleEntries() {
        val file = File.createTempFile("zipraf-multiple", ".zip")
        try {
            writeArchive(
                file,
                listOf(
                    TestEntry("a.bin", byteArrayOf(1)),
                    TestEntry("b.bin", byteArrayOf(2))
                )
            )
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingEntry_isRejected() {
        val file = File.createTempFile("zipraf-missing", ".zip")
        try {
            writeArchive(file, listOf(TestEntry("present.bin", byteArrayOf(1))))
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "missing.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun multiDiskArchive_isRejected() {
        val file = File.createTempFile("zipraf-multidisk", ".zip")
        try {
            writeArchive(
                file,
                listOf(TestEntry("payload.bin", byteArrayOf(1))),
                eocdDiskNumber = 1
            )
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "payload.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun dataDescriptorEntry_isRejected() {
        val file = File.createTempFile("zipraf-descriptor", ".zip")
        try {
            writeArchive(
                file,
                listOf(
                    TestEntry(
                        name = "payload.bin",
                        payload = byteArrayOf(1),
                        flags = FLAG_UTF8 or FLAG_DATA_DESCRIPTOR
                    )
                )
            )
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "payload.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun traversalEntryName_isRejected() {
        val file = File.createTempFile("zipraf-traversal", ".zip")
        try {
            writeArchive(file, listOf(TestEntry("../payload.bin", byteArrayOf(1))))
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "../payload.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatedLocalPayload_isRejected() {
        val file = File.createTempFile("zipraf-truncated", ".bin")
        try {
            writeLocalRecordOnly(file, TestEntry("payload.bin", byteArrayOf(1, 2, 3, 4)))
            RandomAccessFile(file, "rw").use { randomAccess ->
                randomAccess.setLength(randomAccess.length() - 1)
            }
            expectFailure<IllegalArgumentException> {
                ZiprafStoredEntryParser.parse(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingCentralDirectory_isRejected() {
        val file = File.createTempFile("zipraf-no-central", ".bin")
        try {
            writeLocalRecordOnly(file, TestEntry("payload.bin", byteArrayOf(1)))
            expectFailure<IllegalArgumentException> {
                ZiprafArchiveValidator.parseStoredEntry(file, "payload.bin")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun zeroLengthExtent_isRejected() {
        val file = File.createTempFile("zipraf-empty", ".bin")
        try {
            file.writeBytes(byteArrayOf(1))
            expectFailure<IllegalArgumentException> {
                ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 0)).close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun nonStoreMethod_isRejected() {
        val file = File.createTempFile("zipraf-deflate", ".bin")
        try {
            file.writeBytes(ByteArray(64))
            expectFailure<IllegalArgumentException> {
                ZiprafDirectRuntime(file, ZiprafStoredExtent(0, 64, 8)).close()
            }
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

    private data class TestEntry(
        val name: String,
        val payload: ByteArray,
        val flags: Int = FLAG_UTF8,
        val localCrc: Long = crc32(payload),
        val centralCrc: Long = localCrc,
        val centralSize: Int = payload.size
    )

    private fun writeLocalRecordOnly(file: File, entry: TestEntry) {
        val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
        val output = ByteBuffer.allocate(LOCAL_HEADER_SIZE + nameBytes.size + entry.payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        writeLocalHeader(output, entry, nameBytes)
        output.put(entry.payload)
        file.writeBytes(output.array())
    }

    private fun writeArchive(
        file: File,
        entries: List<TestEntry>,
        eocdDiskNumber: Int = 0
    ) {
        val totalSize = entries.sumOf {
            LOCAL_HEADER_SIZE + it.name.toByteArray(Charsets.UTF_8).size + it.payload.size +
                CENTRAL_HEADER_SIZE + it.name.toByteArray(Charsets.UTF_8).size
        } + EOCD_SIZE
        val output = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        val localOffsets = ArrayList<Int>(entries.size)

        entries.forEach { entry ->
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            localOffsets += output.position()
            writeLocalHeader(output, entry, nameBytes)
            output.put(entry.payload)
        }

        val centralOffset = output.position()
        entries.forEachIndexed { index, entry ->
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            output.putInt(CENTRAL_SIGNATURE)
            output.putShort(20)
            output.putShort(20)
            output.putShort(entry.flags.toShort())
            output.putShort(ZiprafStoredExtent.STORE_METHOD.toShort())
            output.putShort(0)
            output.putShort(0)
            output.putInt(entry.centralCrc.toInt())
            output.putInt(entry.centralSize)
            output.putInt(entry.centralSize)
            output.putShort(nameBytes.size.toShort())
            output.putShort(0)
            output.putShort(0)
            output.putShort(0)
            output.putShort(0)
            output.putInt(0)
            output.putInt(localOffsets[index])
            output.put(nameBytes)
        }
        val centralSize = output.position() - centralOffset

        output.putInt(EOCD_SIGNATURE)
        output.putShort(eocdDiskNumber.toShort())
        output.putShort(0)
        output.putShort(entries.size.toShort())
        output.putShort(entries.size.toShort())
        output.putInt(centralSize)
        output.putInt(centralOffset)
        output.putShort(0)
        file.writeBytes(output.array())
    }

    private fun writeLocalHeader(output: ByteBuffer, entry: TestEntry, nameBytes: ByteArray) {
        output.putInt(LOCAL_SIGNATURE)
        output.putShort(20)
        output.putShort(entry.flags.toShort())
        output.putShort(ZiprafStoredExtent.STORE_METHOD.toShort())
        output.putShort(0)
        output.putShort(0)
        output.putInt(entry.localCrc.toInt())
        output.putInt(entry.payload.size)
        output.putInt(entry.payload.size)
        output.putShort(nameBytes.size.toShort())
        output.putShort(0)
        output.put(nameBytes)
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            assertTrue(
                "Expected ${T::class.java.name}, got ${failure::class.java.name}",
                failure is T
            )
        }
    }

    companion object {
        private const val LOCAL_SIGNATURE = 0x04034b50
        private const val CENTRAL_SIGNATURE = 0x02014b50
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val LOCAL_HEADER_SIZE = 30
        private const val CENTRAL_HEADER_SIZE = 46
        private const val EOCD_SIZE = 22
        private const val FLAG_DATA_DESCRIPTOR = 1 shl 3
        private const val FLAG_UTF8 = 1 shl 11

        private fun crc32(payload: ByteArray): Long = CRC32().apply { update(payload) }.value
    }
}
