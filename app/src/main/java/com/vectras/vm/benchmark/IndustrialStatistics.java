package com.vectras.vm.benchmark;

import java.util.Arrays;

/**
 * Statistics for one homogeneous benchmark series only.
 *
 * A series is expected to represent repeated measurements of the same metric,
 * workload, input and unit. This class intentionally has no API for pooling
 * unrelated benchmark metrics into a single reproducibility score.
 */
public final class IndustrialStatistics {

    private IndustrialStatistics() {}

    public static final class SeriesSummary {
        public final int n;
        public final double mean;
        public final double median;
        public final double sampleStdDev;
        public final double mad;
        public final double q1;
        public final double q3;
        public final double ci95Low;
        public final double ci95High;
        public final boolean variabilityEstimable;
        public final boolean confidenceIntervalEstimable;

        private SeriesSummary(int n,
                              double mean,
                              double median,
                              double sampleStdDev,
                              double mad,
                              double q1,
                              double q3,
                              double ci95Low,
                              double ci95High,
                              boolean variabilityEstimable,
                              boolean confidenceIntervalEstimable) {
            this.n = n;
            this.mean = mean;
            this.median = median;
            this.sampleStdDev = sampleStdDev;
            this.mad = mad;
            this.q1 = q1;
            this.q3 = q3;
            this.ci95Low = ci95Low;
            this.ci95High = ci95High;
            this.variabilityEstimable = variabilityEstimable;
            this.confidenceIntervalEstimable = confidenceIntervalEstimable;
        }

        /** CV is defined only for a positive ratio-scale series with n > 1. */
        public double coefficientOfVariationPercent() {
            if (!variabilityEstimable || mean <= 0.0) return Double.NaN;
            return (sampleStdDev / mean) * 100.0;
        }
    }

    public static SeriesSummary summarize(long[] samples) {
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("At least one sample is required");
        }
        for (long sample : samples) {
            if (sample < 0L) throw new IllegalArgumentException("Negative samples are not accepted");
        }

        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        int n = sorted.length;

        double mean = mean(sorted);
        double median = quantile(sorted, 0.5);
        double q1 = quantile(sorted, 0.25);
        double q3 = quantile(sorted, 0.75);

        double[] deviations = new double[n];
        for (int i = 0; i < n; i++) deviations[i] = Math.abs(sorted[i] - median);
        Arrays.sort(deviations);
        double mad = quantile(deviations, 0.5);

        if (n == 1) {
            return new SeriesSummary(n, mean, median, Double.NaN, mad, q1, q3,
                Double.NaN, Double.NaN, false, false);
        }

        double sumSquared = 0.0;
        for (long sample : sorted) {
            double delta = sample - mean;
            sumSquared += delta * delta;
        }
        double sampleStdDev = Math.sqrt(sumSquared / (n - 1));
        double standardError = sampleStdDev / Math.sqrt(n);
        double tCritical = tCritical95(n - 1);
        double margin = tCritical * standardError;

        return new SeriesSummary(n, mean, median, sampleStdDev, mad, q1, q3,
            mean - margin, mean + margin, true, true);
    }

    private static double mean(long[] values) {
        double sum = 0.0;
        for (long value : values) sum += value;
        return sum / values.length;
    }

    private static double quantile(long[] sorted, double p) {
        if (sorted.length == 1) return sorted[0];
        double index = p * (sorted.length - 1);
        int low = (int) Math.floor(index);
        int high = (int) Math.ceil(index);
        if (low == high) return sorted[low];
        double weight = index - low;
        return sorted[low] * (1.0 - weight) + sorted[high] * weight;
    }

    private static double quantile(double[] sorted, double p) {
        if (sorted.length == 1) return sorted[0];
        double index = p * (sorted.length - 1);
        int low = (int) Math.floor(index);
        int high = (int) Math.ceil(index);
        if (low == high) return sorted[low];
        double weight = index - low;
        return sorted[low] * (1.0 - weight) + sorted[high] * weight;
    }

    /** Two-sided 95% Student-t critical value; normal asymptote for df > 30. */
    private static double tCritical95(int df) {
        final double[] table = {
            0.0,
            12.706, 4.303, 3.182, 2.776, 2.571,
            2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131,
            2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060,
            2.056, 2.052, 2.048, 2.045, 2.042
        };
        if (df <= 0) return Double.NaN;
        if (df < table.length) return table[df];
        return 1.96;
    }
}
