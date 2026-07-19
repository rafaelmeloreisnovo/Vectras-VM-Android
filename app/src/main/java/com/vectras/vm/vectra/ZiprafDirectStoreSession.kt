package com.vectras.vm.vectra

import java.io.Closeable
import java.io.File
import java.nio.ByteOrder

fun interface ZiprafSessionWindowConsumer {
    fun onWindow(window: ZiprafMappedWindow)
}

data class ZiprafSessionScanResult(
    val entryName: String,
    val startOffset: Long,
    val bytesVisited: Long,
    val windowCount: Int,
    val laneMask: Int
)

class ZiprafDirectStoreSession private constructor(
    val entry: ZiprafStoredEntry,
    private val runtime: ZiprafDirectRuntime,
    private val plan: ZiprafRuntimePlan
) : Closeable {

    @JvmOverloads
    fun scan(
        stage: ZiprafMemoryStage,
        routeSeed: Long = 0,
        startOffset: Long = 0,
        maxBytes: Long = Long.MAX_VALUE,
        consumer: ZiprafSessionWindowConsumer
    ): ZiprafSessionScanResult {
        require(startOffset >= 0 && startOffset < entry.extent.payloadSize)
        require(maxBytes > 0)

        val scanLength = minOf(maxBytes, entry.extent.payloadSize - startOffset)
        var visited = 0L
        var windowCount = 0
        var laneMask = 0

        while (visited < scanLength) {
            val rawWindow = runtime.window(
                logicalOffset = startOffset + visited,
                stage = stage,
                routeId = routeSeed + windowCount
            )
            val remaining = scanLength - visited
            val exposedLength = minOf(rawWindow.length.toLong(), remaining).toInt()
            val exposedBytes = rawWindow.bytes.asReadOnlyBuffer().apply {
                position(0)
                limit(exposedLength)
            }.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
            require(exposedBytes.isDirect) { "ZIPRAF mapped window must remain a direct buffer" }

            val window = rawWindow.copy(
                length = exposedLength,
                bytes = exposedBytes
            )
            consumer.onWindow(window)
            visited += exposedLength
            windowCount += 1
            laneMask = laneMask or (1 shl window.coreLane)
        }

        return ZiprafSessionScanResult(
            entryName = entry.name,
            startOffset = startOffset,
            bytesVisited = visited,
            windowCount = windowCount,
            laneMask = laneMask
        )
    }

    override fun close() {
        runtime.close()
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun open(
            file: File,
            entryName: String? = null,
            plan: ZiprafRuntimePlan = ZiprafRuntimePlan(),
            verifyCrc32: Boolean = true
        ): ZiprafDirectStoreSession {
            val entry = ZiprafArchiveValidator.parseStoredEntry(file, entryName)
            val runtime = ZiprafDirectRuntime(file, entry.extent, plan)
            if (verifyCrc32 && !runtime.verifyCrc32()) {
                runtime.close()
                throw IllegalArgumentException("ZIP STORE payload CRC-32 verification failed")
            }
            return ZiprafDirectStoreSession(entry, runtime, plan)
        }
    }
}
