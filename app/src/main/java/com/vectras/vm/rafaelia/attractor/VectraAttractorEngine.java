package com.vectras.vm.rafaelia.attractor;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VectraAttractorEngine — Phase-coherence-collapse machine with 42 toroidal attractors.
 *
 * <p>Core model (Images 19 & 20):
 * <pre>
 *   BX  = accumulated phase (live memory + history + interference)
 *   AX_{t+1} = AX_t ⊕ BX_t   (coherence-blend state update)
 *   Δ   = |AX - BX|           (structural incoherence)
 *   if |Δ| > LIMIAR → COLAPSO (state jumps to nearest stable attractor)
 *   Truth = stable state in phase space
 * </pre>
 *
 * <p>8 unified domains: LINGUAGEM, VISUAL, AUDIO, LOGICA, EMOCAO, GEOMETRIA, FISICA, ARTE.
 *
 * <p>Pipeline VECTRA:
 * INPUT → EXTRAIR_INVARIANTES → AVALIAR_COERENCIA(Δ≈0) → ATRACAO → SAIDA
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ATTRACTOR
 */
public final class VectraAttractorEngine {

    // ─── Constants ────────────────────────────────────────────────────────────
    public static final int    ATTRACTOR_COUNT = 42;
    /** Collapse threshold: 1 − 1/φ = 1 − 0.618... = 0.382 */
    public static final double LIMIAR          = 1.0 - (1.0 / RafaeliaKernelV22.PHI);
    public static final double DOMAIN_COUNT    = 8.0;

    public enum Domain {
        LINGUAGEM, VISUAL, AUDIO, LOGICA, EMOCAO, GEOMETRIA, FISICA, ARTE
    }

    public enum PipelineStage {
        INPUT, EXTRAIR_INVARIANTES, AVALIAR_COERENCIA, ATRACAO, SAIDA
    }

    // ─── State arrays ─────────────────────────────────────────────────────────
    /** AX: current state vector (one value per attractor) */
    private final double[] ax = new double[ATTRACTOR_COUNT];
    /** BX: accumulated phase vector */
    private final double[] bx = new double[ATTRACTOR_COUNT];
    /** Domain weights (8 domains mapped across 42 attractors) */
    private final double[] domainWeights = new double[ATTRACTOR_COUNT];

    private final AtomicInteger collapseCount   = new AtomicInteger(0);
    private final AtomicLong    stepCount        = new AtomicLong(0);
    private volatile int        currentAttractor = 0;
    private volatile PipelineStage stage         = PipelineStage.INPUT;

    private VectraAttractorEngine() {
        initAttractors();
    }

    public static VectraAttractorEngine create() {
        return new VectraAttractorEngine();
    }

    // ─── Initialization ───────────────────────────────────────────────────────

    private void initAttractors() {
        for (int i = 0; i < ATTRACTOR_COUNT; i++) {
            double theta = 2.0 * RafaeliaKernelV22.PI * i / ATTRACTOR_COUNT;
            ax[i] = RafaeliaKernelV22.SPIRAL * Math.cos(theta);
            bx[i] = RafaeliaKernelV22.SPIRAL * Math.sin(theta);
            domainWeights[i] = Math.abs(Math.cos(RafaeliaKernelV22.PHI * theta));
        }
    }

    // ─── Pipeline ─────────────────────────────────────────────────────────────

    /**
     * Full pipeline step: observe → extract → evaluate → attract → output.
     * @param observation raw input signal [0..1]
     * @return the emergent coherence output
     */
    public double step(double observation) {
        stage = PipelineStage.INPUT;
        double obs = clamp(observation);

        stage = PipelineStage.EXTRAIR_INVARIANTES;
        double[] invariants = extractInvariants(obs);

        stage = PipelineStage.AVALIAR_COERENCIA;
        double delta = evaluateCoherence(invariants);

        stage = PipelineStage.ATRACAO;
        if (delta > LIMIAR) {
            colapso();
        } else {
            accumulate(invariants, obs);
        }

        stage = PipelineStage.SAIDA;
        stepCount.incrementAndGet();
        return globalCoherence();
    }

    /** AX_{t+1} = AX_t ⊕ BX_t: coherence-blend update with phase interference. */
    private void accumulate(double[] invariants, double obs) {
        for (int i = 0; i < ATTRACTOR_COUNT; i++) {
            double phaseShift = invariants[i % invariants.length] * obs;
            bx[i] = (bx[i] + phaseShift) / (1.0 + Math.abs(phaseShift));
            ax[i] = coherenceBlend(ax[i], bx[i]);
        }
    }

    private void colapso() {
        collapseCount.incrementAndGet();
        int nearest = nearestStableAttractor();
        currentAttractor = nearest;
        double anchor = ax[nearest];
        for (int i = 0; i < ATTRACTOR_COUNT; i++) {
            ax[i] = coherenceBlend(ax[i], anchor * domainWeights[i]);
        }
    }

    // ─── Invariant extraction ─────────────────────────────────────────────────

    private double[] extractInvariants(double obs) {
        double[] inv = new double[8];
        for (int d = 0; d < 8; d++) {
            Domain dom = Domain.values()[d];
            inv[d] = domainInvariant(dom, obs);
        }
        return inv;
    }

    private double domainInvariant(Domain domain, double obs) {
        return switch (domain) {
            case LINGUAGEM  -> Math.sin(obs * RafaeliaKernelV22.PHI   * RafaeliaKernelV22.PI);
            case VISUAL     -> Math.cos(obs * RafaeliaKernelV22.SPIRAL * RafaeliaKernelV22.PI);
            case AUDIO      -> Math.sin(obs * RafaeliaKernelV22.F_OMEGA_LOW  / 1000.0 * RafaeliaKernelV22.PI);
            case LOGICA     -> (obs > 0.5) ? 1.0 - obs : obs;
            case EMOCAO     -> Math.exp(-obs * obs) * RafaeliaKernelV22.SPIRAL;
            case GEOMETRIA  -> Math.abs(Math.sin(obs * 6.0 * RafaeliaKernelV22.PI));
            case FISICA     -> 1.0 - Math.exp(-obs * RafaeliaKernelV22.PHI);
            case ARTE       -> (Math.sin(obs * RafaeliaKernelV22.THETA_999) + 1.0) * 0.5;
        };
    }

    // ─── Coherence evaluation ─────────────────────────────────────────────────

    /** Δ = mean |AX_i − BX_i| across all attractors. */
    private double evaluateCoherence(double[] invariants) {
        double sum = 0.0;
        for (int i = 0; i < ATTRACTOR_COUNT; i++) {
            double projected = invariants[i % invariants.length] * domainWeights[i];
            sum += Math.abs(ax[i] - bx[i] - projected);
        }
        return sum / ATTRACTOR_COUNT;
    }

    // ─── Attractor search ─────────────────────────────────────────────────────

    private int nearestStableAttractor() {
        int best = 0;
        double minIncoherence = Double.MAX_VALUE;
        for (int i = 0; i < ATTRACTOR_COUNT; i++) {
            double incoherence = Math.abs(ax[i] - bx[i]);
            if (incoherence < minIncoherence) {
                minIncoherence = incoherence;
                best = i;
            }
        }
        return best;
    }

    // ─── Metrics ──────────────────────────────────────────────────────────────

    /** Global coherence: mean |AX_i| weighted by domain. */
    public double globalCoherence() {
        double sum = 0.0;
        for (int i = 0; i < ATTRACTOR_COUNT; i++) {
            sum += Math.abs(ax[i]) * domainWeights[i];
        }
        return clamp(sum / ATTRACTOR_COUNT);
    }

    public double deltaIncoherence() {
        double sum = 0.0;
        for (int i = 0; i < ATTRACTOR_COUNT; i++) sum += Math.abs(ax[i] - bx[i]);
        return sum / ATTRACTOR_COUNT;
    }

    public int collapseCount()        { return collapseCount.get(); }
    public long stepCount()           { return stepCount.get(); }
    public int currentAttractor()     { return currentAttractor; }
    @NonNull public PipelineStage stage() { return stage; }

    // ─── Domain projection ────────────────────────────────────────────────────

    /** Project current state onto a specific domain. */
    public double project(@NonNull Domain domain) {
        int start = (domain.ordinal() * ATTRACTOR_COUNT) / 8;
        int end   = ((domain.ordinal() + 1) * ATTRACTOR_COUNT) / 8;
        double sum = 0.0;
        for (int i = start; i < end; i++) sum += ax[i] * domainWeights[i];
        return clamp(Math.abs(sum / Math.max(1, end - start)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** AX_t ⊕ BX_t = weighted coherence blend. */
    private static double coherenceBlend(double a, double b) {
        return (a * RafaeliaKernelV22.PHI + b) / (RafaeliaKernelV22.PHI + 1.0);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @NonNull @Override
    public String toString() {
        return String.format("VectraAttractor[attractor=%d coherence=%.4f delta=%.4f collapses=%d steps=%d]",
                currentAttractor, globalCoherence(), deltaIncoherence(), collapseCount.get(), stepCount.get());
    }
}
