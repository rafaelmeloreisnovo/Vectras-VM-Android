// SPDX-License-Identifier: GPL-2.0-only
package com.vectras.vm.benchmark;

import android.os.SystemClock;

import java.util.Arrays;
import java.util.Locale;

/**
 * Hosted clock/JNI adapter for the separate freestanding C ALU kernel.
 * No timing or performance claim is made by the C kernel itself.
 */
public final class FreestandingMicrobench {
    private static final int ITERATIONS_PER_CALL = 1024;
    private static final int CALLS_PER_TRIAL = 64;
    private static final int TRIALS = 5;
    private static final boolean LOADED;
    private static volatile int sink;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("vectra_core_accel");
            loaded = true;
        } catch (LinkageError ignored) {
            // A missing/incompatible ABI is reported as evidence, never a crash.
        }
        LOADED = loaded;
    }

    private FreestandingMicrobench() {}

    private static native int nativeFold(int seed, int iterations);

    public static String runOnce() {
        if (!LOADED) return "BLOCKED: native ABI library not loadable; no benchmark performed";
        try {
            // Fixed known-answer test prevents a timing-only false PASS.
            if (nativeFold(0x12345678, 100) != 0xc95a1a08) {
                return "FAIL: native kernel known-answer vector mismatch";
            }
            long[] samples = new long[TRIALS];
            int checksum = 0;
            for (int trial = 0; trial < TRIALS; ++trial) {
                final long startNs = SystemClock.elapsedRealtimeNanos();
                for (int call = 0; call < CALLS_PER_TRIAL; ++call) {
                    checksum ^= nativeFold(0x12345678 ^ (trial * 257 + call), ITERATIONS_PER_CALL);
                }
                samples[trial] = SystemClock.elapsedRealtimeNanos() - startNs;
                if (samples[trial] <= 0) return "TOKEN_VAZIO: timer resolution";
            }
            sink = checksum; // Keep output observable.
            Arrays.sort(samples);
            long medianNsPerCall = samples[TRIALS / 2] / CALLS_PER_TRIAL;
            return String.format(Locale.ROOT,
                    "PASS_SCOPED_DEVICE_KERNEL: median %,d ns/native call; 5x64 calls; "
                            + "1024 ALU iterations/call; checksum=%08x; NOT a QEMU/VM benchmark",
                    medianNsPerCall, sink);
        } catch (LinkageError e) {
            return "BLOCKED: JNI symbol not available (" + e.getClass().getSimpleName() + ")";
        } catch (RuntimeException e) {
            return "FAIL: microbenchmark (" + e.getClass().getSimpleName() + ")";
        }
    }
}
