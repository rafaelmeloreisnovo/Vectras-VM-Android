package com.vectras.vm.rafaelia.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RAFAELIA Session Cycle — Ω = (LER → RETROALIMENTAR → EXPANDIR → VALIDAR → EXECUTAR) ★ ÉTICA(s)
 *
 * <p>Implements the 6-step operational cycle from the session architecture:
 * <pre>
 *   1. LER          — Collect facts, data, context with no distortion
 *   2. RETROALIMENTAR — Identify T_a, T_p, T_next from context
 *   3. EXPANDIR     — Integrate new paths, expand connections
 *   4. VALIDAR      — Test hypotheses, evidence, coherence
 *   5. EXECUTAR     — Convert understanding into observable action
 *   6. ÉTICA        — Truth, accountability, coherence and integrity gate
 * </pre>
 *
 * <p>The cycle is governed by Φ_ethica = Min(Entropy) × Max(Coherence).
 * Each step transitions only if the ethical gate passes.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ΣΩΔΦ
 */
public final class RafaeliaSessionCycle {

    // ─── Step constants (1-indexed, matching image) ───────────────────────────
    public static final int STEP_LER             = 1;
    public static final int STEP_RETROALIMENTAR  = 2;
    public static final int STEP_EXPANDIR        = 3;
    public static final int STEP_VALIDAR         = 4;
    public static final int STEP_EXECUTAR        = 5;
    public static final int STEP_ETICA           = 6;

    public static final int TOTAL_STEPS          = 6;

    // ─── Gate thresholds ──────────────────────────────────────────────────────
    /** Minimum Φ_ethica to advance from VALIDAR → EXECUTAR */
    private static final double ETHICAL_GATE_MIN = 0.618;   // φ-derived
    /** Minimum coherence to complete EXECUTAR → ÉTICA */
    private static final double COHERENCE_MIN    = 0.75;
    /** Maximum tolerated entropy per step */
    private static final double ENTROPY_MAX      = 0.5;

    // ─── Cycle state ──────────────────────────────────────────────────────────
    public enum CyclePhase {
        IDLE, LER, RETROALIMENTAR, EXPANDIR, VALIDAR, EXECUTAR, ETICA, COMPLETE, ABORTED
    }

    public enum AbortReason {
        NONE, ETHICAL_GATE_FAILED, COHERENCE_TOO_LOW, ENTROPY_TOO_HIGH, STEP_TIMEOUT, EXTERNAL
    }

    private final AtomicReference<CyclePhase> phase = new AtomicReference<>(CyclePhase.IDLE);
    private final AtomicInteger               cycleCount = new AtomicInteger(0);
    private final AtomicLong                  phaseStartNs = new AtomicLong(0);

    private volatile double   currentEntropy  = 0.0;
    private volatile double   currentCoherence = 1.0;
    private volatile double   phiEthica       = 1.0;
    private volatile AbortReason abortReason  = AbortReason.NONE;

    private final List<StepRecord> history = Collections.synchronizedList(new ArrayList<>());

    @Nullable private volatile StepListener listener;

    private RafaeliaSessionCycle() {}

    public static RafaeliaSessionCycle create() {
        return new RafaeliaSessionCycle();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public void setListener(@Nullable StepListener l) { this.listener = l; }

    /**
     * Begin a new cycle from IDLE. Returns false if already running.
     */
    public boolean begin() {
        if (!phase.compareAndSet(CyclePhase.IDLE, CyclePhase.LER)
                && !phase.compareAndSet(CyclePhase.COMPLETE, CyclePhase.LER)
                && !phase.compareAndSet(CyclePhase.ABORTED, CyclePhase.LER)) {
            return false;
        }
        phaseStartNs.set(System.nanoTime());
        abortReason = AbortReason.NONE;
        notifyStep(STEP_LER);
        return true;
    }

    /**
     * Advance to the next step, providing entropy and coherence measurements for the current step.
     * Returns the new phase, or ABORTED if the ethical gate fails.
     */
    public CyclePhase advance(double entropy, double coherence) {
        CyclePhase current = phase.get();
        if (current == CyclePhase.IDLE || current == CyclePhase.COMPLETE
                || current == CyclePhase.ABORTED) {
            return current;
        }

        long durationNs = System.nanoTime() - phaseStartNs.get();

        currentEntropy   = entropy;
        currentCoherence = coherence;
        phiEthica        = computePhiEthica(entropy, coherence);

        history.add(new StepRecord(current, entropy, coherence, phiEthica, durationNs));

        // Ethical gate check before EXECUTAR
        if (current == CyclePhase.VALIDAR && phiEthica < ETHICAL_GATE_MIN) {
            return abort(AbortReason.ETHICAL_GATE_FAILED);
        }
        if (entropy > ENTROPY_MAX) {
            return abort(AbortReason.ENTROPY_TOO_HIGH);
        }

        CyclePhase next = nextPhase(current);
        phase.set(next);
        phaseStartNs.set(System.nanoTime());

        if (next == CyclePhase.COMPLETE) {
            cycleCount.incrementAndGet();
        }

        notifyStep(phaseToStep(next));
        return next;
    }

    /** Force abort with an external reason. */
    public CyclePhase abort(@NonNull AbortReason reason) {
        abortReason = reason;
        phase.set(CyclePhase.ABORTED);
        StepListener l = listener;
        if (l != null) l.onAbort(reason, phiEthica);
        return CyclePhase.ABORTED;
    }

    /** Reset to IDLE, clearing history. */
    public void reset() {
        phase.set(CyclePhase.IDLE);
        abortReason = AbortReason.NONE;
        currentEntropy   = 0.0;
        currentCoherence = 1.0;
        phiEthica        = 1.0;
        history.clear();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public CyclePhase getPhase()         { return phase.get(); }
    public int getCycleCount()           { return cycleCount.get(); }
    public double getPhiEthica()         { return phiEthica; }
    public double getCurrentEntropy()    { return currentEntropy; }
    public double getCurrentCoherence()  { return currentCoherence; }
    public AbortReason getAbortReason()  { return abortReason; }
    public List<StepRecord> getHistory() { return Collections.unmodifiableList(history); }

    public boolean isRunning() {
        CyclePhase p = phase.get();
        return p != CyclePhase.IDLE && p != CyclePhase.COMPLETE && p != CyclePhase.ABORTED;
    }

    // ─── Formula: Φ_ethica = Min(Entropy) × Max(Coherence) ───────────────────
    static double computePhiEthica(double entropy, double coherence) {
        double safeEntropy   = Math.max(0.0, Math.min(1.0, entropy));
        double safeCoherence = Math.max(0.0, Math.min(1.0, coherence));
        // Min(Entropy) = 1 - entropy  (lower entropy → higher ethical weight)
        return (1.0 - safeEntropy) * safeCoherence * RafaeliaKernelV22.SPIRAL;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private static CyclePhase nextPhase(CyclePhase current) {
        return switch (current) {
            case LER            -> CyclePhase.RETROALIMENTAR;
            case RETROALIMENTAR -> CyclePhase.EXPANDIR;
            case EXPANDIR       -> CyclePhase.VALIDAR;
            case VALIDAR        -> CyclePhase.EXECUTAR;
            case EXECUTAR       -> CyclePhase.ETICA;
            case ETICA          -> CyclePhase.COMPLETE;
            default             -> CyclePhase.COMPLETE;
        };
    }

    private static int phaseToStep(CyclePhase phase) {
        return switch (phase) {
            case LER            -> STEP_LER;
            case RETROALIMENTAR -> STEP_RETROALIMENTAR;
            case EXPANDIR       -> STEP_EXPANDIR;
            case VALIDAR        -> STEP_VALIDAR;
            case EXECUTAR       -> STEP_EXECUTAR;
            case ETICA          -> STEP_ETICA;
            default             -> 0;
        };
    }

    private void notifyStep(int step) {
        StepListener l = listener;
        if (l != null && step > 0) l.onStep(step, phase.get(), phiEthica);
    }

    // ─── Data types ───────────────────────────────────────────────────────────

    public static final class StepRecord {
        public final CyclePhase phase;
        public final double entropy;
        public final double coherence;
        public final double phiEthica;
        public final long   durationNs;

        StepRecord(CyclePhase phase, double entropy, double coherence,
                   double phiEthica, long durationNs) {
            this.phase      = phase;
            this.entropy    = entropy;
            this.coherence  = coherence;
            this.phiEthica  = phiEthica;
            this.durationNs = durationNs;
        }

        @NonNull @Override public String toString() {
            return String.format("[%s] ent=%.4f coh=%.4f φ=%.4f %dns",
                    phase, entropy, coherence, phiEthica, durationNs);
        }
    }

    public interface StepListener {
        void onStep(int stepNumber, CyclePhase phase, double phiEthica);
        void onAbort(AbortReason reason, double phiEthica);
    }
}
