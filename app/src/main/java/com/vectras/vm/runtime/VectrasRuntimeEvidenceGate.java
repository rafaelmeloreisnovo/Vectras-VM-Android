package com.vectras.vm.runtime;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.core.ProcessRuntimeOps.ExecutionCategory;
import com.vectras.vm.core.ProcessRuntimeOps.TimeoutExecutionResult;
import com.vectras.vm.core.ProotCommandBuilder;
import com.vectras.vm.setupwizard.SetupFeatureCore;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * In-app physical runtime evidence gate.
 *
 * <p>Unlike an external run-as doctor, this code runs inside the Vectras app
 * sandbox and can therefore prove its own private PRoot/rootfs/QEMU state even
 * for a non-debuggable release APK. It is fail-closed: a QEMU launch is not
 * admitted unless the same PRoot environment can execute the exact requested
 * QEMU binary and qemu-img version probes and the private runtime files are
 * executable.</p>
 */
public final class VectrasRuntimeEvidenceGate {
    private static final String TAG = "VectrasRuntimeEvidence";
    public static final String SCHEMA = "vectras.runtime-evidence.v1";

    private VectrasRuntimeEvidenceGate() {
    }

    public static final class Result {
        public final boolean ok;
        public final String state;
        public final String receiptPath;
        public final String reason;

        Result(boolean ok, String state, String receiptPath, String reason) {
            this.ok = ok;
            this.state = state;
            this.receiptPath = receiptPath;
            this.reason = reason;
        }
    }

    public static Result probe(Context context, ProotCommandBuilder proot, String qemuBinary) {
        if (context == null || proot == null || qemuBinary == null || qemuBinary.trim().isEmpty()) {
            return new Result(false, "TOKEN_VAZIO", "", "invalid-probe-input");
        }
        if (containsWhitespace(qemuBinary) || qemuBinary.contains("/") || qemuBinary.contains("\\")) {
            return new Result(false, "TOKEN_VAZIO", "", "unsafe-qemu-token");
        }

        File filesDir = context.getFilesDir();
        File prootFile = new File(filesDir, "usr/bin/proot");
        File rootShell = new File(filesDir, "distro/bin/sh");
        File qemuFile = resolveRuntimeFile(filesDir, qemuBinary);
        File qemuImg = resolveRuntimeFile(filesDir, "qemu-img");

        boolean prootReady = executable(prootFile);
        boolean shellReady = executable(rootShell);
        boolean qemuReady = executable(qemuFile);
        boolean qemuImgReady = executable(qemuImg);

        ProbeResult qemuProbe = qemuReady
                ? executeThroughProot(proot, qemuBinary, "--version")
                : ProbeResult.notRun("qemu-binary-not-ready");
        ProbeResult qemuImgProbe = qemuImgReady
                ? executeThroughProot(proot, "qemu-img", "--version")
                : ProbeResult.notRun("qemu-img-not-ready");

        SetupFeatureCore.ProotBootstrapValidationResult bootstrap =
                SetupFeatureCore.validateProotBootstrapState(context);

        boolean ok = prootReady
                && shellReady
                && qemuReady
                && qemuImgReady
                && bootstrap.ok
                && qemuProbe.ok
                && qemuImgProbe.ok;

        String state = ok ? "DEVICE_PROVEN" : "TOKEN_VAZIO";
        String reason = ok ? "all-private-runtime-and-exec-probes-pass" : buildReason(
                prootReady, shellReady, qemuReady, qemuImgReady, bootstrap.ok,
                qemuProbe.ok, qemuImgProbe.ok
        );

        String receiptPath = writeReceipt(
                context, qemuBinary, state, ok, reason,
                prootFile, rootShell, qemuFile, qemuImg,
                qemuProbe, qemuImgProbe, bootstrap
        );
        if (receiptPath.isEmpty() && ok) {
            return new Result(false, "TOKEN_VAZIO", "", "receipt-write-failed");
        }
        return new Result(ok, state, receiptPath, reason);
    }

    private static ProbeResult executeThroughProot(ProotCommandBuilder proot, String binary, String argument) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder();
            proot.applyEnvironment(builder.environment());
            builder.command(proot.buildCommand(Arrays.asList(binary, argument)));
            builder.redirectErrorStream(true);
            process = builder.start();
            TimeoutExecutionResult result = ProcessRuntimeOps.waitForByCategory(
                    process,
                    ExecutionCategory.QUICK_QUERY
            );
            boolean ok = result.status == TimeoutExecutionResult.Status.SUCCESS && result.exitCode == 0;
            return new ProbeResult(ok, result.exitCode, result.status.name(), result.message == null ? "" : result.message);
        } catch (Exception failure) {
            return new ProbeResult(false, -1, "EXCEPTION", failure.getClass().getSimpleName());
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static File resolveRuntimeFile(File filesDir, String name) {
        File[] candidates = new File[] {
                new File(filesDir, "distro/usr/local/bin/" + name),
                new File(filesDir, "distro/usr/bin/" + name),
                new File(filesDir, "usr/bin/" + name),
                new File(filesDir, "bin/" + name)
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) return candidate;
        }
        return candidates[0];
    }

    private static boolean executable(File file) {
        return file != null && file.isFile() && file.canExecute();
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    private static String buildReason(
            boolean proot, boolean shell, boolean qemu, boolean qemuImg,
            boolean bootstrap, boolean qemuExec, boolean qemuImgExec
    ) {
        StringBuilder out = new StringBuilder();
        appendGap(out, "proot", proot);
        appendGap(out, "root-shell", shell);
        appendGap(out, "qemu-file", qemu);
        appendGap(out, "qemu-img-file", qemuImg);
        appendGap(out, "bootstrap", bootstrap);
        appendGap(out, "qemu-exec", qemuExec);
        appendGap(out, "qemu-img-exec", qemuImgExec);
        return out.length() == 0 ? "unknown-gap" : out.toString();
    }

    private static void appendGap(StringBuilder out, String name, boolean ok) {
        if (ok) return;
        if (out.length() > 0) out.append(',');
        out.append(name);
    }

    private static String writeReceipt(
            Context context,
            String requestedQemuBinary,
            String state,
            boolean claimAllowed,
            String reason,
            File proot,
            File shell,
            File qemu,
            File qemuImg,
            ProbeResult qemuProbe,
            ProbeResult qemuImgProbe,
            SetupFeatureCore.ProotBootstrapValidationResult bootstrap
    ) {
        try {
            File evidenceDir = new File(context.getFilesDir(), "evidence");
            if (!evidenceDir.isDirectory() && !evidenceDir.mkdirs()) return "";
            long now = System.currentTimeMillis();
            File receipt = new File(evidenceDir, "vectras-runtime-" + now + ".json");
            JSONObject doc = new JSONObject();
            doc.put("schema", SCHEMA);
            doc.put("receipt_id", now + "-" + context.getPackageName());
            doc.put("created_unix_ms", now);
            doc.put("package_name", context.getPackageName());
            doc.put("requested_qemu_binary", requestedQemuBinary);
            doc.put("apk_sha256", sha256(applicationApk(context)));
            doc.put("proot_sha256", sha256(proot));
            doc.put("qemu_sha256", sha256(qemu));
            doc.put("qemu_img_sha256", sha256(qemuImg));
            doc.put("proot_executable", executable(proot));
            doc.put("root_shell_executable", executable(shell));
            doc.put("qemu_executable", executable(qemu));
            doc.put("qemu_img_executable", executable(qemuImg));
            doc.put("bootstrap_validator_ok", bootstrap.ok);
            doc.put("bootstrap_validator_summary", bootstrap.summary());
            doc.put("qemu_probe", qemuProbe.toJson());
            doc.put("qemu_img_probe", qemuImgProbe.toJson());
            doc.put("device_state", state);
            doc.put("reproduced_state", "TOKEN_VAZIO");
            doc.put("claim_allowed", claimAllowed);
            doc.put("reason", reason);
            byte[] bytes = (doc.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
            writeSynced(receipt, bytes);
            writeSynced(new File(evidenceDir, "vectras-runtime-latest.json"), bytes);
            return receipt.getAbsolutePath();
        } catch (Exception failure) {
            Log.e(TAG, "Unable to write runtime evidence receipt", failure);
            return "";
        }
    }

    private static void writeSynced(File path, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(path)) {
            out.write(bytes);
            out.getFD().sync();
        }
    }

    private static File applicationApk(Context context) throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
        return new File(info.sourceDir);
    }

    private static String sha256(File file) {
        if (file == null || !file.isFile()) return "TOKEN_VAZIO";
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            byte[] raw = digest.digest();
            StringBuilder out = new StringBuilder(64);
            for (byte b : raw) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception failure) {
            return "TOKEN_VAZIO";
        }
    }

    private static final class ProbeResult {
        final boolean ok;
        final int exitCode;
        final String status;
        final String detail;

        ProbeResult(boolean ok, int exitCode, String status, String detail) {
            this.ok = ok;
            this.exitCode = exitCode;
            this.status = status;
            this.detail = detail;
        }

        static ProbeResult notRun(String detail) {
            return new ProbeResult(false, -1, "NOT_RUN", detail);
        }

        JSONObject toJson() throws Exception {
            JSONObject out = new JSONObject();
            out.put("ok", ok);
            out.put("exit_code", exitCode);
            out.put("status", status);
            out.put("detail", detail);
            return out;
        }
    }
}
