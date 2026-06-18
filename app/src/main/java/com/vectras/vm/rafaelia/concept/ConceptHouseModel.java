package com.vectras.vm.rafaelia.concept;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ConceptHouseModel — 7-layer concept house with geometric invariant (Image 18).
 *
 * <pre>
 *   L7 META_ABSTRATA   — Transcendent principles & invariants
 *   L6 FORMAL          — Logics, languages, formal structures
 *   L5 MODELOS         — Generative & predictive models
 *   L4 CONCEITOS       — Concepts, relations, taxonomies
 *   L3 PADROES         — Structural & behavioral patterns
 *   L2 DADOS           — Structured & unstructured data
 *   L1 SINAIS          — Raw signals & observations
 * </pre>
 *
 * <p>4 windows (temporal / spatial views) × 8 semantic vectors × 5 blendings.
 *
 * <p>Geometric invariant threading: conservation across all layers ensures
 * ∀l∈L, vector(l) × window(l) → coherence(l) ≥ COHERENCE_MIN.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-CONCEPT
 */
public final class ConceptHouseModel {

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum Layer {
        L1_SINAIS, L2_DADOS, L3_PADROES, L4_CONCEITOS, L5_MODELOS, L6_FORMAL, L7_META_ABSTRATA;

        public int depth() { return ordinal() + 1; }
        public boolean isAbstract() { return this == L6_FORMAL || this == L7_META_ABSTRATA; }
    }

    public enum Window {
        GLOBAL,   // Holistic view of the entire system
        LOCAL,    // Focus on a specific region/domain
        TEMPORAL, // Temporal slice (point in time)
        MODAL     // Abstraction level (modality of observation)
    }

    public enum SemanticVector {
        SEMANTICO,       // High-dimensional semantic space
        RELACIONAL,      // Relations between entities
        TEMPORAL,        // Evolution over time
        CAUSAL,          // Cause-and-effect chains
        FUNCIONAL,       // System functional roles
        REPRESENTACIONAL,// Hidden representation layers
        OBSERVACIONAL,   // Observations & measurements
        LATENTE          // Latent compressed representation
    }

    public enum Blending {
        HORIZONTAL,   // Combination across windows of same layer
        VERTICAL,     // Integration across different layers
        DIAGONAL,     // Cross-layer × cross-window fusion
        MULTI_JANELA, // Multi-window fusion (adaptive)
        ADAPTATIVO    // Dynamic blending weight adjustment
    }

    // ─── Constants ────────────────────────────────────────────────────────────
    public static final double COHERENCE_MIN = RafaeliaKernelV22.PHI - 1.0; // ≈ 0.618

    // ─── Node ─────────────────────────────────────────────────────────────────

    public static final class ConceptNode {
        public final String  id;
        public final String  label;
        public final Layer   layer;
        public final double  coherence;   // [0,1]
        public final double[] vectorWeights; // 8 semantic weights

        public ConceptNode(@NonNull String id, @NonNull String label,
                           @NonNull Layer layer, double coherence,
                           double[] vectorWeights) {
            this.id             = id;
            this.label          = label;
            this.layer          = layer;
            this.coherence      = Math.max(0.0, Math.min(1.0, coherence));
            this.vectorWeights  = vectorWeights.clone();
        }

        public boolean meetsInvariant() { return coherence >= COHERENCE_MIN; }

        @NonNull @Override
        public String toString() {
            return String.format("ConceptNode[%s/%s coh=%.3f]", layer, id, coherence);
        }
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private final List<ConceptNode>                  nodes  = new ArrayList<>();
    private final EnumMap<Layer,  List<ConceptNode>> byLayer  = new EnumMap<>(Layer.class);
    private final EnumMap<Window, double[]>          windowWeights = new EnumMap<>(Window.class);

    private ConceptHouseModel() {
        for (Layer l : Layer.values())   byLayer.put(l, new ArrayList<>());
        for (Window w : Window.values()) windowWeights.put(w, defaultWindowWeights(w));
    }

    public static ConceptHouseModel create() { return new ConceptHouseModel(); }

    // ─── Node management ──────────────────────────────────────────────────────

    public void addNode(@NonNull ConceptNode node) {
        nodes.add(node);
        byLayer.get(node.layer).add(node);
    }

    @NonNull
    public List<ConceptNode> nodesAt(@NonNull Layer layer) {
        return Collections.unmodifiableList(byLayer.get(layer));
    }

    public int totalNodes() { return nodes.size(); }

    // ─── Geometric invariant ──────────────────────────────────────────────────

    /**
     * Verify geometric invariant: every layer must have at least one node
     * with coherence ≥ COHERENCE_MIN. Returns layer → pass/fail map.
     */
    @NonNull
    public Map<Layer, Boolean> checkInvariant() {
        EnumMap<Layer, Boolean> result = new EnumMap<>(Layer.class);
        for (Layer l : Layer.values()) {
            boolean ok = byLayer.get(l).stream().anyMatch(ConceptNode::meetsInvariant);
            result.put(l, ok);
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean isGeometricallyCoherent() {
        return checkInvariant().values().stream().allMatch(b -> b);
    }

    // ─── Vector projection ────────────────────────────────────────────────────

    /** Project all nodes at layer l onto a semantic vector dimension. */
    public double projectLayer(@NonNull Layer layer, @NonNull SemanticVector vector) {
        List<ConceptNode> layerNodes = byLayer.get(layer);
        if (layerNodes.isEmpty()) return 0.0;
        int vi = vector.ordinal();
        double sum = 0.0;
        for (ConceptNode n : layerNodes) {
            double w = (vi < n.vectorWeights.length) ? n.vectorWeights[vi] : 0.0;
            sum += n.coherence * w;
        }
        return Math.min(1.0, sum / layerNodes.size());
    }

    // ─── Blending ─────────────────────────────────────────────────────────────

    /**
     * Apply a blending strategy and return a composite coherence score.
     */
    public double blend(@NonNull Blending blending, @NonNull Window window) {
        double[] ww = windowWeights.get(window);
        return switch (blending) {
            case HORIZONTAL   -> blendHorizontal(ww);
            case VERTICAL     -> blendVertical();
            case DIAGONAL     -> blendDiagonal(ww);
            case MULTI_JANELA -> blendMultiWindow();
            case ADAPTATIVO   -> blendAdaptive(ww);
        };
    }

    private double blendHorizontal(double[] ww) {
        double sum = 0.0, total = 0.0;
        for (ConceptNode n : nodes) {
            double w = ww[n.layer.ordinal() % ww.length];
            sum += n.coherence * w;
            total += w;
        }
        return total == 0.0 ? 0.0 : sum / total;
    }

    private double blendVertical() {
        int layers = Layer.values().length;
        double sum = 0.0;
        for (int i = 0; i < layers; i++) {
            List<ConceptNode> ln = byLayer.get(Layer.values()[i]);
            double layerCoh = ln.stream().mapToDouble(n -> n.coherence).average().orElse(0.0);
            sum += layerCoh * (i + 1.0) / layers; // deeper layers weighted higher
        }
        return Math.min(1.0, sum / layers);
    }

    private double blendDiagonal(double[] ww) {
        return (blendHorizontal(ww) + blendVertical()) * 0.5;
    }

    private double blendMultiWindow() {
        double sum = 0.0;
        for (Window w : Window.values()) sum += blendHorizontal(windowWeights.get(w));
        return sum / Window.values().length;
    }

    private double blendAdaptive(double[] ww) {
        double coherence = blendVertical();
        double boost = coherence > COHERENCE_MIN ? RafaeliaKernelV22.SPIRAL : 1.0;
        return Math.min(1.0, blendHorizontal(ww) * boost);
    }

    // ─── Window weights ───────────────────────────────────────────────────────

    private static double[] defaultWindowWeights(Window window) {
        int n = Layer.values().length;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = switch (window) {
                case GLOBAL   -> 1.0;                                               // uniform
                case LOCAL    -> Math.exp(-Math.abs(i - n / 2.0) / 2.0);           // Gaussian center
                case TEMPORAL -> (double) (i + 1) / n;                             // recency ramp
                case MODAL    -> Math.pow(RafaeliaKernelV22.PHI, -(n - 1 - i));    // phi decay
            };
        }
        return w;
    }

    @NonNull @Override
    public String toString() {
        return String.format("ConceptHouse[nodes=%d geometricOk=%b]",
                nodes.size(), isGeometricallyCoherent());
    }
}
