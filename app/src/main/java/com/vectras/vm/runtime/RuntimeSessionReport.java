package com.vectras.vm.runtime;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.vectras.vm.AppConfig;
import com.vectras.vm.core.NativeFastPath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Per-VM runtime proof artifact.
 *
 * <p>Each launch can persist one JSON file that ties together command contract,
 * expanded preflight, QEMU socket/process probes and native/fallback counters.</p>
 */
public final class RuntimeSessionReport {
    private static final String TAG = "RuntimeSessionReport";
    public static final String FILE_NAME = "runtime_session_report.json";

    private final JSONObject root = new JSONObject();
    private final JSONArray phases = new JSONArray();

    private RuntimeSessionReport(String vmId, String vmName) {
        try {
            root.put("schema", "vectras-runtime-session-report/v1");
            root.put("vm_id", safeVmId(vmId));
            root.put("vm_name", safe(vmName));
            root.put("created_wall_ms", System.currentTimeMillis());
            root.put("created_mono_ms", android.os.SystemClock.elapsedRealtime());
            root.put("app_version", AppConfig.vectrasVersion);
            root.put("app_version_code", AppConfig.vectrasVersionCode);
            root.put("android_sdk", Build.VERSION.SDK_INT);
            root.put("host_abi", hostAbi());
            root.put("phases", phases);
        } catch (Exception ignored) {
        }
    }

    public static RuntimeSessionReport begin(String vmId, String vmName) {
        return new RuntimeSessionReport(vmId, vmName);
    }

    public RuntimeSessionReport putCommand(QemuArgvContract contract) {
        putObject("qemu_argv_contract", contract == null ? null : contract.toJson());
        return this;
    }

    public RuntimeSessionReport putExpandedPreflight(ExpandedRuntimePreflight.Result result) {
        putObject("expanded_preflight", result == null ? null : result.toJson());
        return this;
    }

    public RuntimeSessionReport putRuntimeProbe(QemuRuntimeProbe.Snapshot snapshot) {
        putObject("runtime_probe", snapshot == null ? null : snapshot.toJson());
        return this;
    }

    public RuntimeSessionReport putNativeSnapshot() {
        JSONObject json = new JSONObject();
        long[] raw = new long[NativeFastPath.getNativeBridgeTelemetryLongCount()];
        try {
            NativeFastPath.readNativeBridgeTelemetryRaw(raw, 0);
            json.put("native_available", NativeFastPath.isNativeAvailable());
            json.put("fallback_active", NativeFastPath.isFallbackActive());
            json.put("native_init_status", NativeFastPath.getNativeInitStatus());
            json.put("native_init_error", safe(NativeFastPath.getNativeInitError()));
            json.put("feature_mask", NativeFastPath.getFeatureMask());
            json.put("pointer_bits", NativeFastPath.getPointerBits());
            json.put("cache_line_bytes", NativeFastPath.getNativeCacheLineBytes());
            json.put("page_bytes", NativeFastPath.getNativePageBytes());
            json.put("asm_marker", String.format(Locale.US, "0x%08x", NativeFastPath.asmBridgeMarker()));
            json.put("copy_calls", raw[0]);
            json.put("copy_bytes", raw[1]);
            json.put("xor_calls", raw[2]);
            json.put("crc_calls", raw[3]);
            json.put("route_calls", raw[4]);
            json.put("audit_calls", raw[5]);
            json.put("native_hits", raw[6]);
            json.put("fallback_hits", raw[7]);
            json.put("necessary_count", raw[8]);
            json.put("urgent_count", raw[9]);
            json.put("complementary_count", raw[10]);
        } catch (Exception e) {
            try {
                json.put("error", e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            } catch (Exception ignored) {
            }
        }
        putObject("native_snapshot", json);
        return this;
    }

    public RuntimeSessionReport phase(String name, String status, String detail) {
        JSONObject phase = new JSONObject();
        try {
            phase.put("mono_ms", android.os.SystemClock.elapsedRealtime());
            phase.put("wall_ms", System.currentTimeMillis());
            phase.put("name", safe(name));
            phase.put("status", safe(status));
            phase.put("detail", safe(detail));
            phases.put(phase);
        } catch (Exception ignored) {
        }
        return this;
    }

    public RuntimeSessionReport putString(String key, String value) {
        try {
            root.put(safe(key), safe(value));
        } catch (Exception ignored) {
        }
        return this;
    }

    public RuntimeSessionReport putBoolean(String key, boolean value) {
        try {
            root.put(safe(key), value);
        } catch (Exception ignored) {
        }
        return this;
    }

    public RuntimeSessionReport putLong(String key, long value) {
        try {
            root.put(safe(key), value);
        } catch (Exception ignored) {
        }
        return this;
    }

    public JSONObject toJson() {
        try {
            root.put("updated_wall_ms", System.currentTimeMillis());
            root.put("updated_mono_ms", android.os.SystemClock.elapsedRealtime());
        } catch (Exception ignored) {
        }
        return root;
    }

    public File persist(Context context) {
        if (context == null) return null;
        String vmId = root.optString("vm_id", "transient");
        File sessionDir = new File(context.getCacheDir(), safeVmId(vmId));
        if (!sessionDir.exists() && !sessionDir.mkdirs()) {
            Log.w(TAG, "Unable to create runtime session dir: " + sessionDir.getAbsolutePath());
            return null;
        }
        File out = new File(sessionDir, FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(out, false)) {
            fos.write(toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            fos.flush();
            return out;
        } catch (Exception e) {
            Log.w(TAG, "Failed to persist runtime session report", e);
            return null;
        }
    }

    private void putObject(String key, JSONObject value) {
        try {
            if (value == null) root.put(safe(key), JSONObject.NULL);
            else root.put(safe(key), value);
        } catch (Exception ignored) {
        }
    }

    private static String safeVmId(String vmId) {
        String safe = safe(vmId).trim();
        if (safe.isEmpty()) return "transient";
        return safe.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static String hostAbi() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) return "unknown";
        return Build.SUPPORTED_ABIS[0];
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
