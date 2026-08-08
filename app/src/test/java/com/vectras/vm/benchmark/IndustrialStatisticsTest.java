package com.vectras.vm.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IndustrialStatisticsTest {

    @Test
    public void oneSampleDoesNotClaimReproducibility() {
        IndustrialStatistics.SeriesSummary summary = IndustrialStatistics.summarize(new long[]{100L});
        assertEquals(1, summary.n);
        assertFalse(summary.variabilityEstimable);
        assertFalse(summary.confidenceIntervalEstimable);
        assertTrue(Double.isNaN(summary.sampleStdDev));
        assertTrue(Double.isNaN(summary.coefficientOfVariationPercent()));
    }

    @Test
    public void repeatedHomogeneousSeriesUsesSampleVariance() {
        IndustrialStatistics.SeriesSummary summary = IndustrialStatistics.summarize(
            new long[]{100L, 110L, 90L, 100L, 100L});
        assertEquals(5, summary.n);
        assertEquals(100.0, summary.mean, 0.0001);
        assertEquals(100.0, summary.median, 0.0001);
        assertTrue(summary.sampleStdDev > 0.0);
        assertTrue(summary.ci95Low < summary.mean);
        assertTrue(summary.ci95High > summary.mean);
        assertTrue(summary.coefficientOfVariationPercent() > 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySeriesFailsClosed() {
        IndustrialStatistics.summarize(new long[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeSampleFailsClosed() {
        IndustrialStatistics.summarize(new long[]{100L, -1L});
    }
}
