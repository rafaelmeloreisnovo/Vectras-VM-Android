package com.vectras.vm.evidence;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import com.vectras.vm.setupwizard.SetupFeatureCore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Append-only evidence for the single causal boundary:
 * embedded APK seed -> extraction/bootstrap -> app filesDir.
 *
 * <p>QEMU is deliberately outside this receipt. The receipt records the assets
 * observable before extraction, the destination state before/after, the
 * extractor result/error channel, observed file modes, SHA-256 and the setup
 * post-check. It supports diagnosis and custody; it does not certify VM boot.</p>
 */
public final class BootstrapExtractionReceipt {
    public static final String SCHEMA = "vectras.bootstrap-extraction-receipt.v1";
    public static final String TOKEN_VAZIO = "TOKEN_VAZIO";

    private static final String[] DESTINATIONS = new String[] {
            "usr/bin/proot",
            "distro/bin/busybox",
            "distro/bin/sh",
            "distro/usr/bin/env"
    };

    private BootstrapExtractionReceipt() {
        throw new AssertionError("utility class");
    }

    public static Session begin(Context context) throws Exception {
        Context app = context.getApplicationContext();
        JSONObject root = new JSONObject();
        root.put("schema_version", SCHEMA);
        root.put("record_kind", "BASE_RUNTIME_EXTRACTION_ATTEMPT");
        root.put("started_at_utc", utcNow());
        root.put("scope", new JSONObject()
                .put("causal_boundary", "APK_ASSET_TO_FILESDIR")
                .put("qemu_in_scope", false)
                .put("claim_allowed", false)
                .put("boundary", "ASSET_PRESENT != EXTRACTED != EXECUTABLE != POST_CHECK_CLEAN != VM_BOOT"));
        root.put("device_abi_context", abiContext());
        root.put("input_assets", relevantAssets(app));
        root.put("before", runtimeState(app));
        return new Session(app, root);
    }

    public static JSONObject latestReference(Context context) throws Exception {
        File directory = new File(context.getFilesDir(), "evidence/bootstrap-extraction");
        File[] entries = directory.listFiles((dir, name) -> name.startsWith("vectras-bootstrap-extraction-")
                && name.endsWith(".json"));
        if (entries == null || entries.length == 0) {
            return new JSONObject()
                    .put("present", false)
                    .put("status", TOKEN_VAZIO)
                    .put("expected_schema", SCHEMA);
        }

        File latest = entries[0];
        for (File file : entries) {
            if (file.lastModified() > latest.lastModified()) latest = file;
        }
        String digest = sha256File(latest);
        File companion = new File(latest.getParentFile(), latest.getName() + ".sha256");
        boolean companionMatches = false;
        String companionDigest = TOKEN_VAZIO;
        if (companion.isFile()) {
            String text = readText(companion).trim();
            if (!text.isEmpty()) {
                companionDigest = text.split("\\s+", 2)[0];
                companionMatches = digest.equalsIgnoreCase(companionDigest);
            }
        }

        JSONObject result = new JSONObject()
                .put("present", true)
                .put("path", "<filesDir>/evidence/bootstrap-extraction/" + latest.getName())
                .put("sha256", digest)
                .put("companion_present", companion.isFile())
                .put("companion_sha256_value", companionDigest)
                .put("companion_matches", companionMatches);
        try {
            result.put("receipt", new JSONObject(readText(latest)));
        } catch (Exception e) {
            result.put("receipt_status", "UNREADABLE:" + e.getClass().getSimpleName());
        }
        return result;
    }

    public static final class Session {
        private final Context app;
        private final JSONObject root;
        private boolean finished;

        private Session(Context app, JSONObject root) {
            this.app = app;
            this.root = root;
        }

        public synchronized ExportResult finish(
                boolean extractorReturned,
                String lastErrorLog,
                Throwable unexpectedFailure
        ) throws Exception {
            if (finished) {
                throw new IllegalStateException("bootstrap extraction receipt session already finished");
            }
            finished = true;

            JSONObject after = runtimeState(app);
            root.put("finished_at_utc", utcNow());
            root.put("extraction_attempt", new JSONObject()
                    .put("attempted", true)
                    .put("extractor", "SetupFeatureCore.startExtractSystemFiles")
                    .put("extractor_returned", extractorReturned)
                    .put("last_error_log", emptyToToken(lastErrorLog))
                    .put("unexpected_exception", unexpectedFailure == null
                            ? TOKEN_VAZIO
                            : unexpectedFailure.getClass().getName() + ":" + safeMessage(unexpectedFailure)));
            root.put("after", after);

            JSONObject gate = deriveGate(after);
            root.put("gate", gate);
            root.put("causal_fields", new JSONArray()
                    .put("input_assets[].sha256")
                    .put("before.destination_files[]")
                    .put("extraction_attempt.last_error_log")
                    .put("after.destination_files[].observed_mode_octal")
                    .put("after.destination_files[].sha256")
                    .put("after.destination_files[].executable")
                    .put("after.post_check"));
            root.put("artifact_integrity", new JSONObject()
                    .put("serialization", "UTF-8 JSON")
                    .put("digest_algorithm", "SHA-256")
                    .put("digest_mode", "out_of_band_companion_file"));
            return export(app, root);
        }
    }

    public static final class ExportResult {
        public final File jsonFile;
        public final File checksumFile;
        public final String sha256;

        ExportResult(File jsonFile, File checksumFile, String sha256) {
            this.jsonFile = jsonFile;
            this.checksumFile = checksumFile;
            this.sha256 = sha256;
        }
    }

    private static JSONObject abiContext() throws Exception {
        JSONArray supported = new JSONArray();
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) supported.put(abi);
        }
        return new JSONObject()
                .put("supported_abis", supported)
                .put("primary_abi", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                        ? Build.SUPPORTED_ABIS[0]
                        : TOKEN_VAZIO)
                .put("os_arch", emptyToToken(System.getProperty("os.arch")));
    }

    private static JSONArray relevantAssets(Context context) throws Exception {
        Set<String> normalizedAbis = new LinkedHashSet<>();
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("armeabi".equals(abi)) {
                    normalizedAbis.add("armeabi-v7a");
                } else if (abi != null && !abi.trim().isEmpty()) {
                    normalizedAbis.add(abi.trim());
                }
            }
        }
        JSONArray out = new JSONArray();
        for (String abi : normalizedAbis) {
            out.put(assetEvidence(context, "bootstrap/" + abi + ".tar", "proot-seed"));
            out.put(assetEvidence(context, "alpine19/" + abi + ".tar", "rootfs-seed"));
        }
        return out;
    }

    private static JSONObject assetEvidence(Context context, String path, String role) throws Exception {
        JSONObject item = new JSONObject()
                .put("asset_path", path)
                .put("role", role);
        try (InputStream in = context.getAssets().open(path)) {
            DigestAndSize digest = sha256Stream(in);
            item.put("present", true)
                    .put("size_bytes", digest.size)
                    .put("sha256", digest.sha256);
        } catch (IOException e) {
            item.put("present", false)
                    .put("size_bytes", 0L)
                    .put("sha256", TOKEN_VAZIO)
                    .put("observation", "asset-not-present-in-this-build")
                    .put("exception_or_errno", e.getClass().getSimpleName() + ":" + safeMessage(e));
        }
        return item;
    }

    private static JSONObject runtimeState(Context context) throws Exception {
        JSONObject state = new JSONObject();
        JSONArray destinations = new JSONArray();
        for (String relative : DESTINATIONS) {
            destinations.put(fileState(context, relative));
        }
        state.put("destination_files", destinations);

        SetupFeatureCore.SetupPostCheckResult post = SetupFeatureCore.runSetupPostCheck(context);
        JSONArray failed = new JSONArray();
        for (String value : post.failedItems) failed.put(value);
        state.put("post_check", new JSONObject()
                .put("ok", post.ok)
                .put("technical_reason", post.technicalReason())
                .put("failed_items", failed));
        return state;
    }

    private static JSONObject fileState(Context context, String relative) throws Exception {
        File file = new File(context.getFilesDir(), relative);
        boolean exists = file.isFile();
        JSONObject out = new JSONObject()
                .put("logical_name", relative)
                .put("path", "<filesDir>/" + relative)
                .put("exists", exists)
                .put("executable", exists && file.canExecute())
                .put("size_bytes", exists ? file.length() : 0L)
                .put("sha256", exists ? sha256File(file) : TOKEN_VAZIO)
                .put("expected_executable_mode_octal", "0755");

        if (!exists) {
            out.put("observed_mode_octal", TOKEN_VAZIO)
                    .put("stat_errno", TOKEN_VAZIO);
            return out;
        }
        try {
            StructStat stat = Os.stat(file.getAbsolutePath());
            out.put("observed_mode_octal", String.format(Locale.US, "%04o", stat.st_mode & 0777))
                    .put("stat_errno", TOKEN_VAZIO);
        } catch (ErrnoException e) {
            out.put("observed_mode_octal", TOKEN_VAZIO)
                    .put("stat_errno", e.errno)
                    .put("stat_error", e.getClass().getSimpleName() + ":" + safeMessage(e));
        }
        return out;
    }

    private static JSONObject deriveGate(JSONObject after) throws Exception {
        JSONArray files = after.getJSONArray("destination_files");
        boolean prootReady = false;
        boolean busyboxReady = false;
        boolean shellReady = false;
        boolean envReady = false;
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            boolean ready = file.getBoolean("exists") && file.getBoolean("executable")
                    && !TOKEN_VAZIO.equals(file.optString("sha256"));
            switch (file.getString("logical_name")) {
                case "usr/bin/proot": prootReady = ready; break;
                case "distro/bin/busybox": busyboxReady = ready; break;
                case "distro/bin/sh": shellReady = ready; break;
                case "distro/usr/bin/env": envReady = ready; break;
                default: break;
            }
        }
        boolean baseReady = prootReady && busyboxReady && shellReady && envReady;
        JSONObject post = after.getJSONObject("post_check");
        JSONArray failed = post.getJSONArray("failed_items");
        boolean basePostCheckReady = !contains(failed, "missing-proot")
                && !contains(failed, "missing-distro-busybox");
        return new JSONObject()
                .put("proot_ready", prootReady)
                .put("busybox_ready", busyboxReady)
                .put("shell_ready", shellReady)
                .put("env_ready", envReady)
                .put("base_runtime_materialized", baseReady)
                .put("base_post_check_ready", basePostCheckReady)
                .put("full_post_check_clean", post.getBoolean("ok"))
                .put("qemu_deliberately_out_of_scope", true)
                .put("next_gate", baseReady && basePostCheckReady ? "QEMU_EXECUTABLE_RUNTIME" : "BOOTSTRAP_EXTRACTION");
    }

    private static boolean contains(JSONArray array, String value) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.getString(i))) return true;
        }
        return false;
    }

    private static ExportResult export(Context context, JSONObject root) throws Exception {
        File directory = new File(context.getFilesDir(), "evidence/bootstrap-extraction");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("unable to create bootstrap extraction evidence directory");
        }
        String stamp = fileTimestamp();
        File json = new File(directory, "vectras-bootstrap-extraction-" + stamp + ".json");
        byte[] bytes = (root.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
        atomicWrite(json, bytes);
        String digest = sha256Bytes(bytes);
        File checksum = new File(directory, json.getName() + ".sha256");
        atomicWrite(checksum, (digest + "  " + json.getName() + "\n").getBytes(StandardCharsets.UTF_8));
        return new ExportResult(json, checksum, digest);
    }

    private static void atomicWrite(File destination, byte[] bytes) throws IOException {
        File parent = destination.getParentFile();
        File temp = new File(parent, "." + destination.getName() + ".tmp-" + System.nanoTime());
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("unable to replace artifact: " + destination);
        }
        if (!temp.renameTo(destination)) {
            throw new IOException("unable to publish artifact atomically: " + destination);
        }
    }

    private static DigestAndSize sha256Stream(InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        long size = 0L;
        int n;
        while ((n = in.read(buffer)) != -1) {
            digest.update(buffer, 0, n);
            size += n;
        }
        return new DigestAndSize(hex(digest.digest()), size);
    }

    private static String sha256File(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            return sha256Stream(in).sha256;
        }
    }

    private static String sha256Bytes(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
    }

    private static String readText(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes) {
        char[] map = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = map[value >>> 4];
            out[i * 2 + 1] = map[value & 0x0f];
        }
        return new String(out);
    }

    private static String emptyToToken(String value) {
        return value == null || value.trim().isEmpty() ? TOKEN_VAZIO : value.trim();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? "no-message" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String fileTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class DigestAndSize {
        final String sha256;
        final long size;

        DigestAndSize(String sha256, long size) {
            this.sha256 = sha256;
            this.size = size;
        }
    }
}
