package com.vectras.vm.qemu;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class QemuBinaryResolver {
    public static final String DEFAULT_LOG_TAG = "QemuBinaryResolver";
    public static final String DEFAULT_ARCH = "X86_64";

    private static final List<String> SUPPORTED_BINARIES = Collections.unmodifiableList(Arrays.asList(
            "qemu-system-x86_64",
            "qemu-system-aarch64",
            "qemu-system-i386",
            "qemu-system-ppc"
    ));
    private static final List<String> RAFAELIA_ALIAS_BINARIES = Collections.unmodifiableList(Arrays.asList(
            "qemu-system-x86_64-rafacodephi",
            "qemu-system-aarch64-rafacodephi",
            "qemu-system-i386-rafacodephi",
            "qemu-system-ppc-rafacodephi",
            "qemu-system-x86_64-rafaelia",
            "qemu-system-aarch64-rafaelia",
            "qemu-system-i386-rafaelia",
            "qemu-system-ppc-rafaelia"
    ));

    private QemuBinaryResolver() {
        throw new AssertionError("QemuBinaryResolver is a utility class and cannot be instantiated");
    }

    public static List<String> supportedBinaryNames() {
        return SUPPORTED_BINARIES;
    }

    public static String primaryBinaryForArch(String arch) {
        // qemu-system-<target> names the emulated system target, not the ELF ABI
        // of the host executable. Never infer host bitness from this filename.
        String normalized = normalizeArch(arch);
        if ("I386".equals(normalized)) return "qemu-system-i386";
        if ("ARM64".equals(normalized)) return "qemu-system-aarch64";
        if ("PPC".equals(normalized)) return "qemu-system-ppc";
        return "qemu-system-x86_64";
    }

    public static boolean isHost64Bit() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) return true;
        String primary = Build.SUPPORTED_ABIS[0];
        return primary.equals("arm64-v8a") || primary.equals("x86_64") || primary.equals("riscv64");
    }

    public static String normalizeArch(@Nullable String arch) {
        String normalized = arch == null ? "" : arch.trim().toUpperCase(Locale.ROOT);
        if ("X86_64".equals(normalized)
                || "I386".equals(normalized)
                || "ARM64".equals(normalized)
                || "PPC".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_ARCH;
    }

    public static Resolution resolveForArch(Context context, @Nullable String arch, @Nullable String logTag) {
        if (context == null) {
            Resolution resolution = Resolution.notFound("context-null", Collections.emptyList());
            logNotFound(logTag, arch, resolution);
            return resolution;
        }
        String filesDir = context.getFilesDir().getAbsolutePath();
        return resolveForArch(filesDir, arch, null, logTag);
    }

    public static Resolution resolveAny(Context context, @Nullable String logTag) {
        return resolveForArch(context, null, logTag);
    }

    public static Resolution resolveForArch(String filesDir, @Nullable String arch, @Nullable Set<String> existingPaths, @Nullable String logTag) {
        List<String> candidates = buildCandidateBinaries(arch);
        if (candidates.isEmpty()) {
            Resolution resolution = Resolution.notFound("no-binary-candidates", Collections.emptyList());
            logNotFound(logTag, arch, resolution);
            return resolution;
        }

        List<String> searchDirectories = buildSearchDirectories(filesDir);
        if (existingPaths != null && !existingPaths.isEmpty()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(searchDirectories);
            for (String path : existingPaths) {
                if (path == null || path.trim().isEmpty()) continue;
                File parent = new File(path).getParentFile();
                if (parent != null) {
                    merged.add(parent.getAbsolutePath());
                }
            }
            searchDirectories = new ArrayList<>(merged);
        }
        if (searchDirectories.isEmpty()) {
            Resolution resolution = Resolution.notFound("no-search-directories", Collections.emptyList());
            logNotFound(logTag, arch, resolution);
            return resolution;
        }

        ArrayList<String> checkedPaths = new ArrayList<>();
        for (String candidate : candidates) {
            for (String directory : searchDirectories) {
                String fullPath = new File(directory, candidate).getAbsolutePath();
                checkedPaths.add(fullPath);
                if (pathExists(fullPath, existingPaths)) {
                    return Resolution.found(candidate, fullPath, checkedPaths);
                }
            }
        }

        Resolution resolution = Resolution.notFound("binary-not-found", checkedPaths);
        logNotFound(logTag, arch, resolution);
        return resolution;
    }

    private static List<String> buildSearchDirectories(String filesDir) {
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        if (filesDir != null && !filesDir.trim().isEmpty()) {
            File root = new File(filesDir);
            registerPathIfDirectory(directories, new File(root, "distro/usr/local/bin"));
            registerPathIfDirectory(directories, new File(root, "distro/usr/bin"));
            registerPathIfDirectory(directories, new File(root, "usr/bin"));
            registerPathIfDirectory(directories, new File(root, "bin"));
        }

        registerPathIfDirectory(directories, new File("/usr/local/bin"));
        registerPathIfDirectory(directories, new File("/usr/bin"));

        String runtimePath = System.getenv("PATH");
        if (runtimePath != null && !runtimePath.trim().isEmpty()) {
            for (String entry : runtimePath.split(":")) {
                if (entry == null || entry.trim().isEmpty()) {
                    continue;
                }
                registerPathIfDirectory(directories, new File(entry.trim()));
            }
        }
        return new ArrayList<>(directories);
    }

    private static void registerPathIfDirectory(Set<String> directories, File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        directories.add(directory.getAbsolutePath());
    }

    private static boolean pathExists(String fullPath, @Nullable Set<String> existingPaths) {
        if (existingPaths != null) {
            return existingPaths.contains(fullPath);
        }
        File candidate = new File(fullPath);
        if (!candidate.exists()) {
            return false;
        }
        if (candidate.canExecute()) {
            return true;
        }
        return candidate.setExecutable(true, true) && candidate.canExecute();
    }

    private static List<String> buildCandidateBinaries(@Nullable String arch) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(primaryBinaryForArch(arch));
        ordered.add(aliasBinaryForArch(arch, "-rafacodephi"));
        ordered.add(aliasBinaryForArch(arch, "-rafaelia"));
        ordered.addAll(SUPPORTED_BINARIES);
        ordered.addAll(RAFAELIA_ALIAS_BINARIES);
        return new ArrayList<>(ordered);
    }

    private static String aliasBinaryForArch(@Nullable String arch, String suffix) {
        return primaryBinaryForArch(arch) + suffix;
    }

    private static void logNotFound(@Nullable String logTag, @Nullable String arch, Resolution resolution) {
        String tag = (logTag == null || logTag.trim().isEmpty()) ? DEFAULT_LOG_TAG : logTag;
        Log.w(tag,
                "event=qemu_binary_resolution_failed"
                        + " arch=" + (arch == null ? "<any>" : arch)
                        + " reason=" + resolution.reason
                        + " checkedPaths=" + String.join(",", resolution.checkedPaths));
    }

    public static final class Resolution {
        public final boolean found;
        public final String binaryName;
        public final String fullPath;
        public final String reason;
        public final List<String> checkedPaths;

        private Resolution(boolean found, String binaryName, String fullPath, String reason, List<String> checkedPaths) {
            this.found = found;
            this.binaryName = binaryName;
            this.fullPath = fullPath;
            this.reason = reason;
            this.checkedPaths = Collections.unmodifiableList(new ArrayList<>(checkedPaths));
        }

        static Resolution found(String binaryName, String fullPath, List<String> checkedPaths) {
            return new Resolution(true, binaryName, fullPath, "found", checkedPaths);
        }

        static Resolution notFound(String reason, List<String> checkedPaths) {
            return new Resolution(false, "", "", reason, checkedPaths);
        }
    }
}
