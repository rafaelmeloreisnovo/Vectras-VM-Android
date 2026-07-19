package com.vectras.vm.vectra

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.CRC32

data class ZiprafStoredExtent(
    val payloadOffset: Long,
    val payloadSize: Long,
    val compressionMethod: Int = STORE_METHOD,
    val expectedCrc32: Long? = null
) {
    init {
        require(expectedCrc32 == null || expectedCrc32 in 0..UINT32_MAX) {
            "ZIP CRC-32 must fit in an unsigned 32-bit value"
        }
    }

    companion object {
        const val STORE_METHOD = 0
        private const val UINT32_MAX = 0xffff_ffffL
    }
}

data class ZiprafStoredEntry(
    val name: String,
    val flags: Int,
    val extent: ZiprafStoredExtent
)

/**
 * Parses a classic ZIP local-file header and emits a validated STORE extent.
 *
 * This intentionally rejects encrypted entries, data-descriptor entries and ZIP64 sentinels.
 * The direct runtime needs sizes and CRC to be present before mapping the payload. A future
 * central-directory validator can cross-check the local header before release promotion.
 */
object ZiprafStoredEntryParser {
    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
    private const val LOCAL_FILE_HEADER_SIZE = 30
    private const val FLAG_ENCRYPTED = 1
    private const val FLAG_DATA_DESCRIPTOR = 1 shl 3
    private const val FLAG_UTF8 = 1 shl 11
    private const val ZIP64_SENTINEL = 0xffff_ffffL

    fun parse(
        file: File,
        localHeaderOffset: Long = 0,
        expectedName: String? = null
    ): ZiprafStoredEntry {
        require(localHeaderOffset >= 0) { "ZIP local-header offset must be non-negative" }

        RandomAccessFile(file, "r").use { randomAccess ->
            val fileSize = randomAccess.length()
            require(localHeaderOffset <= fileSize) { "ZIP local-header offset is outside the file" }
            require(fileSize - localHeaderOffset >= LOCAL_FILE_HEADER_SIZE) {
                "Truncated ZIP local-file header"
            }

            randomAccess.seek(localHeaderOffset)
            val headerBytes = ByteArray(LOCAL_FILE_HEADER_SIZE)
            randomAccess.readFully(headerBytes)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            val signature = header.int
            require(signature == LOCAL_FILE_HEADER_SIGNATURE) { "Invalid ZIP local-file signature" }

            header.short // version needed
            val flags = header.short.toInt() and 0xffff
            val method = header.short.toInt() and 0xffff
            header.short // modification time
            header.short // modification date
            val crc32 = header.int.toLong() and ZIP64_SENTINEL
            val compressedSize = header.int.toLong() and ZIP64_SENTINEL
            val uncompressedSize = header.int.toLong() and ZIP64_SENTINEL
            val nameLength = header.short.toInt() and 0xffff
            val extraLength = header.short.toInt() and 0xffff

            require(flags and FLAG_ENCRYPTED == 0) { "Encrypted ZIP entries are not supported" }
            require(flags and FLAG_DATA_DESCRIPTOR == 0) {
                "ZIP data-descriptor entries are not supported by the direct runtime"
            }
            require(method == ZiprafStoredExtent.STORE_METHOD) {
                "ZIPRAF direct runtime accepts only ZIP STORE"
            }
            require(compressedSize != ZIP64_SENTINEL && uncompressedSize != ZIP64_SENTINEL) {
                "ZIP64 entries require a dedicated parser"
            }
            require(compressedSize == uncompressedSize) {
                "STORE entry compressed and uncompressed sizes must match"
            }
            require(compressedSize > 0) { "Empty STORE entries cannot be memory mapped" }
            require(compressedSize <= Int.MAX_VALUE.toLong()) {
                "Mapped STORE extent exceeds the ByteBuffer capacity limit"
            }

            val metadataSize = LOCAL_FILE_HEADER_SIZE.toLong() + nameLength + extraLength
            require(metadataSize <= fileSize - localHeaderOffset) {
                "ZIP entry metadata exceeds file bounds"
            }

            val nameBytes = ByteArray(nameLength)
            randomAccess.readFully(nameBytes)
            val nameIsAscii = nameBytes.all { (it.toInt() and 0xff) < 0x80 }
            require(flags and FLAG_UTF8 != 0 || nameIsAscii) {
                "Non-UTF-8 ZIP entry names are not accepted by this parser"
            }

            val name = nameBytes.toString(Charsets.UTF_8)
            validateEntryName(name)
            expectedName?.let {
                require(name == it) { "Unexpected ZIP entry name: $name" }
            }

            val payloadOffset = localHeaderOffset + metadataSize
            require(compressedSize <= fileSize - payloadOffset) {
                "ZIP STORE payload exceeds file bounds"
            }

            return ZiprafStoredEntry(
                name = name,
                flags = flags,
                extent = ZiprafStoredExtent(
                    payloadOffset = payloadOffset,
                    payloadSize = compressedSize,
                    compressionMethod = method,
                    expectedCrc32 = crc32
                )
            )
        }
    }

    private fun validateEntryName(name: String) {
        require(name.isNotEmpty()) { "ZIP entry name must not be empty" }
        require('\u0000' !in name) { "ZIP entry name contains NUL" }

        val normalized = name.replace('\\', '/')
        require(!normalized.startsWith('/')) { "Absolute ZIP entry paths are not accepted" }
        require(!Regex("^[A-Za-z]:/").containsMatchIn(normalized)) {
            "Drive-qualified ZIP entry paths are not accepted"
        }
        require(normalized.split('/').none { it == ".." }) {
            "ZIP entry path traversal is not accepted"
        }
    }
}

enum class ZiprafMemoryStage { BUFFER, L1_HOT, L2_SHARED }

data class ZiprafRuntimePlan(
    val bufferBytes: Int = 64 * 1024,
    val l1WindowBytes: Int = 4 * 1024,
    val l2WindowBytes: Int = 256 * 1024,
    val coreCount: Int = 1
) {
    init {
        require(bufferBytes > 0 && l1WindowBytes > 0 && l2WindowBytes > 0)
        require(l1WindowBytes <= l2WindowBytes)
        require(coreCount in 1..8)
    }

    fun lane(routeId: Long): Int = Math.floorMod(routeId, coreCount.toLong()).toInt()

    fun sizeFor(stage: ZiprafMemoryStage): Int = when (stage) {
        ZiprafMemoryStage.BUFFER -> bufferBytes
        ZiprafMemoryStage.L1_HOT -> l1WindowBytes
        ZiprafMemoryStage.L2_SHARED -> l2WindowBytes
    }
}

data class ZiprafMappedWindow(
    val stage: ZiprafMemoryStage,
    val logicalOffset: Long,
    val length: Int,
    val coreLane: Int,
    val bytes: ByteBuffer
)

class ZiprafDirectRuntime(
    file: File,
    private val extent: ZiprafStoredExtent,
    private val plan: ZiprafRuntimePlan = ZiprafRuntimePlan()
) : Closeable {
    private val randomAccess = RandomAccessFile(file, "r")
    private val mapping: MappedByteBuffer

    init {
        try {
            require(extent.compressionMethod == ZiprafStoredExtent.STORE_METHOD) {
                "ZIPRAF direct runtime accepts only ZIP STORE"
            }
            require(extent.payloadOffset >= 0 && extent.payloadSize > 0)
            require(extent.payloadSize <= Int.MAX_VALUE.toLong()) {
                "Mapped STORE extent exceeds the ByteBuffer capacity limit"
            }
            require(extent.payloadOffset <= randomAccess.length())
            require(extent.payloadSize <= randomAccess.length() - extent.payloadOffset)

            mapping = randomAccess.channel.map(
                FileChannel.MapMode.READ_ONLY,
                extent.payloadOffset,
                extent.payloadSize
            )
            mapping.order(ByteOrder.LITTLE_ENDIAN)
        } catch (failure: Throwable) {
            randomAccess.close()
            throw failure
        }
    }

    fun window(
        logicalOffset: Long,
        stage: ZiprafMemoryStage,
        routeId: Long = 0
    ): ZiprafMappedWindow {
        require(logicalOffset >= 0 && logicalOffset < extent.payloadSize)

        val length = minOf(
            plan.sizeFor(stage).toLong(),
            extent.payloadSize - logicalOffset
        ).toInt()
        val position = logicalOffset.toInt()
        val view = mapping.asReadOnlyBuffer()
        view.position(position)
        view.limit(position + length)

        return ZiprafMappedWindow(
            stage = stage,
            logicalOffset = logicalOffset,
            length = length,
            coreLane = plan.lane(routeId),
            bytes = view.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        )
    }

    fun verifyCrc32(
        expectedCrc32: Long = extent.expectedCrc32
            ?: throw IllegalStateException("No expected CRC-32 is attached to this extent"),
        chunkBytes: Int = 64 * 1024
    ): Boolean {
        require(expectedCrc32 in 0..0xffff_ffffL)
        require(chunkBytes > 0)

        val crc = CRC32()
        val view = mapping.asReadOnlyBuffer()
        val chunk = ByteArray(minOf(chunkBytes, view.remaining()))
        while (view.hasRemaining()) {
            val count = minOf(chunk.size, view.remaining())
            view.get(chunk, 0, count)
            crc.update(chunk, 0, count)
        }
        return crc.value == expectedCrc32
    }

    override fun close() {
        randomAccess.close()
    }

    companion object {
        fun preserveFixedBits(candidate: Long, fixedMask: Long, fixedValue: Long): Long =
            (candidate and fixedMask.inv()) or (fixedValue and fixedMask)
    }
}
