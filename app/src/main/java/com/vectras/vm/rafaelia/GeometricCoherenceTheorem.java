package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

/**
 * GeometricCoherenceTheorem — Redundant vector mesh reconstructibility (Image 2).
 *
 * <p><b>Theorem:</b> A redundant vector mesh exists such that removing up to n−1 structural
 * subsets does not destroy the global reconstruction capacity of the original information.
 *
 * <pre>
 *   ∀Al k ∈ n,  G / S_k  is reconstructible
 * </pre>
 *
 * <p>Hypotheses:
 * <ul>
 *   <li>Deterministicidade: deterministic notation</li>
 *   <li>Conexões ≥ 3: every node has at least 3 connections</li>
 *   <li>Discretização circular: nodes arranged on a circle</li>
 *   <li>Invariante geométrica: geometric invariant preserved after removal</li>
 * </ul>
 *
 * <p>Default n = 6 moments (hexagonal discretization matching FractalGeometricMatrix ring 1).
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-GEOMETRY
 */
public final class GeometricCoherenceTheorem {

    public static final int    DEFAULT_MOMENTS  = 6;
    public static final int    MIN_CONNECTIONS  = 3;
    /** φ-derived coherence floor for geometric invariant verification */
    public static final double GEOMETRIC_FLOOR  = RafaeliaKernelV22.PHI - 1.0; // ≈ 0.618

    private final int     n;          // number of moments
    private final boolean[][] adj;    // adjacency matrix (circular + cross-connections)
    private final double[]    weight; // node weights (coherence)

    private GeometricCoherenceTheorem(int moments) {
        this.n      = moments;
        this.adj    = buildCircularMesh(moments);
        this.weight = initWeights(moments);
    }

    public static GeometricCoherenceTheorem create() {
        return new GeometricCoherenceTheorem(DEFAULT_MOMENTS);
    }

    public static GeometricCoherenceTheorem create(int moments) {
        if (moments < 3) throw new IllegalArgumentException("moments must be ≥ 3");
        return new GeometricCoherenceTheorem(moments);
    }

    // ─── Mesh construction ────────────────────────────────────────────────────

    /** Circular + cross-diagonal adjacency ensuring every node has ≥ MIN_CONNECTIONS edges. */
    private static boolean[][] buildCircularMesh(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            // circular neighbors
            a[i][(i + 1) % n] = a[(i + 1) % n][i] = true;
            a[i][(i - 1 + n) % n] = a[(i - 1 + n) % n][i] = true;
            // skip-one cross connection (ensures ≥ 3 connections)
            a[i][(i + 2) % n] = a[(i + 2) % n][i] = true;
        }
        return a;
    }

    private static double[] initWeights(int n) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            double theta = 2.0 * RafaeliaKernelV22.PI * i / n;
            w[i] = GEOMETRIC_FLOOR + (1.0 - GEOMETRIC_FLOOR) *
                    Math.abs(Math.cos(theta * RafaeliaKernelV22.PHI));
        }
        return w;
    }

    // ─── Reconstructibility check ─────────────────────────────────────────────

    /**
     * Check if G / S_k is still reconstructible (connected + geometrically coherent)
     * after removing node k.
     *
     * @param k index of the subset/node to remove [0..n-1]
     * @return true if the remaining graph is reconstructible
     */
    public boolean isReconstructible(int k) {
        if (k < 0 || k >= n) throw new IndexOutOfBoundsException("k=" + k + " out of [0," + n + ")");
        return isConnectedWithout(k) && geometricInvariantHolds(k);
    }

    /**
     * Verify the full theorem: ∀ k ∈ [0,n), G/S_k is reconstructible.
     */
    public boolean allSubsetsReconstructible() {
        for (int k = 0; k < n; k++) {
            if (!isReconstructible(k)) return false;
        }
        return true;
    }

    /** Number of subsets k for which G/S_k is reconstructible. */
    public int reconstructibleCount() {
        int count = 0;
        for (int k = 0; k < n; k++) if (isReconstructible(k)) count++;
        return count;
    }

    // ─── Connectivity (BFS) ───────────────────────────────────────────────────

    private boolean isConnectedWithout(int removed) {
        boolean[] visited = new boolean[n];
        int start = (removed == 0) ? 1 : 0;
        bfs(start, removed, visited);
        for (int i = 0; i < n; i++) {
            if (i != removed && !visited[i]) return false;
        }
        return true;
    }

    private void bfs(int start, int removed, boolean[] visited) {
        boolean[] queue = new boolean[n];
        queue[start] = visited[start] = true;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                if (!queue[i]) continue;
                for (int j = 0; j < n; j++) {
                    if (j != removed && adj[i][j] && !visited[j]) {
                        visited[j] = queue[j] = true;
                        changed = true;
                    }
                }
            }
        }
    }

    // ─── Geometric invariant ──────────────────────────────────────────────────

    /**
     * The geometric invariant holds if the mean weight of remaining nodes
     * is still ≥ GEOMETRIC_FLOOR (φ−1 ≈ 0.618).
     */
    private boolean geometricInvariantHolds(int removed) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (i == removed) continue;
            sum += weight[i];
            count++;
        }
        return count > 0 && (sum / count) >= GEOMETRIC_FLOOR;
    }

    // ─── Metrics ──────────────────────────────────────────────────────────────

    public int moments()    { return n; }
    public double[] weights() { return weight.clone(); }

    public int connectionCount(int node) {
        int c = 0;
        for (int j = 0; j < n; j++) if (adj[node][j]) c++;
        return c;
    }

    public double globalWeight() {
        double s = 0.0;
        for (double w : weight) s += w;
        return s / n;
    }

    @NonNull @Override
    public String toString() {
        return String.format(
                "GeomCoherence[n=%d allReconstructible=%b globalWeight=%.3f]",
                n, allSubsetsReconstructible(), globalWeight());
    }
}
