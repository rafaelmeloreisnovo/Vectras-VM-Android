package com.vectras.vm.vectra

import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ZiprafLayoutBasePolicy(val code: Int) {
    BASE_RELATIVE(0),
    FIXED_VIRTUAL(1),
    FIXED_PHYSICAL(2),
    TOKEN_VAZIO(255)
}

enum class ZiprafLayoutMobility(val code: Int) {
    MOVABLE_BASE(0),
    FIXED_OFFSET(1),
    PINNED_RUNTIME(2),
    REMAP_ONLY(3),
    PHYSICAL_FIXED(4),
    TOKEN_VAZIO(255)
}

enum class ZiprafLayoutRegionState(val code: Int) {
    ABSENT(0),
    EMPTY(1),
    PRESENT(2),
    FAULT(3),
    TOKEN_VAZIO(255)
}

object ZiprafLayoutFlags {
    const val READ_ONLY = 1 shl 0
    const val EXECUTABLE = 1 shl 1
    const val ZERO_FILL = 1 shl 2
    const val NO_RELOCATION_TABLE = 1 shl 3
    const val FIXED_OFFSET_BITS = 1 shl 4
}

data class ZiprafStaticRegion(
    val regionId: Long,
    val offset: Long,
    val size: Long,
    val alignment: Long,
    val fixedOffsetMask: Long = 0,
    val fixedOffsetValue: Long = 0,
    val mobility: ZiprafLayoutMobility = ZiprafLayoutMobility.FIXED_OFFSET,
    val semanticState: ZiprafLayoutRegionState = ZiprafLayoutRegionState.PRESENT,
    val flags: Int = ZiprafLayoutFlags.READ_ONLY
)

data class ZiprafStaticLayoutManifest(
    val abiVersion: Long = ABI_VERSION,
    val layoutEpoch: Long,
    val totalSize: Long,
    val baseAlignment: Long,
    val basePolicy: ZiprafLayoutBasePolicy = ZiprafLayoutBasePolicy.BASE_RELATIVE,
    val regions: List<ZiprafStaticRegion>
) {
    init {
        validate()
    }

    fun validate() {
        require(abiVersion == ABI_VERSION) { "unsupported static-layout ABI" }
        require(totalSize in 0..UINT32_MAX) { "layout totalSize must fit uint32" }
        require(layoutEpoch in 0..UINT32_MAX) { "layout epoch must fit uint32" }
        require(isPowerOfTwo(baseAlignment)) { "base alignment must be a power of two" }
        require(baseAlignment <= UINT32_MAX)
        require(regions.size.toLong() <= UINT32_MAX)

        regions.forEachIndexed { index, region ->
            validateUint32(region.regionId, "regionId")
            validateUint32(region.offset, "offset")
            validateUint32(region.size, "size")
            require(isPowerOfTwo(region.alignment)) { "region alignment must be a power of two" }
            require(region.alignment <= UINT32_MAX)
            require(region.offset and (region.alignment - 1L) == 0L) {
                "region ${region.regionId} offset is not aligned"
            }
            require(region.offset <= totalSize && region.size <= totalSize - region.offset) {
                "region ${region.regionId} exceeds layout bounds"
            }
            when (region.semanticState) {
                ZiprafLayoutRegionState.ABSENT,
                ZiprafLayoutRegionState.EMPTY -> require(region.size == 0L) {
                    "ABSENT/EMPTY region must have zero size"
                }
                ZiprafLayoutRegionState.PRESENT -> require(region.size > 0L) {
                    "PRESENT region must have non-zero size"
                }
                ZiprafLayoutRegionState.FAULT,
                ZiprafLayoutRegionState.TOKEN_VAZIO -> Unit
            }
            if (region.flags and ZiprafLayoutFlags.FIXED_OFFSET_BITS != 0) {
                require(
                    preserveFixedBits(
                        region.offset,
                        region.fixedOffsetMask,
                        region.fixedOffsetValue
                    ) == region.offset
                ) { "region ${region.regionId} violates fixed-offset bits" }
            }

            for (otherIndex in index + 1 until regions.size) {
                val other = regions[otherIndex]
                require(region.regionId != other.regionId) {
                    "duplicate regionId ${region.regionId}"
                }
                require(!rangesOverlap(region, other)) {
                    "regions ${region.regionId} and ${other.regionId} overlap"
                }
            }
        }
    }

    fun signature(): Long {
        var hash = FNV1A64_OFFSET
        hash = feedU64(hash, abiVersion)
        hash = feedU64(hash, layoutEpoch)
        hash = feedU64(hash, totalSize)
        hash = feedU64(hash, baseAlignment)
        hash = feedU64(hash, regions.size.toLong())
        hash = feedU64(hash, basePolicy.code.toLong())
        regions.forEach { region ->
            hash = feedU64(hash, region.regionId)
            hash = feedU64(hash, region.offset)
            hash = feedU64(hash, region.size)
            hash = feedU64(hash, region.alignment)
            hash = feedU64(hash, region.fixedOffsetMask)
            hash = feedU64(hash, region.fixedOffsetValue)
            hash = feedU64(hash, region.mobility.code.toLong())
            hash = feedU64(hash, region.semanticState.code.toLong())
            hash = feedU64(hash, region.flags.toLong())
        }
        return hash
    }

    fun signatureHex(): String = java.lang.Long.toUnsignedString(signature(), 16).padStart(16, '0')

    fun region(regionId: Long): ZiprafStaticRegion =
        regions.firstOrNull { it.regionId == regionId }
            ?: throw IllegalArgumentException("unknown static-layout region: $regionId")

    companion object {
        const val ABI_VERSION = 1L
        private const val UINT32_MAX = 0xffff_ffffL
        private const val FNV1A64_OFFSET = -3750763034362895579L
        private const val FNV1A64_PRIME = 1099511628211L

        private fun validateUint32(value: Long, name: String) {
            require(value in 0..UINT32_MAX) { "$name must fit uint32" }
        }

        private fun isPowerOfTwo(value: Long): Boolean =
            value > 0 && value and (value - 1L) == 0L

        private fun rangesOverlap(a: ZiprafStaticRegion, b: ZiprafStaticRegion): Boolean {
            if (a.size == 0L || b.size == 0L) return false
            return a.offset < b.offset + b.size && b.offset < a.offset + a.size
        }

        private fun preserveFixedBits(candidate: Long, mask: Long, value: Long): Long =
            (candidate and mask.inv()) or (value and mask)

        private fun feedU64(initial: Long, value: Long): Long {
            var hash = initial
            repeat(8) { byteIndex ->
                hash = hash xor ((value ushr (byteIndex * 8)) and 0xffL)
                hash *= FNV1A64_PRIME
            }
            return hash
        }
    }
}

data class ZiprafResolvedSpan(
    val regionId: Long,
    val localOffset: Long,
    val logicalOffset: Long,
    val length: Int,
    val mappingEpoch: Long,
    val manifestSignatureHex: String,
    val bytes: ByteBuffer
)

data class ZiprafStaticLayoutReceipt(
    val schema: String = "zipraf.static-layout.receipt.v1",
    val entryName: String,
    val layoutEpoch: Long,
    val mappingEpoch: Long,
    val manifestSignatureHex: String,
    val offsetsReusable: Boolean,
    val absoluteHandlesSessionScoped: Boolean,
    val metrics: ZiprafRuntimeMetricsSnapshot,
    val claimAllowed: Boolean = false
)

/**
 * Binds the C-compatible RMR immutable offset graph to one validated ZIP STORE payload.
 *
 * Only BASE_RELATIVE is accepted: the archive may move and each process may receive another virtual
 * mapping base, while region offsets remain stable. No physical-address claim is inferred.
 */
class ZiprafStaticLayoutBinding private constructor(
    val entry: ZiprafStoredEntry,
    val manifest: ZiprafStaticLayoutManifest,
    val mappingEpoch: Long,
    private val runtime: ZiprafDirectRuntime,
    private val bindingIdentity: Any = Any()
) : Closeable {
    val manifestSignature: Long = manifest.signature()
    val manifestSignatureHex: String = manifest.signatureHex()

    init {
        require(mappingEpoch >= 0)
        require(manifest.basePolicy == ZiprafLayoutBasePolicy.BASE_RELATIVE) {
            "ZIPRAF supports only BASE_RELATIVE; fixed virtual/physical claims require another runtime"
        }
        require(manifest.totalSize <= entry.extent.payloadSize) {
            "static layout exceeds ZIP STORE payload"
        }
        require(manifest.regions.none { it.mobility == ZiprafLayoutMobility.PHYSICAL_FIXED }) {
            "ZIPRAF cannot promote PHYSICAL_FIXED regions"
        }
    }

    fun resolve(
        regionId: Long,
        localOffset: Long,
        length: Int,
        stage: ZiprafMemoryStage = ZiprafMemoryStage.L2_SHARED,
        routeId: Long = 0
    ): ZiprafResolvedSpan {
        require(localOffset >= 0)
        require(length > 0)
        val region = manifest.region(regionId)
        require(region.semanticState == ZiprafLayoutRegionState.PRESENT) {
            "only PRESENT regions can be resolved"
        }
        require(localOffset <= region.size && length.toLong() <= region.size - localOffset) {
            "relative span exceeds region bounds"
        }

        val logicalOffset = Math.addExact(region.offset, localOffset)
        val raw = runtime.window(logicalOffset, stage, routeId)
        require(raw.length >= length) {
            "requested span exceeds the selected runtime stage window"
        }
        val exact = raw.bytes.asReadOnlyBuffer().apply {
            position(0)
            limit(length)
        }.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

        return ZiprafResolvedSpan(
            regionId = regionId,
            localOffset = localOffset,
            logicalOffset = logicalOffset,
            length = length,
            mappingEpoch = mappingEpoch,
            manifestSignatureHex = manifestSignatureHex,
            bytes = exact
        )
    }

    fun canReuseOffsets(candidate: ZiprafStaticLayoutManifest): Boolean =
        manifestSignature == candidate.signature()

    fun canReuseAbsoluteHandle(candidate: ZiprafStaticLayoutBinding): Boolean =
        bindingIdentity === candidate.bindingIdentity && mappingEpoch == candidate.mappingEpoch

    fun receipt(candidateManifest: ZiprafStaticLayoutManifest = manifest): ZiprafStaticLayoutReceipt =
        ZiprafStaticLayoutReceipt(
            entryName = entry.name,
            layoutEpoch = manifest.layoutEpoch,
            mappingEpoch = mappingEpoch,
            manifestSignatureHex = manifestSignatureHex,
            offsetsReusable = canReuseOffsets(candidateManifest),
            absoluteHandlesSessionScoped = true,
            metrics = runtime.metricsSnapshot()
        )

    fun metricsSnapshot(): ZiprafRuntimeMetricsSnapshot = runtime.metricsSnapshot()

    override fun close() = runtime.close()

    companion object {
        @JvmStatic
        @JvmOverloads
        fun open(
            file: File,
            entryName: String,
            manifest: ZiprafStaticLayoutManifest,
            mappingEpoch: Long,
            plan: ZiprafRuntimePlan = ZiprafRuntimePlan(),
            verifyCrc32: Boolean = true
        ): ZiprafStaticLayoutBinding {
            val entry = ZiprafArchiveValidator.parseStoredEntry(file, entryName)
            val runtime = ZiprafDirectRuntime(file, entry.extent, plan)
            try {
                if (verifyCrc32 && !runtime.verifyCrc32()) {
                    throw IllegalArgumentException("ZIP STORE payload CRC-32 verification failed")
                }
                return ZiprafStaticLayoutBinding(
                    entry = entry,
                    manifest = manifest,
                    mappingEpoch = mappingEpoch,
                    runtime = runtime
                )
            } catch (failure: Throwable) {
                runtime.close()
                throw failure
            }
        }
    }
}
