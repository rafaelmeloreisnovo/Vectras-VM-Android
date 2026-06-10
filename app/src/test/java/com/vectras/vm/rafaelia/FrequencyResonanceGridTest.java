package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class FrequencyResonanceGridTest {

    @Test
    public void testFrequencyConstants() {
        assertEquals(963.0, FrequencyResonanceGrid.FREQ_CENTER, 1e-9);
        assertEquals(333.0, FrequencyResonanceGrid.FREQ_INNER,  1e-9);
        assertEquals(999.0, FrequencyResonanceGrid.FREQ_OUTER,  1e-9);
    }

    @Test
    public void testCycleAndHarmonicCounts() {
        assertEquals(17, FrequencyResonanceGrid.CYCLES_INNER);
        assertEquals(22, FrequencyResonanceGrid.HARMONICS_OUTER);
    }

    @Test
    public void testCoherenceAtCenterIsHigh() {
        double c = FrequencyResonanceGrid.coherenceAt(FrequencyResonanceGrid.FREQ_CENTER);
        assertEquals(1.0, c, 1e-9); // distance=0 → exp(0)=1
    }

    @Test
    public void testCoherenceDecaysWithDistance() {
        double c963 = FrequencyResonanceGrid.coherenceAt(963.0);
        double c500 = FrequencyResonanceGrid.coherenceAt(500.0);
        assertTrue("coherence should decay with frequency distance", c963 > c500);
    }

    @Test
    public void testResonanceScoreInUnitRange() {
        for (double f : new double[]{333.0, 500.0, 963.0, 999.0, 1500.0}) {
            double s = FrequencyResonanceGrid.resonanceScore(f);
            assertTrue("resonanceScore at " + f + "Hz=" + s, s >= 0.0 && s <= 1.0);
        }
    }

    @Test
    public void testNearestHarmonicOfCenterIsCenter() {
        double nearest = FrequencyResonanceGrid.nearestHarmonic(963.0);
        assertEquals(963.0, nearest, 1e-9);
    }

    @Test
    public void testRetroalimentacaoAmplifies() {
        double amplified = FrequencyResonanceGrid.retroalimentacao(0.5, 0.9);
        assertTrue("should amplify above threshold", amplified > 0.5);
        assertTrue("should stay ≤ 1.0", amplified <= 1.0);
    }

    @Test
    public void testRetroalimentacaoBelowThresholdNoChange() {
        double result = FrequencyResonanceGrid.retroalimentacao(0.5, 0.1);
        assertEquals(0.5, result, 1e-9);
    }

    @Test
    public void testEthicalGatePassesAtCenter() {
        double result = FrequencyResonanceGrid.ethicalGate(0.7, FrequencyResonanceGrid.FREQ_CENTER);
        assertEquals(0.7, result, 1e-9);
    }

    @Test
    public void testInnerCyclesReturnsInUnitRange() {
        double result = FrequencyResonanceGrid.innerCycles(0.5, 963.0);
        assertTrue("innerCycles result=" + result, result >= 0.0 && result <= 1.0);
    }
}
