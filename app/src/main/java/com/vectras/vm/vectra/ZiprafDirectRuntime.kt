package com.vectras.vm.vectra

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ZiprafStoredExtent(
    val payloadOffset: Long,
    val payloadSize: Long,
    val compressionMethod: Int = STORE_METHOD
) {
    companion object { const val STORE_METHOD = 0 }
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
        require(extent.compressionMethod == ZiprafStoredExtent.STORE_METHOD) {
            "ZIPRAF direct runtime accepts only ZIP STORE"
        }
        require(extent.payloadOffset >= 0 && extent.payloadSize > 0)
        require(extent.payloadOffset <= randomAccess.length())
        require(extent.payloadSize <= randomAccess.length() - extent.payloadOffset)
        require(extent.payloadOffset + extent.payloadSize <= Int.MAX_VALUE.toLong()) {
            "extent exceeds 2GiB addressable limit"
        }
        mapping = randomAccess.channel.map(
            FileChannel.MapMode.READ_ONLY,
            extent.payloadOffset,
            extent.payloadSize
        ).order(ByteOrder.LITTLE_ENDIAN) as MappedByteBuffer
    }

    fun window(logicalOffset: Long, stage: ZiprafMemoryStage, routeId: Long = 0): ZiprafMappedWindow {
        require(logicalOffset >= 0 && logicalOffset < extent.payloadSize)
        val length = minOf(plan.sizeFor(stage).toLong(), extent.payloadSize - logicalOffset).toInt()
        require(logicalOffset + length <= Int.MAX_VALUE)
        val view = mapping.asReadOnlyBuffer()
        view.position(logicalOffset.toInt())
        view.limit((logicalOffset + length).toInt())
        return ZiprafMappedWindow(stage, logicalOffset, length, plan.lane(routeId), view.slice().asReadOnlyBuffer())
    }

    override fun close() { randomAccess.close() }

    companion object {
        private const val SIG_EOCD = 0x06054b50.toInt()
        private const val SIG_CD_ENTRY = 0x02014b50.toInt()
        private const val SIG_LOCAL_HEADER = 0x04034b50.toInt()

        fun parseStoredExtent(file: File, entryName: String): ZiprafStoredExtent {
            require(!entryName.contains("..")) { "Path traversal rejected: $entryName" }
            require(!entryName.startsWith("/")) { "Absolute path rejected: $entryName" }

            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()

                // Find EOCD (search backward, accounting for up to 65535-byte comment)
                val searchStart = maxOf(0L, fileLen - 22 - 65535)
                var eocdPos = fileLen - 22
                var eocdFound = false
                while (eocdPos >= searchStart) {
                    raf.seek(eocdPos)
                    if (readIntLE(raf) == SIG_EOCD) { eocdFound = true; break }
                    eocdPos--
                }
                require(eocdFound) { "EOCD not found — not a valid ZIP file" }

                // Guard against ZIP64 (total entries == 0xFFFF signals ZIP64 EOCD)
                raf.seek(eocdPos + 10)
                val totalEntries = readShortLE(raf)
                require(totalEntries != 0xFFFF) { "ZIP64 format is not supported" }

                // Read CD offset from EOCD
                raf.seek(eocdPos + 16)
                val cdOffset = readIntLE(raf).toLong() and 0xFFFFFFFFL

                // Scan Central Directory for the named entry
                var cdPos = cdOffset
                var localHeaderOffset = -1L
                var payloadSize = -1L
                var compressionMethod = -1

                while (cdPos < fileLen) {
                    raf.seek(cdPos)
                    val sig = readIntLE(raf)
                    if (sig != SIG_CD_ENTRY) break

                    raf.seek(cdPos + 10)
                    val method = readShortLE(raf)
                    raf.seek(cdPos + 20)
                    val compressedSize = readIntLE(raf).toLong() and 0xFFFFFFFFL
                    val uncompressedSize = readIntLE(raf).toLong() and 0xFFFFFFFFL
                    val fnLen = readShortLE(raf)
                    val extraLen = readShortLE(raf)
                    val commentLen = readShortLE(raf)
                    raf.seek(cdPos + 42)
                    val localOff = readIntLE(raf).toLong() and 0xFFFFFFFFL

                    raf.seek(cdPos + 46)
                    val nameBytes = ByteArray(fnLen)
                    raf.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)

                    if (name == entryName) {
                        compressionMethod = method
                        payloadSize = if (method == 0) uncompressedSize else compressedSize
                        localHeaderOffset = localOff
                        break
                    }
                    cdPos += 46 + fnLen + extraLen + commentLen
                }

                require(localHeaderOffset >= 0) { "Entry '$entryName' not found in ZIP" }
                require(compressionMethod == 0) {
                    "Entry '$entryName' uses compression method $compressionMethod, expected STORED (0)"
                }
                require(payloadSize > 0) { "Entry '$entryName' has empty payload" }
                require(payloadSize <= Int.MAX_VALUE.toLong()) {
                    "ZIP64 entry size $payloadSize exceeds 2GiB addressable limit"
                }

                // Read Local File Header to compute exact data start offset
                raf.seek(localHeaderOffset)
                val localSig = readIntLE(raf)
                require(localSig == SIG_LOCAL_HEADER) {
                    "Invalid local file header signature at offset $localHeaderOffset"
                }
                raf.seek(localHeaderOffset + 26)
                val localFnLen = readShortLE(raf)
                val localExtraLen = readShortLE(raf)
                val dataOffset = localHeaderOffset + 30 + localFnLen + localExtraLen

                return ZiprafStoredExtent(dataOffset, payloadSize, ZiprafStoredExtent.STORE_METHOD)
            }
        }

        private fun readIntLE(raf: RandomAccessFile): Int {
            val b0 = raf.read(); val b1 = raf.read(); val b2 = raf.read(); val b3 = raf.read()
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        private fun readShortLE(raf: RandomAccessFile): Int {
            val b0 = raf.read(); val b1 = raf.read()
            return b0 or (b1 shl 8)
        }

        fun preserveFixedBits(candidate: Long, fixedMask: Long, fixedValue: Long): Long =
            (candidate and fixedMask.inv()) or (fixedValue and fixedMask)
    }
}
