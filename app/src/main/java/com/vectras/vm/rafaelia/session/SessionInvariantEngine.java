package com.vectras.vm.rafaelia.session;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.EnumMap;
import java.util.Map;

/**
 * Session Invariant Engine — verifies the 9 canonical RAFAELIA invariants.
 *
 * <p>Invariants (from session review architecture):
 * <pre>
 *   1. SYMMETRY      — Relations preserved under transformations
 *   2. CONSERVATION  — Information, energy and resources remain within bounds
 *   3. SCALE         — Certainty exists at micro and macro levels
 *   4. GEOMETRY      — Structure, vectors and connections are coherent
 *   5. CAUSALITY     — Causes precede observable effects
 *   6. ENTROPY       — Path monitored and growth controlled
 *   7. CYCLES        — Retroalimentations identified and stable
 *   8. LIMITS        — Restrictions, logic and operations are bounded
 *   9. COMPOSITION   — Integrated parts without contradictions
 * </pre>
 *
 * <p>Tesseract Logic: CONSERVATION × CAUSALITY × GEOMETRY × COMPOSITION
 * forms the 4D kernel check — all four must pass for STABLE classification.
 *
 * @author ∆RafaelVerboΩ
 */
public final class SessionInvariantEngine {

    public enum Invariant {
        SYMMETRY, CONSERVATION, SCALE, GEOMETRY, CAUSALITY, ENTROPY, CYCLES, LIMITS, COMPOSITION
    }

    public enum InvariantStatus { OK, WARNING, VIOLATION }

    /** Tesseract kernel: these 4 must all pass for global STABLE */
    private static final Invariant[] TESSERACT = {
            Invariant.CONSERVATION, Invariant.CAUSALITY,
            Invariant.GEOMETRY,     Invariant.COMPOSITION
    };

    // ─── Thresholds ───────────────────────────────────────────────────────────
    private static final double SYMMETRY_TOLERANCE     = 0.05;
    private static final double CONSERVATION_TOLERANCE = 0.02;
    private static final double ENTROPY_GROWTH_MAX     = 0.1;
    private static final double COHERENCE_MIN          = 0.618;  // φ-derived

    private final EnumMap<Invariant, InvariantResult> results =
            new EnumMap<>(Invariant.class);

    private SessionInvariantEngine() {}

    public static SessionInvariantEngine create() { return new SessionInvariantEngine(); }

    // ─── Run all invariant checks ─────────────────────────────────────────────

    /**
     * Evaluate all 9 invariants against the provided snapshot.
     * Returns the review result.
     */
    public InvariantReview evaluate(@NonNull SystemSnapshot snap) {
        results.clear();

        results.put(Invariant.SYMMETRY,     checkSymmetry(snap));
        results.put(Invariant.CONSERVATION, checkConservation(snap));
        results.put(Invariant.SCALE,        checkScale(snap));
        results.put(Invariant.GEOMETRY,     checkGeometry(snap));
        results.put(Invariant.CAUSALITY,    checkCausality(snap));
        results.put(Invariant.ENTROPY,      checkEntropy(snap));
        results.put(Invariant.CYCLES,       checkCycles(snap));
        results.put(Invariant.LIMITS,       checkLimits(snap));
        results.put(Invariant.COMPOSITION,  checkComposition(snap));

        return buildReview();
    }

    public Map<Invariant, InvariantResult> getLastResults() {
        return new EnumMap<>(results);
    }

    // ─── Individual invariant checks ─────────────────────────────────────────

    private InvariantResult checkSymmetry(SystemSnapshot s) {
        // SYMMETRY: relations preserved under transformation
        // Measured via input/output vector delta symmetry
        double asymmetry = Math.abs(s.inputMagnitude - s.outputMagnitude)
                / Math.max(1.0, s.inputMagnitude);
        boolean ok = asymmetry <= SYMMETRY_TOLERANCE;
        double score = 1.0 - Math.min(1.0, asymmetry / SYMMETRY_TOLERANCE);
        return new InvariantResult(Invariant.SYMMETRY, ok ? InvariantStatus.OK : InvariantStatus.WARNING,
                score, "asymmetry=" + String.format("%.4f", asymmetry));
    }

    private InvariantResult checkConservation(SystemSnapshot s) {
        // CONSERVATION: total resource budget does not exceed bounds
        double drift = Math.abs(s.resourceConsumed - s.resourceBudget) / Math.max(1.0, s.resourceBudget);
        boolean ok = drift <= CONSERVATION_TOLERANCE;
        double score = 1.0 - Math.min(1.0, drift / CONSERVATION_TOLERANCE);
        return new InvariantResult(Invariant.CONSERVATION,
                ok ? InvariantStatus.OK : InvariantStatus.VIOLATION,
                score, "drift=" + String.format("%.4f", drift));
    }

    private InvariantResult checkScale(SystemSnapshot s) {
        // SCALE: micro and macro signals are coherent
        boolean coherentMicro = s.microCoherence >= COHERENCE_MIN;
        boolean coherentMacro = s.macroCoherence >= COHERENCE_MIN;
        double score = (s.microCoherence + s.macroCoherence) / 2.0;
        InvariantStatus status = (coherentMicro && coherentMacro)
                ? InvariantStatus.OK : InvariantStatus.WARNING;
        return new InvariantResult(Invariant.SCALE, status, score,
                "micro=" + String.format("%.3f", s.microCoherence) +
                " macro=" + String.format("%.3f", s.macroCoherence));
    }

    private InvariantResult checkGeometry(SystemSnapshot s) {
        // GEOMETRY: vector connections coherent — tested via Φ_ethica
        double phi = RafaeliaSessionCycle.computePhiEthica(s.entropy, s.coherence);
        boolean ok = phi >= COHERENCE_MIN * RafaeliaKernelV22.SPIRAL;
        return new InvariantResult(Invariant.GEOMETRY,
                ok ? InvariantStatus.OK : InvariantStatus.WARNING,
                phi, "Φ_ethica=" + String.format("%.4f", phi));
    }

    private InvariantResult checkCausality(SystemSnapshot s) {
        // CAUSALITY: each effect has a preceding cause (no orphan effects)
        double orphanRatio = s.effectCount > 0
                ? (double) s.orphanEffectCount / s.effectCount : 0.0;
        boolean ok = orphanRatio == 0.0;
        double score = 1.0 - orphanRatio;
        return new InvariantResult(Invariant.CAUSALITY,
                ok ? InvariantStatus.OK : InvariantStatus.VIOLATION,
                score, "orphanRatio=" + String.format("%.4f", orphanRatio));
    }

    private InvariantResult checkEntropy(SystemSnapshot s) {
        // ENTROPY: growth is within permitted rate
        boolean ok = s.entropyGrowthRate <= ENTROPY_GROWTH_MAX;
        double score = 1.0 - Math.min(1.0, s.entropyGrowthRate / ENTROPY_GROWTH_MAX);
        return new InvariantResult(Invariant.ENTROPY,
                ok ? InvariantStatus.OK : InvariantStatus.WARNING,
                score, "growthRate=" + String.format("%.4f", s.entropyGrowthRate));
    }

    private InvariantResult checkCycles(SystemSnapshot s) {
        // CYCLES: retroalimentations stable (no diverging feedback)
        boolean ok = s.feedbackStable && s.cycleDepth <= s.maxCycleDepth;
        double depthScore = s.maxCycleDepth > 0
                ? 1.0 - (double) s.cycleDepth / s.maxCycleDepth : 1.0;
        return new InvariantResult(Invariant.CYCLES,
                ok ? InvariantStatus.OK : InvariantStatus.WARNING,
                depthScore, "depth=" + s.cycleDepth + "/" + s.maxCycleDepth
                + " stable=" + s.feedbackStable);
    }

    private InvariantResult checkLimits(SystemSnapshot s) {
        // LIMITS: operations bounded within declared limits
        boolean ok = s.activeOperations <= s.operationLimit;
        double score = s.operationLimit > 0
                ? 1.0 - (double) s.activeOperations / s.operationLimit : 1.0;
        return new InvariantResult(Invariant.LIMITS,
                ok ? InvariantStatus.OK : InvariantStatus.VIOLATION,
                score, "ops=" + s.activeOperations + "/" + s.operationLimit);
    }

    private InvariantResult checkComposition(SystemSnapshot s) {
        // COMPOSITION: integrated parts without contradictions
        boolean ok = s.contradictionCount == 0;
        double score = s.componentCount > 0
                ? 1.0 - (double) s.contradictionCount / s.componentCount : 1.0;
        return new InvariantResult(Invariant.COMPOSITION,
                ok ? InvariantStatus.OK : InvariantStatus.VIOLATION,
                score, "contradictions=" + s.contradictionCount + "/" + s.componentCount);
    }

    // ─── Build review ─────────────────────────────────────────────────────────

    private InvariantReview buildReview() {
        int passed = 0, warnings = 0, violations = 0;
        double scoreSum = 0.0;

        for (InvariantResult r : results.values()) {
            switch (r.status) {
                case OK        -> passed++;
                case WARNING   -> warnings++;
                case VIOLATION -> violations++;
            }
            scoreSum += r.score;
        }

        // Tesseract kernel check
        boolean tesseractPassed = true;
        for (Invariant inv : TESSERACT) {
            InvariantResult r = results.get(inv);
            if (r != null && r.status == InvariantStatus.VIOLATION) {
                tesseractPassed = false;
                break;
            }
        }

        boolean stable = violations == 0 && tesseractPassed;
        double globalScore = scoreSum / results.size();

        return new InvariantReview(
                new EnumMap<>(results),
                passed, warnings, violations,
                stable, tesseractPassed, globalScore
        );
    }

    // ─── Data types ───────────────────────────────────────────────────────────

    /** Snapshot of system state for invariant evaluation. */
    public static final class SystemSnapshot {
        public final double inputMagnitude;
        public final double outputMagnitude;
        public final double resourceConsumed;
        public final double resourceBudget;
        public final double microCoherence;
        public final double macroCoherence;
        public final double entropy;
        public final double coherence;
        public final int    effectCount;
        public final int    orphanEffectCount;
        public final double entropyGrowthRate;
        public final boolean feedbackStable;
        public final int    cycleDepth;
        public final int    maxCycleDepth;
        public final int    activeOperations;
        public final int    operationLimit;
        public final int    contradictionCount;
        public final int    componentCount;

        private SystemSnapshot(Builder b) {
            this.inputMagnitude    = b.inputMagnitude;
            this.outputMagnitude   = b.outputMagnitude;
            this.resourceConsumed  = b.resourceConsumed;
            this.resourceBudget    = b.resourceBudget;
            this.microCoherence    = b.microCoherence;
            this.macroCoherence    = b.macroCoherence;
            this.entropy           = b.entropy;
            this.coherence         = b.coherence;
            this.effectCount       = b.effectCount;
            this.orphanEffectCount = b.orphanEffectCount;
            this.entropyGrowthRate = b.entropyGrowthRate;
            this.feedbackStable    = b.feedbackStable;
            this.cycleDepth        = b.cycleDepth;
            this.maxCycleDepth     = b.maxCycleDepth;
            this.activeOperations  = b.activeOperations;
            this.operationLimit    = b.operationLimit;
            this.contradictionCount = b.contradictionCount;
            this.componentCount    = b.componentCount;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            double inputMagnitude = 0, outputMagnitude = 0, resourceConsumed = 0,
                   resourceBudget = 1, microCoherence = 1, macroCoherence = 1,
                   entropy = 0, coherence = 1, entropyGrowthRate = 0;
            int effectCount = 0, orphanEffectCount = 0, cycleDepth = 0, maxCycleDepth = 10,
                activeOperations = 0, operationLimit = 1000, contradictionCount = 0, componentCount = 1;
            boolean feedbackStable = true;

            public Builder inputMagnitude(double v)    { inputMagnitude = v;    return this; }
            public Builder outputMagnitude(double v)   { outputMagnitude = v;   return this; }
            public Builder resourceConsumed(double v)  { resourceConsumed = v;  return this; }
            public Builder resourceBudget(double v)    { resourceBudget = v;    return this; }
            public Builder microCoherence(double v)    { microCoherence = v;    return this; }
            public Builder macroCoherence(double v)    { macroCoherence = v;    return this; }
            public Builder entropy(double v)           { entropy = v;           return this; }
            public Builder coherence(double v)         { coherence = v;         return this; }
            public Builder effectCount(int v)          { effectCount = v;       return this; }
            public Builder orphanEffectCount(int v)    { orphanEffectCount = v; return this; }
            public Builder entropyGrowthRate(double v) { entropyGrowthRate = v; return this; }
            public Builder feedbackStable(boolean v)   { feedbackStable = v;    return this; }
            public Builder cycleDepth(int v)           { cycleDepth = v;        return this; }
            public Builder maxCycleDepth(int v)        { maxCycleDepth = v;     return this; }
            public Builder activeOperations(int v)     { activeOperations = v;  return this; }
            public Builder operationLimit(int v)       { operationLimit = v;    return this; }
            public Builder contradictionCount(int v)   { contradictionCount = v; return this; }
            public Builder componentCount(int v)       { componentCount = v;    return this; }

            public SystemSnapshot build() { return new SystemSnapshot(this); }
        }
    }

    public static final class InvariantResult {
        public final Invariant       invariant;
        public final InvariantStatus status;
        public final double          score;   // 0.0 = worst, 1.0 = perfect
        public final String          detail;

        InvariantResult(Invariant invariant, InvariantStatus status,
                        double score, String detail) {
            this.invariant = invariant;
            this.status    = status;
            this.score     = score;
            this.detail    = detail;
        }

        @NonNull @Override public String toString() {
            return invariant + "[" + status + "] score=" + String.format("%.3f", score)
                    + " (" + detail + ")";
        }
    }

    public static final class InvariantReview {
        public final Map<Invariant, InvariantResult> results;
        public final int     passed;
        public final int     warnings;
        public final int     violations;
        public final boolean stable;
        public final boolean tesseractPassed;
        public final double  globalScore;

        InvariantReview(Map<Invariant, InvariantResult> results,
                        int passed, int warnings, int violations,
                        boolean stable, boolean tesseractPassed, double globalScore) {
            this.results         = results;
            this.passed          = passed;
            this.warnings        = warnings;
            this.violations      = violations;
            this.stable          = stable;
            this.tesseractPassed = tesseractPassed;
            this.globalScore     = globalScore;
        }

        @NonNull @Override public String toString() {
            return String.format(
                    "InvariantReview[passed=%d warn=%d viol=%d stable=%b tesseract=%b score=%.3f]",
                    passed, warnings, violations, stable, tesseractPassed, globalScore);
        }
    }
}
