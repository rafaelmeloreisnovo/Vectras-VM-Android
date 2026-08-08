package com.vectras.vm.benchmark;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Runtime exporter for the industrial benchmark method contract.
 *
 * This generator is intentionally usable before any benchmark run. Methodology
 * is not a result and must not be hidden behind a successful measurement.
 */
public final class IndustrialBenchmarkMethodology {

    public static final String FILE_NAME = "VECTRAS_INDUSTRIAL_BENCHMARK_METHODS_V1.md";

    private IndustrialBenchmarkMethodology() {}

    public static File write(Context context) throws IOException {
        File directory = context.getExternalFilesDir("benchmark-methods");
        if (directory == null) directory = new File(context.getFilesDir(), "benchmark-methods");
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IOException("Unable to create benchmark methodology directory: " + directory);
        }
        File file = new File(directory, FILE_NAME);
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            byte[] bytes = build(context).getBytes(StandardCharsets.UTF_8);
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            atomic.finishWrite(output);
            return file;
        } catch (IOException error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
    }

    public static String build(Context context) {
        VectraBenchmark.DeviceSpecification device = VectraBenchmark.getDeviceSpecification();
        StringBuilder sb = new StringBuilder(14_000);
        sb.append("# Vectras Industrial Benchmark Methods V1\n\n");
        sb.append("Generated: ").append(utcNow()).append("\n\n");
        sb.append("## DUT snapshot\n\n");
        sb.append("- Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n");
        sb.append("- Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("- CPU: ").append(device.cpuModel).append("\n");
        sb.append("- Cores visible: ").append(device.cpuCores).append("\n");
        sb.append("- Max frequency reported: ").append(device.getFormattedCpuFreq()).append("\n");
        sb.append("- RAM reported: ").append(device.getFormattedRam()).append("\n");
        sb.append("- ABI: ").append(device.supportedAbis == null ? "UNAVAILABLE" : join(device.supportedAbis)).append("\n\n");

        sb.append("## Measurement invariant\n\n");
        sb.append("Only compare observations that share the same metric definition, workload, input, unit, execution route and provenance. ")
            .append("Different metric IDs are not repeated samples. A user-space benchmark observes the complete software/hardware stack and cannot be promoted to an isolated-silicon claim without direct counter/control evidence.\n\n");

        String[] domains = {
            "CPU/instruction execution",
            "memory hierarchy",
            "storage/durability",
            "kernel/scheduler/concurrency",
            "virtualization/emulation",
            "sensors/edge timing",
            "integrity/build/provenance"
        };
        String[][] controls = {
            {"operation-counted integer/bit workload", "FP32/FP64 workload with declared flags", "SIMD versus scalar A/B", "branch/control-flow distribution", "single/multi scaling", "syscall transition separated", "ABI + binary hash"},
            {"sequential bandwidth", "fixed-seed random access", "copy/fill path identity", "working-set sweep", "stride sweep", "warm/first-touch distinction", "cache fields only when positively detected"},
            {"fixture path/size", "sequential bytes transferred", "random 4KiB I/O", "buffered versus synchronized write", "fsync latency", "cache state disclosure", "free-space + fixture integrity"},
            {"clock/timer calibration", "background interference", "threads/priority/affinity availability", "context-switch/synchronization", "GC/runtime interference", "invalidation threshold", "queue/governance telemetry"},
            {"native baseline", "guest architecture/accelerator", "emulated syscall transition", "map/copy overhead", "timer/event dispatch", "serialization bytes/ops", "host/guest/config hashes"},
            {"inventory versus acquisition", "requested versus observed period", "callback latency distribution", "cancel/timeout/error paths", "UNAVAILABLE not zero", "framework power metadata boundary", "timestamp clock provenance"},
            {"APK/ELF/source hashes", "compiler/version/flags", "CRC/hash byte count", "anti-elimination sink/check", "linker/dependency route", "atomic receipt publication", "claim scope bounded by evidence"}
        };
        sb.append("## Seven production domains × seven controls\n\n");
        for (int d = 0; d < domains.length; d++) {
            sb.append("### ").append((char)('A' + d)).append(". ").append(domains[d]).append("\n\n");
            for (int i = 0; i < controls[d].length; i++) {
                sb.append(i + 1).append(". ").append(controls[d][i]).append(".\n");
            }
            sb.append("\n");
        }

        sb.append("## Statistical contract\n\n");
        sb.append("- Preserve every raw sample for one homogeneous repeated series.\n");
        sb.append("- Report n, median, mean, sample SD (N-1), MAD and IQR.\n");
        sb.append("- One sample cannot establish reproducibility.\n");
        sb.append("- Use Student-t CI for a small approximately normal repeated series or a declared bootstrap procedure when assumptions are not justified.\n");
        sb.append("- CV is valid only inside one positive ratio-scale metric series.\n");
        sb.append("- Never average different workloads to manufacture a reproducibility percentage.\n");
        sb.append("- Composite scores require a separately versioned dimensionless normalization/baseline/weight/uncertainty model.\n\n");

        sb.append("## Evidence states\n\n");
        sb.append("PASS | FAIL | NOT_MEASURED | UNAVAILABLE | BLOCKED | INVALIDATED | OBSERVED_LIMITED\n\n");
        sb.append("`TOKEN_VAZIO` is retained only as an auditable absence marker when the reason is genuinely unknown or evidence has not yet been produced.\n\n");

        sb.append("## Industrial gate\n\n");
        sb.append("Provenance complete; metric dimensionally coherent; execution route observed; repeated samples homogeneous; interference bounded/disclosed; estimator compatible with the data; raw evidence retained; invalidated/blocked runs cannot promote a claim. References to ISO/IEEE/SPEC/NIST/MLPerf are methodology guidance only unless a real conformance program is executed.\n");
        return sb.toString();
    }

    private static String join(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
