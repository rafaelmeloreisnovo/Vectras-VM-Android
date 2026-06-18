package com.vectras.vm.rafaelia.token;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TokenVectorizationEngine — Multilevel token vectorization in 7 directions × 3 chains (Image 16).
 *
 * <pre>
 * 7 semantic directions per token:
 *   D1 FORMAL_ARITMETICA       — Formal / arithmetic representation
 *   D2 COMPUTACIONAL           — Computational / algorithmic topology
 *   D3 GEOMETRICA_TOPOLOGICA   — Geometric / topological structure
 *   D4 SENSORIAL               — Sensory / perceptual quality
 *   D5 LINGUISTICA_SEMANTICA   — Linguistic / semantic distribution
 *   D6 SISTEMICA_ARQUITETURAL  — Systemic / architectural role
 *   D7 ETICA_VALIDACAO         — Ethical / validation gate
 *
 * 3 semantic evolution chains:
 *   A — NUCLEO_FORMAL        (proof-driven, tight formal convergence)
 *   B — PONTE_ENTRE_AREAS    (cross-domain bridges)
 *   C — CONVERGENCIA_DISTANTE (distant, creative, structurally open convergence)
 *
 * Token classification: FORTE, MODERADO, FRACO, ABORTADO
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-TOKEN
 */
public final class TokenVectorizationEngine {

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum Direction {
        FORMAL_ARITMETICA, COMPUTACIONAL, GEOMETRICA_TOPOLOGICA, SENSORIAL,
        LINGUISTICA_SEMANTICA, SISTEMICA_ARQUITETURAL, ETICA_VALIDACAO
    }

    public static final int DIRECTION_COUNT = Direction.values().length; // 7

    public enum Chain {
        NUCLEO_FORMAL,        // A — tight formal proof
        PONTE_ENTRE_AREAS,    // B — cross-domain bridge
        CONVERGENCIA_DISTANTE // C — distant creative convergence
    }

    public enum Classification {
        FORTE,    // coherence ≥ 0.75, all directions active
        MODERADO, // coherence ≥ 0.5, most directions active
        FRACO,    // coherence ≥ 0.25, some directions active
        ABORTADO  // coherence < 0.25, structural failure
    }

    // ─── Thresholds ───────────────────────────────────────────────────────────
    static final double FORTE_MIN    = 0.75;
    static final double MODERADO_MIN = 0.50;
    static final double FRACO_MIN    = 0.25;

    // ─── VectorizedToken ──────────────────────────────────────────────────────

    public static final class VectorizedToken {
        public final String         text;
        public final double[]       vector;       // length = 7
        public final Chain          chain;
        public final Classification classification;
        public final double         coherence;    // aggregate score [0,1]

        VectorizedToken(@NonNull String text, double[] vector, Chain chain) {
            this.text           = text;
            this.vector         = vector.clone();
            this.coherence      = computeCoherence(vector);
            this.chain          = chain;
            this.classification = classify(this.coherence);
        }

        private static double computeCoherence(double[] v) {
            double sum = 0.0;
            for (double d : v) sum += Math.abs(d);
            return Math.min(1.0, sum / v.length);
        }

        private static Classification classify(double coh) {
            if (coh >= FORTE_MIN)    return Classification.FORTE;
            if (coh >= MODERADO_MIN) return Classification.MODERADO;
            if (coh >= FRACO_MIN)    return Classification.FRACO;
            return Classification.ABORTADO;
        }

        @NonNull @Override
        public String toString() {
            return String.format("Token[\"%s\" %s chain=%s coh=%.3f]",
                    text, classification, chain, coherence);
        }
    }

    // ─── Engine state ─────────────────────────────────────────────────────────

    private final List<VectorizedToken> vocabulary = new ArrayList<>();
    private int generation = 0;

    private TokenVectorizationEngine() {}

    public static TokenVectorizationEngine create() { return new TokenVectorizationEngine(); }

    // ─── Vectorization ────────────────────────────────────────────────────────

    /**
     * Vectorize a token into 7 semantic directions and assign to an evolution chain.
     */
    @NonNull
    public VectorizedToken vectorize(@NonNull String text) {
        double[] v = computeVector(text);
        Chain chain = assignChain(v);
        VectorizedToken tok = new VectorizedToken(text, v, chain);
        vocabulary.add(tok);
        return tok;
    }

    private double[] computeVector(@NonNull String text) {
        double[] v = new double[DIRECTION_COUNT];
        int hash = text.hashCode();
        for (int d = 0; d < DIRECTION_COUNT; d++) {
            v[d] = directionScore(Direction.values()[d], text, hash);
        }
        return v;
    }

    private double directionScore(Direction dir, String text, int hash) {
        return switch (dir) {
            case FORMAL_ARITMETICA ->
                    Math.abs(Math.sin((hash ^ (hash >>> 16)) * RafaeliaKernelV22.PHI));
            case COMPUTACIONAL ->
                    Math.abs(Math.cos(text.length() * RafaeliaKernelV22.SPIRAL));
            case GEOMETRICA_TOPOLOGICA ->
                    Math.abs(Math.sin(text.length() * RafaeliaKernelV22.PI / 6.0));
            case SENSORIAL ->
                    Math.abs(Math.tanh(entropy(text)));
            case LINGUISTICA_SEMANTICA ->
                    Math.abs(Math.sin(uniqueChars(text) * RafaeliaKernelV22.PHI));
            case SISTEMICA_ARQUITETURAL ->
                    Math.abs(Math.cos((hash * RafaeliaKernelV22.SPIRAL_PI_PHI) % 1.0 * RafaeliaKernelV22.PI));
            case ETICA_VALIDACAO ->
                    Math.abs(Math.sin(((double) text.length() / Math.max(1, uniqueChars(text)))
                            * RafaeliaKernelV22.SPIRAL));
        };
    }

    // ─── Chain assignment ─────────────────────────────────────────────────────

    private Chain assignChain(double[] v) {
        double formalScore   = v[0] + v[2]; // D1 + D3 → formal/geometric
        double bridgeScore   = v[1] + v[4]; // D2 + D5 → computational + linguistic
        double distantScore  = v[3] + v[6]; // D4 + D7 → sensorial + ethical
        if (formalScore >= bridgeScore && formalScore >= distantScore) return Chain.NUCLEO_FORMAL;
        if (bridgeScore >= distantScore)                                return Chain.PONTE_ENTRE_AREAS;
        return Chain.CONVERGENCIA_DISTANTE;
    }

    // ─── Evolutionary cycle ───────────────────────────────────────────────────

    /**
     * Run one evolution cycle: evaluate → select strong → formal test → reclassify.
     * Returns list of surviving FORTE tokens after one generation.
     */
    @NonNull
    public List<VectorizedToken> evolve() {
        generation++;
        List<VectorizedToken> strong = new ArrayList<>();
        for (VectorizedToken tok : vocabulary) {
            if (tok.classification == Classification.FORTE ||
                    tok.classification == Classification.MODERADO) {
                strong.add(tok);
            }
        }
        return Collections.unmodifiableList(strong);
    }

    /** All tokenized vocabulary so far. */
    @NonNull
    public List<VectorizedToken> vocabulary() {
        return Collections.unmodifiableList(vocabulary);
    }

    public int generation() { return generation; }
    public int size()        { return vocabulary.size(); }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static double entropy(String s) {
        if (s.isEmpty()) return 0.0;
        int[] freq = new int[128];
        for (char c : s.toCharArray()) if (c < 128) freq[c]++;
        double h = 0.0;
        int n = s.length();
        for (int f : freq) {
            if (f > 0) {
                double p = (double) f / n;
                h -= p * Math.log(p);
            }
        }
        return h;
    }

    private static int uniqueChars(String s) {
        boolean[] seen = new boolean[128];
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c < 128 && !seen[c]) { seen[c] = true; count++; }
        }
        return count;
    }

    @NonNull @Override
    public String toString() {
        return String.format("TokenVectorizationEngine[vocab=%d generation=%d]",
                vocabulary.size(), generation);
    }
}
