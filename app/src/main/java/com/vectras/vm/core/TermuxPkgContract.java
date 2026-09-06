package com.vectras.vm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic Vectras -> Termux package contract.
 *
 * This class does not execute a shell. It only exposes ordered package and
 * argv vectors so the caller can preserve the same package topology used by
 * the RAFCODEPHI freestanding exec gate.
 */
public final class TermuxPkgContract {

    public static final String CONTRACT_ID = "raf.termux-pkg.v1";

    public enum Stage {
        BOOTSTRAP_TOOLCHAIN,
        VECTRAS_QEMU
    }

    private static final String[] BOOTSTRAP_TOOLCHAIN = {
            "bash",
            "aria2",
            "tar",
            "xterm",
            "pulseaudio",
            "x11-repo",
            "proot",
            "proot-distro",
            "ninja",
            "clang",
            "lld",
            "cmake",
            "make",
            "binutils",
            "file",
            "patchelf"
    };

    private static final String[] VECTRAS_QEMU = {
            "qemu-common",
            "qemu-system-x86-64-headless",
            "qemu-utils"
    };

    private static final String[] BOOTSTRAP_EXECUTABLES = {
            "pkg",
            "proot",
            "proot-distro",
            "ninja",
            "clang",
            "cmake"
    };

    private static final String[] VECTRAS_EXECUTABLES = {
            "qemu-system-x86_64",
            "qemu-img"
    };

    private TermuxPkgContract() {
    }

    public static List<String> packages(Stage stage) {
        return immutableCopy(stage == Stage.VECTRAS_QEMU ? VECTRAS_QEMU : BOOTSTRAP_TOOLCHAIN);
    }

    public static List<String> requiredExecutables(Stage stage) {
        return immutableCopy(stage == Stage.VECTRAS_QEMU ? VECTRAS_EXECUTABLES : BOOTSTRAP_EXECUTABLES);
    }

    /**
     * Exact argv vector; no shell quoting or concatenation is required.
     */
    public static List<String> pkgInstallArgv(Stage stage) {
        List<String> argv = new ArrayList<>();
        argv.add("pkg");
        argv.add("install");
        argv.add("-y");
        argv.addAll(packages(stage));
        return Collections.unmodifiableList(argv);
    }

    public static String packageString(Stage stage) {
        StringBuilder out = new StringBuilder();
        for (String pkg : packages(stage)) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(pkg);
        }
        return out.toString();
    }

    public static String allRequiredPackageString() {
        String first = packageString(Stage.BOOTSTRAP_TOOLCHAIN);
        String second = packageString(Stage.VECTRAS_QEMU);
        return first + " " + second;
    }

    public static boolean hasDuplicatePackages() {
        Set<String> seen = new HashSet<>();
        for (Stage stage : Stage.values()) {
            for (String pkg : packages(stage)) {
                if (!seen.add(pkg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> immutableCopy(String[] source) {
        List<String> out = new ArrayList<>(source.length);
        Collections.addAll(out, source);
        return Collections.unmodifiableList(out);
    }
}
