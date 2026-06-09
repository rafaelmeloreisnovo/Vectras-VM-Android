package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

/**
 * FrequencyResonanceGrid — FRACTAL mandala frequency resonance (Image 11).
 *
 * <pre>
 * Center:   963 Hz   — Solfeggio DNA repair / transformation
 * Inner:    333 Hz   — Trinity harmonic
 * Outer:    999 Hz   — Completion / highest resonance
 *
 * Inner ring: 10 + 7 = 17 cycles
 * Outer ring: 10 + 12 = 22 harmonics
 *
 * Nodes: RETROALIMENTACAO (feedback amplifier), ETICA (ethical gate)
 *
 * Coherence at frequency f: C(f) = 1 − |f − f_center| / f_center × dampingFactor
 * Retroalimentação: amplifies signal by (1 + SPIRAL) when coherence > PHI−1
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-FREQ
 */
public final class FrequencyResonanceGrid {

    // ─── Grid constants ───────────────────────────────────────────────────────
    public static final double FREQ_CENTER  = 963.0;   // Hz
    public static final double FREQ_INNER   = 333.0;   // Hz
    public static final double FREQ_OUTER   = 999.0;   // Hz
    public static final int    CYCLES_INNER = 17;      // 10 + 7
    public static final int    HARMONICS_OUTER = 22;   // 10 + 12

    /** Harmonic series: multiples of 963 within [333, 999]. */
    private static final double[] HARMONICS = buildHarmonics();

    // ─── Constructor ──────────────────────────────────────────────────────────

    private FrequencyResonanceGrid() {}

    // ─── Coherence ────────────────────────────────────────────────────────────

    /**
     * Coherence of a signal at frequency f against the center grid.
     * C(f) = exp(−|f − f_nearest| / f_center × 2π)
     */
    public static double coherenceAt(double freqHz) {
        double nearest = nearestHarmonic(freqHz);
        double distance = Math.abs(freqHz - nearest);
        return Math.exp(-distance / FREQ_CENTER * 2.0 * RafaeliaKernelV22.PI);
    }

    /**
     * Resonance score: combines distance to center, inner, outer harmonics.
     * Score ∈ [0,1]; 1.0 = perfect resonance with all three rings.
     */
    public static double resonanceScore(double freqHz) {
        double cCenter = coherenceAt(freqHz);
        double cInner  = Math.exp(-Math.abs(freqHz - FREQ_INNER)  / FREQ_CENTER * RafaeliaKernelV22.PI);
        double cOuter  = Math.exp(-Math.abs(freqHz - FREQ_OUTER)  / FREQ_CENTER * RafaeliaKernelV22.PI);
        return (cCenter * RafaeliaKernelV22.PHI + cInner + cOuter) / (RafaeliaKernelV22.PHI + 2.0);
    }

    // ─── Harmonic series ──────────────────────────────────────────────────────

    /** All harmonics in the grid: 963/n and 963×n for n in [1..HARMONICS_OUTER]. */
    private static double[] buildHarmonics() {
        double[] h = new double[HARMONICS_OUTER * 2 + 3];
        int idx = 0;
        h[idx++] = FREQ_CENTER;
        h[idx++] = FREQ_INNER;
        h[idx++] = FREQ_OUTER;
        for (int n = 1; n <= HARMONICS_OUTER; n++) {
            h[idx++] = FREQ_CENTER / n;
            h[idx++] = FREQ_CENTER * n;
        }
        return h;
    }

    public static double nearestHarmonic(double freqHz) {
        double nearest = HARMONICS[0];
        double minDist = Math.abs(freqHz - nearest);
        for (double h : HARMONICS) {
            double d = Math.abs(freqHz - h);
            if (d < minDist) { minDist = d; nearest = h; }
        }
        return nearest;
    }

    // ─── Retroalimentação node ────────────────────────────────────────────────

    /**
     * RETROALIMENTACAO: amplifies value when coherence is above PHI−1 threshold.
     * amplified = value × (1 + SPIRAL × coherence)
     */
    public static double retroalimentacao(double value, double coherence) {
        double safe = Math.max(0.0, Math.min(1.0, coherence));
        if (safe < RafaeliaKernelV22.PHI - 1.0) return value; // below threshold, no amplification
        double amplified = value * (1.0 + RafaeliaKernelV22.SPIRAL * safe);
        return Math.min(1.0, amplified);
    }

    // ─── ÉTICA node ───────────────────────────────────────────────────────────

    /**
     * ETICA gate: passes value only if resonance with the ethical frequency band is strong enough.
     * Ethical band is centered on FREQ_CENTER (963 Hz = integration/healing).
     * Returns value if resonanceScore(freqHz) ≥ RafaeliaKernelV22.PHI−1, else 0.
     */
    public static double ethicalGate(double value, double freqHz) {
        double resonance = resonanceScore(freqHz);
        return resonance >= (RafaeliaKernelV22.PHI - 1.0) ? value : 0.0;
    }

    // ─── Cycle traversal ──────────────────────────────────────────────────────

    /**
     * Step through CYCLES_INNER inner cycles, applying retroalimentação at each step.
     * @param seed  initial value [0,1]
     * @param freqHz operating frequency
     * @return value after 17 inner cycles
     */
    public static double innerCycles(double seed, double freqHz) {
        double v = seed;
        double coh = coherenceAt(freqHz);
        for (int i = 0; i < CYCLES_INNER; i++) {
            v = retroalimentacao(v, coh);
            coh = Math.min(1.0, coh * (1.0 + 1.0 / CYCLES_INNER)); // slight coherence buildup
        }
        return v;
    }

    /**
     * Step through HARMONICS_OUTER outer harmonics, applying ethical gate.
     * @param seed  initial value [0,1]
     * @param freqHz operating frequency
     * @return value after 22 outer harmonic passes
     */
    public static double outerHarmonics(double seed, double freqHz) {
        double v = seed;
        for (int i = 0; i < HARMONICS_OUTER; i++) {
            double harmonicFreq = FREQ_CENTER * (i + 1.0) / HARMONICS_OUTER;
            v = ethicalGate(v, harmonicFreq);
            if (v == 0.0) break; // gate blocked
        }
        return v;
    }

    // ─── Mandala info ─────────────────────────────────────────────────────────

    public static double[] harmonics() { return HARMONICS.clone(); }

    @NonNull
    public static String summary() {
        return String.format(
                "FreqGrid[center=%.0fHz inner=%.0fHz outer=%.0fHz cycles=%d harmonics=%d]",
                FREQ_CENTER, FREQ_INNER, FREQ_OUTER, CYCLES_INNER, HARMONICS_OUTER);
    }
}
