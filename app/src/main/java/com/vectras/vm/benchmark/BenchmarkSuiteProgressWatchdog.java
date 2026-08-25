package com.vectras.vm.benchmark;

import android.os.SystemClock;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs the monolithic Vectra benchmark suite while exposing truthful phase-level
 * liveness from the worker stack. The legacy runner does not yet expose a
 * per-metric callback, so this class deliberately reports coarse execution
 * phases rather than inventing metric completion.
 */
final class BenchmarkSuiteProgressWatchdog {
    static final int PROGRESS_INITIALIZING = 25;
    static final int PROGRESS_CPU_SINGLE = 30;
    static final int PROGRESS_CPU_MULTI = 42;
    static final int PROGRESS_MEMORY = 52;
    static final int PROGRESS_STORAGE = 66;
    static final int PROGRESS_INTEGRITY = 76;
    static final int PROGRESS_EMULATION = 82;

    private static final long POLL_MS = 750L;

    interface Listener {
        void onPhase(int progress, String label, long elapsedMs);
    }

    private BenchmarkSuiteProgressWatchdog() {
    }

    static <T> T run(Callable<T> operation, long timeoutMs, Listener listener) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        long effectiveTimeoutMs = Math.max(30_000L, timeoutMs);
        FutureTask<T> task = new FutureTask<>(operation);
        Thread worker = new Thread(task, "vectras-benchmark-suite");
        worker.setDaemon(true);
        long started = SystemClock.elapsedRealtime();
        int highestProgress = PROGRESS_INITIALIZING;
        String lastLabel = "Initializing 79-metric suite";
        worker.start();

        try {
            while (true) {
                try {
                    return task.get(POLL_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException stillRunning) {
                    long elapsed = SystemClock.elapsedRealtime() - started;
                    if (elapsed >= effectiveTimeoutMs) {
                        task.cancel(true);
                        worker.interrupt();
                        throw new TimeoutException(
                                "benchmark suite exceeded wall-clock watchdog: " + elapsed + "ms");
                    }

                    Phase phase = classify(worker.getStackTrace());
                    if (phase.progress > highestProgress) {
                        highestProgress = phase.progress;
                        lastLabel = phase.label;
                    } else if (!phase.label.isEmpty()) {
                        lastLabel = phase.label;
                    }
                    if (listener != null) {
                        listener.onPhase(highestProgress, lastLabel, elapsed);
                    }
                } catch (ExecutionException failed) {
                    Throwable cause = failed.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new RuntimeException(cause);
                }
            }
        } finally {
            if (!task.isDone()) {
                task.cancel(true);
            }
            if (worker.isAlive()) {
                worker.interrupt();
            }
        }
    }

    static Phase classify(StackTraceElement[] stack) {
        if (stack == null || stack.length == 0) {
            return new Phase(PROGRESS_INITIALIZING, "Executing 79-metric suite");
        }

        Phase best = new Phase(PROGRESS_INITIALIZING, "Executing 79-metric suite");
        for (StackTraceElement frame : stack) {
            if (frame == null) continue;
            String method = frame.getMethodName();
            if (method == null) continue;

            Phase candidate = classifyMethod(method);
            if (candidate.progress > best.progress) {
                best = candidate;
            }
        }
        return best;
    }

    static Phase classifyMethod(String method) {
        if (method == null) {
            return new Phase(PROGRESS_INITIALIZING, "Executing 79-metric suite");
        }
        if (method.startsWith("benchEmu")) {
            return new Phase(PROGRESS_EMULATION, "Emulation metrics");
        }
        if (method.startsWith("benchIntegrity")) {
            return new Phase(PROGRESS_INTEGRITY, "Integrity/parity metrics");
        }
        if (method.startsWith("benchStorage")) {
            return new Phase(PROGRESS_STORAGE, "Real storage metrics");
        }
        if (method.startsWith("benchMem")) {
            return new Phase(PROGRESS_MEMORY, "Memory/storage-sim metrics");
        }
        if (method.startsWith("benchCpuMt")) {
            return new Phase(PROGRESS_CPU_MULTI, "CPU multi-thread metrics");
        }
        if (method.startsWith("benchCpu")) {
            return new Phase(PROGRESS_CPU_SINGLE, "CPU single-thread metrics");
        }
        return new Phase(PROGRESS_INITIALIZING, "Executing 79-metric suite");
    }

    static final class Phase {
        final int progress;
        final String label;

        Phase(int progress, String label) {
            this.progress = progress;
            this.label = label == null ? "" : label;
        }
    }
}
