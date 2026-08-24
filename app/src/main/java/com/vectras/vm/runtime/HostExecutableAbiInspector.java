package com.vectras.vm.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Inspects the actual ELF header of a host executable.
 *
 * <p>The QEMU filename (for example qemu-system-x86_64) describes the emulated
 * target and must never be used as evidence of the Android host ELF ABI.</p>
 */
public final class HostExecutableAbiInspector {
    public enum Status { MATCH, MISMATCH, NOT_ELF, UNKNOWN_HOST_ABI, UNREADABLE }

    public static final class Result {
        public final Status status;
        public final String hostAbi;
        public final int elfClass;
        public final int machine;
        public final String detail;

        Result(Status status, String hostAbi, int elfClass, int machine, String detail) {
            this.status = status;
            this.hostAbi = hostAbi == null ? "" : hostAbi;
            this.elfClass = elfClass;
            this.machine = machine;
            this.detail = detail == null ? "" : detail;
        }

        public boolean isMatch() {
            return status == Status.MATCH;
        }
    }

    private static final int EM_386 = 3;
    private static final int EM_ARM = 40;
    private static final int EM_X86_64 = 62;
    private static final int EM_AARCH64 = 183;
    private static final int EM_RISCV = 243;

    private HostExecutableAbiInspector() {
        throw new AssertionError("utility class");
    }

    public static Result inspect(File file, String hostAbi) {
        Expected expected = expectedFor(hostAbi);
        if (expected == null) {
            return new Result(Status.UNKNOWN_HOST_ABI, hostAbi, 0, 0,
                    "host ABI has no ELF mapping: " + hostAbi);
        }
        if (file == null || !file.isFile()) {
            return new Result(Status.UNREADABLE, hostAbi, 0, 0, "file missing");
        }

        byte[] header = new byte[20];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int read = in.read(header, offset, header.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset < header.length) {
                return new Result(Status.UNREADABLE, hostAbi, 0, 0,
                        "ELF header too short: " + offset);
            }
        } catch (IOException e) {
            return new Result(Status.UNREADABLE, hostAbi, 0, 0,
                    "unable to read executable header: " + e.getClass().getSimpleName());
        }

        if ((header[0] & 0xff) != 0x7f || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
            return new Result(Status.NOT_ELF, hostAbi, 0, 0,
                    "executable is not ELF; launch receipt must decide compatibility");
        }

        int elfClass = header[4] & 0xff; // 1=ELF32, 2=ELF64
        int dataEncoding = header[5] & 0xff; // 1=little, 2=big
        if (dataEncoding != 1 && dataEncoding != 2) {
            return new Result(Status.UNREADABLE, hostAbi, elfClass, 0,
                    "unsupported ELF data encoding=" + dataEncoding);
        }
        int b18 = header[18] & 0xff;
        int b19 = header[19] & 0xff;
        int machine = dataEncoding == 1 ? (b18 | (b19 << 8)) : ((b18 << 8) | b19);

        boolean match = elfClass == expected.elfClass && machine == expected.machine;
        String detail = "hostAbi=" + hostAbi
                + " expectedClass=" + expected.elfClass
                + " expectedMachine=" + expected.machine
                + " actualClass=" + elfClass
                + " actualMachine=" + machine;
        return new Result(match ? Status.MATCH : Status.MISMATCH,
                hostAbi, elfClass, machine, detail);
    }

    private static Expected expectedFor(String hostAbi) {
        if (hostAbi == null) return null;
        String abi = hostAbi.trim().toLowerCase(Locale.ROOT);
        switch (abi) {
            case "armeabi":
            case "armeabi-v7a":
                return new Expected(1, EM_ARM);
            case "arm64-v8a":
                return new Expected(2, EM_AARCH64);
            case "x86":
                return new Expected(1, EM_386);
            case "x86_64":
                return new Expected(2, EM_X86_64);
            case "riscv64":
                return new Expected(2, EM_RISCV);
            default:
                return null;
        }
    }

    private static final class Expected {
        final int elfClass;
        final int machine;

        Expected(int elfClass, int machine) {
            this.elfClass = elfClass;
            this.machine = machine;
        }
    }
}
