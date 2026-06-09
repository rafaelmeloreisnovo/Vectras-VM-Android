package com.vectras.vm.benchmark;

import org.junit.Test;

import static org.junit.Assert.*;

public class HexGridBenchmarkTest {

    @Test
    public void testMedianOfSortedArray() {
        long[] arr = {1, 2, 3, 4, 5};
        assertEquals(3.0, HexGridBenchmark.median(arr), 0.001);
    }

    @Test
    public void testMedianOfEvenArray() {
        long[] arr = {2, 4, 6, 8};
        assertEquals(5.0, HexGridBenchmark.median(arr), 0.001);
    }

    @Test
    public void testMedianSortsInput() {
        long[] arr = {5, 1, 3, 2, 4};
        assertEquals(3.0, HexGridBenchmark.median(arr), 0.001);
    }

    @Test
    public void testMetricNamesAndUnitsLengths() {
        assertEquals(HexGridBenchmark.METRIC_COUNT, HexGridBenchmark.METRIC_NAMES.length);
        assertEquals(HexGridBenchmark.METRIC_COUNT, HexGridBenchmark.METRIC_UNITS.length);
    }

    @Test
    public void testMetricCount() {
        assertEquals(13, HexGridBenchmark.METRIC_COUNT);
    }

    @Test
    public void testB1MallocFreeReturnsPositive() {
        double result = HexGridBenchmark.runB1();
        assertTrue("B1 should be > 0", result > 0.0);
    }

    @Test
    public void testB3CrcBaselineReturnsPositive() {
        double result = HexGridBenchmark.runB3();
        assertTrue("B3 CRC baseline should be > 0", result > 0.0);
    }

    @Test
    public void testB4Crc32cReturnsPositive() {
        double result = HexGridBenchmark.runB4();
        assertTrue("B4 CRC32C should be > 0", result > 0.0);
    }

    @Test
    public void testM5ToroidalUpdateReturnsPositive() {
        double result = HexGridBenchmark.runM5();
        assertTrue("M5 toroidal update should be > 0", result > 0.0);
    }

    @Test
    public void testH1FractalCoherenceReturnsPositive() {
        double result = HexGridBenchmark.runH1();
        assertTrue("H1 fractal coherence should be > 0", result > 0.0);
    }

    @Test
    public void testH2BiTRafEvolveReturnsPositive() {
        double result = HexGridBenchmark.runH2();
        assertTrue("H2 BiTRaf evolve should be > 0", result > 0.0);
    }

    @Test
    public void testH3AttentionStepReturnsPositive() {
        double result = HexGridBenchmark.runH3();
        assertTrue("H3 attention step should be > 0", result > 0.0);
    }

    @Test
    public void testH4ZiprafPackReturnsPositive() {
        double result = HexGridBenchmark.runH4();
        assertTrue("H4 zipraf pack should be > 0", result > 0.0);
    }

    @Test
    public void testH5SyncHashReturnsPositive() {
        double result = HexGridBenchmark.runH5();
        assertTrue("H5 sync hash should be > 0", result > 0.0);
    }

    @Test
    public void testRunAllReturnsAllMetrics() {
        HexGridBenchmark.HexGridResult result = HexGridBenchmark.runAll();
        assertNotNull(result);
        assertEquals(HexGridBenchmark.METRIC_COUNT, result.medians.length);
        for (int i = 0; i < HexGridBenchmark.METRIC_COUNT; i++) {
            assertTrue("metric " + HexGridBenchmark.METRIC_NAMES[i] + " should be >= 0",
                    result.medians[i] >= 0.0);
        }
    }

    @Test
    public void testResultToStringContainsMetricNames() {
        HexGridBenchmark.HexGridResult result = HexGridBenchmark.runAll();
        String s = result.toString();
        assertTrue(s.contains("B1_MALLOC_FREE_64B"));
        assertTrue(s.contains("M5_T7_TOROIDAL_UPDATE"));
        assertTrue(s.contains("H2_BITRAF_EVOLVE"));
    }
}
