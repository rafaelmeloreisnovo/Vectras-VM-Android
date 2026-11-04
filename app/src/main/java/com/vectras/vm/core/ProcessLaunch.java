package com.vectras.vm.core;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.vectras.vm.VMManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wrapper para start/wait/register/unregister com orçamento de execução por categoria.
 */
public final class ProcessLaunch {

    private static final String TAG = "ProcessLaunch";
    private static final long COLLECTOR_JOIN_BOUND_MS = 1_000L;
    private static final AtomicLong REGISTRY_SEQUENCE = new AtomicLong();

    public enum LaunchStatus {
        SUCCESS,
        ERROR,
        TIMEOUT,
        CANCELLED,
        START_ERROR
    }

    public interface StreamLineCallback {
        void onLine(String line);
    }

    public interface ProcessInputWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    public interface ProcessStarter {
        Process start() throws IOException;
    }

    public static final class LaunchResult {
        public final LaunchStatus status;
        public final int exitCode;
        public final boolean timedOut;
        public final String diagnosis;
        public final String feature;
        public final String tag;
        public final String caller;
        public final String registryId;
        public final ProcessRuntimeOps.ExecutionCategory category;

        private LaunchResult(LaunchStatus status,
                             int exitCode,
                             boolean timedOut,
                             String diagnosis,
                             String feature,
                             String tag,
                             String caller,
                             String registryId,
                             ProcessRuntimeOps.ExecutionCategory category) {
            this.status = status;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.diagnosis = diagnosis;
            this.feature = feature;
            this.tag = tag;
            this.caller = caller;
            this.registryId = registryId;
            this.category = category;
        }

        public boolean isSuccess() {
            return status == LaunchStatus.SUCCESS;
        }
    }

    public static final class LaunchTicket implements AutoCloseable {
        private final String registryId;
        private final Process process;
        private final long timeoutMs;
        private final String feature;
        private final String tag;
        private final String caller;
        private boolean closed;

        private LaunchTicket(String registryId, Process process, long timeoutMs, String feature, String tag, String caller) {
            this.registryId = registryId;
            this.process = process;
            this.timeoutMs = timeoutMs;
            this.feature = feature;
            this.tag = tag;
            this.caller = caller;
        }

        public Process process() {
            return process;
        }

        public long timeoutMs() {
            return timeoutMs;
        }

        public String diagnosticPrefix() {
            return ProcessLaunch.diagnosticPrefix(feature, tag, caller);
        }

        public void release(String reason) {
            close();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            if (process != null && process.isAlive()
                    && !ProcessRuntimeOps.stopProcessWithTimeout(process, 250L, 250L)) {
                Log.w(TAG, "LaunchTicket close: process termination not confirmed id=" + registryId);
            }
            // VMManager now keeps a still-alive process supervised rather than marking STOP.
            VMManager.unregisterVmProcess(registryId, process);
        }
    }

    public static final class LaunchLease implements AutoCloseable {
        private final LaunchTicket ticket;

        private LaunchLease(LaunchTicket ticket) {
            this.ticket = ticket;
        }

        public Process getProcess() {
            return ticket == null ? null : ticket.process();
        }

        public Process process() {
            return getProcess();
        }

        public ProcessRuntimeOps.TimeoutExecutionResult waitFor(ProcessRuntimeOps.ExecutionCategory category) throws InterruptedException {
            return ProcessRuntimeOps.waitForByCategory(getProcess(), category);
        }

        public void release(String reason) {
            close();
        }

        @Override
        public void close() {
            if (ticket != null) {
                ticket.close();
            }
        }
    }

    private ProcessLaunch() {
        throw new AssertionError("ProcessLaunch is utility-only");
    }

    public static LaunchResult withBudget(Context context,
                                          String feature,
                                          String tag,
                                          String caller,
                                          @Nullable String vmOrRegistryId,
                                          ProcessRuntimeOps.ExecutionCategory category,
                                          ProcessBuilder processBuilder,
                                          @Nullable StreamLineCallback stdoutCallback,
                                          @Nullable StreamLineCallback stderrCallback,
                                          @Nullable ProcessInputWriter inputWriter) {
        String registryId = buildRegistryId(feature, tag, caller, vmOrRegistryId);
        Process process = null;
        Thread stdoutThread = null;
        Thread stderrThread = null;
        try {
            process = processBuilder.start();
            VMManager.registerVmProcess(context, registryId, process);

            stdoutThread = startCollector("stdout", process.getInputStream(), stdoutCallback);
            stderrThread = startCollector("stderr", process.getErrorStream(), stderrCallback);

            if (inputWriter != null) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    inputWriter.write(writer);
                    writer.flush();
                }
            }

            ProcessRuntimeOps.TimeoutExecutionResult waitResult = ProcessRuntimeOps.waitForByCategory(process, category);
            boolean stdoutDrained = joinCollector(stdoutThread);
            boolean stderrDrained = joinCollector(stderrThread);
            if (!stdoutDrained || !stderrDrained) {
                // Preserve the timeout classification even when a descendant retains a log pipe.
                LaunchStatus status = waitResult.timedOut ? LaunchStatus.TIMEOUT : LaunchStatus.ERROR;
                return new LaunchResult(
                        status, waitResult.exitCode, waitResult.timedOut,
                        "stream_collector_drain_incomplete:" + waitResult.message,
                        feature, tag, caller, registryId, category);
            }

            LaunchStatus launchStatus = mapStatus(waitResult);
            return new LaunchResult(
                    launchStatus,
                    waitResult.exitCode,
                    waitResult.timedOut,
                    waitResult.message,
                    feature,
                    tag,
                    caller,
                    registryId,
                    category
            );
        } catch (IOException e) {
            return new LaunchResult(
                    LaunchStatus.START_ERROR,
                    -1,
                    false,
                    e.toString(),
                    feature,
                    tag,
                    caller,
                    registryId,
                    category
            );
        } finally {
            if (process != null) {
                if (process.isAlive()
                        && !ProcessRuntimeOps.stopProcessWithTimeout(process, 250L, 250L)) {
                    Log.w(TAG, "ProcessLaunch finally: live process retained in supervisor id=" + registryId);
                }
                VMManager.unregisterVmProcess(registryId, process);
            }
        }
    }

    public static LaunchLease withBudget(ProcessBuilder processBuilder,
                                         String feature,
                                         String tag,
                                         String caller,
                                         @Nullable String vmOrRegistryId) throws IOException {
        String registryId = buildRegistryId(feature, tag, caller, vmOrRegistryId);
        Process process = processBuilder.start();
        VMManager.registerVmProcess(null, registryId, process);
        return new LaunchLease(new LaunchTicket(registryId, process, 0L, feature, tag, caller));
    }

    public static LaunchTicket withBudget(String feature,
                                          String tag,
                                          String caller,
                                          long timeoutMs,
                                          ProcessStarter starter) throws IOException {
        if (starter == null) {
            throw new IOException("starter_missing");
        }
        String registryId = buildRegistryId(feature, tag, caller, null);
        Process process = starter.start();
        VMManager.registerVmProcess(null, registryId, process);
        return new LaunchTicket(registryId, process, timeoutMs, feature, tag, caller);
    }

    public static String diagnosticPrefix(String feature, String tag, String caller) {
        return "feature=" + sanitize(feature) + ",tag=" + sanitize(tag) + ",caller=" + sanitize(caller);
    }

    private static LaunchStatus mapStatus(ProcessRuntimeOps.TimeoutExecutionResult waitResult) {
        if (waitResult == null || waitResult.status == null) {
            return LaunchStatus.ERROR;
        }
        if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.TIMEOUT) {
            return LaunchStatus.TIMEOUT;
        }
        if (waitResult.status == ProcessRuntimeOps.TimeoutExecutionResult.Status.ERROR) {
            if (waitResult.message != null && waitResult.message.toLowerCase(Locale.US).contains("interrupted")) {
                return LaunchStatus.CANCELLED;
            }
            return LaunchStatus.ERROR;
        }
        return LaunchStatus.SUCCESS;
    }

    private static Thread startCollector(String label,
                                         InputStream inputStream,
                                         @Nullable StreamLineCallback callback) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (callback != null) {
                        callback.onLine(line);
                    }
                }
            } catch (IOException ioException) {
                Log.w(TAG, "collector(" + label + ") failed: " + ioException.getMessage());
            }
        }, "process-launch-" + label + "-collector");
        // A descendant may inherit a pipe: the collector must not pin app shutdown.
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static boolean joinCollector(Thread thread) {
        if (thread == null) {
            return true;
        }
        try {
            thread.join(COLLECTOR_JOIN_BOUND_MS);
            return !thread.isAlive();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String buildRegistryId(String feature,
                                          String tag,
                                          String caller,
                                          @Nullable String vmOrRegistryId) {
        String stablePrefix = sanitize(feature) + "-" + sanitize(tag) + "-" + sanitize(caller);
        String vmPart = sanitize(vmOrRegistryId == null ? "none" : vmOrRegistryId);
        String tsPart = Long.toHexString(ProcessRuntimeOps.wallMs());
        // Wall clock can repeat across concurrent launches. Sequence gives each lease a unique key.
        return stablePrefix + "-" + vmPart + "-" + tsPart
                + "-" + Long.toHexString(REGISTRY_SEQUENCE.incrementAndGet());
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
