package com.vectras.vm.benchmark;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BenchmarkSuiteProgressWatchdogTest {

    @Test
    public void classifiesExecutionPhasesWithoutInventingMetricCounts() {
        assertEquals(30, BenchmarkSuiteProgressWatchdog.classifyMethod("benchCpuIntegerAdd").progress);
        assertEquals(42, BenchmarkSuiteProgressWatchdog.classifyMethod("benchCpuMtCas").progress);
        assertEquals(52, BenchmarkSuiteProgressWatchdog.classifyMethod("benchMemRandomRead").progress);
        assertEquals(66, BenchmarkSuiteProgressWatchdog.classifyMethod("benchStorageRealSequentialWrite").progress);
        assertEquals(76, BenchmarkSuiteProgressWatchdog.classifyMethod("benchIntegrityCrc32c").progress);
        assertEquals(82, BenchmarkSuiteProgressWatchdog.classifyMethod("benchEmuTimerPrecision").progress);
    }

    @Test
    public void stackClassificationUsesMostAdvancedObservedPhase() {
        StackTraceElement[] stack = {
                new StackTraceElement("x", "benchmarkMedian", "x.java", 1),
                new StackTraceElement("x", "benchIntegrityHashMix", "x.java", 2),
                new StackTraceElement("x", "runAllBenchmarks", "x.java", 3)
        };
        BenchmarkSuiteProgressWatchdog.Phase phase = BenchmarkSuiteProgressWatchdog.classify(stack);
        assertEquals(76, phase.progress);
        assertEquals("Integrity/parity metrics", phase.label);
    }
}
