package com.vectras.vm.runtime;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.core.ProcessRuntimeOps.ExecutionCategory;
import com.vectras.vm.core.ProcessRuntimeOps.TimeoutExecutionResult;
import com.vectras.vm.core.ProotCommandBuilder;
import com.vectras.vm.setupwizard.SetupFeatureCore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fail-closed in-app evidence gate for Vectras' private PRoot/QEMU runtime.
 *
 * <p>The gate resolves executable files only inside the app-owned rootfs, hashes
 * those exact files, executes their corresponding absolute guest paths through
 * the same {@link ProotCommandBuilder}, resolves the final launch argv, hashes
 * that argv, and writes a synced receipt before QEMU launch is admitted.</p>
 */
public final class VectrasRuntimeEvidenceGate {
    private static final String TAG = "VectrasRuntimeEvidence";
    public static final String SCHEMA = "vectras.runtime-evidence.v1";
    public static final String GUEST_PATH =
            "/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin";
    private static final Pattern QEMU_TOKEN = Pattern.compile("^qemu-system-[A-Za-z0-9_.-]+$");

    private VectrasRuntimeEvidenceGate() {
    }

    public static final class Result {
        public final boolean ok;
        public final String state;
        public final String receiptPath;
        public final String reason;
        public final String resolvedQemuGuestPath;
        public final String resolvedLaunchArgvSha256;
        public final List<String> resolvedLaunchArgv;

        Result(
                boolean ok,
                String state,
                String receiptPath,
                String reason,
                String resolvedQemuGuestPath,
                String resolvedLaunchArgvSha256,
                List<String> resolvedLaunchArgv
        ) {
            this.ok = ok;
            this.state = state;
            this.receiptPath = receiptPath;
            this.reason = reason;
            this.resolvedQemuGuestPath = resolvedQemuGuestPath;
            this.resolvedLaunchArgvSha256 = resolvedLaunchArgvSha256;
            this.resolvedLaunchArgv = Collections.unmodifiableList(
                    new ArrayList<>(resolvedLaunchArgv == null ? Collections.emptyList() : resolvedLaunchArgv)
            );
        }
    }

    private static final class PrivateExecutable {
        final File hostFile;
        final String guestPath;

        PrivateExecutable(File hostFile, String guestPath) {
            this.hostFile = hostFile;
            this.guestPath = guestPath;
        }

        boolean executable() {
            return hostFile.isFile() && hostFile.canExecute();
        }
    }

    public static Result probe(
            Context context,
            ProotCommandBuilder proot,
            List<String> requestedProcessArgv
    ) {
        if (context == null || proot == null || requestedProcessArgv == null || requestedProcessArgv.isEmpty()) {
            return failed("invalid-probe-input", "");
        }
        for (int i = 0; i < requestedProcessArgv.size(); i++) {
            if (requestedProcessArgv.get(i) == null) {
                return failed("null-argv-item-" + i, "");
            }
        }

        String requestedToken = requestedProcessArgv.get(0).trim();
        String token = basename(requestedToken);
        if (!QEMU_TOKEN.matcher(token).matches()) {
            return failed("unsafe-or-unrecognized-qemu-token", "");
        }

        File filesDir = context.getFilesDir();
        File prootFile = new File(filesDir, "usr/bin/proot");
        File rootShell = new File(filesDir, "distro/bin/sh");
        PrivateExecutable qemu = resolvePrivateGuestExecutable(filesDir, token);
        PrivateExecutable qemuImg = resolvePrivateGuestExecutable(filesDir, "qemu-img");

        boolean prootReady = executable(prootFile);
        boolean shellReady = executable(rootShell);
        boolean qemuReady = qemu != null && qemu.executable();
        boolean qemuImgReady = qemuImg != null && qemuImg.executable();

        ProbeResult qemuProbe = qemuReady
                ? executeThroughProot(proot, qemu.guestPath, "--version")
                : ProbeResult.notRun("qemu-binary-not-ready");
        ProbeResult qemuImgProbe = qemuImgReady
                ? executeThroughProot(proot, qemuImg.guestPath, "--version")
                : ProbeResult.notRun("qemu-img-not-ready");

        SetupFeatureCore.ProotBootstrapValidationResult bootstrap =
                SetupFeatureCore.validateProotBootstrapState(context);

        String apkSha = sha256(applicationApkOrNull(context));
        String prootSha = sha256(prootFile);
        String qemuSha = sha256(qemu == null ? null : qemu.hostFile);
        String qemuImgSha = sha256(qemuImg == null ? null : qemuImg.hostFile);
        boolean hashesBound = isSha256(apkSha)
                && isSha256(prootSha)
                && isSha256(qemuSha)
                && isSha256(qemuImgSha);

        ArrayList<String> resolvedLaunchArgv = new ArrayList<>(requestedProcessArgv);
        if (qemu != null) {
            resolvedLaunchArgv.set(0, qemu.guestPath);
        }
        String resolvedArgvSha = sha256Argv(resolvedLaunchArgv);
        boolean argvBound = qemu != null && isSha256(resolvedArgvSha);

        boolean runtimeOk = prootReady
                && shellReady
                && qemuReady
                && qemuImgReady
                && bootstrap.ok
                && qemuProbe.ok
                && qemuImgProbe.ok
                && hashesBound
                && argvBound;

        String state = runtimeOk ? "DEVICE_PROVEN" : "TOKEN_VAZIO";
        String reason = runtimeOk ? "private-runtime-hash-exec-and-argv-probes-pass" : buildReason(
                prootReady,
                shellReady,
                qemuReady,
                qemuImgReady,
                bootstrap.ok,
                qemuProbe.ok,
                qemuImgProbe.ok,
                hashesBound,
                argvBound
        );

        String receiptPath = writeReceipt(
                context,
                requestedToken,
                token,
                qemu == null ? "" : qemu.guestPath,
                qemuImg == null ? "" : qemuImg.guestPath,
                resolvedArgvSha,
                resolvedLaunchArgv,
                state,
                runtimeOk,
                reason,
                apkSha,
                prootSha,
                qemuSha,
                qemuImgSha,
                prootReady,
                shellReady,
                qemuReady,
                qemuImgReady,
                qemuProbe,
                qemuImgProbe,
                bootstrap
        );
        if (receiptPath.isEmpty()) {
            return failed("receipt-write-failed", qemu == null ? "" : qemu.guestPath);
        }
        return new Result(
                runtimeOk,
                state,
                receiptPath,
                reason,
                qemu == null ? "" : qemu.guestPath,
                resolvedArgvSha,
                resolvedLaunchArgv
        );
    }

    private static Result failed(String reason, String guestPath) {
        return new Result(
                false,
                "TOKEN_VAZIO",
                "",
                reason,
                guestPath,
                "TOKEN_VAZIO",
                Collections.emptyList()
        );
    }

    private static String basename(String value) {
        if (value == null) return "";
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static PrivateExecutable resolvePrivateGuestExecutable(File filesDir, String name) {
        if (filesDir == null || name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) {
            return null;
        }
        File rootfs = new File(filesDir, "distro");
        String[] guestDirs = {"/usr/local/bin", "/usr/bin", "/bin", "/usr/local/sbin", "/usr/sbin", "/sbin"};
        for (String guestDir : guestDirs) {
            File candidate = new File(rootfs, guestDir.substring(1) + "/" + name);
            if (candidate.isFile()) {
                return new PrivateExecutable(candidate, guestDir + "/" + name);
            }
        }
        return null;
    }

    private static ProbeResult executeThroughProot(ProotCommandBuilder proot, String guestPath, String argument) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder();
            proot.applyEnvironment(builder.environment());
            builder.command(proot.buildCommand(java.util.Arrays.asList(guestPath, argument)));
            builder.redirectErrorStream(true);
            process = builder.start();
            TimeoutExecutionResult result = ProcessRuntimeOps.waitForByCategory(
                    process,
                    ExecutionCategory.QUICK_QUERY
            );
            boolean ok = result.status == TimeoutExecutionResult.Status.SUCCESS && result.exitCode == 0;
            return new ProbeResult(ok, result.exitCode, result.status.name(), safeDetail(result.message));
        } catch (Exception failure) {
            return new ProbeResult(false, -1, "EXCEPTION", failure.getClass().getSimpleName());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String safeDetail(String value) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private static boolean executable(File file) {
        return file != null && file.isFile() && file.canExecute();
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static String sha256Argv(List<String> argv) {
        if (argv == null || argv.isEmpty()) return "TOKEN_VAZIO";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String arg : argv) {
                if (arg == null) return "TOKEN_VAZIO";
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                int length = bytes.length;
                digest.update((byte) ((length >>> 24) & 0xff));
                digest.update((byte) ((length >>> 16) & 0xff));
                digest.update((byte) ((length >>> 8) & 0xff));
                digest.update((byte) (length & 0xff));
                digest.update(bytes);
            }
            return hex(digest.digest());
        } catch (Exception failure) {
            return "TOKEN_VAZIO";
        }
    }

    private static String buildReason(
            boolean proot,
            boolean shell,
            boolean qemu,
            boolean qemuImg,
            boolean bootstrap,
            boolean qemuExec,
            boolean qemuImgExec,
            boolean hashesBound,
            boolean argvBound
    ) {
        StringBuilder out = new StringBuilder();
        appendGap(out, "proot", proot);
        appendGap(out, "root-shell", shell);
        appendGap(out, "qemu-file", qemu);
        appendGap(out, "qemu-img-file", qemuImg);
        appendGap(out, "bootstrap", bootstrap);
        appendGap(out, "qemu-exec", qemuExec);
        appendGap(out, "qemu-img-exec", qemuImgExec);
        appendGap(out, "artifact-hashes", hashesBound);
        appendGap(out, "resolved-argv", argvBound);
        return out.length() == 0 ? "unknown-gap" : out.toString();
    }

    private static void appendGap(StringBuilder out, String name, boolean ok) {
        if (ok) return;
        if (out.length() > 0) out.append(',');
        out.append(name);
    }

    private static String writeReceipt(
            Context context,
            String requestedQemuToken,
            String requestedQemuBinary,
            String resolvedQemuGuestPath,
            String resolvedQemuImgGuestPath,
            String resolvedLaunchArgvSha256,
            List<String> resolvedLaunchArgv,
            String state,
            boolean claimAllowed,
            String reason,
            String apkSha,
            String prootSha,
            String qemuSha,
            String qemuImgSha,
            boolean prootReady,
            boolean shellReady,
            boolean qemuReady,
            boolean qemuImgReady,
            ProbeResult qemuProbe,
            ProbeResult qemuImgProbe,
            SetupFeatureCore.ProotBootstrapValidationResult bootstrap
    ) {
        try {
            File evidenceDir = new File(context.getFilesDir(), "evidence");
            if (!evidenceDir.isDirectory() && !evidenceDir.mkdirs()) return "";
            long now = System.currentTimeMillis();
            String receiptId = now + "-" + context.getPackageName();
            File receipt = new File(evidenceDir, "vectras-runtime-" + now + ".json");
            JSONObject doc = new JSONObject();
            doc.put("schema", SCHEMA);
            doc.put("receipt_id", receiptId);
            doc.put("created_unix_ms", now);
            doc.put("package_name", context.getPackageName());
            doc.put("requested_qemu_token", requestedQemuToken);
            doc.put("requested_qemu_binary", requestedQemuBinary);
            doc.put("guest_path", GUEST_PATH);
            doc.put("resolved_qemu_guest_path", resolvedQemuGuestPath);
            doc.put("resolved_qemu_img_guest_path", resolvedQemuImgGuestPath);
            doc.put("resolved_launch_argv_sha256", resolvedLaunchArgvSha256);
            JSONArray launchArgv = new JSONArray();
            for (String arg : resolvedLaunchArgv) launchArgv.put(arg);
            doc.put("resolved_launch_argv", launchArgv);
            doc.put("apk_sha256", apkSha);
            doc.put("proot_sha256", prootSha);
            doc.put("qemu_sha256", qemuSha);
            doc.put("qemu_img_sha256", qemuImgSha);
            doc.put("proot_executable", prootReady);
            doc.put("root_shell_executable", shellReady);
            doc.put("qemu_executable", qemuReady);
            doc.put("qemu_img_executable", qemuImgReady);
            doc.put("bootstrap_validator_ok", bootstrap.ok);
            doc.put("bootstrap_validator_summary", bootstrap.summary());
            doc.put("qemu_probe", qemuProbe.toJson());
            doc.put("qemu_img_probe", qemuImgProbe.toJson());
            doc.put("android_fingerprint", Build.FINGERPRINT == null ? "" : Build.FINGERPRINT);
            doc.put("android_model", Build.MODEL == null ? "" : Build.MODEL);
            doc.put("android_manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
            doc.put("android_hardware", Build.HARDWARE == null ? "" : Build.HARDWARE);
            JSONArray abis = new JSONArray();
            if (Build.SUPPORTED_ABIS != null) {
                for (String abi : Build.SUPPORTED_ABIS) abis.put(abi);
            }
            doc.put("android_supported_abis", abis);
            doc.put("device_state", state);
            doc.put("reproduced_state", "TOKEN_VAZIO");
            doc.put("claim_allowed", claimAllowed);
            doc.put("reason", reason);
            byte[] bytes = (doc.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
            writeAtomicSynced(receipt, bytes);
            writeAtomicSynced(new File(evidenceDir, "vectras-runtime-latest.json"), bytes);
            return receipt.getAbsolutePath();
        } catch (Exception failure) {
            Log.e(TAG, "Unable to write runtime evidence receipt", failure);
            return "";
        }
    }

    private static void writeAtomicSynced(File path, byte[] bytes) throws Exception {
        File parent = path.getParentFile();
        if (parent == null) throw new IllegalStateException("receipt parent unavailable");
        File temp = new File(parent, "." + path.getName() + ".tmp-" + System.nanoTime());
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (path.exists() && !path.delete()) {
            temp.delete();
            throw new IllegalStateException("unable to replace prior receipt");
        }
        if (!temp.renameTo(path)) {
            temp.delete();
            throw new IllegalStateException("unable to atomically publish receipt");
        }
    }

    private static File applicationApkOrNull(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            return info.sourceDir == null ? null : new File(info.sourceDir);
        } catch (Exception failure) {
            return null;
        }
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
            return hex(digest.digest());
        } catch (Exception failure) {
            return "TOKEN_VAZIO";
        }
    }

    private static String hex(byte[] raw) {
        StringBuilder out = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return out.toString();
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
