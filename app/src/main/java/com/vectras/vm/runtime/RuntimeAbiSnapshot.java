// SPDX-License-Identifier: GPL-2.0-only
package com.vectras.vm.runtime;

import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Read-only runtime observations; never an installer or a VM-boot receipt.
 * SetupFeatureCore / BootstrapAbiMapper remain authoritative installers.
 * ELF header classification is in the pure C core; file I/O/JNI are hosted.
 */
public final class RuntimeAbiSnapshot {
    private static final String[] PACKAGED_ABIS = {
            "arm64-v8a", "armeabi-v7a", "x86_64", "x86"
    };
    private static final boolean NATIVE_READY;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("vectra_core_accel");
            loaded = true;
        } catch (LinkageError ignored) {
            // An unavailable shared library never authorizes a runtime claim.
        }
        NATIVE_READY = loaded;
    }

    private RuntimeAbiSnapshot() {}

    private static native int nativeProbeElfHeader(byte[] bytes, int validBytes, int hostAbi);

    private static boolean matchesProcess(String abi, boolean process64) {
        return process64 == ("arm64-v8a".equals(abi) || "x86_64".equals(abi));
    }

    private static int hostCode(String abi) {
        if ("armeabi-v7a".equals(abi)) return 1;
        if ("arm64-v8a".equals(abi)) return 2;
        if ("x86".equals(abi)) return 3;
        if ("x86_64".equals(abi)) return 4;
        return 0;
    }

    private static boolean hasAsset(Context context, String prefix, String abi) {
        try (InputStream ignored = context.getAssets().open(prefix + "/" + abi + ".tar")) {
            return true;
        } catch (IOException missingOrUnreadable) {
            return false;
        }
    }

    private static String probeExecutable(File file, int abiCode) {
        if (!file.isFile()) return "MISSING";
        if (!file.canExecute()) return "NOT_EXECUTABLE";
        if (!NATIVE_READY) return "TOKEN_VAZIO_NATIVE_LIBRARY";
        final byte[] first = new byte[64];
        int n = 0;
        try (FileInputStream stream = new FileInputStream(file)) {
            while (n < first.length) {
                int read = stream.read(first, n, first.length - n);
                if (read < 0) break;
                if (read == 0) return "TOKEN_VAZIO_READ";
                n += read;
            }
        } catch (IOException failedToRead) {
            return "TOKEN_VAZIO_READ";
        }
        final int status;
        try {
            status = nativeProbeElfHeader(first, n, abiCode);
        } catch (LinkageError missingOrWrongSymbol) {
            return "TOKEN_VAZIO_JNI";
        }
        switch (status) {
            case 1: return "HEADER_MATCH_ONLY";
            case -7: return "HOST_ABI_MISMATCH";
            case -8: return "ARM_EABI_MISMATCH";
            case -9: return "ARM_HARDFLOAT_MISMATCH";
            case -2: return "TRUNCATED_HEADER";
            default: return "HEADER_REJECTED(" + status + ")";
        }
    }

    public static String summary(Context context) {
        if (context == null) return "TOKEN_VAZIO: context missing";
        final boolean process64 = Process.is64Bit();
        String hostAbi = "TOKEN_VAZIO";
        if (Build.SUPPORTED_ABIS != null) {
            for (String candidate : Build.SUPPORTED_ABIS) {
                for (String packaged : PACKAGED_ABIS) {
                    if (packaged.equals(candidate) && matchesProcess(packaged, process64)) {
                        hostAbi = packaged;
                        break;
                    }
                }
                if (!"TOKEN_VAZIO".equals(hostAbi)) break;
            }
        }
        if ("TOKEN_VAZIO".equals(hostAbi)) {
            return "Host ABI=TOKEN_VAZIO; runtime not verified";
        }
        final boolean bootstrap = hasAsset(context, "bootstrap", hostAbi);
        final boolean distro = hasAsset(context, "alpine19", hostAbi);
        final File filesDir = context.getFilesDir();
        final int code = hostCode(hostAbi);
        return "Host ABI=" + hostAbi
                + " | bootstrap=" + (bootstrap ? "PRESENT" : "MISSING")
                + " | rootfs=" + (distro ? "PRESENT" : "MISSING")
                + " | PRoot=" + probeExecutable(new File(filesDir, "usr/bin/proot"), code)
                + " | BusyBox=" + probeExecutable(new File(filesDir, "distro/bin/busybox"), code)
                + " | QEMU=TOKEN_VAZIO_PATH"
                + " | VM=NOT_VERIFIED";
    }
}
