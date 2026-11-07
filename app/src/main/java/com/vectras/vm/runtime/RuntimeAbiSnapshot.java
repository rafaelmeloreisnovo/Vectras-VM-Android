// SPDX-License-Identifier: GPL-2.0-only
package com.vectras.vm.runtime;

import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.IOException;
import java.io.InputStream;

/**
 * Read-only, side-effect-free asset/host ABI observation.
 *
 * The existing SetupFeatureCore/BootstrapAbiMapper owns actual installation.
 * Selecting an asset here never grants permission to execute it.
 */
public final class RuntimeAbiSnapshot {
    private static final String[] PACKAGED_ABIS = {
            "arm64-v8a", "armeabi-v7a", "x86_64", "x86"
    };

    private RuntimeAbiSnapshot() {}

    private static boolean matchesProcess(String abi, boolean process64) {
        return process64 == ("arm64-v8a".equals(abi) || "x86_64".equals(abi));
    }

    private static boolean hasAsset(Context context, String prefix, String abi) {
        try (InputStream ignored = context.getAssets().open(prefix + "/" + abi + ".tar")) {
            return true;
        } catch (IOException missingOrUnreadable) {
            return false;
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
        return "Host ABI=" + hostAbi
                + " | bootstrap=" + (bootstrap ? "PRESENT" : "MISSING")
                + " | rootfs=" + (distro ? "PRESENT" : "MISSING")
                + " | VM=NOT_VERIFIED";
    }
}
