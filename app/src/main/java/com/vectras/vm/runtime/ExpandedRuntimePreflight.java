package com.vectras.vm.runtime;

import android.content.Context;
import android.os.Build;

import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.AppConfig;
import com.vectras.vm.qemu.QemuBinaryResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Expanded launch-time preflight for the real VM runtime surface.
 *
 * <p>VectrasRuntimePreflight proves the minimal proot/rootfs/qemu state. This
 * class adds the missing launch proof: ABI blockers, BIOS/EFI assets, QEMU
 * version probing targets and disk/media existence checks. It is intentionally
 * side-effect-light and safe to call before StartVM.startNow(...).</p>
 */
public final class ExpandedRuntimePreflight {
    public enum Severity { PASS, WARN, FAIL, BLOCKER }

    private static final String HASH_DEFERRED = "hash_deferred_startup_safe";

    public static final class Item {
        public final String id;
        public final String path;
        public final Severity severity;
        public final String message;
        public final long sizeBytes;
        public final String sha256;

        Item(String id, String path, Severity severity, String message, long sizeBytes, String sha256) {
            this.id = safe(id);
            this.path = safe(path);
            this.severity = severity == null ? Severity.FAIL : severity;
            this.message = safe(message);
            this.sizeBytes = sizeBytes;
            this.sha256 = safe(sha256);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("path", path);
                json.put("severity", severity.name());
                json.put("message", message);
                json.put("size_bytes", sizeBytes);
                json.put("sha256", sha256);
            } catch (Exception ignored) {
            }
            return json;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String arch;
        public final String hostAbi;
        public final boolean host64Bit;
        public final List<Item> items;

        Result(boolean ok, String arch, String hostAbi, boolean host64Bit, List<Item> items) {
            this.ok = ok;
            this.arch = safe(arch);
            this.hostAbi = safe(hostAbi);
            this.host64Bit = host64Bit;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }

        public boolean hasBlocker() {
            for (Item item : items) {
                if (item.severity == Severity.BLOCKER) return true;
            }
            return false;
        }

        public String shortSummary() {
            int blockers = 0;
            int fails = 0;
            int warnings = 0;
            for (Item item : items) {
                if (item.severity == Severity.BLOCKER) blockers++;
                else if (item.severity == Severity.FAIL) fails++;
                else if (item.severity == Severity.WARN) warnings++;
            }
            return ok ? "Expanded preflight OK" : "Expanded preflight blocked: blockers=" + blockers + ",fails=" + fails + ",warn=" + warnings;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Item item : items) arr.put(item.toJson());
            try {
                json.put("ok", ok);
                json.put("arch", arch);
                json.put("host_abi", hostAbi);
                json.put("host_64_bit", host64Bit);
                json.put("summary", shortSummary());
                json.put("hash_mode", "deferred_during_launch");
                json.put("items", arr);
            } catch (Exception ignored) {
            }
            return json;
        }
    }

    private ExpandedRuntimePreflight() {
        throw new AssertionError("ExpandedRuntimePreflight is a utility class and cannot be instantiated");
    }

    public static Result run(Context context, String imgPath, String cdromPath) {
        ArrayList<Item> items = new ArrayList<>();
        if (context == null) {
            items.add(new Item("context", "", Severity.BLOCKER, "context-null", 0L, ""));
            return new Result(false, "unknown", hostAbi(), false, items);
        }

        AppConfig.ensureStoragePaths(context);
        String arch = QemuBinaryResolver.normalizeArch(MainSettingsManager.getArch(context));
        boolean host64 = QemuBinaryResolver.isHost64Bit();
        String hostAbi = hostAbi();

        checkAbiCompatibility(items, arch, hostAbi, host64);
        checkExecutable(items, "proot", new File(context.getFilesDir(), "usr/bin/proot"), true, true);
        checkDirectory(items, "rootfs", new File(context.getFilesDir(), "distro"), true);
        checkExecutable(items, "rootfs_shell", new File(context.getFilesDir(), "distro/bin/sh"), true, true);
        checkDirectory(items, "proot_tmp", new File(context.getFilesDir(), "usr/tmp"), false);

        QemuBinaryResolver.Resolution resolution = QemuBinaryResolver.resolveForArch(context, arch, "ExpandedRuntimePreflight");
        if (resolution.found) {
            File qemuBinary = new File(resolution.fullPath);
            checkExecutable(items, "qemu_binary", qemuBinary, true, true);
            checkHostExecutableAbi(items, qemuBinary, hostAbi);
        } else {
            items.add(new Item("qemu_binary", "(resolver)", Severity.BLOCKER, "missing: " + resolution.reason + " checked=" + resolution.checkedPaths, 0L, ""));
        }

        checkFirmwareAssets(items, arch);
        if (imgPath != null && !imgPath.trim().isEmpty()) {
            checkFile(items, "disk0", new File(imgPath.trim()), true, false);
        }
        if (cdromPath != null && !cdromPath.trim().isEmpty()) {
            checkFile(items, "cdrom", new File(cdromPath.trim()), true, false);
        } else {
            File defaultCdrom = new File(context.getFilesDir(), "data/Vectras/drive.iso");
            if (defaultCdrom.exists()) checkFile(items, "cdrom_default", defaultCdrom, false, false);
        }

        File hdd1 = new File(context.getFilesDir(), "data/Vectras/hdd1.qcow2");
        if (hdd1.exists()) checkFile(items, "hdd1", hdd1, false, false);

        boolean ok = true;
        for (Item item : items) {
            if (item.severity == Severity.BLOCKER) {
                ok = false;
                break;
            }
        }
        return new Result(ok, arch, hostAbi, host64, items);
    }

    private static void checkAbiCompatibility(ArrayList<Item> items, String arch, String hostAbi, boolean host64) {
        // Guest/system target and host executable ABI are orthogonal. A filename
        // such as qemu-system-x86_64 says which machine is emulated, not whether
        // the Android binary itself is 32-bit or 64-bit. Actual ELF inspection
        // is performed only after the resolver locates the executable.
        items.add(new Item(
                "abi_mapping",
                hostAbi,
                Severity.PASS,
                "guest target=" + arch + " does not determine host ELF ABI; host64Bit=" + host64,
                0L,
                ""
        ));
    }

    private static void checkHostExecutableAbi(ArrayList<Item> items, File qemuBinary, String hostAbi) {
        HostExecutableAbiInspector.Result result = HostExecutableAbiInspector.inspect(qemuBinary, hostAbi);
        Severity severity;
        switch (result.status) {
            case MATCH:
                severity = Severity.PASS;
                break;
            case MISMATCH:
                severity = Severity.BLOCKER;
                break;
            case NOT_ELF:
            case UNKNOWN_HOST_ABI:
            case UNREADABLE:
            default:
                severity = Severity.WARN;
                break;
        }
        items.add(new Item(
                "qemu_host_elf_abi",
                qemuBinary == null ? "" : qemuBinary.getAbsolutePath(),
                severity,
                result.status.name() + ": " + result.detail,
                qemuBinary != null && qemuBinary.isFile() ? qemuBinary.length() : 0L,
                ""
        ));
    }

    private static void checkFirmwareAssets(ArrayList<Item> items, String arch) {
        if ("ARM64".equals(arch)) {
            checkFile(items, "firmware_qemu_efi", new File(AppConfig.basefiledir, "QEMU_EFI.img"), true, true);
            checkFile(items, "firmware_qemu_vars", new File(AppConfig.basefiledir, "QEMU_VARS.img"), true, true);
        } else if ("X86_64".equals(arch)) {
            checkFile(items, "firmware_ovmf", new File(AppConfig.basefiledir, "RELEASEX64_OVMF.fd"), false, true);
            checkFile(items, "firmware_ovmf_vars", new File(AppConfig.basefiledir, "RELEASEX64_OVMF_VARS.fd"), false, true);
            checkFile(items, "firmware_bios", new File(AppConfig.basefiledir, "bios-vectras.bin"), false, true);
        } else if ("PPC".equals(arch)) {
            File pcBios = new File(AppConfig.basefiledir, "pc-bios");
            checkDirectory(items, "firmware_pc_bios", pcBios, false);
        } else {
            checkFile(items, "firmware_bios", new File(AppConfig.basefiledir, "bios-vectras.bin"), false, true);
        }
    }

    private static void checkDirectory(ArrayList<Item> items, String id, File path, boolean blocker) {
        boolean ok = path != null && path.isDirectory();
        items.add(new Item(id, path == null ? "" : path.getAbsolutePath(), ok ? Severity.PASS : (blocker ? Severity.BLOCKER : Severity.WARN), ok ? "directory exists" : "directory missing", 0L, ""));
    }

    private static void checkExecutable(ArrayList<Item> items, String id, File path, boolean blocker, boolean withHash) {
        boolean ok = path != null && path.isFile() && path.canExecute();
        String msg = ok ? "executable" : "missing or not executable";
        items.add(fileItem(id, path, ok ? Severity.PASS : (blocker ? Severity.BLOCKER : Severity.WARN), msg, withHash));
    }

    private static void checkFile(ArrayList<Item> items, String id, File path, boolean blocker, boolean withHash) {
        boolean ok = path != null && path.isFile();
        String msg = ok ? "file exists" : "file missing";
        items.add(fileItem(id, path, ok ? Severity.PASS : (blocker ? Severity.BLOCKER : Severity.WARN), msg, withHash));
    }

    private static Item fileItem(String id, File path, Severity severity, String msg, boolean withHash) {
        long size = path != null && path.isFile() ? path.length() : 0L;
        String hash = withHash && path != null && path.isFile() ? HASH_DEFERRED + ":size=" + size : "";
        return new Item(id, path == null ? "" : path.getAbsolutePath(), severity, msg, size, hash);
    }

    private static String hostAbi() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) return "unknown";
        return Build.SUPPORTED_ABIS[0];
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
