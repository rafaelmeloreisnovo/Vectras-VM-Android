package com.vectras.vm.vectra

import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main() {
    val checks = linkedMapOf<String, Boolean>()
    val archive = File.createTempFile("zipraf-kat", ".zip")
    try {
        val first = ByteArray(4096) { (it * 31).toByte() }
        val second = "RAFAELIA-DIRECT-RUNTIME".toByteArray()
        writeStoredArchive(
            archive,
            linkedMapOf(
                "runtime/core.bin" to first,
                "runtime/meta.txt" to second
            )
        )

        val parsed = ZiprafArchiveValidator.parseStoredEntry(archive, "runtime/core.bin")
        checks["central_directory"] =
            parsed.validationLevel == ZiprafValidationLevel.CENTRAL_DIRECTORY
        checks["payload_size"] = parsed.extent.payloadSize == first.size.toLong()

        val plan = ZiprafRuntimePlan(
            bufferBytes = 512,
            l1WindowBytes = 64,
            l2WindowBytes = 1024,
            coreCount = 8
        )
        ZiprafDirectRuntime.openValidated(
            file = archive,
            entryName = "runtime/core.bin",
            plan = plan
        ).use { runtime ->
            val l1 = runtime.window(63, ZiprafMemoryStage.L1_HOT, routeId = 17)
            val reused = runtime.window(127, ZiprafMemoryStage.L1_HOT, routeId = 18)
            checks["window_length"] = l1.length == 64
            checks["lane"] = l1.coreLane == 1
            checks["window_content"] =
                (l1.bytes.get(0).toInt() and 0xff) == (first[63].toInt() and 0xff)
            checks["window_reused_content"] =
                (reused.bytes.get(0).toInt() and 0xff) == (first[127].toInt() and 0xff)
            checks["crc"] = runtime.verifyCrc32()

            val metrics = runtime.metricsSnapshot()
            checks["bounded_mapping"] = metrics.bytesMapped <= plan.l2WindowBytes.toLong()
            checks["mapping_cache_reuse"] = metrics.mapOperations == 1L && metrics.mapReuseHits >= 1L
            checks["crc_bytes_accounted"] = metrics.crcBytesRead == first.size.toLong() * 2L
            checks["latency_percentiles"] =
                metrics.mappingSamples == 1 &&
                    metrics.mapLatencyP50Nanos <= metrics.mapLatencyP95Nanos &&
                    metrics.mapLatencyP95Nanos <= metrics.mapLatencyP99Nanos
        }

        var scanChecksum = 0L
        ZiprafDirectStoreSession.open(
            archive,
            "runtime/core.bin",
            plan
        ).use { session ->
            val scan = session.scan(
                stage = ZiprafMemoryStage.L1_HOT,
                routeSeed = 0,
                startOffset = 17,
                maxBytes = 1000
            ) { window ->
                while (window.bytes.hasRemaining()) {
                    scanChecksum += window.bytes.get().toInt() and 0xff
                }
            }
            checks["scan_bytes"] = scan.bytesVisited == 1000L
            checks["scan_windows"] = scan.windowCount == 16
            checks["scan_lanes"] = scan.laneMask == 0xff
            checks["scan_checksum"] = scanChecksum == first.copyOfRange(17, 1017)
                .sumOf { it.toInt() and 0xff }.toLong()
            checks["session_metrics"] = session.metricsSnapshot().mapReuseHits >= 14L
        }

        val manifest = ZiprafStaticLayoutManifest(
            layoutEpoch = 42,
            totalSize = first.size.toLong(),
            baseAlignment = 64,
            regions = listOf(
                ZiprafStaticRegion(
                    regionId = 1,
                    offset = 0,
                    size = 1024,
                    alignment = 64,
                    mobility = ZiprafLayoutMobility.FIXED_OFFSET,
                    semanticState = ZiprafLayoutRegionState.PRESENT,
                    flags = ZiprafLayoutFlags.READ_ONLY or
                        ZiprafLayoutFlags.NO_RELOCATION_TABLE
                ),
                ZiprafStaticRegion(
                    regionId = 2,
                    offset = 1024,
                    size = 2048,
                    alignment = 64,
                    mobility = ZiprafLayoutMobility.REMAP_ONLY,
                    semanticState = ZiprafLayoutRegionState.PRESENT
                ),
                ZiprafStaticRegion(
                    regionId = 3,
                    offset = 3072,
                    size = 1024,
                    alignment = 64,
                    mobility = ZiprafLayoutMobility.REMAP_ONLY,
                    semanticState = ZiprafLayoutRegionState.FAULT
                )
            )
        )
        checks["c_compatible_signature"] = manifest.signatureHex() == "dc16075f7047df36"

        ZiprafStaticLayoutBinding.open(
            file = archive,
            entryName = "runtime/core.bin",
            manifest = manifest,
            mappingEpoch = 7,
            plan = plan
        ).use { binding ->
            val span = binding.resolve(
                regionId = 2,
                localOffset = 7,
                length = 64,
                stage = ZiprafMemoryStage.L1_HOT,
                routeId = 9
            )
            checks["layout_resolve"] = span.logicalOffset == 1031L
            checks["layout_content"] =
                (span.bytes.get(0).toInt() and 0xff) == (first[1031].toInt() and 0xff)
            checks["layout_epoch"] = span.mappingEpoch == 7L
            checks["offset_reuse"] = binding.canReuseOffsets(manifest.copy())
            checks["fault_region_rejected"] = runCatching {
                binding.resolve(3, 0, 1, ZiprafMemoryStage.L1_HOT)
            }.isFailure
            val receipt = binding.receipt()
            checks["receipt_claim_limited"] = !receipt.claimAllowed
            checks["receipt_signature"] = receipt.manifestSignatureHex == "dc16075f7047df36"
        }

        checks["overlap_rejected"] = runCatching {
            ZiprafStaticLayoutManifest(
                layoutEpoch = 1,
                totalSize = 128,
                baseAlignment = 8,
                regions = listOf(
                    ZiprafStaticRegion(1, 0, 64, 8),
                    ZiprafStaticRegion(2, 32, 64, 8)
                )
            )
        }.isFailure
        checks["physical_fixed_rejected"] = runCatching {
            val physical = manifest.copy(basePolicy = ZiprafLayoutBasePolicy.FIXED_PHYSICAL)
            ZiprafStaticLayoutBinding.open(
                archive,
                "runtime/core.bin",
                physical,
                mappingEpoch = 8,
                plan = plan
            ).close()
        }.isFailure

        val crc = CRC32().apply { update(first) }.value
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(first)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        ZiprafDirectPolicyVerifier.open(
            archive,
            ZiprafDirectEntryPolicy(
                entryName = "runtime/core.bin",
                maxPayloadBytes = 8192,
                expectedPayloadBytes = first.size.toLong(),
                expectedCrc32 = crc,
                expectedSha256Hex = sha
            ),
            plan
        ).use { opened ->
            checks["policy_size"] = opened.evidence.payloadBytes == first.size.toLong()
            checks["policy_crc"] = opened.evidence.crc32 == crc
            checks["policy_sha256"] = opened.evidence.sha256Hex == sha
        }
        checks["policy_wrong_sha_rejected"] = runCatching {
            ZiprafDirectPolicyVerifier.open(
                archive,
                ZiprafDirectEntryPolicy(
                    entryName = "runtime/core.bin",
                    maxPayloadBytes = 8192,
                    expectedSha256Hex = "00".repeat(32)
                ),
                plan
            ).close()
        }.isFailure

        checks["missing_entry_rejected"] = runCatching {
            ZiprafArchiveValidator.parseStoredEntry(archive, "missing.bin")
        }.isFailure

        val passed = checks.values.all { it }
        println(buildJson(checks, passed))
        check(passed) { "ZIPRAF standalone KAT failed" }
    } finally {
        archive.delete()
    }
}

private fun writeStoredArchive(file: File, entries: LinkedHashMap<String, ByteArray>) {
    ZipOutputStream(file.outputStream().buffered()).use { zip ->
        entries.forEach { (name, payload) ->
            val crc = CRC32().apply { update(payload) }.value
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

private fun buildJson(checks: Map<String, Boolean>, passed: Boolean): String = buildString {
    append("{\n  \"schema\": \"zipraf.kat.v2\",\n  \"status\": \"")
    append(if (passed) "PASS" else "FAIL")
    append("\",\n  \"checks\": {\n")
    checks.entries.forEachIndexed { index, (name, value) ->
        append("    \"").append(name).append("\": ").append(value)
        if (index + 1 < checks.size) append(',')
        append('\n')
    }
    append("  }\n}")
}
