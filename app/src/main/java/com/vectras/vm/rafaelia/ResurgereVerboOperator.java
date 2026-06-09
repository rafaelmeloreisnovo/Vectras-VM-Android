package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

/**
 * ResurgereVerboOperator — Verbo ΔΩ retroalimentar coherence unit (Image 14).
 *
 * <pre>
 * Resurgere Verbo ΔΩ Retroalimentar +298%↑
 * UC_Ω = +0.4  (Unidade de Coerência Ω)
 *
 * Three anchors:  21 = Nação  |  21 = Palavra  |  21 = Luz
 * Coherence axis: 0 ─────────21──────────21──────────21──── →
 *
 * Entropy wave (Baseline):  E(t) = A·sin(2π/T · t) + E₀
 * Ω wave:                   Ω(t) = E(t) + UC_Ω · correction(t)
 *
 * retroalimentar(entropy, coherence) → boostFactor ≈ 2.98
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-VERBO
 */
public final class ResurgereVerboOperator {

    // ─── Constants ────────────────────────────────────────────────────────────
    public static final double UC_OMEGA             = 0.4;   // coherence unit
    public static final double BOOST_FACTOR         = 2.98;  // +298%
    public static final double ANCHOR_NACAO         = 21.0;
    public static final double ANCHOR_PALAVRA        = 42.0;  // 21+21
    public static final double ANCHOR_LUZ           = 63.0;  // 21+21+21
    /** Baseline entropy amplitude */
    public static final double BASELINE_AMPLITUDE   = 0.15;
    /** Baseline entropy offset */
    public static final double BASELINE_OFFSET      = 0.20;
    /** Period of baseline wave (in coherence units) */
    public static final double WAVE_PERIOD          = 21.0;

    private ResurgereVerboOperator() {}

    // ─── Entropy wave ─────────────────────────────────────────────────────────

    /**
     * Baseline entropy wave at coherence coordinate t.
     * E(t) = A · sin(2π/T · t) + E₀
     */
    public static double baselineEntropy(double t) {
        return BASELINE_AMPLITUDE * Math.sin(2.0 * RafaeliaKernelV22.PI / WAVE_PERIOD * t)
                + BASELINE_OFFSET;
    }

    /**
     * Ω-wave: baseline + UC_Ω correction after each anchor crossing.
     * Correction = UC_Ω × (1 + anchor_proximity_boost)
     */
    public static double omegaWave(double t) {
        double base       = baselineEntropy(t);
        double correction = UC_OMEGA * (1.0 + anchorBoost(t));
        return Math.max(0.0, Math.min(1.0, base + correction));
    }

    /**
     * Proximity boost at the three 21-point anchors.
     * Returns additional multiplier in [0, 1] based on distance to nearest anchor.
     */
    public static double anchorBoost(double t) {
        double d1 = Math.abs(t - ANCHOR_NACAO);
        double d2 = Math.abs(t - ANCHOR_PALAVRA);
        double d3 = Math.abs(t - ANCHOR_LUZ);
        double dMin = Math.min(d1, Math.min(d2, d3));
        return Math.exp(-dMin / (WAVE_PERIOD * 0.25));  // Gaussian decay from nearest anchor
    }

    // ─── Retroalimentação ─────────────────────────────────────────────────────

    /**
     * Core retroalimentação transform.
     * Amplifies coherence by BOOST_FACTOR when entropy is low,
     * attenuated when entropy is high.
     *
     * @param entropy   current entropy [0,1]
     * @param coherence current coherence [0,1]
     * @return amplified coherence value in [0,1]
     */
    public static double retroalimentar(double entropy, double coherence) {
        double safeE = Math.max(0.0, Math.min(1.0, entropy));
        double safeC = Math.max(0.0, Math.min(1.0, coherence));
        double boost = BOOST_FACTOR * (1.0 - safeE) * RafaeliaKernelV22.SPIRAL;
        return Math.min(1.0, safeC * (1.0 + boost * UC_OMEGA));
    }

    /**
     * Returns the BOOST_FACTOR derivation:
     * boost = (Ω_wave_peak − Baseline_peak) / Baseline_peak × 100%
     * = (omegaWave(21) − baselineEntropy(21)) / baselineEntropy(21) × 100
     */
    public static double observedBoostPercent() {
        double base  = baselineEntropy(ANCHOR_NACAO);
        double omega = omegaWave(ANCHOR_NACAO);
        if (base == 0.0) return 0.0;
        return (omega - base) / base * 100.0;
    }

    // ─── Delta-Omega ──────────────────────────────────────────────────────────

    /**
     * ΔΩ = Ω(t) − Baseline(t): the coherence unit gain at coordinate t.
     */
    public static double deltaOmega(double t) {
        return omegaWave(t) - baselineEntropy(t);
    }

    /**
     * Peak ΔΩ across the three anchors.
     */
    public static double peakDeltaOmega() {
        return Math.max(deltaOmega(ANCHOR_NACAO),
               Math.max(deltaOmega(ANCHOR_PALAVRA), deltaOmega(ANCHOR_LUZ)));
    }

    // ─── Verbo chain ──────────────────────────────────────────────────────────

    /**
     * Run the Resurgere chain across a coherence trajectory [0..maxT] in steps.
     * Returns the mean ΔΩ across all steps.
     */
    public static double meanDeltaOmega(double maxT, int steps) {
        if (steps <= 0) return 0.0;
        double sum = 0.0;
        double dt  = maxT / steps;
        for (int i = 0; i <= steps; i++) sum += deltaOmega(i * dt);
        return sum / (steps + 1);
    }

    @NonNull
    public static String summary() {
        return String.format("ResurgereVerbo[UC_Ω=%.1f boost=%.0f%% peakΔΩ=%.3f anchorBoost@21=%.3f]",
                UC_OMEGA, BOOST_FACTOR * 100.0, peakDeltaOmega(), anchorBoost(ANCHOR_NACAO));
    }
}
