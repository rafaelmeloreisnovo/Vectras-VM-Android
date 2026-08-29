package com.vectras.vm.evidence;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.runtime.QemuArgvContract;
import com.vectras.vm.runtime.QemuRuntimeProbe;
import com.vectras.vterm.Terminal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Closes device-evidence gates only from direct observations.
 *
 * <p>The launch receipt is append-only and SHA-256 accompanied. The catalog is
 * enriched at collection time with a live QEMU probe so a durable dispatch
 * observation is never confused with current VM readiness.</p>
 */
public final class EvidenceClosure {
    private static final String TAG = "EvidenceClosure";
    private static final String TOKEN_VAZIO = EvidenceCatalogCollector.TOKEN_VAZIO;
    private static final String RECEIPT_SCHEMA = "vectras.physical-vm-launch-receipt.v1";
    private static final String RECEIPT_PREFIX = "physical-vm-launch-";
    private static final String RECEIPT_SUFFIX = ".json";

    private EvidenceClosure() {
        throw new AssertionError("utility class");
    }

    /**
     * Records the exact moment ProcessBuilder returned a live process reference.
     * Failure to write evidence never changes VM execution behavior.
     */
    public static void recordVmLaunch(Context context,
                                      String vmId,
                                      QemuArgvContract contract,
                                      Process process) {
        if (context == null || contract == null || process == null) return;
        try {
            Context app = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            long pid = ProcessRuntimeOps.safePid(process);
            QemuRuntimeProbe.Snapshot probe = QemuRuntimeProbe.capture(process, 100);

            JSONObject receipt = new JSONObject();
            receipt.put("schema_version", RECEIPT_SCHEMA);
            receipt.put("record_kind", "PHYSICAL_DEVICE_VM_LAUNCH_DISPATCH_OBSERVATION");
            receipt.put("wall_clock_ms", System.currentTimeMillis());
            receipt.put("elapsed_realtime_ms", SystemClock.elapsedRealtime());
            receipt.put("vm_id", safe(vmId));
            receipt.put("qemu_binary_token", safe(contract.getQemuBinary()));
            receipt.put("argv_sha256", safe(contract.getArgvSha256()));
            receipt.put("process_started", true);
            receipt.put("process_alive_on_receipt", process.isAlive());
            receipt.put("pid", pid);
            receipt.put("runtime_probe_at_dispatch", probe.toJson());
            receipt.put("boundary", "PROCESS_START != VM_SERVICE_READY != GUEST_OS_READY");
            receipt.put("claim_allowed", false);

            byte[] bytes = (receipt.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
            String digest = sha256(bytes);
            File directory = new File(app.getFilesDir(), "evidence/runtime");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IllegalStateException("unable to create runtime evidence directory");
            }
            String stem = RECEIPT_PREFIX + System.currentTimeMillis() + "-" + System.nanoTime();
            File json = new File(directory, stem + RECEIPT_SUFFIX);
            File checksum = new File(directory, json.getName() + ".sha256");
            atomicWrite(json, bytes);
            atomicWrite(checksum,
                    (digest + "  " + json.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            Log.i(TAG, "physical VM launch receipt=" + json.getName() + " sha256=" + digest);
        } catch (Exception e) {
            Log.w(TAG, "unable to persist physical VM launch receipt", e);
        }
    }

    /**
     * Enriches the installation catalog with verifiable closure transitions.
     */
    public static JSONObject enrich(Context context, JSONObject root) throws Exception {
        if (context == null || root == null) return root;
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;

        JSONObject runtime = root.getJSONObject("runtime_filesystem");
        JSONObject postCheck = runtime.getJSONObject("post_check");
        JSONObject qemu = runtime.getJSONObject("qemu");
        String elfStatus = qemu.optJSONObject("host_elf_abi") == null
                ? TOKEN_VAZIO
                : qemu.getJSONObject("host_elf_abi").optString("status", TOKEN_VAZIO);

        boolean postOk = postCheck.optBoolean("ok", false);
        boolean qemuFound = qemu.optBoolean("found", false);
        boolean qemuAbiMatch = "MATCH".equals(elfStatus);
        boolean deviceRuntimeVerified = postOk && qemuFound && qemuAbiMatch;

        ReceiptObservation launch = readLatestLaunchReceipt(app);
        Process liveProcess = Terminal.qemuProcess;
        QemuRuntimeProbe.Snapshot liveProbe = QemuRuntimeProbe.capture(liveProcess, 250);
        boolean sameLiveProcess = launch.valid
                && liveProbe.processAlive
                && launch.pid > 0L
                && liveProbe.pid == launch.pid;
        boolean serviceReady = sameLiveProcess
                && (liveProbe.qmpSocketExists || liveProbe.vncSocketExists || liveProbe.vncTcpOpen);

        JSONObject closure = new JSONObject();
        closure.put("device_runtime_verified", deviceRuntimeVerified);
        closure.put("post_check_clean", postOk);
        closure.put("qemu_executable_found", qemuFound);
        closure.put("qemu_host_elf_abi_match", qemuAbiMatch);
        closure.put("latest_launch_receipt", launch.json);
        closure.put("latest_launch_receipt_valid", launch.valid);
        closure.put("live_runtime_probe", liveProbe.toJson());
        closure.put("live_process_matches_receipt", sameLiveProcess);
        closure.put("vm_service_ready", serviceReady);
        closure.put("guest_os_ready", TOKEN_VAZIO);
        closure.put("boundary", "RUNTIME_READY != GUEST_OS_READY");
        root.put("operational_closure", closure);

        JSONObject state = root.getJSONObject("evidence_state");
        state.put("device_runtime_verified", deviceRuntimeVerified);
        state.put("physical_vm_launch_verified", launch.valid);
        state.put("end_to_end_vm_runtime_verified", serviceReady);
        state.put("operational_claim_allowed", deviceRuntimeVerified && launch.valid && serviceReady);
        // Certification remains a distinct authority/gate; do not auto-promote it.
        state.put("claim_allowed", false);

        root.put("token_vazio", normalizeGaps(
                root.optJSONArray("token_vazio"),
                postOk,
                qemuFound,
                qemuAbiMatch,
                launch.valid,
                serviceReady));
        return root;
    }

    private static JSONArray normalizeGaps(JSONArray source,
                                            boolean postOk,
                                            boolean qemuFound,
                                            boolean qemuAbiMatch,
                                            boolean launchReceiptValid,
                                            boolean serviceReady) throws Exception {
        JSONArray out = new JSONArray();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                String gap = source.optString(i, "");
                if ("POST_CHECK_CLEAN_RECEIPT".equals(gap) && postOk) continue;
                if ("QEMU_EXECUTABLE_RUNTIME".equals(gap) && qemuFound) continue;
                if ("QEMU_HOST_ELF_ABI_RECEIPT".equals(gap) && qemuAbiMatch) continue;
                if ("PHYSICAL_VM_LAUNCH_RECEIPT".equals(gap) && launchReceiptValid) continue;
                if ("END_TO_END_VM_BOOT_EVIDENCE".equals(gap) && serviceReady) continue;
                putUnique(out, gap);
            }
        }
        if (!postOk) putUnique(out, "POST_CHECK_CLEAN_RECEIPT");
        if (!qemuFound) putUnique(out, "QEMU_EXECUTABLE_RUNTIME");
        if (!qemuAbiMatch) putUnique(out, "QEMU_HOST_ELF_ABI_RECEIPT");
        if (!launchReceiptValid) putUnique(out, "PHYSICAL_VM_LAUNCH_RECEIPT");
        if (!serviceReady) putUnique(out, "END_TO_END_VM_BOOT_EVIDENCE");
        return out;
    }

    private static void putUnique(JSONArray array, String value) {
        if (value == null || value.trim().isEmpty()) return;
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return;
        }
        array.put(value);
    }

    private static ReceiptObservation readLatestLaunchReceipt(Context context) {
        try {
            File directory = new File(context.getFilesDir(), "evidence/runtime");
            File[] entries = directory.listFiles();
            if (entries == null || entries.length == 0) return ReceiptObservation.empty();
            File newest = null;
            for (File entry : entries) {
                if (!entry.isFile()) continue;
                String name = entry.getName();
                if (!name.startsWith(RECEIPT_PREFIX) || !name.endsWith(RECEIPT_SUFFIX)) continue;
                if (newest == null || entry.lastModified() > newest.lastModified()) newest = entry;
            }
            if (newest == null) return ReceiptObservation.empty();

            byte[] bytes = readAll(newest);
            String actualSha = sha256(bytes);
            File companion = new File(newest.getParentFile(), newest.getName() + ".sha256");
            String expectedSha = companion.isFile()
                    ? firstToken(new String(readAll(companion), StandardCharsets.UTF_8)) : "";
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            long pid = json.optLong("pid", -1L);
            boolean valid = RECEIPT_SCHEMA.equals(json.optString("schema_version"))
                    && json.optBoolean("process_started", false)
                    && pid > 0L
                    && json.optString("argv_sha256", "").matches("[0-9a-fA-F]{64}")
                    && expectedSha.matches("[0-9a-fA-F]{64}")
                    && MessageDigest.isEqual(
                            expectedSha.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                            actualSha.getBytes(StandardCharsets.US_ASCII));
            json.put("artifact_name", newest.getName());
            json.put("artifact_sha256", actualSha);
            json.put("companion_sha256_match", valid);
            return new ReceiptObservation(valid, pid, json);
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            try {
                error.put("status", TOKEN_VAZIO);
                error.put("reason", e.getClass().getSimpleName());
            } catch (Exception ignored) {
            }
            return new ReceiptObservation(false, -1L, error);
        }
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static void atomicWrite(File destination, byte[] bytes) throws Exception {
        File parent = destination.getParentFile();
        File temp = new File(parent, "." + destination.getName() + ".tmp-" + System.nanoTime());
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        if (!temp.renameTo(destination)) {
            throw new IllegalStateException("unable to publish evidence artifact: " + destination.getName());
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }

    private static String firstToken(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        int split = trimmed.indexOf(' ');
        return split < 0 ? trimmed : trimmed.substring(0, split);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ReceiptObservation {
        final boolean valid;
        final long pid;
        final JSONObject json;

        ReceiptObservation(boolean valid, long pid, JSONObject json) {
            this.valid = valid;
            this.pid = pid;
            this.json = json;
        }

        static ReceiptObservation empty() {
            JSONObject json = new JSONObject();
            try {
                json.put("status", TOKEN_VAZIO);
                json.put("reason", "no-physical-launch-receipt");
            } catch (Exception ignored) {
            }
            return new ReceiptObservation(false, -1L, json);
        }
    }
}
