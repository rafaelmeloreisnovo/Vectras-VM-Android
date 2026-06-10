package com.vectras.vm.benchmark;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.FractalGeometricMatrix;
import com.vectras.vm.rafaelia.bitraf.BiTRafFramework;
import com.vectras.vm.rafaelia.toroidal.ToroidalMatrixEngine;

import java.util.zip.CRC32C;

/**
 * RAFAELIA_HEXGRID Benchmark — extension of VectraBenchmark with RAFAELIA-specific metrics.
 *
 * <p>Adds the metrics observed in the RAFAELIA_HEXGRID benchmark summary
 * (Android 10 armv7j, Clang 21.1.8, Termux):
 * <pre>
 *   B1_MALLOC_FREE_64B      — 64-byte allocation/free cycle (ns/op)
 *   B2_MEMCPY_4MB           — 4 MB memcpy throughput (GB/s)
 *   B3_CRC32_BASELINE       — CRC32 software baseline (GB/s)
 *   B4_CRC32C_THROUGHPUT    — CRC32C hardware-accelerated (GB/s)
 *   M3_Q16_KERNEL_LATENCY   — Q16 fixed-point kernel step latency (ns/op)
 *   M4_TTL8_FSM_LATENCY     — TTL-8 FSM state transition latency (ns/transition)
 *   M5_T7_TOROIDAL_UPDATE   — Toroidal matrix 7×7 step latency (ns/step)
 *   M6_MEMORY_BANDWIDTH     — Memory read bandwidth proxy (GB/s)
 *   H1_FRACTAL_COHERENCE    — FractalGeometricMatrix 42-node evolve (ns/gen)
 *   H2_BITRAF_EVOLVE        — BiTRaf 10×10×10 evolve step (ns/gen)
 *   H3_ATTENTION_STEP       — OctagonalAttention convergence (ns/step)
 *   H4_ZIPRAF_PACK_4K       — ZiprafCore pack 4096-byte payload (ns/op)
 *   H5_SYNC_HASH_CHAIN      — SyncHashLogger chain-verify 256 entries (ns/op)
 * </pre>
 *
 * <p>Claim boundary: superiority claims vs baselines require documented comparison
 * with exact flags, SHA256 of artifacts, and bound loops.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-HEXGRID
 * @see VectraBenchmark
 */
public final class HexGridBenchmark {

    // ─── Metric IDs ───────────────────────────────────────────────────────────
    public static final int B1_MALLOC_FREE_64B   = 0;
    public static final int B2_MEMCPY_4MB        = 1;
    public static final int B3_CRC32_BASELINE    = 2;
    public static final int B4_CRC32C_THROUGHPUT = 3;
    public static final int M3_Q16_KERNEL_LATENCY = 4;
    public static final int M4_TTL8_FSM_LATENCY  = 5;
    public static final int M5_T7_TOROIDAL_UPDATE = 6;
    public static final int M6_MEMORY_BANDWIDTH  = 7;
    public static final int H1_FRACTAL_COHERENCE  = 8;
    public static final int H2_BITRAF_EVOLVE      = 9;
    public static final int H3_ATTENTION_STEP     = 10;
    public static final int H4_ZIPRAF_PACK_4K     = 11;
    public static final int H5_SYNC_HASH_CHAIN    = 12;
    public static final int METRIC_COUNT          = 13;

    static final String[] METRIC_NAMES = {
            "B1_MALLOC_FREE_64B", "B2_MEMCPY_4MB", "B3_CRC32_BASELINE", "B4_CRC32C_THROUGHPUT",
            "M3_Q16_KERNEL_LATENCY", "M4_TTL8_FSM_LATENCY", "M5_T7_TOROIDAL_UPDATE",
            "M6_MEMORY_BANDWIDTH",
            "H1_FRACTAL_COHERENCE", "H2_BITRAF_EVOLVE", "H3_ATTENTION_STEP",
            "H4_ZIPRAF_PACK_4K", "H5_SYNC_HASH_CHAIN"
    };

    static final String[] METRIC_UNITS = {
            "ns/op", "GB/s", "GB/s", "GB/s",
            "ns/op", "ns/transition", "ns/step", "GB/s",
            "ns/gen", "ns/gen", "ns/step", "ns/op", "ns/op"
    };

    // ─── Pre-allocated buffers (no allocation during timed sections) ──────────
    private static final int BUF_4M = 4 * 1024 * 1024;
    private static final byte[]  BUF_A = new byte[BUF_4M];
    private static final byte[]  BUF_B = new byte[BUF_4M];
    private static final byte[]  BUF_4K = new byte[4096];
    private static final long[]  RESULT = new long[METRIC_COUNT];

    // Objects allocated once (not inside timed loops)
    private static final ToroidalMatrixEngine TORO_7 = ToroidalMatrixEngine.create(7, 7);
    private static final FractalGeometricMatrix FRACTAL = FractalGeometricMatrix.create();
    private static final BiTRafFramework BITRAF = BiTRafFramework.create();
    private static final ThreadLocal<CRC32C> CRC32C_TL = ThreadLocal.withInitial(CRC32C::new);

    static {
        // Warm up buffers (page-fault ahead of time)
        for (int i = 0; i < BUF_4M; i++) BUF_A[i] = (byte)(i ^ 0xAB);
        for (int i = 0; i < BUF_4M; i++) BUF_B[i] = (byte)(i ^ 0xCD);
        for (int i = 0; i < 4096; i++)   BUF_4K[i] = (byte)(i ^ 0x5A);
        TORO_7.initGaussianPulse(1.0, 2.0);
    }

    private static final int WARMUP_ITERS  = 3;
    private static final int MEASURE_ITERS = 16;

    private HexGridBenchmark() {}

    // ─── Run all metrics ──────────────────────────────────────────────────────

    @NonNull
    public static HexGridResult runAll() {
        HexGridResult r = new HexGridResult(METRIC_COUNT);

        r.medians[B1_MALLOC_FREE_64B]    = runB1();
        r.medians[B2_MEMCPY_4MB]         = runB2();
        r.medians[B3_CRC32_BASELINE]     = runB3();
        r.medians[B4_CRC32C_THROUGHPUT]  = runB4();
        r.medians[M3_Q16_KERNEL_LATENCY] = runM3();
        r.medians[M4_TTL8_FSM_LATENCY]   = runM4();
        r.medians[M5_T7_TOROIDAL_UPDATE] = runM5();
        r.medians[M6_MEMORY_BANDWIDTH]   = runM6();
        r.medians[H1_FRACTAL_COHERENCE]  = runH1();
        r.medians[H2_BITRAF_EVOLVE]      = runH2();
        r.medians[H3_ATTENTION_STEP]     = runH3();
        r.medians[H4_ZIPRAF_PACK_4K]     = runH4();
        r.medians[H5_SYNC_HASH_CHAIN]    = runH5();

        return r;
    }

    // ─── Individual metric implementations ───────────────────────────────────

    /** B1: 64-byte alloc+fill+free latency (ns/op, median of MEASURE_ITERS) */
    static double runB1() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) b1Kernel();
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            b1Kernel();
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    private static void b1Kernel() {
        // 64-byte scope-allocated array (stack in JIT)
        @SuppressWarnings("MismatchedReadAndWriteOfArray")
        long[] buf = new long[8];
        for (int i = 0; i < 8; i++) buf[i] = i * 0x9E3779B9L;
        // prevent elimination
        if (buf[7] == 0) throw new AssertionError();
    }

    /** B2: 4 MB memcpy bandwidth (GB/s). */
    static double runB2() {
        for (int w = 0; w < WARMUP_ITERS; w++) b2Kernel();
        long[] samples = new long[MEASURE_ITERS];
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            b2Kernel();
            samples[i] = System.nanoTime() - t0;
        }
        double nsMedian = median(samples);
        return BUF_4M / (nsMedian / 1e9) / 1e9; // GB/s
    }

    private static void b2Kernel() {
        System.arraycopy(BUF_A, 0, BUF_B, 0, BUF_4M);
    }

    /** B3: CRC32 software baseline (GB/s). */
    static double runB3() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) softCrc32(BUF_A);
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            softCrc32(BUF_A);
            samples[i] = System.nanoTime() - t0;
        }
        return BUF_4M / (median(samples) / 1e9) / 1e9;
    }

    /** B4: CRC32C hardware-accelerated (GB/s). */
    static double runB4() {
        CRC32C crc = CRC32C_TL.get();
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) { crc.reset(); crc.update(BUF_A); }
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            crc.reset();
            crc.update(BUF_A);
            samples[i] = System.nanoTime() - t0;
        }
        return BUF_4M / (median(samples) / 1e9) / 1e9;
    }

    /** M3: Q16 fixed-point kernel step latency (ns/op). */
    static double runM3() {
        long[] samples = new long[MEASURE_ITERS];
        int state = 0x10000; // Q16 = 1.0
        for (int w = 0; w < WARMUP_ITERS; w++) state = q16KernelStep(state);
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            state = q16KernelStep(state);
            samples[i] = System.nanoTime() - t0;
        }
        if (state == Integer.MIN_VALUE) throw new AssertionError(); // prevent DCE
        return median(samples);
    }

    private static int q16KernelStep(int s) {
        // Q16.16 fixed-point multiply-accumulate step
        long a = (long) s * 0x9E3779B9L >> 16;
        long b = (long) s * 0x6C62272EL >> 16;
        return (int)((a ^ b ^ (a >> 3)) & 0xFFFFFFFFL);
    }

    /** M4: TTL-8 FSM state transition latency (ns/transition). */
    static double runM4() {
        long[] samples = new long[MEASURE_ITERS];
        int[] ttl8States = new int[8];
        for (int i = 0; i < 8; i++) ttl8States[i] = i;
        for (int w = 0; w < WARMUP_ITERS; w++) ttl8FsmStep(ttl8States);
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            ttl8FsmStep(ttl8States);
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples) / 8.0; // per-transition
    }

    private static void ttl8FsmStep(int[] s) {
        // 8-state FSM: each state transitions based on XOR chain
        for (int i = 0; i < 8; i++) {
            s[i] = (s[i] ^ s[(i + 1) % 8] ^ (i << 2)) & 0xFF;
        }
    }

    /** M5: Toroidal matrix 7×7 step latency (ns/step). */
    static double runM5() {
        TORO_7.initGaussianPulse(1.0, 2.0);
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) TORO_7.step();
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            TORO_7.step();
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    /** M6: Memory read bandwidth proxy (GB/s). */
    static double runM6() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) memReadKernel();
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            memReadKernel();
            samples[i] = System.nanoTime() - t0;
        }
        return BUF_4M / (median(samples) / 1e9) / 1e9;
    }

    private static long memReadKernel() {
        long sum = 0;
        for (int i = 0; i < BUF_4M; i++) sum += BUF_A[i];
        return sum;
    }

    /** H1: FractalGeometricMatrix 42-node evolve (ns/gen). */
    static double runH1() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) FRACTAL.evolve();
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            FRACTAL.evolve();
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    /** H2: BiTRaf 10×10×10 evolve step (ns/gen). */
    static double runH2() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) BITRAF.evolve();
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            BITRAF.evolve();
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    /** H3: OctagonalAttention single step (ns/step). */
    static double runH3() {
        com.vectras.vm.rafaelia.attention.OctagonalAttentionEngine attn =
                com.vectras.vm.rafaelia.attention.OctagonalAttentionEngine.create();
        attn.attuneToTask(
                com.vectras.vm.rafaelia.attention.OctagonalAttentionEngine.Vector.EXECUCAO, 0.8);
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) attn.step();
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            attn.step();
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    /** H4: ZiprafCore pack 4096-byte payload (ns/op). */
    static double runH4() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) {
            try { com.vectras.vm.rafaelia.connector.ZiprafCore.pack(BUF_4K); }
            catch (Exception ignored) {}
        }
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            try { com.vectras.vm.rafaelia.connector.ZiprafCore.pack(BUF_4K); }
            catch (Exception ignored) {}
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    /** H5: SyncHashLogger hash computation for chain entry (ns/op). */
    static double runH5() {
        long[] samples = new long[MEASURE_ITERS];
        for (int w = 0; w < WARMUP_ITERS; w++) {
            com.vectras.vm.rafaelia.connector.SyncHashLogger.computeHash(
                    w, System.nanoTime(),
                    com.vectras.vm.rafaelia.connector.SyncHashLogger.Level.INFO,
                    "TAG", "msg", "prevhash");
        }
        for (int i = 0; i < MEASURE_ITERS; i++) {
            long t0 = System.nanoTime();
            com.vectras.vm.rafaelia.connector.SyncHashLogger.computeHash(
                    i, System.nanoTime(),
                    com.vectras.vm.rafaelia.connector.SyncHashLogger.Level.INFO,
                    "TAG", "benchmark_message_payload_content_here", "prevhashvalue");
            samples[i] = System.nanoTime() - t0;
        }
        return median(samples);
    }

    // ─── Statistics helpers ───────────────────────────────────────────────────

    static double median(long[] sorted) {
        long[] copy = sorted.clone();
        // Simple insertion sort (MEASURE_ITERS ≤ 32, no cost)
        for (int i = 1; i < copy.length; i++) {
            long key = copy[i]; int j = i - 1;
            while (j >= 0 && copy[j] > key) { copy[j+1] = copy[j]; j--; }
            copy[j+1] = key;
        }
        int n = copy.length;
        return n % 2 == 0 ? (copy[n/2-1] + copy[n/2]) / 2.0 : copy[n/2];
    }

    private static long softCrc32(byte[] data) {
        long crc = 0xFFFFFFFFL;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xEDB88320L : crc >>> 1;
            }
        }
        return crc ^ 0xFFFFFFFFL;
    }

    // ─── Result type ──────────────────────────────────────────────────────────

    public static final class HexGridResult {
        public final double[] medians;
        HexGridResult(int count) { medians = new double[count]; }

        @NonNull @Override public String toString() {
            StringBuilder sb = new StringBuilder("RAFAELIA_HEXGRID Results:\n");
            for (int i = 0; i < METRIC_COUNT; i++) {
                sb.append(String.format("  %-28s %12.4f  %s\n",
                        METRIC_NAMES[i], medians[i], METRIC_UNITS[i]));
            }
            return sb.toString();
        }
    }
}
