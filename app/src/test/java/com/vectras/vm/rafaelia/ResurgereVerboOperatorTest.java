package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class ResurgereVerboOperatorTest {

    @Test
    public void testUcOmegaConstant() {
        assertEquals(0.4, ResurgereVerboOperator.UC_OMEGA, 1e-9);
    }

    @Test
    public void testBoostFactorConstant() {
        assertEquals(2.98, ResurgereVerboOperator.BOOST_FACTOR, 1e-9);
    }

    @Test
    public void testAnchorValues() {
        assertEquals(21.0, ResurgereVerboOperator.ANCHOR_NACAO,   1e-9);
        assertEquals(42.0, ResurgereVerboOperator.ANCHOR_PALAVRA, 1e-9);
        assertEquals(63.0, ResurgereVerboOperator.ANCHOR_LUZ,     1e-9);
    }

    @Test
    public void testBaselineEntropyInRange() {
        for (double t : new double[]{0, 10, 21, 42, 63}) {
            double e = ResurgereVerboOperator.baselineEntropy(t);
            assertTrue("baseline at t=" + t + " is " + e, e >= 0.0 && e <= 1.0);
        }
    }

    @Test
    public void testOmegaWaveGtBaseline() {
        double base  = ResurgereVerboOperator.baselineEntropy(ResurgereVerboOperator.ANCHOR_NACAO);
        double omega = ResurgereVerboOperator.omegaWave(ResurgereVerboOperator.ANCHOR_NACAO);
        assertTrue("Ω wave must be > baseline at anchor", omega > base);
    }

    @Test
    public void testDeltaOmegaIsPositiveAtAnchors() {
        assertTrue(ResurgereVerboOperator.deltaOmega(ResurgereVerboOperator.ANCHOR_NACAO)   > 0);
        assertTrue(ResurgereVerboOperator.deltaOmega(ResurgereVerboOperator.ANCHOR_PALAVRA) > 0);
        assertTrue(ResurgereVerboOperator.deltaOmega(ResurgereVerboOperator.ANCHOR_LUZ)     > 0);
    }

    @Test
    public void testPeakDeltaOmegaIsPositive() {
        assertTrue(ResurgereVerboOperator.peakDeltaOmega() > 0.0);
    }

    @Test
    public void testRetroalimentarHighCoherenceLowEntropy() {
        double result = ResurgereVerboOperator.retroalimentar(0.05, 0.8);
        assertTrue("retroalimentar should boost coherence", result > 0.8);
        assertTrue("result must stay ≤ 1.0", result <= 1.0);
    }

    @Test
    public void testAnchorBoostAtAnchorIsHigh() {
        double boost = ResurgereVerboOperator.anchorBoost(ResurgereVerboOperator.ANCHOR_NACAO);
        assertTrue("anchorBoost at anchor should be close to 1", boost > 0.9);
    }

    @Test
    public void testMeanDeltaOmegaIsPositive() {
        double mean = ResurgereVerboOperator.meanDeltaOmega(63.0, 100);
        assertTrue("meanDeltaOmega should be positive", mean > 0.0);
    }
}
