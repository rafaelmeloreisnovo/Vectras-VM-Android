package com.vectras.vm.rafaelia.attention;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Octagonal Attention Engine — 8-vector attention model.
 *
 * <p>Implements the "Atenção Octagonal" model with 8 directional vectors
 * arranged in an octagon and governed by Yang-Yin duality:
 * <pre>
 *   1. FOCO        (Focus)        — directed attention
 *   2. PERCEPÇÃO   (Perception)   — sensory intake
 *   3. INTUIÇÃO    (Intuition)    — pattern recognition below threshold
 *   4. EMOÇÃO      (Emotion)      — affective weighting
 *   5. VONTADE     (Will)         — intention / drive
 *   6. IMAGINAÇÃO  (Imagination)  — generative projection
 *   7. EXECUÇÃO    (Execution)    — output / action
 *   8. MEMÓRIA     (Memory)       — retention and retrieval
 * </pre>
 *
 * <p>The octagon is stable when all 8 vectors are in equilibrium
 * (sum of opposite pairs ≈ 1.0). Yang vectors: FOCO, PERCEPÇÃO, VONTADE, EXECUÇÃO.
 * Yin vectors: INTUIÇÃO, EMOÇÃO, IMAGINAÇÃO, MEMÓRIA.
 *
 * <p>Fibonacci Discrete mapping: the essence is in 1, then 1, 2, 3, 5, 8.
 * Reduction: 1, 2, 3 → descritization. 3 = Yin-Yang-Tao (dual dynamic).
 * 5 = quintessence. 6 = hexagonal balance. 8 = octagon.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ATTENTION
 */
public final class OctagonalAttentionEngine {

    public enum Vector {
        FOCO(1, true),       // Yang
        PERCEPCAO(2, false), // Yin
        INTUICAO(3, false),  // Yin
        EMOCAO(4, false),    // Yin
        VONTADE(5, true),    // Yang
        IMAGINACAO(6, false),// Yin
        EXECUCAO(7, true),   // Yang
        MEMORIA(8, false);   // Yin

        public final int  ordinalOctagon;
        public final boolean isYang; // true=Yang, false=Yin

        Vector(int ord, boolean isYang) {
            this.ordinalOctagon = ord;
            this.isYang         = isYang;
        }

        /** External angle in the octagon (45° per step, starting at top). */
        public double angleRad() { return Math.toRadians((ordinalOctagon - 1) * 45.0); }

        /** Opposite vector in the octagon (180° rotated). */
        public Vector opposite() {
            Vector[] vals = values();
            int oppIdx = (ordinalOctagon - 1 + 4) % 8;
            for (Vector v : vals) if (v.ordinalOctagon == oppIdx + 1) return v;
            return this;
        }
    }

    // ─── Operator directions (7 per vector in the directional matrix) ─────────
    public enum Direction {
        INPUT, OUTPUT, STORAGE, PROCESSING, INFERENCE, CONTROL, AUDIT
    }

    // ─── State ────────────────────────────────────────────────────────────────

    // Weights: 0.0 = suppressed, 1.0 = full activation
    private final double[] weights = new double[8];
    // 8×7 directional projection matrix
    private final double[][] directionMatrix = new double[8][7];
    // Running averages for drift detection
    private final double[] history = new double[8];

    private int  stepCount = 0;
    private boolean stable = false;

    private OctagonalAttentionEngine() {
        Arrays.fill(weights, 0.5);        // neutral start
        Arrays.fill(history, 0.5);
        buildDefaultDirectionMatrix();
    }

    public static OctagonalAttentionEngine create() { return new OctagonalAttentionEngine(); }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Set weight for a specific vector (clamped to [0,1]). */
    public void setWeight(@NonNull Vector v, double weight) {
        weights[v.ordinal()] = Math.max(0.0, Math.min(1.0, weight));
    }

    public double getWeight(@NonNull Vector v) { return weights[v.ordinal()]; }

    /**
     * Compute the attention step: propagate weights through direction matrix,
     * apply Yang-Yin balance, and update history.
     * Returns the new stable state.
     */
    public boolean step() {
        double[] next = new double[8];
        for (int i = 0; i < 8; i++) {
            double sum = 0.0;
            for (int j = 0; j < 7; j++) {
                sum += weights[i] * directionMatrix[i][j];
            }
            next[i] = sigmoid(sum);
        }

        // Yang-Yin balance: normalize opposite pairs
        Vector[] all = Vector.values();
        for (int i = 0; i < 4; i++) {
            Vector yang = all[i * 2];
            Vector yin  = all[i * 2 + 1];
            double yangW = next[yang.ordinal()];
            double yinW  = next[yin.ordinal()];
            double total = yangW + yinW;
            if (total > 0) {
                next[yang.ordinal()] = yangW / total;
                next[yin.ordinal()]  = yinW  / total;
            }
        }

        // Drift detection
        double maxDrift = 0.0;
        for (int i = 0; i < 8; i++) {
            maxDrift = Math.max(maxDrift, Math.abs(next[i] - weights[i]));
            history[i] = 0.8 * history[i] + 0.2 * next[i];
        }

        System.arraycopy(next, 0, weights, 0, 8);
        stable = maxDrift < 0.01;
        stepCount++;
        return stable;
    }

    /** Run until stable or maxSteps reached. */
    public boolean converge(int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            if (step()) return true;
        }
        return false;
    }

    /** Compute the coherence score = (∑ weight_i × φ_i) / 8 where φ_i = SPIRAL factor. */
    public double coherenceScore() {
        double sum = 0.0;
        for (int i = 0; i < 8; i++) {
            double angle = Vector.values()[i].angleRad();
            sum += weights[i] * (RafaeliaKernelV22.SPIRAL * Math.cos(angle)
                    + RafaeliaKernelV22.PHI * Math.sin(angle));
        }
        return Math.abs(sum) / 8.0;
    }

    /** Project through a given direction, returning weighted sum for that direction. */
    public double project(@NonNull Direction dir) {
        double sum = 0.0;
        int d = dir.ordinal();
        for (int i = 0; i < 8; i++) {
            sum += weights[i] * directionMatrix[i][d];
        }
        return sigmoid(sum);
    }

    public Map<Vector, Double> snapshot() {
        Map<Vector, Double> m = new EnumMap<>(Vector.class);
        for (Vector v : Vector.values()) m.put(v, weights[v.ordinal()]);
        return m;
    }

    public boolean isStable()  { return stable; }
    public int    getStepCount(){ return stepCount; }

    /** Attune to a task: amplify the relevant vectors, dampen others. */
    public void attuneToTask(@NonNull Vector primary, double primaryStrength) {
        double base = (1.0 - primaryStrength) / 7.0;
        for (Vector v : Vector.values()) {
            weights[v.ordinal()] = (v == primary) ? primaryStrength : base;
        }
    }

    /** Reset to neutral state. */
    public void reset() {
        Arrays.fill(weights, 0.5);
        Arrays.fill(history, 0.5);
        stepCount = 0;
        stable    = false;
    }

    // ─── Direction matrix construction ────────────────────────────────────────

    private void buildDefaultDirectionMatrix() {
        // Row = vector (8), Col = direction (7)
        // Values derived from the RAFAELIA 8×7 directional matrix:
        // ψ-INIT=FOCO, χ-OBSERVE=PERCEPÇÃO, ρ-DENOISE=INTUIÇÃO, Δ-TRANSMUTE=EMOÇÃO,
        // Σ-MEMORY=MEMÓRIA, Ω-COMPLETE=EXECUÇÃO, √3/2-SPIRAL=IMAGINAÇÃO, Φ-COHERENCE=VONTADE
        double[][] m = {
            // INPUT  OUTPUT  STORAGE PROCESS INFER   CONTROL AUDIT
            { 0.9,   0.3,    0.2,    0.8,    0.6,    0.9,    0.4 }, // FOCO
            { 0.8,   0.5,    0.3,    0.7,    0.9,    0.5,    0.6 }, // PERCEPÇÃO
            { 0.4,   0.2,    0.6,    0.5,    0.9,    0.3,    0.7 }, // INTUIÇÃO
            { 0.6,   0.4,    0.7,    0.5,    0.8,    0.4,    0.5 }, // EMOÇÃO
            { 0.7,   0.8,    0.3,    0.9,    0.5,    0.8,    0.4 }, // VONTADE
            { 0.5,   0.7,    0.8,    0.6,    0.7,    0.3,    0.6 }, // IMAGINAÇÃO
            { 0.3,   0.9,    0.4,    0.9,    0.4,    0.7,    0.5 }, // EXECUÇÃO
            { 0.5,   0.3,    0.9,    0.4,    0.8,    0.5,    0.9 }, // MEMÓRIA
        };
        for (int i = 0; i < 8; i++) {
            System.arraycopy(m[i], 0, directionMatrix[i], 0, 7);
        }
    }

    private static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    @NonNull @Override public String toString() {
        StringBuilder sb = new StringBuilder("OctagonalAttention[\n");
        for (Vector v : Vector.values()) {
            sb.append(String.format("  %s[%s] = %.3f\n",
                    v.name(), v.isYang ? "Yang" : "Yin", weights[v.ordinal()]));
        }
        sb.append("  coherence=").append(String.format("%.4f", coherenceScore()));
        sb.append(", stable=").append(stable);
        sb.append("]");
        return sb.toString();
    }
}
