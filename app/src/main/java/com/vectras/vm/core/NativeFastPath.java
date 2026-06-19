package com.vectras.vm.core;

import android.os.Build;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NativeFastPath: JNI thin adapter for unified kernel contracts.
 */
public final class NativeFastPath {

    private static final int NATIVE_OK_MAGIC = 0x56414343;
    private static final boolean NATIVE_AVAILABLE;
    private static final boolean ARENA_AVAILABLE;
    private static final int NATIVE_INIT_STATUS;
    private static final String NATIVE_INIT_ERROR;

    private static final int HW_CONTRACT_SIGNATURE = 0;
    private static final int HW_CONTRACT_POINTER_BITS = 1;
    private static final int HW_CONTRACT_CACHE_LINE = 2;
    private static final int HW_CONTRACT_PAGE_SIZE = 3;
    private static final int HW_CONTRACT_FEATURES = 4;
    private static final int HW_CONTRACT_REG0 = 5;
    private static final int HW_CONTRACT_REG1 = 6;
    private static final int HW_CONTRACT_REG2 = 7;
    private static final int HW_CONTRACT_GPIO_WORD_BITS = 8;
    private static final int HW_CONTRACT_GPIO_PIN_STRIDE = 9;
    private static final int HW_CONTRACT_SIZE = 10;

    private static final int KERNEL_CONTRACT_SIGNATURE = 0;
    private static final int KERNEL_CONTRACT_POINTER_BITS = 1;
    private static final int KERNEL_CONTRACT_CACHE_LINE = 2;
    private static final int KERNEL_CONTRACT_PAGE_SIZE = 3;
    private static final int KERNEL_CONTRACT_FEATURES = 4;
    private static final int KERNEL_CONTRACT_CPU_CORES = 5;
    private static final int KERNEL_CONTRACT_ARENA_BYTES = 6;
    private static final int KERNEL_CONTRACT_IO_QUANTUM = 7;
    private static final int KERNEL_CONTRACT_SIZE = 8;

    public static final int ARCH_UNKNOWN = 0x0000;
    public static final int ARCH_ARM64 = 0x0100;
    public static final int ARCH_ARM32 = 0x0200;
    public static final int ARCH_X64 = 0x0300;
    public static final int ARCH_X86 = 0x0400;
    public static final int ARCH_RISCV64 = 0x0500;
    public static final int ARCH_RISCV32 = 0x0600;

    public static boolean isStableArchCode(int archCode) {
        return archCode == ARCH_UNKNOWN
                || archCode == ARCH_ARM64
                || archCode == ARCH_ARM32
                || archCode == ARCH_X64
                || archCode == ARCH_X86
                || archCode == ARCH_RISCV64
                || archCode == ARCH_RISCV32;
    }

    public static final int OS_UNKNOWN = 0x0000;
    public static final int OS_ANDROID = 0x0010;
    public static final int OS_LINUX = 0x0020;

    // Canonical JNI feature-mask contract (must match RMR_UK_FEATURE_* in native headers).
    public static final int FEATURE_NEON = 1 << 0;
    public static final int FEATURE_AES = 1 << 1;
    public static final int FEATURE_CRC32 = 1 << 2;
    public static final int FEATURE_POPCNT = 1 << 3;
    public static final int FEATURE_SSE42 = 1 << 4;
    public static final int FEATURE_AVX2 = 1 << 5;
    public static final int FEATURE_SIMD = 1 << 6;

    private static final AtomicLong TELEMETRY_COPY_CALLS = new AtomicLong();
    private static final AtomicLong TELEMETRY_COPY_BYTES = new AtomicLong();
    private static final AtomicLong TELEMETRY_XOR_CALLS = new AtomicLong();
    private static final AtomicLong TELEMETRY_CRC_CALLS = new AtomicLong();
    private static final AtomicLong TELEMETRY_ROUTE_CALLS = new AtomicLong();
    private static final AtomicLong TELEMETRY_AUDIT_CALLS = new AtomicLong();
    private static final AtomicLong TELEMETRY_NATIVE_HITS = new AtomicLong();
    private static final AtomicLong TELEMETRY_FALLBACK_HITS = new AtomicLong();
    private static final AtomicBoolean TELEMETRY_BOOT_EMITTED = new AtomicBoolean(false);

    private static final int ENTERPRISE_NECESSARY_COUNT = 7;
    private static final int ENTERPRISE_URGENT_COUNT = 5;
    private static final int ENTERPRISE_COMPLEMENTARY_COUNT = 14;
    private static final int RAW_TELEMETRY_LONG_COUNT = 11;

    private static final HardwareProfile BOOT_PROFILE;

    static {
        boolean loaded = false;
        int nativeInitStatus = Integer.MIN_VALUE;
        String nativeInitError = "";

        try {
            System.loadLibrary("vectra_core_accel");
            nativeInitStatus = nativeInit();
            loaded = (nativeInitStatus == NATIVE_OK_MAGIC);
            if (!loaded) {
                nativeInitError = "nativeInit status=0x" + Integer.toHexString(nativeInitStatus);
            }
        } catch (Throwable t) {
            nativeInitError = t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
        }

        if (!loaded) {
            Log.e("NativeFastPath", "Native accel desativado; fallback Java ativo. " + nativeInitError);
        }

        NATIVE_AVAILABLE = loaded;
        ARENA_AVAILABLE = loaded;
        NATIVE_INIT_STATUS = nativeInitStatus;
        NATIVE_INIT_ERROR = nativeInitError;
        BOOT_PROFILE = readNativeHardwareProfile();
    }

    private NativeFastPath() {
        throw new AssertionError("NativeFastPath is a utility class and cannot be instantiated");
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    public static boolean isFallbackActive() {
        return !NATIVE_AVAILABLE;
    }

    public static boolean isArenaAvailable() {
        return ARENA_AVAILABLE;
    }

    public static int getNativeInitStatus() {
        return NATIVE_INIT_STATUS;
    }

    public static String getNativeInitError() {
        return NATIVE_INIT_ERROR;
    }


    static HardwareProfile getHardwareProfile() {
        return BOOT_PROFILE;
    }

    public static int getPlatformSignature() {
        return BOOT_PROFILE.signature;
    }

    public static int getPointerBits() {
        return BOOT_PROFILE.pointerBits;
    }

    public static int getNativeCacheLineBytes() {
        return BOOT_PROFILE.cacheLineBytes;
    }

    public static int getNativePageBytes() {
        return BOOT_PROFILE.pageBytes;
    }

    public static int getFeatureMask() {
        return BOOT_PROFILE.featureMask;
    }

    /**
     * Java -> JNI C -> ASM marker used to assert low-level bridge path at runtime.
     */
    public static int asmBridgeMarker() {
        if (NATIVE_AVAILABLE) {
            int marker = nativeAsmBridgeMarker();
            if (marker != 0) {
                telemetryNativeHit();
                return marker;
            }
        }
        telemetryFallbackHit();
        return 0x4A564D31; // "JVM1"
    }

    public static NativeBridgeTelemetrySnapshot readNativeBridgeTelemetry() {
        KernelUnitProfile kernel = readKernelUnitProfile();
        long[] rawCounters = new long[RAW_TELEMETRY_LONG_COUNT];
        readNativeBridgeTelemetryRaw(rawCounters, 0);
        return new NativeBridgeTelemetrySnapshot(NATIVE_AVAILABLE, BOOT_PROFILE, kernel, rawCounters);
    }

    /**
     * Deterministic torus-flow checksum hotfix path (Java mirror of engine/rmr kernel).
     * Keeps Android/Kotlin diagnostics coherent even when JNI symbol exposure is delayed.
     */
    public static int torusFlowChecksum(int seed, int steps) {
        final int len = 128;
        final int[] prev = new int[len];
        final int[] input = new int[len];
        final int[] output = new int[len];

        final int alphaQ16 = 16384;     // 0.25
        final int invAlphaQ16 = 49152;  // 0.75
        final int phiSigmaQ16 = 91830;  // 1.401222
        final int gainQ16 = (int) ((((long) phiSigmaQ16) * ((long) alphaQ16)) >> 16);

        int localSeed = seed == 0 ? 0x963 : seed;
        for (int i = 0; i < len; i++) {
            prev[i] = 65536;
            input[i] = (int) ((((long) i) << 16) / len);
            output[i] = 0;
        }

        for (int step = 0; step < steps; step++) {
            int grammarSeed = localSeed ^ ((step + 1) * 0x9E3779B9);
            for (int i = 0; i < len; i++) {
                grammarSeed = grammarSeed * 1103515245 + 12345;
                input[i] = grammarSeed & 0xFFFF;
            }
            localSeed = grammarSeed;
            for (int i = 0; i < len; i++) {
                int jitter = localSeed ^ (i * 0x9E3779B9);
                int inDyn = input[i] ^ (jitter & 0x0FFF);
                int prevMix = (int) ((((long) prev[i]) * ((long) invAlphaQ16)) >> 16);
                int inMix = (int) ((((long) inDyn) * ((long) gainQ16)) >> 16);
                output[i] = prevMix + inMix;
                prev[i] = output[i];
                localSeed = localSeed * 1664525 + 1013904223;
            }
        }

        int chk = steps * 0xA5A5A5A5;
        for (int i = 0; i < len; i++) {
            chk ^= (output[i] + (i * 17));
            chk = (chk << 3) | (chk >>> 29);
        }
        return chk;
    }

    public static int getNativeBridgeTelemetryLongCount() {
        return RAW_TELEMETRY_LONG_COUNT;
    }

    /**
     * Raw telemetry read API for polling paths. Caller provides the output buffer to avoid
     * allocating a new telemetry snapshot object.
     * Layout:
     * [0]=copyCalls, [1]=copyBytes, [2]=xorCalls, [3]=crcCalls, [4]=routeCalls, [5]=auditCalls,
     * [6]=nativeHits, [7]=fallbackHits, [8]=necessaryCount, [9]=urgentCount, [10]=complementaryCount
     */
    public static void readNativeBridgeTelemetryRaw(long[] out, int offset) {
        if (out == null) {
            throw new IllegalArgumentException("Output buffer must not be null");
        }
        if (offset < 0 || offset + RAW_TELEMETRY_LONG_COUNT > out.length) {
            throw new IllegalArgumentException("Output buffer too small");
        }
        out[offset] = TELEMETRY_COPY_CALLS.get();
        out[offset + 1] = TELEMETRY_COPY_BYTES.get();
        out[offset + 2] = TELEMETRY_XOR_CALLS.get();
        out[offset + 3] = TELEMETRY_CRC_CALLS.get();
        out[offset + 4] = TELEMETRY_ROUTE_CALLS.get();
        out[offset + 5] = TELEMETRY_AUDIT_CALLS.get();
        out[offset + 6] = TELEMETRY_NATIVE_HITS.get();
        out[offset + 7] = TELEMETRY_FALLBACK_HITS.get();
        out[offset + 8] = ENTERPRISE_NECESSARY_COUNT;
        out[offset + 9] = ENTERPRISE_URGENT_COUNT;
        out[offset + 10] = ENTERPRISE_COMPLEMENTARY_COUNT;
    }

    /**
     * Debug/observability formatter only (not for hot path).
     */
    public static String formatHardwareKernelContractLine(String sourceTag) {
        NativeBridgeTelemetrySnapshot snapshot = readNativeBridgeTelemetry();
        StringBuilder sb = new StringBuilder(192);
        sb.append("NATIVE_CONTRACT[")
                .append(sourceTag)
                .append("] avail=")
                .append(snapshot.nativeAvailable ? '1' : '0')
                .append(" sig=0x")
                .append(toHex8(snapshot.hardwareSignature))
                .append(" ptr=")
                .append(snapshot.pointerBits)
                .append(" cache=")
                .append(snapshot.cacheLineBytes)
                .append(" page=")
                .append(snapshot.pageBytes)
                .append(" features=")
                .append(describeFeatureMask(snapshot.featureMask))
                .append(" cores=")
                .append(snapshot.kernelCpuCores)
                .append(" arena=")
                .append(snapshot.kernelArenaBytes)
                .append(" io=")
                .append(snapshot.kernelIoQuantumBytes);
        return sb.toString();
    }

    /**
     * Debug/observability formatter only (not for hot path).
     */
    public static String describeFeatureMask(int featureMask) {
        StringBuilder sb = new StringBuilder(32);
        appendFeature(sb, featureMask, FEATURE_NEON, "NEON");
        appendFeature(sb, featureMask, FEATURE_AES, "AES");
        appendFeature(sb, featureMask, FEATURE_CRC32, "CRC32");
        appendFeature(sb, featureMask, FEATURE_POPCNT, "POPCNT");
        appendFeature(sb, featureMask, FEATURE_SSE42, "SSE4_2");
        appendFeature(sb, featureMask, FEATURE_AVX2, "AVX2");
        appendFeature(sb, featureMask, FEATURE_SIMD, "SIMD");
        if (sb.length() == 0) {
            return "none";
        }
        return sb.toString();
    }

    private static void appendFeature(StringBuilder sb, int featureMask, int flag, String label) {
        if ((featureMask & flag) == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('|');
        }
        sb.append(label);
    }

    public static int getDeterminismScore() {
        NativeBridgeTelemetrySnapshot snapshot = readNativeBridgeTelemetry();
        long nativeHits = snapshot.nativeHits;
        long fallbackHits = snapshot.fallbackHits;
        long total = nativeHits + fallbackHits;
        if (total <= 0) {
            return snapshot.nativeAvailable ? 100 : 60;
        }
        long weighted = (nativeHits * 100L) / total;
        if (!snapshot.nativeAvailable) {
            weighted = Math.min(weighted, 70L);
        }
        if (weighted < 0L) {
            weighted = 0L;
        }
        if (weighted > 100L) {
            weighted = 100L;
        }
        return (int) weighted;
    }

    /**
     * Debug/observability formatter only (not for hot path).
     */
    public static String formatNativeBridgeTelemetryLine(String sourceTag) {
        NativeBridgeTelemetrySnapshot snapshot = readNativeBridgeTelemetry();
        int determinismScore = getDeterminismScore();
        StringBuilder sb = new StringBuilder(384);
        sb.append("NATIVE_BRIDGE[")
                .append(sourceTag)
                .append("] avail=")
                .append(snapshot.nativeAvailable ? '1' : '0')
                .append(" hw(sig=0x")
                .append(toHex8(snapshot.hardwareSignature))
                .append(" ptr=")
                .append(snapshot.pointerBits)
                .append(" cache=")
                .append(snapshot.cacheLineBytes)
                .append(" page=")
                .append(snapshot.pageBytes)
                .append(" feat=0x")
                .append(toHex8(snapshot.featureMask))
                .append('/')
                .append(describeFeatureMask(snapshot.featureMask))
                .append(") kernel(cores=")
                .append(snapshot.kernelCpuCores)
                .append(" arena=")
                .append(snapshot.kernelArenaBytes)
                .append(" io=")
                .append(snapshot.kernelIoQuantumBytes)
                .append(") ops(copy=")
                .append(snapshot.copyCalls)
                .append(" bytes=")
                .append(snapshot.copyBytes)
                .append(" xor=")
                .append(snapshot.xorCalls)
                .append(" crc=")
                .append(snapshot.crcCalls)
                .append(" route=")
                .append(snapshot.routeCalls)
                .append(" audit=")
                .append(snapshot.auditCalls)
                .append(" nativeHit=")
                .append(snapshot.nativeHits)
                .append(" fallback=")
                .append(snapshot.fallbackHits)
                .append(") gates(n=")
                .append(snapshot.necessaryCount)
                .append("/u=")
                .append(snapshot.urgentCount)
                .append("/c=")
                .append(snapshot.complementaryCount)
                .append(") det=")
                .append(determinismScore);
        return sb.toString();
    }

    public static String emitBootTelemetryLineIfNeeded() {
        if (TELEMETRY_BOOT_EMITTED.compareAndSet(false, true)) {
            return formatNativeBridgeTelemetryLine("boot");
        }
        return null;
    }

    private static void telemetryNativeHit() {
        TELEMETRY_NATIVE_HITS.incrementAndGet();
    }

    private static void telemetryFallbackHit() {
        TELEMETRY_FALLBACK_HITS.incrementAndGet();
    }

    private static void telemetryAddBytes(AtomicLong counter, int bytes) {
        counter.addAndGet(Math.max(0, bytes));
    }

    public static KernelUnitProfile readKernelUnitProfile() {
        if (NATIVE_AVAILABLE) {
            int[] contract = nativeReadKernelUnitContract();
            if (contract != null && contract.length == KERNEL_CONTRACT_SIZE) {
                telemetryNativeHit();
                return KernelUnitProfile.fromKernelContract(contract);
            }
            telemetryFallbackHit();
            return CompatibilityFallback.kernelUnitProfile("invalid kernel unit contract");
        }
        telemetryFallbackHit();
        return CompatibilityFallback.kernelUnitProfile("native library unavailable");
    }

    private static HardwareProfile readNativeHardwareProfile() {
        if (NATIVE_AVAILABLE) {
            int[] contract = nativeReadHardwareContract();
            if (contract != null && contract.length == HW_CONTRACT_SIZE) {
                try {
                    HardwareProfile profile = HardwareProfile.fromHardwareContract(contract);
                    telemetryNativeHit();
                    return profile;
                } catch (IllegalArgumentException ex) {
                    telemetryFallbackHit();
                    return CompatibilityFallback.hardwareProfile("invalid hardware contract: " + ex.getMessage());
                }
            }
            telemetryFallbackHit();
            return CompatibilityFallback.hardwareProfile("invalid hardware contract");
        }
        telemetryFallbackHit();
        return CompatibilityFallback.hardwareProfile("native library unavailable");
    }

    public static void copyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
        if (src == null || dst == null || length <= 0) return;
        TELEMETRY_COPY_CALLS.incrementAndGet();
        telemetryAddBytes(TELEMETRY_COPY_BYTES, length);
        if (srcOffset < 0 || dstOffset < 0 || srcOffset + length > src.length || dstOffset + length > dst.length) {
            throw new IllegalArgumentException("Invalid copy range");
        }

        // Explicit fallback path when JNI arena acceleration is not available.
        if (NATIVE_AVAILABLE && nativeCopyBytes(src, srcOffset, dst, dstOffset, length) == 0) {
            telemetryNativeHit();
            return;
        }
        telemetryFallbackHit();

        if (src == dst && dstOffset > srcOffset && dstOffset < srcOffset + length) {
            for (int i = length - 1; i >= 0; i--) {
                dst[dstOffset + i] = src[srcOffset + i];
            }
            return;
        }

        int i = 0;
        int end = length & ~15;
        while (i < end) {
            dst[dstOffset + i] = src[srcOffset + i];
            dst[dstOffset + i + 1] = src[srcOffset + i + 1];
            dst[dstOffset + i + 2] = src[srcOffset + i + 2];
            dst[dstOffset + i + 3] = src[srcOffset + i + 3];
            dst[dstOffset + i + 4] = src[srcOffset + i + 4];
            dst[dstOffset + i + 5] = src[srcOffset + i + 5];
            dst[dstOffset + i + 6] = src[srcOffset + i + 6];
            dst[dstOffset + i + 7] = src[srcOffset + i + 7];
            dst[dstOffset + i + 8] = src[srcOffset + i + 8];
            dst[dstOffset + i + 9] = src[srcOffset + i + 9];
            dst[dstOffset + i + 10] = src[srcOffset + i + 10];
            dst[dstOffset + i + 11] = src[srcOffset + i + 11];
            dst[dstOffset + i + 12] = src[srcOffset + i + 12];
            dst[dstOffset + i + 13] = src[srcOffset + i + 13];
            dst[dstOffset + i + 14] = src[srcOffset + i + 14];
            dst[dstOffset + i + 15] = src[srcOffset + i + 15];
            i += 16;
        }
        while (i < length) {
            dst[dstOffset + i] = src[srcOffset + i];
            i++;
        }
    }

    public static int xorChecksum(byte[] data, int offset, int length) {
        if (data == null || length <= 0) return 0;
        if (offset < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid checksum range");
        }
        TELEMETRY_XOR_CALLS.incrementAndGet();
        if (NATIVE_AVAILABLE) {
            int nativeValue = nativeXorChecksum(data, offset, length);
            if (nativeValue != Integer.MIN_VALUE) {
                telemetryNativeHit();
                return nativeValue;
            }
        }
        telemetryFallbackHit();
        return LowLevelDeterminism.xorChecksumCompatFallback(data, offset, length);
    }

    public static int fold32(int a, int b, int c, int d) {
        if (NATIVE_AVAILABLE) {
            telemetryNativeHit();
            return nativeFold32(a, b, c, d);
        }
        telemetryFallbackHit();
        return LowLevelDeterminism.fold32Fallback(a, b, c, d);
    }

    public static int reduceXor(byte[] data, int offset, int length) {
        if (NATIVE_AVAILABLE) {
            int nativeValue = nativeReduceXor(data, offset, length);
            telemetryNativeHit();
            return nativeValue;
        }
        telemetryFallbackHit();
        return LowLevelDeterminism.reduceXorFallback(data, offset, length);
    }

    public static int checksum32(byte[] data, int offset, int length, int seed) {
        if (NATIVE_AVAILABLE) {
            int nativeValue = nativeChecksum32(data, offset, length, seed);
            telemetryNativeHit();
            return nativeValue;
        }
        telemetryFallbackHit();
        return LowLevelDeterminism.checksum32Fallback(data, offset, length, seed);
    }

    public static int crc32c(int initial, byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return initial;
        }
        if (!isValidRange(data.length, offset, length)) {
            throw new IllegalArgumentException("Invalid crc range");
        }
        TELEMETRY_CRC_CALLS.incrementAndGet();
        if (NATIVE_AVAILABLE) {
            int nativeValue = nativeDeterministicCrc32c(initial, data, offset, length);
            telemetryNativeHit();
            return nativeValue;
        }
        telemetryFallbackHit();
        return LowLevelDeterminism.crc32cCompatFallback(initial, data, offset, length);
    }

    public static int allocArena(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Arena size must be > 0");
        }
        if (!ARENA_AVAILABLE) {
            return 0;
        }
        int handle = nativeAllocArena(bytes);
        return handle > 0 ? handle : 0;
    }

    public static boolean freeArena(int handle) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeFreeArena(handle) == 0;
    }

    public static boolean copyArena(int srcHandle, int srcOffset, int dstHandle, int dstOffset, int length) {
        if (srcHandle <= 0 || dstHandle <= 0) {
            throw new IllegalArgumentException("Arena handles must be > 0");
        }
        if (srcOffset < 0 || dstOffset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena copy offsets/length must be >= 0");
        }
        if (length == 0) {
            return true;
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeArenaCopy(srcHandle, srcOffset, dstHandle, dstOffset, length) == 0;
    }

    public static int xorChecksumArena(int handle, int offset, int length) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena checksum offset/length must be >= 0");
        }
        if (length == 0) {
            return 0;
        }
        if (!ARENA_AVAILABLE) {
            return 0;
        }
        return nativeArenaXorChecksum(handle, offset, length);
    }

    public static boolean fillArena(int handle, int offset, int length, int value) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena fill offset/length must be >= 0");
        }
        if ((value & ~0xFF) != 0) {
            throw new IllegalArgumentException("Arena fill value must be in [0, 255]");
        }
        if (length == 0) {
            return true;
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeArenaFill(handle, offset, length, value) == 0;
    }

    public static boolean writeArena(int handle, int offset, byte[] src, int srcOffset, int length) {
        if (handle <= 0) {
            throw new IllegalArgumentException("Arena handle must be > 0");
        }
        if (src == null) {
            throw new IllegalArgumentException("Source buffer must not be null");
        }
        if (offset < 0 || srcOffset < 0 || length < 0) {
            throw new IllegalArgumentException("Arena write offsets/length must be >= 0");
        }
        if (!isValidRange(src.length, srcOffset, length)) {
            throw new IllegalArgumentException("Source range exceeds buffer length");
        }
        if (length == 0) {
            return true;
        }
        if (!ARENA_AVAILABLE) {
            return false;
        }
        return nativeArenaWrite(handle, offset, src, srcOffset, length) == 0;
    }


    public static int byteSwap32(int x) {
        if (NATIVE_AVAILABLE) {
            return nativeByteSwap32(x);
        }
        return (x >>> 24)
                | ((x >>> 8) & 0x0000FF00)
                | ((x << 8) & 0x00FF0000)
                | (x << 24);
    }

    public static int rotateLeft32(int x, int distance) {
        if (NATIVE_AVAILABLE) {
            return nativeRotateLeft32(x, distance);
        }
        int d = distance & 31;
        return (x << d) | (x >>> ((32 - d) & 31));
    }

    public static int rotateRight32(int x, int distance) {
        if (NATIVE_AVAILABLE) {
            return nativeRotateRight32(x, distance);
        }
        int d = distance & 31;
        return (x >>> d) | (x << ((32 - d) & 31));
    }

    public static int popcount32(int x) {
        if (NATIVE_AVAILABLE) {
            int value = nativePopcount32(x);
            if (value >= 0) {
                return value;
            }
        }

        int v = x;
        v = v - ((v >>> 1) & 0x55555555);
        v = (v & 0x33333333) + ((v >>> 2) & 0x33333333);
        v = (v + (v >>> 4)) & 0x0F0F0F0F;
        v = v + (v >>> 8);
        v = v + (v >>> 16);
        return v & 0x3F;
    }

    public static int vec2Pack(int x, int y) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Pack(x, y);
        }
        return ((y & 0xFFFF) << 16) | (x & 0xFFFF);
    }

    public static int vec2X(int vec) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2X(vec);
        }
        return (short) (vec & 0xFFFF);
    }

    public static int vec2Y(int vec) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Y(vec);
        }
        return (short) (vec >>> 16);
    }

    public static int vec2AddSat(int a, int b) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2AddSat(a, b);
        }
        int ax = vec2X(a);
        int ay = vec2Y(a);
        int bx = vec2X(b);
        int by = vec2Y(b);
        return vec2Pack(clamp16(ax + bx), clamp16(ay + by));
    }

    public static int vec2Dot(int a, int b) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Dot(a, b);
        }
        int ax = vec2X(a);
        int ay = vec2Y(a);
        int bx = vec2X(b);
        int by = vec2Y(b);
        return ax * bx + ay * by;
    }

    public static int vec2Mag2(int vec) {
        if (NATIVE_AVAILABLE) {
            return nativeVec2Mag2(vec);
        }
        return vec2Dot(vec, vec);
    }


    public static int coreIngest(byte[] payload) {
        if (!NATIVE_AVAILABLE || payload == null) {
            return Integer.MIN_VALUE;
        }
        return nativeCoreIngest(payload);
    }

    public static int coreProcess(int a, int b, int mode) {
        if (!NATIVE_AVAILABLE) {
            return Integer.MIN_VALUE;
        }
        return nativeCoreProcess(a, b, mode);
    }

    public static int coreRoute(long cpuCycles, long storageReadBytes, long storageWriteBytes,
                                long inputBytes, long outputBytes,
                                long m00, long m01, long m10, long m11) {
        if (!NATIVE_AVAILABLE) {
            return -1;
        }
        return nativeCoreRoute(cpuCycles, storageReadBytes, storageWriteBytes, inputBytes, outputBytes, m00, m01, m10, m11);
    }

    public static boolean coreVerify(byte[] payload, int expected) {
        if (!NATIVE_AVAILABLE || payload == null) {
            return false;
        }
        return nativeCoreVerify(payload, expected) == 1;
    }

    public static long[] coreAudit() {
        if (!NATIVE_AVAILABLE) {
            return null;
        }
        return nativeCoreAudit();
    }

    public static int[] readUnifiedCapabilities() {
        if (!NATIVE_AVAILABLE) {
            return null;
        }
        return nativeReadUnifiedCapabilities();
    }

    public static void coreShutdown() {
        if (NATIVE_AVAILABLE) {
            nativeCoreShutdown();
        }
    }

    private static int clamp16(int value) {
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return value;
    }

    private static boolean isValidRange(int bufferLength, int offset, int length) {
        if (offset < 0 || length < 0 || bufferLength < 0 || offset > bufferLength) {
            return false;
        }
        return (long) offset + (long) length <= (long) bufferLength;
    }

    private static String toHex8(int value) {
        char[] out = new char[8];
        for (int i = 7; i >= 0; i--) {
            int nibble = value & 0xF;
            out[i] = (char) (nibble < 10 ? ('0' + nibble) : ('A' + (nibble - 10)));
            value >>>= 4;
        }
        return new String(out);
    }

    public static final class KernelUnitProfile {
        public final int signature;
        public final int pointerBits;
        public final int cacheLineBytes;
        public final int pageBytes;
        public final int featureMask;
        public final int cpuCores;
        public final int arenaBytes;
        public final int ioQuantumBytes;

        KernelUnitProfile(int signature, int pointerBits, int cacheLineBytes, int pageBytes,
                          int featureMask, int cpuCores, int arenaBytes, int ioQuantumBytes) {
            this.signature = signature;
            this.pointerBits = pointerBits;
            this.cacheLineBytes = cacheLineBytes;
            this.pageBytes = pageBytes;
            this.featureMask = featureMask;
            this.cpuCores = cpuCores;
            this.arenaBytes = arenaBytes;
            this.ioQuantumBytes = ioQuantumBytes;
        }

        private static KernelUnitProfile fromKernelContract(int[] contract) {
            return new KernelUnitProfile(
                    contract[KERNEL_CONTRACT_SIGNATURE],
                    contract[KERNEL_CONTRACT_POINTER_BITS],
                    contract[KERNEL_CONTRACT_CACHE_LINE],
                    contract[KERNEL_CONTRACT_PAGE_SIZE],
                    contract[KERNEL_CONTRACT_FEATURES],
                    contract[KERNEL_CONTRACT_CPU_CORES],
                    contract[KERNEL_CONTRACT_ARENA_BYTES],
                    contract[KERNEL_CONTRACT_IO_QUANTUM]);
        }
    }

    public static final class HardwareProfile {
        public final int signature;
        public final int pointerBits;
        public final int cacheLineBytes;
        public final int pageBytes;
        public final int featureMask;
        public final int regSignature0;
        public final int regSignature1;
        public final int regSignature2;
        public final int gpioWordBits;
        public final int gpioPinStride;

        HardwareProfile(int signature, int pointerBits, int cacheLineBytes, int pageBytes, int featureMask,
                        int regSignature0, int regSignature1, int regSignature2,
                        int gpioWordBits, int gpioPinStride) {
            this.signature = signature;
            this.pointerBits = pointerBits;
            this.cacheLineBytes = cacheLineBytes;
            this.pageBytes = pageBytes;
            this.featureMask = featureMask;
            this.regSignature0 = regSignature0;
            this.regSignature1 = regSignature1;
            this.regSignature2 = regSignature2;
            this.gpioWordBits = gpioWordBits;
            this.gpioPinStride = gpioPinStride;
        }

        private static int normalizeStableSignature(int signature) {
            int archCode = signature & 0xFF00;
            if (isStableArchCode(archCode)) {
                return archCode;
            }
            return ARCH_UNKNOWN;
        }

        private static HardwareProfile fromHardwareContract(int[] contract) {
            if (contract == null || contract.length != HW_CONTRACT_SIZE) {
                throw new IllegalArgumentException("size");
            }
            int pointerBits = contract[HW_CONTRACT_POINTER_BITS];
            int cacheLine = contract[HW_CONTRACT_CACHE_LINE];
            int pageBytes = contract[HW_CONTRACT_PAGE_SIZE];
            int reg0 = contract[HW_CONTRACT_REG0];
            int reg1 = contract[HW_CONTRACT_REG1];
            int reg2 = contract[HW_CONTRACT_REG2];
            int gpioWordBits = contract[HW_CONTRACT_GPIO_WORD_BITS];
            int gpioPinStride = contract[HW_CONTRACT_GPIO_PIN_STRIDE];
            if (pointerBits <= 0 || cacheLine <= 0 || pageBytes <= 0) {
                throw new IllegalArgumentException("layout");
            }
            if ((reg0 | reg1 | reg2) == 0) {
                throw new IllegalArgumentException("register-signature");
            }
            if (gpioWordBits <= 0 || gpioPinStride <= 0) {
                throw new IllegalArgumentException("gpio");
            }
            return new HardwareProfile(
                    normalizeStableSignature(contract[HW_CONTRACT_SIGNATURE]),
                    pointerBits,
                    cacheLine,
                    pageBytes,
                    contract[HW_CONTRACT_FEATURES],
                    reg0,
                    reg1,
                    reg2,
                    gpioWordBits,
                    gpioPinStride);
        }
    }

    private static final class CompatibilityFallback {
        private static boolean hardwareFallbackLogged;
        private static boolean kernelFallbackLogged;

        static HardwareProfile hardwareProfile(String reason) {
            if (!hardwareFallbackLogged) {
                hardwareFallbackLogged = true;
                System.err.println("NativeFastPath compatibility fallback (hardware): " + reason);
            }
            int pointerBits = detectHostPointerBits();
            return new HardwareProfile(ARCH_UNKNOWN, pointerBits, 64, 4096, 0, 0, 0, 0, 0, 0);
        }

        private static int detectHostPointerBits() {
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                String primaryAbi = Build.SUPPORTED_ABIS[0];
                if (primaryAbi.equals("arm64-v8a")
                        || primaryAbi.equals("x86_64")
                        || primaryAbi.equals("riscv64")) {
                    return 64;
                }
            }
            return 32;
        }

        static KernelUnitProfile kernelUnitProfile(String reason) {
            if (!kernelFallbackLogged) {
                kernelFallbackLogged = true;
                System.err.println("NativeFastPath compatibility fallback (kernel): " + reason);
            }
            return new KernelUnitProfile(
                    BOOT_PROFILE.signature,
                    BOOT_PROFILE.pointerBits,
                    BOOT_PROFILE.cacheLineBytes,
                    BOOT_PROFILE.pageBytes,
                    BOOT_PROFILE.featureMask,
                    Math.max(1, Runtime.getRuntime().availableProcessors()),
                    0,
                    Math.max(1, BOOT_PROFILE.cacheLineBytes) * 64);
        }
    }

    public static int ingest(byte[] payload) {
        if (!NATIVE_AVAILABLE || payload == null) {
            return 0;
        }
        return nativeIngest(payload);
    }

    public static long[] processRoute(long cpuCycles, long storageReadBytes, long storageWriteBytes,
                                      long inputBytes, long outputBytes,
                                      long m00, long m01, long m10, long m11) {
        TELEMETRY_ROUTE_CALLS.incrementAndGet();
        if (!NATIVE_AVAILABLE) {
            telemetryFallbackHit();
            return new long[]{0L, 0L, 0L, (m00 * m11) - (m01 * m10), 0L};
        }
        long[] route = nativeProcessRoute(cpuCycles, storageReadBytes, storageWriteBytes, inputBytes, outputBytes, m00, m01, m10, m11);
        if (route != null && route.length == 5) {
            telemetryNativeHit();
            return route;
        }
        telemetryFallbackHit();
        return new long[]{0L, 0L, 0L, 0L, 0L};
    }

    public static boolean verify(byte[] payload, int expectedCrc) {
        return NATIVE_AVAILABLE && payload != null && nativeVerify(payload, expectedCrc) == 1;
    }

    public static long audit(int crc, long entropy, long matrixDeterminant, long routeTag, boolean verifyOk) {
        TELEMETRY_AUDIT_CALLS.incrementAndGet();
        if (!NATIVE_AVAILABLE) {
            telemetryFallbackHit();
            return 0L;
        }
        if ((entropy & ~0xFFFF_FFFFL) != 0L) {
            throw new IllegalArgumentException("Entropy out of uint32 range");
        }
        long signature = nativeAudit(crc, entropy, matrixDeterminant, routeTag, verifyOk ? 1 : 0);
        if (signature != 0L) {
            telemetryNativeHit();
            return signature;
        }
        telemetryFallbackHit();
        return signature;
    }

    public static int parity2D8(int data16) {
        if (NATIVE_AVAILABLE) {
            return nativeDeterministicParity2D8(data16);
        }
        int parity = 0;
        for (int row = 0; row < 4; row++) {
            int rowParity = 0;
            for (int col = 0; col < 4; col++) {
                int idx = (row << 2) | col;
                rowParity ^= (data16 >>> idx) & 1;
            }
            parity |= (rowParity << (row + 4));
        }
        for (int col = 0; col < 4; col++) {
            int colParity = 0;
            for (int row = 0; row < 4; row++) {
                int idx = (row << 2) | col;
                colParity ^= (data16 >>> idx) & 1;
            }
            parity |= (colParity << col);
        }
        return parity;
    }

    public static boolean verify4x4Block(int packedBlock) {
        if (NATIVE_AVAILABLE) {
            return nativeDeterministicVerify4x4Block(packedBlock) == 1;
        }
        int data = (packedBlock >>> 8) & 0xFFFF;
        int storedParity = packedBlock & 0xFF;
        return storedParity == (parity2D8(data) & 0xFF);
    }

    public static int[] policyTransition(int hitStreak, int missStreak, boolean hasEvent) {
        if (NATIVE_AVAILABLE) {
            int[] nativeValue = nativeDeterministicPolicyTransition(hitStreak, missStreak, hasEvent ? 1 : 0);
            if (nativeValue != null && nativeValue.length == 3) {
                return nativeValue;
            }
        }
        int hits = hitStreak;
        int misses = missStreak;
        if (hasEvent) {
            hits++;
            misses = 0;
        } else {
            misses++;
            hits = 0;
        }
        int policy = misses >= 2 ? 1 : 0;
        return new int[]{hits, misses, policy};
    }

    public static final class NativeBridgeTelemetrySnapshot {
        public final boolean nativeAvailable;
        public final int hardwareSignature;
        public final int pointerBits;
        public final int cacheLineBytes;
        public final int pageBytes;
        public final int featureMask;
        public final int kernelCpuCores;
        public final int kernelArenaBytes;
        public final int kernelIoQuantumBytes;
        public final long copyCalls;
        public final long copyBytes;
        public final long xorCalls;
        public final long crcCalls;
        public final long routeCalls;
        public final long auditCalls;
        public final long nativeHits;
        public final long fallbackHits;
        public final int necessaryCount;
        public final int urgentCount;
        public final int complementaryCount;

        NativeBridgeTelemetrySnapshot(boolean nativeAvailable,
                                     HardwareProfile hardware,
                                     KernelUnitProfile kernel,
                                     long[] rawCounters) {
            this.nativeAvailable = nativeAvailable;
            this.hardwareSignature = hardware.signature;
            this.pointerBits = hardware.pointerBits;
            this.cacheLineBytes = hardware.cacheLineBytes;
            this.pageBytes = hardware.pageBytes;
            this.featureMask = hardware.featureMask;
            this.kernelCpuCores = kernel.cpuCores;
            this.kernelArenaBytes = kernel.arenaBytes;
            this.kernelIoQuantumBytes = kernel.ioQuantumBytes;
            this.copyCalls = rawCounters[0];
            this.copyBytes = rawCounters[1];
            this.xorCalls = rawCounters[2];
            this.crcCalls = rawCounters[3];
            this.routeCalls = rawCounters[4];
            this.auditCalls = rawCounters[5];
            this.nativeHits = rawCounters[6];
            this.fallbackHits = rawCounters[7];
            this.necessaryCount = (int) rawCounters[8];
            this.urgentCount = (int) rawCounters[9];
            this.complementaryCount = (int) rawCounters[10];
        }
    }

    private static native int nativeInit();

    private static native int[] nativeReadHardwareContract();

    private static native int[] nativeReadKernelUnitContract();

    private static native int nativeCopyBytes(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length);

    private static native int nativeXorChecksum(byte[] data, int offset, int length);

    private static native int nativeFold32(int a, int b, int c, int d);

    private static native int nativeReduceXor(byte[] data, int offset, int length);

    private static native int nativeChecksum32(byte[] data, int offset, int length, int seed);

    private static native int nativePopcount32(int value);

    private static native int nativeByteSwap32(int value);

    private static native int nativeRotateLeft32(int value, int distance);

    private static native int nativeRotateRight32(int value, int distance);

    private static native int nativeVec2Pack(int x, int y);

    private static native int nativeVec2X(int vec);

    private static native int nativeVec2Y(int vec);

    private static native int nativeVec2AddSat(int a, int b);

    private static native int nativeVec2Dot(int a, int b);

    private static native int nativeVec2Mag2(int vec);

    private static native int nativeAllocArena(int bytes);

    private static native int nativeFreeArena(int handle);

    private static native int nativeArenaCopy(int srcHandle, int srcOffset, int dstHandle, int dstOffset, int length);

    private static native int nativeArenaXorChecksum(int handle, int offset, int length);

    private static native int nativeArenaFill(int handle, int offset, int length, int value);

    private static native int nativeArenaWrite(int handle, int offset, byte[] src, int srcOffset, int length);


    private static native int nativeCoreInit(int seed);

    private static native int nativeCoreShutdown();

    private static native int nativeCoreIngest(byte[] payload);

    private static native int nativeCoreProcess(int a, int b, int mode);

    private static native int nativeCoreRoute(long cpuCycles, long storageReadBytes, long storageWriteBytes,
                                              long inputBytes, long outputBytes,
                                              long m00, long m01, long m10, long m11);

    private static native int nativeCoreVerify(byte[] payload, int expected);

    private static native long[] nativeCoreAudit();

    private static native int[] nativeReadUnifiedCapabilities();



    private static native int nativeIngest(byte[] payload);

    private static native long[] nativeProcessRoute(long cpuCycles, long storageReadBytes, long storageWriteBytes,
                                                    long inputBytes, long outputBytes,
                                                    long m00, long m01, long m10, long m11);

    private static native int nativeVerify(byte[] payload, int expectedCrc);

    private static native long nativeAudit(int crc, long entropy, long matrixDeterminant, long routeTag, int verifyOk);

    private static native int nativeDeterministicCrc32c(int initial, byte[] data, int offset, int length);

    private static native int nativeDeterministicParity2D8(int data16);

    private static native int nativeDeterministicVerify4x4Block(int packedBlock);

    private static native int[] nativeDeterministicPolicyTransition(int hitStreak, int missStreak, int hasEvent);

    private static native int nativeTorusFlowChecksum(int seed, int steps);

    private static native int nativePointerBits();

    private static native int nativeAsmBridgeMarker();

    public static native int runNativeLoop(int maxSteps, long stopConditionPtr);


}
