package com.vectras.vm.qemu;

import android.app.Activity;
import android.os.Build;
import android.util.Log;

import com.vectras.vm.AppConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;

public final class QemuExecConfig {
    private static final String TAG = "QemuExecConfig";
    private static final String CONFIG_FILE_NAME = "qemu-exec.json";
    private static final String REQUIRED_SOURCE_REPO = "rafaelmeloreisnovo/qemu_rafaelia";
    private static final String REQUIRED_EXECUTION_MODE = "proot";
    public static final boolean ALLOW_QEMU_NAME_FALLBACK = false;

    private QemuExecConfig() {
    }

    public static String resolveBinary(Activity activity, String arch) {
        QemuBinaryResolver.Resolution strict = resolveBinaryStrict(activity, arch);
        if (strict.found) {
            return strict.fullPath;
        }
        return ALLOW_QEMU_NAME_FALLBACK
                ? QemuBinaryResolver.primaryBinaryForArch(arch)
                : "";
    }

    public static QemuBinaryResolver.Resolution resolveBinaryStrict(Activity activity, String arch) {
        File configFile = configuredArtifactFile(activity);
        if (configFile != null && configFile.isFile()) {
            try {
                String hostAbi = primaryHostAbi();
                if (hostAbi.isEmpty()) {
                    throw new SecurityException("host ABI unavailable");
                }
                File rootfs = new File(activity.getFilesDir(), "distro");
                String rootfsLibc = detectRootfsLibc(rootfs);
                String configured = resolveArtifactBinary(configFile, arch, hostAbi, rootfsLibc);
                return QemuBinaryResolver.Resolution.found(
                        new File(configured).getName(),
                        configured,
                        Collections.singletonList(configured)
                );
            } catch (Exception rejected) {
                Log.e(TAG, "event=qemu_artifact_contract_rejected config="
                        + configFile.getAbsolutePath(), rejected);
                return QemuBinaryResolver.Resolution.notFound(
                        "artifact-contract-rejected",
                        Collections.singletonList(configFile.getAbsolutePath())
                );
            }
        }

        // Legacy discovery is allowed only when no producer manifest exists.
        // Once qemu-exec.json is present, any invalid field fails closed.
        return QemuBinaryResolver.resolveForArch(activity, arch, TAG);
    }

    private static File configuredArtifactFile(Activity activity) {
        if (activity == null) {
            return null;
        }
        try {
            AppConfig.ensureStoragePaths(activity);
            return new File(AppConfig.maindirpath, CONFIG_FILE_NAME);
        } catch (Exception e) {
            Log.w(TAG, "Unable to resolve qemu-exec.json path", e);
            return null;
        }
    }

    /**
     * Resolves a binary from a producer artifact without trusting a free-form path.
     * Package-private for deterministic contract tests.
     */
    static String resolveArtifactBinary(File configFile,
                                        String guestArch,
                                        String hostAbi,
                                        String rootfsLibc) throws Exception {
        if (configFile == null || !configFile.isFile()) {
            throw new IllegalArgumentException("qemu-exec.json is missing");
        }

        JSONObject root = new JSONObject(readUtf8(configFile));
        if (!REQUIRED_SOURCE_REPO.equals(root.optString("source_repo", "").trim())) {
            throw new SecurityException("unexpected source_repo");
        }
        if (root.optString("source_commit", "").trim().isEmpty()) {
            throw new SecurityException("source_commit is required");
        }

        JSONObject runtime = root.optJSONObject("runtime");
        if (runtime == null) {
            throw new SecurityException("runtime contract is required");
        }
        String runtimeOs = lower(runtime.optString("os", ""));
        String runtimeArch = lower(runtime.optString("arch", ""));
        String runtimeAbi = lower(runtime.optString("abi", ""));
        String runtimeLibc = lower(runtime.optString("libc", ""));
        String executionMode = lower(runtime.optString("execution_mode", ""));

        if (!"linux".equals(runtimeOs)) {
            throw new SecurityException("PRoot launcher requires runtime.os=linux");
        }
        if (!REQUIRED_EXECUTION_MODE.equals(executionMode)) {
            throw new SecurityException("PRoot launcher rejects execution_mode=" + executionMode);
        }
        if (!("musl".equals(runtimeLibc) || "glibc".equals(runtimeLibc))) {
            throw new SecurityException("PRoot launcher requires musl or glibc");
        }
        if (!runtimeAbi.equals(runtimeOs + "-" + runtimeArch)) {
            throw new SecurityException("runtime.abi mismatch");
        }

        String expectedRuntimeArch = normalizeHostAbi(hostAbi);
        if (expectedRuntimeArch.isEmpty() || !expectedRuntimeArch.equals(runtimeArch)) {
            throw new SecurityException(
                    "artifact runtime arch " + runtimeArch + " incompatible with host ABI " + hostAbi
            );
        }

        String detectedRootfsLibc = lower(rootfsLibc);
        if (detectedRootfsLibc.isEmpty()) {
            throw new SecurityException("rootfs libc could not be detected");
        }
        if (!runtimeLibc.equals(detectedRootfsLibc)) {
            throw new SecurityException(
                    "artifact libc " + runtimeLibc + " incompatible with rootfs libc " + detectedRootfsLibc
            );
        }

        JSONObject binaries = root.optJSONObject("binary");
        JSONObject hashes = root.optJSONObject("sha256");
        if (binaries == null || hashes == null) {
            throw new SecurityException("binary and sha256 maps are required");
        }

        String guestKey = QemuBinaryResolver.normalizeArch(guestArch).toLowerCase(Locale.ROOT);
        String relativePath = binaries.optString(guestKey, "").trim();
        if (relativePath.isEmpty()) {
            relativePath = binaries.optString("default", "").trim();
        }
        if (relativePath.isEmpty()) {
            throw new IllegalArgumentException("guest binary is absent: " + guestKey);
        }
        if (new File(relativePath).isAbsolute()
                || relativePath.contains("..")
                || relativePath.indexOf('\\') >= 0) {
            throw new SecurityException("binary path must be relative and traversal-free");
        }

        File artifactRoot = configFile.getParentFile().getCanonicalFile();
        File executable = new File(artifactRoot, relativePath).getCanonicalFile();
        String allowedPrefix = artifactRoot.getPath() + File.separator;
        if (!executable.getPath().startsWith(allowedPrefix)) {
            throw new SecurityException("binary escapes artifact root");
        }
        if (!executable.isFile()) {
            throw new IllegalArgumentException("configured QEMU binary missing: " + relativePath);
        }

        String expectedSha256 = lower(hashes.optString(relativePath, ""));
        if (!expectedSha256.matches("[0-9a-f]{64}")) {
            throw new SecurityException("valid SHA-256 is required for " + relativePath);
        }
        String actualSha256 = sha256(executable);
        if (!MessageDigest.isEqual(
                expectedSha256.getBytes(StandardCharsets.US_ASCII),
                actualSha256.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("SHA-256 mismatch for " + relativePath);
        }

        if (!executable.canExecute() && !executable.setExecutable(true, true)) {
            throw new SecurityException("QEMU binary is not executable: " + relativePath);
        }
        return executable.getAbsolutePath();
    }

    static String normalizeHostAbi(String abi) {
        String value = lower(abi);
        if ("arm64-v8a".equals(value) || "aarch64".equals(value)) return "aarch64";
        if ("armeabi-v7a".equals(value) || "armeabi".equals(value)
                || "armv7".equals(value) || "arm".equals(value)) return "arm";
        if ("x86_64".equals(value)) return "x86_64";
        if ("x86".equals(value) || "i386".equals(value) || "i686".equals(value)) return "i386";
        return "";
    }

    static String detectRootfsLibc(File rootfs) {
        if (rootfs == null || !rootfs.isDirectory()) {
            return "";
        }

        if (new File(rootfs, "etc/alpine-release").isFile()
                || hasAnyFile(rootfs,
                "lib/ld-musl-aarch64.so.1",
                "lib/ld-musl-armhf.so.1",
                "lib/ld-musl-arm.so.1",
                "lib/ld-musl-x86_64.so.1",
                "lib/ld-musl-i386.so.1",
                "usr/lib/libc.musl-aarch64.so.1",
                "usr/lib/libc.musl-armhf.so.1",
                "usr/lib/libc.musl-x86_64.so.1")) {
            return "musl";
        }

        if (hasAnyFile(rootfs,
                "lib64/ld-linux-x86-64.so.2",
                "lib/x86_64-linux-gnu/ld-linux-x86-64.so.2",
                "lib/ld-linux-aarch64.so.1",
                "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "lib/ld-linux-armhf.so.3",
                "lib/arm-linux-gnueabihf/ld-linux-armhf.so.3",
                "usr/glibc-compat/lib/ld-linux-aarch64.so.1")) {
            return "glibc";
        }
        return "";
    }

    private static boolean hasAnyFile(File rootfs, String... relativePaths) {
        if (relativePaths == null) return false;
        for (String relativePath : relativePaths) {
            if (new File(rootfs, relativePath).isFile()) {
                return true;
            }
        }
        return false;
    }

    private static String primaryHostAbi() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) {
            return "";
        }
        return Build.SUPPORTED_ABIS[0] == null ? "" : Build.SUPPORTED_ABIS[0];
    }

    private static String readUtf8(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
