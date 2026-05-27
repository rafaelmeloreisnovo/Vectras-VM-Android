package com.vectras.vm.runtime;

import android.app.Activity;
import android.content.Context;

import com.termux.app.TermuxService;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.qemu.QemuBinaryResolver;
import com.vectras.vm.qemu.QemuExecConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VectrasRuntimePreflight {
    public enum Status { PASS, WARN, FAIL, BLOCKER }

    public static final class Item {
        public final String id;
        public final String path;
        public final Status status;
        public final String message;
        public Item(String id, String path, Status status, String message) { this.id = id; this.path = path; this.status = status; this.message = message; }
    }

    public static final class Result {
        public final boolean ok, hasProot, hasRootfs, hasShell, hasQemu;
        public final List<Item> items;
        public final String message;
        Result(boolean ok, List<Item> items, boolean hasProot, boolean hasRootfs, boolean hasShell, boolean hasQemu, String message) {
            this.ok = ok; this.items = Collections.unmodifiableList(items); this.hasProot = hasProot; this.hasRootfs = hasRootfs; this.hasShell = hasShell; this.hasQemu = hasQemu; this.message = message;
        }
    }

    public static Result run(Context context) {
        String filesDir = context.getFilesDir().getAbsolutePath();
        String prootPath = TermuxService.PREFIX_PATH + "/bin/proot";
        String rootfsPath = filesDir + "/distro";
        String shellPath = rootfsPath + "/bin/sh";
        String tmpPath = filesDir + "/usr/tmp";
        String rootHome = rootfsPath + "/root";
        boolean hasQemu = context instanceof Activity && QemuExecConfig.resolveBinaryStrict((Activity) context, MainSettingsManager.getArch(context)).found;
        List<String> checkedPaths = context instanceof Activity ? QemuExecConfig.resolveBinaryStrict((Activity) context, MainSettingsManager.getArch(context)).checkedPaths : Collections.emptyList();
        return evaluate(prootPath, rootfsPath, shellPath, tmpPath, rootHome, hasQemu, checkedPaths);
    }

    static Result evaluate(String prootPath, String rootfsPath, String shellPath, String tmpPath, String rootHome, boolean hasQemu, List<String> checkedPaths) {
        List<Item> items = new ArrayList<>();
        boolean hasProot = checkExec(items, "proot", prootPath, true);
        boolean hasRootfs = checkDir(items, "rootfs", rootfsPath, true);
        boolean hasShell = checkExec(items, "shell", shellPath, true);
        checkDir(items, "tmp", tmpPath, false);
        checkDir(items, "root_home", rootHome, false);

        String qemuMsg = hasQemu ? "qemu binary resolved" : "QEMU binary not found. Runtime/rootfs is incomplete. checkedPaths=" + checkedPaths;
        items.add(new Item("qemu", "(resolver)", hasQemu ? Status.PASS : Status.BLOCKER, qemuMsg));

        boolean ok = hasProot && hasRootfs && hasShell && hasQemu;
        String message = ok ? "Runtime preflight ok" : "Runtime preflight failed";
        return new Result(ok, items, hasProot, hasRootfs, hasShell, hasQemu, message);
    }

    private static boolean checkDir(List<Item> items, String id, String path, boolean blocker) {
        File f = new File(path);
        boolean ok = f.isDirectory();
        items.add(new Item(id, path, ok ? Status.PASS : (blocker ? Status.BLOCKER : Status.FAIL), ok ? "exists" : "missing"));
        return ok;
    }

    private static boolean checkExec(List<Item> items, String id, String path, boolean blocker) {
        File f = new File(path);
        boolean ok = f.exists() && f.canExecute();
        items.add(new Item(id, path, ok ? Status.PASS : (blocker ? Status.BLOCKER : Status.FAIL), ok ? "executable" : "missing or not executable"));
        return ok;
    }
}
