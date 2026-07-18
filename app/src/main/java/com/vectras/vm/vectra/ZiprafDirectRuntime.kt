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
        require(extent.payloadOffset >= 0 && extent.payloadSize >= 0)
        require(extent.payloadOffset <= randomAccess.length())
        require(extent.payloadSize <= randomAccess.length() - extent.payloadOffset)
        mapping = randomAccess.channel.map(FileChannel.MapMode.READ_ONLY, 0, randomAccess.length())
            .order(ByteOrder.LITTLE_ENDIAN) as MappedByteBuffer
    }

    fun window(logicalOffset: Long, stage: ZiprafMemoryStage, routeId: Long = 0): ZiprafMappedWindow {
        require(logicalOffset >= 0 && logicalOffset < extent.payloadSize)
        val length = minOf(plan.sizeFor(stage).toLong(), extent.payloadSize - logicalOffset).toInt()
        val absolute = extent.payloadOffset + logicalOffset
        require(absolute <= Int.MAX_VALUE && absolute + length <= Int.MAX_VALUE)
        val view = mapping.asReadOnlyBuffer()
        view.position(absolute.toInt())
        view.limit((absolute + length).toInt())
        return ZiprafMappedWindow(stage, logicalOffset, length, plan.lane(routeId), view.slice().asReadOnlyBuffer())
    }

    override fun close() { randomAccess.close() }

    companion object {
        fun preserveFixedBits(candidate: Long, fixedMask: Long, fixedValue: Long): Long =
            (candidate and fixedMask.inv()) or (fixedValue and fixedMask)
    }
}
