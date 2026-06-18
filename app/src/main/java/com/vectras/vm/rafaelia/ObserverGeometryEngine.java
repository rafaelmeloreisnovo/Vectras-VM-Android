package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

/**
 * ObserverGeometryEngine — Fibonacci / Primes / Bases / Error / Observer wave collapse (Image 10).
 *
 * <pre>
 * 1. Classic Fibonacci   F(n): 0,1,1,2,3,5,8,13,21,34...
 * 2. Shifted window      Fn−3, Fn−2, Fn−1, Fn, Fn+1, Fn+2, Fn+3 (7-point stability window)
 * 3. Fibonacci of Primes F(p_k): F values at prime-index positions (2,3,5,13,89,233...)
 * 4. Base φ representation: [n]_φ = Σ c_k · φ^k  (Zeckendorf decomposition)
 * 5. Error Δ = |real − base_value|
 * 6. Stability = 1 / (1 + Δ) × |F(n)|_p  (prime-weighted Fibonacci stability)
 * 7. Wave collapse: Ψ → X₀ (observer coordinate, wavefunction projection onto real axis)
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-OBSERVER
 */
public final class ObserverGeometryEngine {

    // ─── Pre-computed tables ──────────────────────────────────────────────────
    static final int TABLE_SIZE = 48;
    static final long[] FIB = new long[TABLE_SIZE]; // F(0)..F(47)
    static final int[]  PRIMES_BELOW_50 = { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47 };

    static {
        FIB[0] = 0; FIB[1] = 1;
        for (int i = 2; i < TABLE_SIZE; i++) FIB[i] = FIB[i-1] + FIB[i-2];
    }

    private ObserverGeometryEngine() {}

    // ─── 1. Classic Fibonacci ─────────────────────────────────────────────────

    public static long fib(int n) {
        if (n < 0)           throw new IllegalArgumentException("n must be ≥ 0");
        if (n < TABLE_SIZE)  return FIB[n];
        // extend via iterative
        long a = FIB[TABLE_SIZE - 2], b = FIB[TABLE_SIZE - 1];
        for (int i = TABLE_SIZE; i <= n; i++) { long c = a + b; a = b; b = c; }
        return b;
    }

    // ─── 2. Shifted 7-point stability window ─────────────────────────────────

    /**
     * Returns [F(n-3), F(n-2), F(n-1), F(n), F(n+1), F(n+2), F(n+3)].
     * Indices below 0 treated as 0.
     */
    @NonNull
    public static long[] stabilityWindow(int n) {
        long[] w = new long[7];
        for (int i = 0; i < 7; i++) {
            int idx = n - 3 + i;
            w[i] = (idx >= 0) ? fib(idx) : 0L;
        }
        return w;
    }

    /** Window stability score: variance-like measure. Smaller = more stable. */
    public static double windowStability(int n) {
        long[] w = stabilityWindow(n);
        double mean = 0.0;
        for (long v : w) mean += v;
        mean /= 7.0;
        double variance = 0.0;
        for (long v : w) variance += (v - mean) * (v - mean);
        return variance / 7.0;
    }

    // ─── 3. Fibonacci of Primes ───────────────────────────────────────────────

    /**
     * F(p_k): Fibonacci value at the k-th prime index.
     * k=0 → F(2)=1, k=1 → F(3)=2, k=2 → F(5)=5, ...
     */
    public static long fibOfPrime(int k) {
        if (k < 0 || k >= PRIMES_BELOW_50.length)
            throw new IndexOutOfBoundsException("k=" + k + " out of [0," + PRIMES_BELOW_50.length + ")");
        return fib(PRIMES_BELOW_50[k]);
    }

    /** True if F(n) is prime. Useful for prime resonance detection. */
    public static boolean isFibPrime(int n) {
        long f = fib(n);
        return f > 1 && isPrime(f);
    }

    // ─── 4. Base φ (Zeckendorf decomposition) ─────────────────────────────────

    /**
     * Decompose a non-negative integer into a sum of non-consecutive Fibonacci numbers
     * (Zeckendorf's theorem). Returns the coefficients array c[] such that
     * n = Σ c[k] × F(k+2), c[k] ∈ {0,1}, no two consecutive 1s.
     */
    @NonNull
    public static int[] zeckendorf(long n) {
        if (n < 0) throw new IllegalArgumentException("n must be ≥ 0");
        int maxK = TABLE_SIZE - 1;
        while (maxK > 0 && FIB[maxK] > n) maxK--;
        int[] c = new int[maxK + 1];
        long rem = n;
        for (int k = maxK; k >= 2; k--) {
            if (FIB[k] <= rem) { c[k] = 1; rem -= FIB[k]; }
        }
        return c;
    }

    /** Reconstructs the value from a Zeckendorf coefficient array. */
    public static long fromZeckendorf(int[] c) {
        long sum = 0;
        for (int k = 0; k < c.length && k < TABLE_SIZE; k++) sum += c[k] * FIB[k];
        return sum;
    }

    // ─── 5. Error Δ ───────────────────────────────────────────────────────────

    /**
     * Error Δ = |realValue − baseValue|.
     * baseValue is the nearest Fibonacci number to realValue.
     */
    public static double fibError(double realValue) {
        long nearest = nearestFib(realValue);
        return Math.abs(realValue - nearest);
    }

    public static long nearestFib(double v) {
        if (v <= 0) return 0;
        long prev = FIB[0], curr = FIB[1];
        for (int i = 2; i < TABLE_SIZE; i++) {
            if (FIB[i] > v) {
                return (Math.abs(v - prev) <= Math.abs(v - FIB[i])) ? prev : FIB[i];
            }
            prev = curr; curr = FIB[i];
        }
        return curr;
    }

    // ─── 6. Stability = 1/(1+Δ) × |F(n)|_p ──────────────────────────────────

    /**
     * Prime-weighted Fibonacci stability at index n.
     * |F(n)|_p = F(n) mod p_min where p_min = smallest prime factor of F(n)
     * (or F(n) itself if prime).
     * stability = 1/(1+Δ) × normalised(|F(n)|_p)
     */
    public static double stability(int n, double realValue) {
        double delta = fibError(realValue);
        long fn = fib(n);
        double fn_p = (fn == 0) ? 0.0 : ((double) primeFactor(fn) / fn);
        return (1.0 / (1.0 + delta)) * fn_p;
    }

    private static long primeFactor(long n) {
        if (n <= 1) return n;
        if (n % 2 == 0) return 2;
        for (long i = 3; i * i <= n; i += 2) if (n % i == 0) return i;
        return n; // n is prime
    }

    // ─── 7. Wave collapse: Ψ → X₀ ────────────────────────────────────────────

    /**
     * Project wavefunction Ψ (complex represented as [re, im]) onto the
     * observer coordinate X₀ (real axis).
     *
     * <pre>
     *   X₀ = Re(Ψ) / |Ψ| × coherence_factor
     *   coherence_factor = 1/(1 + fibError(|Ψ|))
     * </pre>
     *
     * @param re real part of Ψ
     * @param im imaginary part of Ψ
     * @return observer coordinate X₀ ∈ [−1, 1]
     */
    public static double waveCollapse(double re, double im) {
        double amplitude = Math.sqrt(re * re + im * im);
        if (amplitude == 0.0) return 0.0;
        double coherence = 1.0 / (1.0 + fibError(amplitude));
        return (re / amplitude) * coherence;
    }

    /** Wavefunction interference: Ψ₁ ⊕ Ψ₂ (vector addition, then collapse). */
    public static double interference(double re1, double im1, double re2, double im2) {
        return waveCollapse(re1 + re2, im1 + im2);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    static boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (long i = 3; i * i <= n; i += 2) if (n % i == 0) return false;
        return true;
    }
}
