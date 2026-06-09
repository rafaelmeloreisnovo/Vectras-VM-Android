package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

/**
 * Fractal Geometric Matrix — 42 points, Base 60.
 *
 * <p>Implements module 10 of the 12-module architecture: the fractal-geometric
 * coherence matrix. Based on the "RAFAELIA – Matriz Coerente Geométrica Fractal"
 * design with the number 42 at its center hexagon.
 *
 * <p>Architecture:
 * <pre>
 *   42 nodes arranged in a hexagonal fractal:
 *     Center:     1 node
 *     Ring 1:     6 nodes  (hexagon)
 *     Ring 2:    12 nodes
 *     Ring 3:    18 nodes
 *     Ring 4:     5 nodes  (apex/toroid)
 *     Total:     42 nodes
 *
 *   Base-60 (sexagesimal) mapping:
 *     Each node index n maps to base-60 coordinate (n div 60, n mod 60)
 *     Loop 55 = key resonance loop (55 = 5 × 11, Fibonacci proximity)
 *     n_mod_10 = discrete spatial index within each sexagesimal group
 *
 *   Fractal layers (Toroidal + Sin curves):
 *     A1 = sin(n - 9G)          — inner spiral
 *     A2 = cos(10n - 18) × 0.5  — outer ring
 *     Loop = 2(m_i × x_Loop mod 10)  — recursive modular loop
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-FRACTAL
 */
public final class FractalGeometricMatrix {

    public static final int  NODES    = 42;
    public static final int  BASE_60  = 60;
    public static final int  LOOP_55  = 55;

    // Ring sizes: center + 4 rings = 42
    private static final int[] RING_SIZES = { 1, 6, 12, 18, 5 };

    // Node coordinates in polar form [radius, angle_rad]
    private final double[][] polar = new double[NODES][2];
    // Cartesian projections [x, y]
    private final double[][] cartesian = new double[NODES][2];
    // Coherence value per node ∈ [0,1]
    private final double[]   coherence = new double[NODES];
    // Base-60 address per node [major, minor]
    private final int[][]    base60    = new int[NODES][2];

    // Fractal generation state
    private int generation = 0;

    private FractalGeometricMatrix() {
        initGeometry();
        initBase60();
        initCoherence();
    }

    public static FractalGeometricMatrix create() { return new FractalGeometricMatrix(); }

    // ─── Initialization ───────────────────────────────────────────────────────

    private void initGeometry() {
        int nodeIdx = 0;
        double[] radii = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        for (int ring = 0; ring < RING_SIZES.length; ring++) {
            int count  = RING_SIZES[ring];
            double r   = radii[ring];
            for (int k = 0; k < count; k++) {
                double angle = ring == 0 ? 0.0
                        : (2.0 * Math.PI * k / count) + (ring % 2 == 0 ? 0 : Math.PI / count);
                polar[nodeIdx][0]     = r;
                polar[nodeIdx][1]     = angle;
                cartesian[nodeIdx][0] = r * Math.cos(angle);
                cartesian[nodeIdx][1] = r * Math.sin(angle);
                nodeIdx++;
            }
        }
    }

    private void initBase60() {
        for (int i = 0; i < NODES; i++) {
            base60[i][0] = i / BASE_60;
            base60[i][1] = i % BASE_60;
        }
    }

    private void initCoherence() {
        for (int n = 0; n < NODES; n++) {
            coherence[n] = computeFractalCoherence(n, 0);
        }
    }

    // ─── Fractal evolution ────────────────────────────────────────────────────

    /**
     * Advance the fractal one generation.
     * Each node's coherence is updated by its fractal layer formulas.
     */
    public void evolve() {
        generation++;
        for (int n = 0; n < NODES; n++) {
            coherence[n] = computeFractalCoherence(n, generation);
        }
    }

    // ─── Fractal formulas ─────────────────────────────────────────────────────

    /**
     * A1 = sin(n - 9G) — inner spiral
     * A2 = cos(10n - 18) × 0.5 — outer ring
     * Loop = 2(m_i × x_Loop mod 10) — recursive modular loop
     */
    double computeFractalCoherence(int n, int g) {
        double a1 = Math.sin(n - 9.0 * g);
        double a2 = Math.cos(10.0 * n - 18.0) * 0.5;

        // Loop 55: modular resonance
        int loopIdx = (int)((LOOP_55 * n * (g + 1)) % NODES);
        double xLoop = coherence[loopIdx]; // self-referential
        double loop  = 2.0 * ((loopIdx * xLoop) % 10.0) / 10.0;

        // Combine with base-60 harmonic
        int major = base60[n][0], minor = base60[n][1];
        double base60Harmonic = Math.sin(2.0 * Math.PI * minor / BASE_60)
                * Math.cos(2.0 * Math.PI * major / BASE_60 * RafaeliaKernelV22.PHI);

        double raw = (a1 + a2 + loop + base60Harmonic) / 4.0;
        return clamp01((raw + 1.0) / 2.0);  // normalize to [0,1]
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public double   getCoherence(int n)         { return coherence[n]; }
    public double[] getCartesian(int n)          { return cartesian[n].clone(); }
    public double[] getPolar(int n)              { return polar[n].clone(); }
    public int[]    getBase60(int n)             { return base60[n].clone(); }
    public int      getGeneration()              { return generation; }

    public double globalCoherence() {
        double sum = 0.0;
        for (double c : coherence) sum += c;
        return sum / NODES;
    }

    /** Find the node with maximum coherence. */
    public int maxCoherenceNode() {
        int maxIdx = 0;
        for (int i = 1; i < NODES; i++) {
            if (coherence[i] > coherence[maxIdx]) maxIdx = i;
        }
        return maxIdx;
    }

    /** Identify ring membership of node n. */
    public int ringOf(int n) {
        int pos = 0;
        for (int ring = 0; ring < RING_SIZES.length; ring++) {
            pos += RING_SIZES[ring];
            if (n < pos) return ring;
        }
        return RING_SIZES.length - 1;
    }

    // ─── Distance matrix (toroidal-like: base-60 wrap) ───────────────────────

    public double toroidalDistance(int a, int b) {
        double dx = cartesian[a][0] - cartesian[b][0];
        double dy = cartesian[a][1] - cartesian[b][1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    @NonNull @Override public String toString() {
        return String.format("FractalGeoMatrix[42nodes base60 gen=%d coh=%.4f]",
                generation, globalCoherence());
    }
}
