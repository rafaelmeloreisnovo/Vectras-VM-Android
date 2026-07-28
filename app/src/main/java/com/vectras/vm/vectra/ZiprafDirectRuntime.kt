package com.vectras.vm.vectra

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        internal const val UINT32_MAX = 0xffff_ffffL
    }
}

enum class ZiprafValidationLevel {
    LOCAL_HEADER,
    CENTRAL_DIRECTORY
}

data class ZiprafStoredEntry(
    val name: String,
    val flags: Int,
    val extent: ZiprafStoredExtent,
    val localHeaderOffset: Long = 0,
    val validationLevel: ZiprafValidationLevel = ZiprafValidationLevel.LOCAL_HEADER
)

private object ZiprafZipFormat {
    const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
    const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
    const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
    const val LOCAL_FILE_HEADER_SIZE = 30
    const val CENTRAL_DIRECTORY_HEADER_SIZE = 46
    const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
    const val MAX_COMMENT_SIZE = 0xffff
    const val FLAG_ENCRYPTED = 1
    const val FLAG_DATA_DESCRIPTOR = 1 shl 3
    const val FLAG_UTF8 = 1 shl 11
    const val ZIP64_UINT16_SENTINEL = 0xffff
    const val ZIP64_UINT32_SENTINEL = 0xffff_ffffL
}

private object ZiprafEntryNamePolicy {
    fun decode(nameBytes: ByteArray, flags: Int): String {
        val nameIsAscii = nameBytes.all { (it.toInt() and 0xff) < 0x80 }
        require(flags and ZiprafZipFormat.FLAG_UTF8 != 0 || nameIsAscii) {
            "Non-UTF-8 ZIP entry names are not accepted by this parser"
        }
        return nameBytes.toString(Charsets.UTF_8).also(::validate)
    }

    fun validate(name: String) {
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

/**
 * Parses one classic ZIP local-file header and emits a bounded STORE extent.
 *
 * This low-level parser validates the local record only. Distribution or trusted execution should
 * use [ZiprafArchiveValidator.parseStoredEntry], which cross-checks the central directory and EOCD.
 */
object ZiprafStoredEntryParser {
    fun parse(
        file: File,
        localHeaderOffset: Long = 0,
        expectedName: String? = null
    ): ZiprafStoredEntry = RandomAccessFile(file, "r").use { randomAccess ->
        parse(randomAccess, localHeaderOffset, expectedName)
    }

    internal fun parse(
        randomAccess: RandomAccessFile,
        localHeaderOffset: Long,
        expectedName: String?
    ): ZiprafStoredEntry {
        require(localHeaderOffset >= 0) { "ZIP local-header offset must be non-negative" }
        val fileSize = randomAccess.length()
        require(localHeaderOffset <= fileSize) { "ZIP local-header offset is outside the file" }
        require(fileSize - localHeaderOffset >= ZiprafZipFormat.LOCAL_FILE_HEADER_SIZE) {
            "Truncated ZIP local-file header"
        }

        randomAccess.seek(localHeaderOffset)
        val headerBytes = ByteArray(ZiprafZipFormat.LOCAL_FILE_HEADER_SIZE)
        randomAccess.readFully(headerBytes)
        val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

        require(header.int == ZiprafZipFormat.LOCAL_FILE_HEADER_SIGNATURE) {
            "Invalid ZIP local-file signature"
        }

        header.short
        val flags = header.short.toInt() and 0xffff
        val method = header.short.toInt() and 0xffff
        header.short
        header.short
        val crc32 = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX
        val compressedSize = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX
        val uncompressedSize = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX
        val nameLength = header.short.toInt() and 0xffff
        val extraLength = header.short.toInt() and 0xffff

        validateStoreMetadata(flags, method, compressedSize, uncompressedSize)

        val metadataSize = ZiprafZipFormat.LOCAL_FILE_HEADER_SIZE.toLong() + nameLength + extraLength
        require(metadataSize <= fileSize - localHeaderOffset) {
            "ZIP entry metadata exceeds file bounds"
        }

        val nameBytes = ByteArray(nameLength)
        randomAccess.readFully(nameBytes)
        val name = ZiprafEntryNamePolicy.decode(nameBytes, flags)
        expectedName?.let { require(name == it) { "Unexpected ZIP entry name: $name" } }

        val payloadOffset = Math.addExact(localHeaderOffset, metadataSize)
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
            ),
            localHeaderOffset = localHeaderOffset
        )
    }

    internal fun validateStoreMetadata(
        flags: Int,
        method: Int,
        compressedSize: Long,
        uncompressedSize: Long
    ) {
        require(flags and ZiprafZipFormat.FLAG_ENCRYPTED == 0) {
            "Encrypted ZIP entries are not supported"
        }
        require(flags and ZiprafZipFormat.FLAG_DATA_DESCRIPTOR == 0) {
            "ZIP data-descriptor entries are not supported by the direct runtime"
        }
        require(method == ZiprafStoredExtent.STORE_METHOD) {
            "ZIPRAF direct runtime accepts only ZIP STORE"
        }
        require(
            compressedSize != ZiprafZipFormat.ZIP64_UINT32_SENTINEL &&
                uncompressedSize != ZiprafZipFormat.ZIP64_UINT32_SENTINEL
        ) { "ZIP64 entries require a dedicated parser" }
        require(compressedSize == uncompressedSize) {
            "STORE entry compressed and uncompressed sizes must match"
        }
        require(compressedSize > 0) { "Empty STORE entries cannot be memory mapped" }
    }
}

private data class ZiprafCentralDirectoryEntry(
    val name: String,
    val flags: Int,
    val method: Int,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long
)

private data class ZiprafCentralDirectory(
    val offset: Long,
    val size: Long,
    val entries: List<ZiprafCentralDirectoryEntry>
)

/**
 * Strict classic-ZIP validator for the direct STORE runtime.
 *
 * It validates EOCD, single-disk layout, central-directory bounds, entry uniqueness and equality
 * between central and local metadata. ZIP64, encrypted entries and data descriptors remain blocked.
 */
object ZiprafArchiveValidator {
    fun parseStoredEntry(file: File, expectedName: String? = null): ZiprafStoredEntry {
        RandomAccessFile(file, "r").use { randomAccess ->
            val centralDirectory = readCentralDirectory(randomAccess)
            val matches = if (expectedName == null) {
                require(centralDirectory.entries.size == 1) {
                    "Archive contains multiple entries; expectedName is required"
                }
                centralDirectory.entries
            } else {
                centralDirectory.entries.filter { it.name == expectedName }
            }

            require(matches.isNotEmpty()) {
                "ZIP central directory does not contain the requested entry: $expectedName"
            }
            require(matches.size == 1) {
                "Duplicate ZIP central-directory entry name is not accepted: ${matches.first().name}"
            }

            val central = matches.single()
            require(central.localHeaderOffset < centralDirectory.offset) {
                "ZIP local header overlaps the central directory"
            }

            val local = ZiprafStoredEntryParser.parse(
                randomAccess = randomAccess,
                localHeaderOffset = central.localHeaderOffset,
                expectedName = central.name
            )
            crossCheck(local, central, centralDirectory.offset)
            return local.copy(validationLevel = ZiprafValidationLevel.CENTRAL_DIRECTORY)
        }
    }

    private fun readCentralDirectory(randomAccess: RandomAccessFile): ZiprafCentralDirectory {
        val fileSize = randomAccess.length()
        require(fileSize >= ZiprafZipFormat.END_OF_CENTRAL_DIRECTORY_SIZE) {
            "ZIP file is too small to contain EOCD"
        }

        val tailSize = minOf(
            fileSize,
            (ZiprafZipFormat.END_OF_CENTRAL_DIRECTORY_SIZE + ZiprafZipFormat.MAX_COMMENT_SIZE).toLong()
        ).toInt()
        val tailOffset = fileSize - tailSize
        val tail = ByteArray(tailSize)
        randomAccess.seek(tailOffset)
        randomAccess.readFully(tail)

        val eocdIndex = findSignatureBackwards(
            bytes = tail,
            signature = ZiprafZipFormat.END_OF_CENTRAL_DIRECTORY_SIGNATURE,
            latestStart = tail.size - ZiprafZipFormat.END_OF_CENTRAL_DIRECTORY_SIZE
        )
        require(eocdIndex >= 0) { "ZIP end-of-central-directory record was not found" }

        val eocd = ByteBuffer.wrap(tail, eocdIndex, tail.size - eocdIndex)
            .slice()
            .order(ByteOrder.LITTLE_ENDIAN)
        require(eocd.int == ZiprafZipFormat.END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        val diskNumber = eocd.short.toInt() and 0xffff
        val centralDirectoryDisk = eocd.short.toInt() and 0xffff
        val entriesOnDisk = eocd.short.toInt() and 0xffff
        val totalEntries = eocd.short.toInt() and 0xffff
        val centralDirectorySize = eocd.int.toLong() and ZiprafStoredExtent.UINT32_MAX
        val centralDirectoryOffset = eocd.int.toLong() and ZiprafStoredExtent.UINT32_MAX
        val commentLength = eocd.short.toInt() and 0xffff

        require(eocdIndex + ZiprafZipFormat.END_OF_CENTRAL_DIRECTORY_SIZE + commentLength == tail.size) {
            "ZIP EOCD comment length or trailing bytes are inconsistent"
        }
        require(diskNumber == 0 && centralDirectoryDisk == 0) {
            "Multi-disk ZIP archives are not supported"
        }
        require(entriesOnDisk == totalEntries) {
            "Split ZIP central-directory entry counts are inconsistent"
        }
        require(totalEntries != ZiprafZipFormat.ZIP64_UINT16_SENTINEL) {
            "ZIP64 entry count requires a dedicated parser"
        }
        require(
            centralDirectorySize != ZiprafZipFormat.ZIP64_UINT32_SENTINEL &&
                centralDirectoryOffset != ZiprafZipFormat.ZIP64_UINT32_SENTINEL
        ) { "ZIP64 central-directory metadata requires a dedicated parser" }
        require(totalEntries > 0) { "ZIP archive contains no entries" }

        val eocdAbsoluteOffset = tailOffset + eocdIndex
        require(centralDirectoryOffset <= eocdAbsoluteOffset) {
            "ZIP central-directory offset is outside the archive"
        }
        require(centralDirectorySize <= eocdAbsoluteOffset - centralDirectoryOffset) {
            "ZIP central-directory size exceeds archive bounds"
        }
        require(centralDirectoryOffset + centralDirectorySize == eocdAbsoluteOffset) {
            "Unexpected records exist between central directory and EOCD"
        }

        val entries = ArrayList<ZiprafCentralDirectoryEntry>(totalEntries)
        randomAccess.seek(centralDirectoryOffset)
        val centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize
        repeat(totalEntries) {
            require(centralDirectoryEnd - randomAccess.filePointer >= ZiprafZipFormat.CENTRAL_DIRECTORY_HEADER_SIZE) {
                "Truncated ZIP central-directory header"
            }
            val fixed = ByteArray(ZiprafZipFormat.CENTRAL_DIRECTORY_HEADER_SIZE)
            randomAccess.readFully(fixed)
            val header = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN)
            require(header.int == ZiprafZipFormat.CENTRAL_DIRECTORY_SIGNATURE) {
                "Invalid ZIP central-directory signature"
            }

            header.short
            header.short
            val flags = header.short.toInt() and 0xffff
            val method = header.short.toInt() and 0xffff
            header.short
            header.short
            val crc32 = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX
            val compressedSize = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX
            val uncompressedSize = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX
            val nameLength = header.short.toInt() and 0xffff
            val extraLength = header.short.toInt() and 0xffff
            val entryCommentLength = header.short.toInt() and 0xffff
            val diskStart = header.short.toInt() and 0xffff
            header.short
            header.int
            val localHeaderOffset = header.int.toLong() and ZiprafStoredExtent.UINT32_MAX

            require(diskStart == 0) { "Multi-disk ZIP entry is not supported" }
            require(localHeaderOffset != ZiprafZipFormat.ZIP64_UINT32_SENTINEL) {
                "ZIP64 local-header offsets require a dedicated parser"
            }
            ZiprafStoredEntryParser.validateStoreMetadata(
                flags,
                method,
                compressedSize,
                uncompressedSize
            )

            val variableSize = nameLength.toLong() + extraLength + entryCommentLength
            require(variableSize <= centralDirectoryEnd - randomAccess.filePointer) {
                "ZIP central-directory variable fields exceed bounds"
            }
            val nameBytes = ByteArray(nameLength)
            randomAccess.readFully(nameBytes)
            val name = ZiprafEntryNamePolicy.decode(nameBytes, flags)
            randomAccess.seek(randomAccess.filePointer + extraLength + entryCommentLength)

            entries += ZiprafCentralDirectoryEntry(
                name = name,
                flags = flags,
                method = method,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset
            )
        }

        require(randomAccess.filePointer == centralDirectoryEnd) {
            "ZIP central-directory size does not match parsed entries"
        }
        return ZiprafCentralDirectory(
            offset = centralDirectoryOffset,
            size = centralDirectorySize,
            entries = entries
        )
    }

    private fun crossCheck(
        local: ZiprafStoredEntry,
        central: ZiprafCentralDirectoryEntry,
        centralDirectoryOffset: Long
    ) {
        require(local.flags == central.flags) { "ZIP flags differ between local and central records" }
        require(local.extent.compressionMethod == central.method) {
            "ZIP compression method differs between local and central records"
        }
        require(local.extent.expectedCrc32 == central.crc32) {
            "ZIP CRC-32 differs between local and central records"
        }
        require(local.extent.payloadSize == central.compressedSize) {
            "ZIP compressed size differs between local and central records"
        }
        require(central.compressedSize == central.uncompressedSize) {
            "ZIP STORE central-directory sizes must match"
        }
        require(local.extent.payloadOffset + local.extent.payloadSize <= centralDirectoryOffset) {
            "ZIP payload overlaps the central directory"
        }
    }

    private fun findSignatureBackwards(bytes: ByteArray, signature: Int, latestStart: Int): Int {
        val b0 = signature and 0xff
        val b1 = signature ushr 8 and 0xff
        val b2 = signature ushr 16 and 0xff
        val b3 = signature ushr 24 and 0xff
        for (index in latestStart downTo 0) {
            if (
                bytes[index].toInt() and 0xff == b0 &&
                bytes[index + 1].toInt() and 0xff == b1 &&
                bytes[index + 2].toInt() and 0xff == b2 &&
                bytes[index + 3].toInt() and 0xff == b3
            ) {
                return index
            }
        }
        return -1
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

/**
 * Read-only direct runtime for a validated STORE payload.
 *
 * Each request maps only the requested logical window. The whole payload is never forced into one
 * Int-sized ByteBuffer, so large extents remain addressable while every individual window stays
 * bounded by [ZiprafRuntimePlan].
 */
class ZiprafDirectRuntime(
    file: File,
    private val extent: ZiprafStoredExtent,
    private val plan: ZiprafRuntimePlan = ZiprafRuntimePlan()
) : Closeable {
    private val randomAccess = RandomAccessFile(file, "r")
    private val channel: FileChannel = randomAccess.channel

    init {
        try {
            require(extent.compressionMethod == ZiprafStoredExtent.STORE_METHOD) {
                "ZIPRAF direct runtime accepts only ZIP STORE"
            }
            require(extent.payloadOffset >= 0 && extent.payloadSize > 0)
            require(extent.payloadOffset <= randomAccess.length())
            require(extent.payloadSize <= randomAccess.length() - extent.payloadOffset)
        } catch (failure: Throwable) {
            randomAccess.close()
            throw failure
        }
        require(extent.payloadOffset + extent.payloadSize <= Int.MAX_VALUE.toLong()) {
            "extent exceeds 2GiB addressable limit"
        }
        mapping = randomAccess.channel.map(
            FileChannel.MapMode.READ_ONLY,
            extent.payloadOffset,
            extent.payloadSize
        ).order(ByteOrder.LITTLE_ENDIAN) as MappedByteBuffer
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
        val absoluteOffset = Math.addExact(extent.payloadOffset, logicalOffset)
        val mapping = channel.map(FileChannel.MapMode.READ_ONLY, absoluteOffset, length.toLong())
        mapping.order(ByteOrder.LITTLE_ENDIAN)

        return ZiprafMappedWindow(
            stage = stage,
            logicalOffset = logicalOffset,
            length = length,
            coreLane = plan.lane(routeId),
            bytes = mapping.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        )
    }

    fun verifyCrc32(
        expectedCrc32: Long = extent.expectedCrc32
            ?: throw IllegalStateException("No expected CRC-32 is attached to this extent"),
        chunkBytes: Int = 64 * 1024
    ): Boolean {
        require(expectedCrc32 in 0..ZiprafStoredExtent.UINT32_MAX)
        require(chunkBytes > 0)

        val crc = CRC32()
        val chunk = ByteBuffer.allocate(chunkBytes)
        var logicalOffset = 0L
        while (logicalOffset < extent.payloadSize) {
            chunk.clear()
            chunk.limit(minOf(chunk.capacity().toLong(), extent.payloadSize - logicalOffset).toInt())
            val absoluteOffset = Math.addExact(extent.payloadOffset, logicalOffset)
            readFullyAt(channel, chunk, absoluteOffset)
            val count = chunk.position()
            crc.update(chunk.array(), 0, count)
            logicalOffset += count
        }
        return crc.value == expectedCrc32
    }

    override fun close() {
        randomAccess.close()
    }

    companion object {
        fun openValidated(
            file: File,
            entryName: String? = null,
            plan: ZiprafRuntimePlan = ZiprafRuntimePlan(),
            verifyCrc32: Boolean = true
        ): ZiprafDirectRuntime {
            val entry = ZiprafArchiveValidator.parseStoredEntry(file, entryName)
            val runtime = ZiprafDirectRuntime(file, entry.extent, plan)
            if (verifyCrc32 && !runtime.verifyCrc32()) {
                runtime.close()
                throw IllegalArgumentException("ZIP STORE payload CRC-32 verification failed")
            }
            return runtime
        }

        fun preserveFixedBits(candidate: Long, fixedMask: Long, fixedValue: Long): Long =
            (candidate and fixedMask.inv()) or (fixedValue and fixedMask)

        private fun readFullyAt(channel: FileChannel, target: ByteBuffer, absoluteOffset: Long) {
            var position = absoluteOffset
            while (target.hasRemaining()) {
                val read = channel.read(target, position)
                require(read >= 0) { "ZIP STORE payload was truncated during CRC verification" }
                require(read > 0) { "ZIP STORE payload read made no progress" }
                position += read
            }
        }
    }
}
