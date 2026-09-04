package com.vectras.vm.evidence;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.BuildConfig;
import com.vectras.vm.qemu.QemuBinaryResolver;
import com.vectras.vm.runtime.HostExecutableAbiInspector;
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
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Evidence catalog for one installed Vectras instance.
 *
 * <p>The collector intentionally separates direct observation from claims. It
 * records build identity, installed APK integrity, signing identity, embedded
 * build context, runtime seed state, QEMU resolution/ELF ABI, device context and
 * requested permissions. Persistent personal identifiers (Android ID, serial,
 * accounts, MAC addresses and adopted-storage UUIDs) are intentionally excluded.</p>
 */
public final class EvidenceCatalogCollector {
    public static final String SCHEMA = "vectras.device-evidence-catalog.v1";
    public static final String TOKEN_VAZIO = "TOKEN_VAZIO";

    private static final String[] EMBEDDED_RUNTIME_ASSETS = new String[] {
            "bootstrap/arm64-v8a.tar",
            "bootstrap/armeabi-v7a.tar",
            "bootstrap/x86.tar",
            "bootstrap/x86_64.tar",
            "alpine19/arm64-v8a.tar",
            "alpine19/armeabi-v7a.tar",
            "alpine19/x86.tar",
            "alpine19/x86_64.tar",
            "bootstrap/loader.apk",
            "evidence/build-context.json"
    };

    private EvidenceCatalogCollector() {
        throw new AssertionError("utility class");
    }

    public static JSONObject collect(Context context) throws Exception {
        Context app = context.getApplicationContext();
        JSONObject root = new JSONObject();
        root.put("schema_version", SCHEMA);
        root.put("generated_at_utc", utcNow());
        root.put("record_kind", "INSTALLED_DEVICE_OBSERVATION");
        root.put("evidence_state", evidenceState());
        root.put("methodology", methodology());
        root.put("build_identity", buildIdentity(app));
        root.put("installed_application", installedApplication(app));
        root.put("embedded_build_context", readJsonAsset(app, "evidence/build-context.json"));
        root.put("embedded_runtime_assets", embeddedAssets(app));
        root.put("device_context", deviceContext(app));
        root.put("runtime_filesystem", runtimeFilesystem(app));
        root.put("native_libraries", nativeLibraries(app));
        root.put("permissions", permissions(app));
        root.put("privacy_exclusions", privacyExclusions());
        root.put("token_vazio", deriveTokenVazio(root));
        root.put("artifact_integrity", new JSONObject()
                .put("serialization", "UTF-8 JSON")
                .put("digest_algorithm", "SHA-256")
                .put("digest_mode", "out_of_band_companion_file")
                .put("companion_suffix", ".sha256"));
        return root;
    }

    private static JSONObject evidenceState() throws Exception {
        return new JSONObject()
                .put("observation_recorded", true)
                .put("build_certified", false)
                .put("device_runtime_verified", false)
                .put("physical_vm_launch_verified", false)
                .put("claim_allowed", false)
                .put("boundary", "OBSERVATION != EXECUTION_RECEIPT != CERTIFICATION != CLAIM");
    }

    private static JSONObject methodology() throws Exception {
        JSONArray principles = new JSONArray();
        principles.put("identity");
        principles.put("provenance");
        principles.put("integrity");
        principles.put("traceability");
        principles.put("reproducibility");
        principles.put("uncertainty_separation");
        principles.put("chain_of_custody");
        return new JSONObject()
                .put("profile", "EVIDENCE_SUPPORT_NOT_CERTIFICATION")
                .put("observation_method", "direct_local_inspection")
                .put("hash_algorithm", "SHA-256")
                .put("unknown_state", TOKEN_VAZIO)
                .put("principles", principles)
                .put("scientific_reference_fields", new JSONArray()
                        .put("timestamp")
                        .put("instrument_context")
                        .put("software_identity")
                        .put("input_and_output_integrity")
                        .put("runtime_observation")
                        .put("uncertainty_ledger"));
    }

    private static JSONObject buildIdentity(Context context) throws Exception {
        PackageInfo info = packageInfo(context);
        long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
        return new JSONObject()
                .put("application_id", BuildConfig.APPLICATION_ID)
                .put("runtime_package_name", context.getPackageName())
                .put("namespace", "com.vectras.vm")
                .put("build_type", BuildConfig.BUILD_TYPE)
                .put("debug", BuildConfig.DEBUG)
                .put("version_name", info.versionName == null ? TOKEN_VAZIO : info.versionName)
                .put("version_code", versionCode);
    }

    private static JSONObject installedApplication(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = packageInfo(context);
        ApplicationInfo appInfo = context.getApplicationInfo();
        File apk = new File(appInfo.sourceDir);

        JSONObject result = new JSONObject();
        result.put("package_name", context.getPackageName());
        result.put("first_install_time_ms", info.firstInstallTime);
        result.put("last_update_time_ms", info.lastUpdateTime);
        result.put("installer_package", safe(pm.getInstallerPackageName(context.getPackageName())));
        result.put("apk", new JSONObject()
                .put("name", apk.getName())
                .put("size_bytes", apk.isFile() ? apk.length() : 0L)
                .put("sha256", apk.isFile() ? sha256File(apk) : TOKEN_VAZIO)
                .put("source_path", normalizePath(context, apk)));
        result.put("signing_certificates_sha256", signingCertificates(info));
        return result;
    }

    private static JSONObject deviceContext(Context context) throws Exception {
        File files = context.getFilesDir();
        JSONArray abis = new JSONArray();
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) abis.put(abi);
        }

        return new JSONObject()
                .put("manufacturer", safe(Build.MANUFACTURER))
                .put("brand", safe(Build.BRAND))
                .put("model", safe(Build.MODEL))
                .put("device", safe(Build.DEVICE))
                .put("board", safe(Build.BOARD))
                .put("hardware", safe(Build.HARDWARE))
                .put("android_release", safe(Build.VERSION.RELEASE))
                .put("sdk_int", Build.VERSION.SDK_INT)
                .put("build_id", safe(Build.ID))
                .put("build_display", safe(Build.DISPLAY))
                .put("build_fingerprint", safe(Build.FINGERPRINT))
                .put("supported_abis", abis)
                .put("kernel_version", safe(System.getProperty("os.version")))
                .put("os_arch", safe(System.getProperty("os.arch")))
                .put("available_processors", Runtime.getRuntime().availableProcessors())
                .put("runtime_max_memory_bytes", Runtime.getRuntime().maxMemory())
                .put("files_volume_total_bytes", files.getTotalSpace())
                .put("files_volume_usable_bytes", files.getUsableSpace())
                .put("locale", Locale.getDefault().toLanguageTag())
                .put("timezone", TimeZone.getDefault().getID());
    }

    private static JSONArray embeddedAssets(Context context) throws Exception {
        JSONArray out = new JSONArray();
        for (String path : EMBEDDED_RUNTIME_ASSETS) {
            JSONObject item = new JSONObject();
            item.put("asset_path", path);
            try (InputStream in = context.getAssets().open(path)) {
                DigestAndSize digest = sha256Stream(in);
                item.put("present", true);
                item.put("size_bytes", digest.size);
                item.put("sha256", digest.sha256);
            } catch (IOException e) {
                item.put("present", false);
                item.put("size_bytes", 0L);
                item.put("sha256", TOKEN_VAZIO);
                item.put("observation", "asset-not-present-in-this-build");
            }
            out.put(item);
        }
        return out;
    }

    private static JSONObject readJsonAsset(Context context, String path) throws Exception {
        try (InputStream in = context.getAssets().open(path)) {
            byte[] bytes = readAll(in);
            JSONObject value = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            value.put("_embedded_asset_sha256", sha256Bytes(bytes));
            return value;
        } catch (Exception e) {
            return new JSONObject()
                    .put("status", TOKEN_VAZIO)
                    .put("asset_path", path)
                    .put("reason", e.getClass().getSimpleName());
        }
    }

    private static JSONObject runtimeFilesystem(Context context) throws Exception {
        JSONObject runtime = new JSONObject();
        JSONArray files = new JSONArray();
        String[] relative = new String[] {
                "usr/bin/proot",
                "distro/bin/busybox",
                "distro/bin/sh",
                "distro/usr/bin/env"
        };
        for (String rel : relative) {
            files.put(fileEvidence(context, new File(context.getFilesDir(), rel), rel));
        }
        runtime.put("base_files", files);

        SetupFeatureCore.SetupPostCheckResult post = SetupFeatureCore.runSetupPostCheck(context);
        JSONArray failed = new JSONArray();
        for (String value : post.failedItems) failed.put(value);
        runtime.put("post_check", new JSONObject()
                .put("ok", post.ok)
                .put("technical_reason", post.technicalReason())
                .put("failed_items", failed));

        String selectedGuestArch = MainSettingsManager.getArch(context);
        QemuBinaryResolver.Resolution resolution = QemuBinaryResolver.resolveForArch(
                context, selectedGuestArch, "EvidenceCatalog");
        JSONObject qemu = new JSONObject()
                .put("selected_guest_arch", safe(selectedGuestArch))
                .put("found", resolution.found)
                .put("binary_name", resolution.found ? resolution.binaryName : TOKEN_VAZIO)
                .put("reason", resolution.reason)
                .put("resolved_path", resolution.found
                        ? normalizePath(context, new File(resolution.fullPath))
                        : TOKEN_VAZIO);
        JSONArray checked = new JSONArray();
        for (String path : resolution.checkedPaths) {
            checked.put(normalizePath(context, new File(path)));
        }
        qemu.put("checked_paths", checked);
        if (resolution.found) {
            File qemuFile = new File(resolution.fullPath);
            qemu.put("file", fileEvidence(context, qemuFile, "resolved-qemu"));
            String hostAbi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                    ? Build.SUPPORTED_ABIS[0]
                    : "unknown";
            HostExecutableAbiInspector.Result abi = HostExecutableAbiInspector.inspect(qemuFile, hostAbi);
            qemu.put("host_elf_abi", new JSONObject()
                    .put("status", abi.status.name())
                    .put("host_abi", abi.hostAbi)
                    .put("elf_class", abi.elfClass)
                    .put("elf_machine", abi.machine)
                    .put("detail", abi.detail));
        } else {
            qemu.put("host_elf_abi", new JSONObject().put("status", TOKEN_VAZIO));
        }
        runtime.put("qemu", qemu);
        return runtime;
    }

    private static JSONArray nativeLibraries(Context context) throws Exception {
        JSONArray out = new JSONArray();
        File dir = new File(context.getApplicationInfo().nativeLibraryDir);
        File[] entries = dir.listFiles();
        if (entries == null) return out;
        for (File file : entries) {
            if (!file.isFile()) continue;
            out.put(new JSONObject()
                    .put("name", file.getName())
                    .put("size_bytes", file.length())
                    .put("sha256", sha256File(file))
                    .put("executable", file.canExecute())
                    .put("path", normalizePath(context, file)));
        }
        return out;
    }

    private static JSONArray permissions(Context context) throws Exception {
        PackageInfo info = packageInfo(context);
        JSONArray out = new JSONArray();
        if (info.requestedPermissions == null) return out;
        for (int i = 0; i < info.requestedPermissions.length; i++) {
            String permission = info.requestedPermissions[i];
            boolean granted = context.getPackageManager().checkPermission(
                    permission, context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
            out.put(new JSONObject()
                    .put("name", permission)
                    .put("granted", granted));
        }
        return out;
    }

    private static JSONObject fileEvidence(Context context, File file, String logicalName) throws Exception {
        boolean exists = file.isFile();
        return new JSONObject()
                .put("logical_name", logicalName)
                .put("path", normalizePath(context, file))
                .put("exists", exists)
                .put("executable", exists && file.canExecute())
                .put("size_bytes", exists ? file.length() : 0L)
                .put("sha256", exists ? sha256File(file) : TOKEN_VAZIO);
    }

    private static JSONArray signingCertificates(PackageInfo info) throws Exception {
        JSONArray out = new JSONArray();
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        }
        if (signatures == null) signatures = info.signatures;
        if (signatures == null) return out;
        for (Signature signature : signatures) {
            out.put(sha256Bytes(signature.toByteArray()));
        }
        return out;
    }

    private static PackageInfo packageInfo(Context context) throws PackageManager.NameNotFoundException {
        int flags = PackageManager.GET_PERMISSIONS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            flags |= PackageManager.GET_SIGNATURES;
        }
        return context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
    }

    private static JSONArray privacyExclusions() {
        return new JSONArray()
                .put("ANDROID_ID")
                .put("hardware_serial")
                .put("IMEI_MEID")
                .put("SIM_identifiers")
                .put("accounts")
                .put("MAC_addresses")
                .put("adopted_storage_UUID")
                .put("absolute_external_or_unknown_paths")
                .put("user_documents");
    }

    private static JSONArray deriveTokenVazio(JSONObject root) throws Exception {
        JSONArray gaps = new JSONArray();
        JSONObject buildContext = root.getJSONObject("embedded_build_context");
        if (TOKEN_VAZIO.equals(buildContext.optString("status"))) {
            gaps.put("EMBEDDED_BUILD_CONTEXT");
        }
        JSONObject installed = root.getJSONObject("installed_application");
        if (TOKEN_VAZIO.equals(installed.getJSONObject("apk").optString("sha256"))) {
            gaps.put("INSTALLED_APK_SHA256");
        }
        if (installed.getJSONArray("signing_certificates_sha256").length() == 0) {
            gaps.put("APK_SIGNING_CERTIFICATE_SHA256");
        }
        JSONObject runtime = root.getJSONObject("runtime_filesystem");
        if (!runtime.getJSONObject("post_check").getBoolean("ok")) {
            gaps.put("POST_CHECK_CLEAN_RECEIPT");
        }
        if (!runtime.getJSONObject("qemu").getBoolean("found")) {
            gaps.put("QEMU_EXECUTABLE_RUNTIME");
            gaps.put("QEMU_HOST_ELF_ABI_RECEIPT");
        }
        gaps.put("PHYSICAL_VM_LAUNCH_RECEIPT");
        gaps.put("END_TO_END_VM_BOOT_EVIDENCE");
        return gaps;
    }

    public static ExportResult export(Context context, JSONObject catalog) throws Exception {
        File directory = new File(context.getFilesDir(), "evidence/catalog");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("unable to create evidence directory: " + directory);
        }
        String stamp = fileTimestamp();
        File json = new File(directory, "vectras-evidence-catalog-" + stamp + ".json");
        byte[] bytes = (catalog.toString(2) + "\n").getBytes(StandardCharsets.UTF_8);
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

    /**
     * Serializes only logical application roots. Unknown paths are redacted
     * rather than falling back to an absolute filesystem location, which may
     * carry guest data or an adopted-storage volume UUID.
     */
    private static String normalizePath(Context context, File file) {
        try {
            ApplicationInfo appInfo = context.getApplicationInfo();
            return EvidencePathRedactor.normalizeForReceipt(
                    file == null ? null : file.getCanonicalPath(),
                    context.getFilesDir() == null ? null : context.getFilesDir().getCanonicalPath(),
                    appInfo.sourceDir == null ? null : new File(appInfo.sourceDir).getCanonicalPath(),
                    appInfo.nativeLibraryDir == null
                            ? null
                            : new File(appInfo.nativeLibraryDir).getCanonicalPath());
        } catch (Exception e) {
            return EvidencePathRedactor.REDACTED_PATH;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ArrayList<byte[]> chunks = new ArrayList<>();
        int total = 0;
        byte[] buffer = new byte[32 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            byte[] chunk = new byte[n];
            System.arraycopy(buffer, 0, chunk, 0, n);
            chunks.add(chunk);
            total += n;
        }
        byte[] all = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, all, offset, chunk.length);
            offset += chunk.length;
        }
        return all;
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

    private static String sha256Bytes(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
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

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? TOKEN_VAZIO : value;
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
}
