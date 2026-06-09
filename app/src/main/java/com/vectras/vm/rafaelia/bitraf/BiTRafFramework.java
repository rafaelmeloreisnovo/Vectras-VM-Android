package com.vectras.vm.rafaelia.bitraf;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

import java.util.Arrays;

/**
 * BiTRaf 10×10×10 Framework — discrete expansion with Fibonacci Rafael.
 *
 * <p>Implements the BiTRaf framework from the architecture:
 * <pre>
 *   BIT10³ = 10 × 10 × 10 = 1000 discrete expansion nodes
 *
 *   Fibonacci Rafael sequence:
 *     f_{n,0} = Σ α_k · F_{nk} + Σ β_k · F̃_{nk} + π_n + ν
 *   where F = standard Fibonacci, F̃ = anti-Fibonacci (negaFibonacci),
 *   α_k, β_k are spiral weights, π_n = parity term, ν = noise/entropy
 *
 *   Parity (dual parity):
 *     T_n = (f_{mod n}) mod 2
 *
 *   Curvature/Coherence:
 *     C_n = F_{n-1} - 2F_n + F_{n+1}  (discrete second derivative)
 *     interval: -1 ≤ C_n ≤ +1  (normalized)
 *
 *   BIT10³ fractals:  F₁ (nodes), F₂ (branches), F₃ (leaves) = tree fractals
 * </pre>
 *
 * <p>The 10×10×10 cube stores: [dimension 0: spatial] [dim 1: temporal] [dim 2: ethical]
 * Each cell holds a double value ∈ [0,1] representing local coherence.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-BITRAF
 */
public final class BiTRafFramework {

    // ─── Dimensions ────────────────────────────────────────────────────────────
    public static final int DIM   = 10;
    public static final int NODES = DIM * DIM * DIM;  // 1000

    // ─── Fibonacci Rafael constants ────────────────────────────────────────────
    /** Standard Fibonacci up to index 30 (precomputed). */
    static final long[] FIB = new long[31];
    static {
        FIB[0] = 0; FIB[1] = 1;
        for (int i = 2; i <= 30; i++) FIB[i] = FIB[i-1] + FIB[i-2];
    }

    /** Spiral weight α_k = φ^(-k) */
    static double alphaWeight(int k) { return Math.pow(RafaeliaKernelV22.PHI, -k); }

    /** Spiral weight β_k = (√3/2)^k */
    static double betaWeight(int k)  { return Math.pow(RafaeliaKernelV22.SPIRAL, k); }

    // ─── State: 10×10×10 coherence cube ──────────────────────────────────────
    private final double[] cube = new double[NODES];  // flat: [x*100 + y*10 + z]
    private final double[] fibRafael;                  // Fibonacci Rafael sequence [0..DIM*3)
    private final int[]    parity;                     // parity T_n
    private final double[] curvature;                  // curvature C_n

    private int    generation = 0;
    private double globalCoherence = 0.0;

    private BiTRafFramework() {
        int seqLen = DIM * 3;
        fibRafael = new double[seqLen];
        parity    = new int[seqLen];
        curvature = new double[seqLen];
        initSequence(seqLen);
        initCube();
    }

    public static BiTRafFramework create() { return new BiTRafFramework(); }

    // ─── Sequence initialization ───────────────────────────────────────────────

    private void initSequence(int len) {
        for (int n = 0; n < len; n++) {
            fibRafael[n] = fibRafaelAt(n, 0.01);
            parity[n]    = (int)(Math.abs(Math.round(fibRafael[n])) % 2);
        }
        // Curvature (discrete second derivative, normalized)
        curvature[0] = 0.0;
        curvature[len - 1] = 0.0;
        double maxAbs = 1.0;
        for (int n = 1; n < len - 1; n++) {
            curvature[n] = fibRafael[n-1] - 2.0 * fibRafael[n] + fibRafael[n+1];
            maxAbs = Math.max(maxAbs, Math.abs(curvature[n]));
        }
        for (int n = 1; n < len - 1; n++) {
            curvature[n] /= maxAbs;  // normalize to [-1, +1]
        }
    }

    private void initCube() {
        for (int x = 0; x < DIM; x++) {
            for (int y = 0; y < DIM; y++) {
                for (int z = 0; z < DIM; z++) {
                    // Seed coherence from Fibonacci Rafael parity and curvature
                    int  idx = x * DIM * DIM + y * DIM + z;
                    int  n   = (x + y + z) % fibRafael.length;
                    double base = 0.5 + 0.4 * Math.sin(fibRafael[n] * RafaeliaKernelV22.PI / 10.0);
                    cube[idx] = clamp01(base);
                }
            }
        }
        updateGlobalCoherence();
    }

    // ─── Evolution step ────────────────────────────────────────────────────────

    /**
     * Evolve the cube one generation: each cell's coherence is updated
     * based on its 6 face-neighbors (3D lattice Laplacian) + Fibonacci Rafael drift.
     */
    public void evolve() {
        double[] next = new double[NODES];
        for (int x = 0; x < DIM; x++) {
            for (int y = 0; y < DIM; y++) {
                for (int z = 0; z < DIM; z++) {
                    int idx = flat(x, y, z);
                    double self = cube[idx];
                    double lap  = laplacian(x, y, z);
                    int    n    = (x + y + z + generation) % curvature.length;
                    double drift = curvature[n] * 0.05;
                    next[idx] = clamp01(self + lap * 0.1 + drift);
                }
            }
        }
        System.arraycopy(next, 0, cube, 0, NODES);
        generation++;
        updateGlobalCoherence();
    }

    // ─── Cube accessors ────────────────────────────────────────────────────────

    public double get(int x, int y, int z) { return cube[flat(x, y, z)]; }
    public void   set(int x, int y, int z, double v) { cube[flat(x, y, z)] = clamp01(v); }

    /** Slice through Z-plane at z=k (returns 10×10 matrix). */
    public double[][] sliceZ(int z) {
        double[][] m = new double[DIM][DIM];
        for (int x = 0; x < DIM; x++)
            for (int y = 0; y < DIM; y++)
                m[x][y] = cube[flat(x, y, z)];
        return m;
    }

    public double  getGlobalCoherence() { return globalCoherence; }
    public int     getGeneration()      { return generation; }
    public double  getFibRafael(int n)  { return fibRafael[n % fibRafael.length]; }
    public int     getParity(int n)     { return parity[n % parity.length]; }
    public double  getCurvature(int n)  { return curvature[n % curvature.length]; }

    // ─── Fibonacci Rafael formula ─────────────────────────────────────────────

    /**
     * f_{n,0} = Σ_{k=0}^{K} α_k · F_{nk mod 30}
     *         + Σ_{k=0}^{K} β_k · negaFib(nk mod 30)
     *         + parityTerm(n) + noise
     */
    static double fibRafaelAt(int n, double noise) {
        int K = 4;
        double sumAlpha = 0.0, sumBeta = 0.0;
        for (int k = 1; k <= K; k++) {
            int idxA = (n * k) % 30;
            int idxB = (n * k) % 30;
            sumAlpha += alphaWeight(k) * FIB[idxA];
            sumBeta  += betaWeight(k)  * negaFib(idxB);
        }
        double pi_n = (n % 2 == 0) ? RafaeliaKernelV22.SPIRAL : -RafaeliaKernelV22.SPIRAL;
        return (sumAlpha + sumBeta + pi_n + noise) / (FIB[Math.min(n, 30)] + 1.0);
    }

    /** NegaFibonacci: F(-n) = (-1)^(n+1) × F(n) */
    static double negaFib(int n) {
        int absN = Math.min(Math.abs(n), 30);
        double base = FIB[absN];
        return (n < 0 && n % 2 == 0) ? -base : base;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double laplacian(int x, int y, int z) {
        double center = cube[flat(x, y, z)];
        double sum = 0.0;
        int count = 0;
        for (int[] delta : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
            int nx = x+delta[0], ny = y+delta[1], nz = z+delta[2];
            if (nx>=0&&nx<DIM&&ny>=0&&ny<DIM&&nz>=0&&nz<DIM) {
                sum += cube[flat(nx, ny, nz)] - center;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private static int flat(int x, int y, int z) { return x * DIM * DIM + y * DIM + z; }
    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private void updateGlobalCoherence() {
        double sum = 0.0;
        for (double v : cube) sum += v;
        globalCoherence = sum / NODES;
    }

    @NonNull @Override public String toString() {
        return String.format("BiTRaf10x10x10[gen=%d coh=%.4f nodes=%d]",
                generation, globalCoherence, NODES);
    }
}
