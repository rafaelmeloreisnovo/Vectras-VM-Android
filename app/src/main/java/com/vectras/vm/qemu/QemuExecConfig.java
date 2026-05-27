package com.vectras.vm.qemu;

import android.app.Activity;
import android.util.Log;

import com.vectras.vm.AppConfig;
import com.vectras.vm.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;

public final class QemuExecConfig {
    private static final String TAG = "QemuExecConfig";
    private static final String CONFIG_FILE_NAME = "qemu-exec.json";
    public static final boolean ALLOW_QEMU_NAME_FALLBACK = false;

    private QemuExecConfig() {
    }

    public static String resolveBinary(Activity activity, String arch) {
        QemuBinaryResolver.Resolution strict = resolveBinaryStrict(activity, arch);
        if (strict.found) {
            return strict.fullPath;
        }
        if (!ALLOW_QEMU_NAME_FALLBACK) {
            return "";
        }
        String fromConfig = resolveConfiguredBinary(activity, arch);
        if (fromConfig != null) {
            return fromConfig;
        }
        QemuBinaryResolver.Resolution resolution = QemuBinaryResolver.resolveForArch(activity, arch, TAG);
        return resolution.found ? fromConfigPath(resolution.fullPath) : QemuBinaryResolver.primaryBinaryForArch(arch);
    }

    public static QemuBinaryResolver.Resolution resolveBinaryStrict(Activity activity, String arch) {
        String fromConfig = resolveConfiguredBinary(activity, arch);
        if (fromConfig != null) {
            return QemuBinaryResolver.Resolution.found(new File(fromConfig).getName(), fromConfig, java.util.Collections.singletonList(fromConfig));
        }
        return QemuBinaryResolver.resolveForArch(activity, arch, TAG);
    }

    private static String resolveConfiguredBinary(Activity activity, String arch) {
        try {
            AppConfig.ensureStoragePaths(activity);
            File configFile = new File(AppConfig.maindirpath, CONFIG_FILE_NAME);
            if (!configFile.exists()) {
                return null;
            }

            String content = FileUtils.readAFile(configFile.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) {
                return null;
            }

            JSONObject root = new JSONObject(content);
            JSONObject binary = root.optJSONObject("binary");
            if (binary == null) {
                return null;
            }

            String normalizedArch = QemuBinaryResolver.normalizeArch(arch);
            String key = normalizedArch.toLowerCase();
            String path = binary.optString(key, "").trim();
            if (path.isEmpty()) {
                path = binary.optString("default", "").trim();
            }
            if (path.isEmpty()) {
                return null;
            }

            File executable = new File(path);
            if (!executable.exists()) {
                Log.w(TAG, "Configured QEMU binary missing: " + path);
                return null;
            }
            if (!executable.canExecute()) {
                boolean chmodOk = executable.setExecutable(true, true);
                if (!chmodOk) {
                    Log.w(TAG, "Configured QEMU binary not executable and chmod failed: " + path);
                    return null;
                }
            }
            return executable.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "Invalid qemu-exec config. Falling back to runtime resolver.", e);
            return null;
        }
    }

    private static String fromConfigPath(String path) {
        return path == null ? "" : path;
    }
}
