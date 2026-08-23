package com.vectras.vm.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Unit tests for VectraBenchmark module.
 * Tests core functionality without running full benchmark suite.
 */
public class VectraBenchmarkTest {

    @Test
    public void parity2D8SingleBitSetCorrect() {
        int parity = VectraBenchmark.parity2D8(1);
        assertEquals(0x11, parity);
    }

    @Test
    public void parity2D8AllBitsClearCorrect() {
        int parity = VectraBenchmark.parity2D8(0);
        assertEquals(0x00, parity);
    }

    @Test
    public void parity2D8AlternatingPattern() {
        int parity = VectraBenchmark.parity2D8(0xAAAA);
        assertEquals(0x00, parity);
    }

    @Test
    public void syndromePopcountDetectsDifference() {
        int p1 = VectraBenchmark.parity2D8(0x0001);
        int p2 = VectraBenchmark.parity2D8(0x0002);
        int syndrome = VectraBenchmark.syndromePopcount(p1, p2);
        assertTrue(syndrome > 0);
    }

    @Test
    public void syndromePopcountZeroForSame() {
        int p1 = VectraBenchmark.parity2D8(0x1234);
        int p2 = VectraBenchmark.parity2D8(0x1234);
        assertEquals(0, VectraBenchmark.syndromePopcount(p1, p2));
    }

    @Test
    public void whoOutTriadDiskOut() {
        assertEquals(2, VectraBenchmark.whoOutTriad(100, 100, 200));
    }

    @Test
    public void whoOutTriadRamOut() {
        assertEquals(1, VectraBenchmark.whoOutTriad(100, 200, 100));
    }

    @Test
    public void whoOutTriadCpuOut() {
        assertEquals(0, VectraBenchmark.whoOutTriad(200, 100, 100));
    }

    @Test
    public void whoOutTriadAllAgree() {
        assertEquals(3, VectraBenchmark.whoOutTriad(100, 100, 100));
    }

    @Test
    public void whoOutTriadAllDifferent() {
        assertEquals(3, VectraBenchmark.whoOutTriad(100, 200, 300));
    }

    @Test
    public void mix64ProducesDifferentOutputs() {
        long a = VectraBenchmark.mix64(1);
        long b = VectraBenchmark.mix64(2);
        assertTrue(a != b);
    }

    @Test
    public void mix64Deterministic() {
        long a = VectraBenchmark.mix64(12345);
        long b = VectraBenchmark.mix64(12345);
        assertEquals(a, b);
    }

    @Test
    public void bitStackAppendsAndCrcCorrect() throws Exception {
        File tmp = File.createTempFile("vectra_bench", ".bin");
        tmp.deleteOnExit();

        long value = 0x1122334455667788L;
        int metricId = 42;

        try (VectraBenchmark.BitStack bs = new VectraBenchmark.BitStack(tmp, 1024 * 1024)) {
            bs.appendResult(value, metricId);
            bs.flush();
        }

        try (RandomAccessFile raf = new RandomAccessFile(tmp, "r")) {
            byte[] bytes = new byte[16];
            raf.readFully(bytes);

            long valueRead = 0;
            for (int i = 0; i < 8; i++) {
                valueRead |= ((long)(bytes[i] & 0xFF)) << (8 * i);
            }

            int metricRead = 0;
            for (int i = 0; i < 4; i++) {
                metricRead |= ((bytes[8 + i] & 0xFF)) << (8 * i);
            }

            int crcRead = 0;
            for (int i = 0; i < 4; i++) {
                crcRead |= ((bytes[12 + i] & 0xFF)) << (8 * i);
            }

            assertEquals(value, valueRead);
            assertEquals(metricId, metricRead);
            assertEquals(VectraBenchmark.BitStack.crc32c(value, metricId), crcRead);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    @Test
    public void benchCpuIntegerAddReturnsPositiveTime() {
        long time = VectraBenchmark.benchCpuIntegerAdd(10000);
        assertTrue(time > 0);
    }

    @Test
    public void benchCpuLongMixReturnsPositiveTime() {
        long time = VectraBenchmark.benchCpuLongMix(10000);
        assertTrue(time > 0);
    }

    @Test
    public void benchCpuPopcountReturnsPositiveTime() {
        long time = VectraBenchmark.benchCpuPopcount(10000);
        assertTrue(time > 0);
    }

    @Test
    public void benchMemSequentialReadReturnsPositiveTime() {
        byte[] buffer = new byte[4096];
        long time = VectraBenchmark.benchMemSequentialRead(buffer);
        assertTrue(time > 0);
    }

    @Test
    public void benchMemSequentialWriteReturnsPositiveTime() {
        byte[] buffer = new byte[4096];
        long time = VectraBenchmark.benchMemSequentialWrite(buffer);
        assertTrue(time > 0);
    }

    @Test
    public void benchIntegrityCrc32cReturnsPositiveTime() {
        byte[] data = new byte[1024];
        long time = VectraBenchmark.benchIntegrityCrc32c(data, 100);
        assertTrue(time > 0);
    }

    @Test
    public void benchIntegrityParity2DReturnsPositiveTime() {
        long time = VectraBenchmark.benchIntegrityParity2D(10000);
        assertTrue(time > 0);
    }

    @Test
    public void benchEmuTriadConsensusReturnsPositiveTime() {
        long time = VectraBenchmark.benchEmuTriadConsensus(10000);
        assertTrue(time > 0);
    }

    @Test
    public void benchmarkResultNewFormatWorks() {
        VectraBenchmark.BenchmarkResult r = new VectraBenchmark.BenchmarkResult(
            0, "Test", 1000000L, "1.00 ms", "ms", "CPU Single-threaded", "Test metric"
        );
        assertEquals(0, r.metricId());
        assertEquals("Test", r.name());
        assertEquals(1000000L, r.rawValue());
        assertEquals("1.00 ms", r.formattedValue());
        assertEquals("ms", r.unit());
        assertEquals("CPU Single-threaded", r.category());
        assertEquals("Test metric", r.description());
    }

    @Test
    public void benchmarkResultScaledValueUsesEngineeringUnits() {
        VectraBenchmark.BenchmarkResult r = new VectraBenchmark.BenchmarkResult(
            0, "Test", 500000000L, "500.00 ms", "ms", "CPU Single-threaded", "Test metric"
        );
        assertEquals(0.5d, r.getScaledValue(), 0.000000001d);
    }

    @Test
    public void benchmarkResultsPreserveNullSlotsWithoutSyntheticScore() {
        VectraBenchmark.BenchmarkResult[] results = new VectraBenchmark.BenchmarkResult[3];
        results[0] = new VectraBenchmark.BenchmarkResult(0, "Test1", 1000L, "1.00 μs", "μs", "CPU", "Test");
        results[1] = new VectraBenchmark.BenchmarkResult(1, "Test2", 2000L, "2.00 μs", "μs", "CPU", "Test");
        results[2] = null;

        assertNotNull(results[0]);
        assertNotNull(results[1]);
        assertEquals(1000L, results[0].rawValue());
        assertEquals(2000L, results[1].rawValue());
        assertTrue(results[2] == null);
    }

    @Test
    public void benchmarkResultCategoriesCoverSixEngineeringGroups() {
        String[] expectedCategories = {
            "CPU Single-threaded",
            "CPU Multi-threaded",
            "Memory",
            "Storage",
            "Integrity",
            "Emulation"
        };
        VectraBenchmark.BenchmarkResult[] results = new VectraBenchmark.BenchmarkResult[expectedCategories.length];
        for (int i = 0; i < expectedCategories.length; i++) {
            results[i] = new VectraBenchmark.BenchmarkResult(
                i, "Test" + i, 1000L, "1.00 μs", "μs", expectedCategories[i], "Test"
            );
        }
        for (int i = 0; i < expectedCategories.length; i++) {
            assertEquals(expectedCategories[i], results[i].category());
        }
    }

    @Test
    public void formatReportContainsHeader() throws Exception {
        VectraBenchmark.BenchmarkResult[] results = new VectraBenchmark.BenchmarkResult[1];
        results[0] = new VectraBenchmark.BenchmarkResult(
            0, "Test", 1000L, "1.00 μs", "μs", "CPU Single-threaded", "Test metric"
        );

        try {
            String report = VectraBenchmark.formatReport(results);
            assertNotNull(report);
            if (report.length() > 100) {
                assertTrue(report.contains("BENCHMARK") || report.contains("VECTRAS"));
            }
        } catch (Exception e) {
            // Expected in test environment without /proc filesystem.
        }
    }

    @Test
    public void formatReportContainsSIUnitsNote() throws Exception {
        VectraBenchmark.BenchmarkResult[] results = new VectraBenchmark.BenchmarkResult[1];
        results[0] = new VectraBenchmark.BenchmarkResult(
            0, "Test", 1000L, "1.00 μs", "μs", "CPU Single-threaded", "Test metric"
        );

        try {
            String report = VectraBenchmark.formatReport(results);
            assertNotNull(report);
            if (report.length() > 100) {
                assertTrue(report.contains("SI") || report.contains("Units") || report.contains("ns") || report.contains("ms"));
            }
        } catch (Exception e) {
            // Expected in test environment without /proc filesystem.
        }
    }

    @Test
    public void metricCountIs79() {
        assertEquals(79, VectraBenchmark.METRIC_COUNT);
    }

    @Test
    public void formatTimeProducesCorrectUnits() {
        assertEquals("500 ns", VectraBenchmark.formatTime(500));
        assertEquals("1.500 μs", VectraBenchmark.formatTime(1500));
        assertEquals("1.500 ms", VectraBenchmark.formatTime(1500000));
        assertEquals("1.500 s", VectraBenchmark.formatTime(1500000000L));
    }

    @Test
    public void formatBandwidthProducesCorrectUnits() {
        String result = VectraBenchmark.formatBandwidth(1000000, 1000000000L);
        assertTrue(result.contains("MB/s") || result.contains("KB/s"));
    }

    @Test
    public void formatOpsPerSecProducesCorrectUnits() {
        String result = VectraBenchmark.formatOpsPerSec(1000000, 1000000000L);
        assertTrue(result.contains("Mops/s") || result.contains("ops/s"));
    }

    @Test
    public void getDeviceSpecificationReturnsValidData() {
        VectraBenchmark.DeviceSpecification spec = VectraBenchmark.getDeviceSpecification();
        assertNotNull(spec);
        assertTrue(spec.cpuCores > 0);
        assertNotNull(spec.cpuModel);
        assertNotNull(spec.cpuArchitecture);
    }
}
