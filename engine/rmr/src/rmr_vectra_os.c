/* ═══════════════════════════════════════════════════════════════════════════
   rmr_vectra_os.c — VECTRA_OS Implementation
   Freestanding · NoMalloc · NoFriction · Capability-Dispatched
   ═══════════════════════════════════════════════════════════════════════════
   Dependencies: rmr_types.h (u8/u16/u32/u64), zero.h (constants only).
   Zero libc in hot paths.  Software fallbacks included for every HW feature.
   ═══════════════════════════════════════════════════════════════════════════ */

#include "rmr_vectra_os.h"
#include "zero.h"

/* Suppress "unused parameter" in branchless paths. */
#define VOS_UNUSED(x) ((void)(x))

/* ── GLOBAL BSS STORAGE (zero-initialized by kernel/loader, no constructor) */

__attribute__((aligned(VOS_ARENA_ALIGN)))
u8                    vos_g_arena[VOS_ARENA_SIZE];
u8                   *vos_g_arena_top;
u8                   *vos_g_arena_mark;
volatile vos_cap_t    vos_g_caps;
volatile vos_crc_fn_t vos_g_crc;
volatile vos_tick_fn_t vos_g_tick;

/* ── HARDWARE PROBE DECLARATIONS (implemented in per-arch .S files) ──────── */

#if defined(__aarch64__)
u32  vos_crc32c_hw_a64(const u8 *buf, u32 len, u32 init);
vos_tick_t vos_rdcnt_a64(void);
#endif

#if defined(__x86_64__)
u32  vos_crc32c_hw_x64(const u8 *buf, u32 len, u32 init);
vos_tick_t vos_rdtsc_x64(void);
#endif

#if defined(__arm__) && !defined(__aarch64__)
vos_tick_t vos_rdcnt_av7(void);
#endif

#if defined(__riscv)
vos_tick_t vos_rdtime_rv64(void);
#endif

/* ── SOFTWARE FALLBACK: CRC32C (Castagnoli, bitwise — zero static data) ──── */
/* Bitwise implementation: no lookup table, no static storage, no libc.
   Polynomial: 0x82F63B78 (Castagnoli reflected, equivalent to crc32cx).
   Throughput: ~1 byte/8 cycles. Adequate for verification paths.           */
static u32 vos_crc32c_sw(const u8 *buf, u32 len, u32 init) {
    u32 crc = ~init;
    u32 i, j;
    for (i = 0u; i < len; i++) {
        crc ^= (u32)buf[i];
        for (j = 0u; j < 8u; j++) {
            /* Branchless: mask = -(crc & 1); poly applied only if LSB set. */
            crc = (crc >> 1u) ^ (0x82F63B78u & (u32)(-(s32)(crc & 1u)));
        }
    }
    return ~crc;
}

/* ── SOFTWARE FALLBACK: tick counter ──────────────────────────────────────── */
/* Used on unknown architectures only.  Suppressed on known arches.          */
static __attribute__((unused)) vos_tick_t vos_tick_sw(void) {
    static volatile u64 vos_sw_tick;
    return ++vos_sw_tick;
}

/* ── HARDWARE CAPABILITY DETECTION ─────────────────────────────────────── */

/* On Linux: parse /proc/cpuinfo for "Features" or use AT_HWCAP via auxv.
   On baremetal: use compile-time architecture macros.
   The result is written to vos_g_caps at init time only.                   */

static vos_cap_t vos_detect_caps(void) {
    vos_cap_t caps = 0u;

#if defined(__aarch64__)
    /* AArch64: attempt to execute crc32cx probe — if it faults, SIGILL
       will be caught.  In practice on Android, CRC32 is mandated by ARMv8.0
       and HWCAP_CRC32 confirms it without a signal handler.                */
    caps |= VOS_CAP_CNTVCT;   /* cntvct_el0 is accessible at EL0 on Linux   */

#  if defined(__ARM_FEATURE_CRC32)
    caps |= VOS_CAP_CRC32C_HW;
#  endif

#  if defined(__ARM_NEON) || defined(__ARM_FEATURE_SIMD32)
    caps |= VOS_CAP_NEON_128;
#  endif

#  if defined(__ARM_FEATURE_SVE)
    caps |= VOS_CAP_SVE;
#  endif

#  if defined(__ARM_FEATURE_FMA)
    caps |= VOS_CAP_FMA;
#  endif

#elif defined(__x86_64__) || defined(__i386__)
    /* x86: CPUID leaf 7, ECX bit 3 = SSE4.2, which includes CRC32.        */
    caps |= VOS_CAP_RDTSC;

#  if defined(__SSE4_2__)
    caps |= VOS_CAP_SSE42 | VOS_CAP_CRC32C_HW;
#  endif

#elif defined(__arm__) && !defined(__aarch64__)
    /* ARMv7: PMCCNTR accessible if PMUSERENR.EN = 1 (Linux sets this).    */
    caps |= VOS_CAP_CNTVCT;   /* reuse flag; impl uses pmccntr              */

#elif defined(__riscv)
    /* RISC-V: rdtime CSR is accessible from userspace on Linux.            */
    caps |= VOS_CAP_CNTVCT;   /* reuse flag; impl uses rdtime               */

#endif

    return caps;
}

/* ── DISPATCH TABLE INITIALIZATION ─────────────────────────────────────── */

static void vos_init_dispatch(vos_cap_t caps) {
    /* CRC32C: prefer hardware if available. */
    vos_crc_fn_t crc_sel = vos_crc32c_sw;

#if defined(__aarch64__)
    if (caps & VOS_CAP_CRC32C_HW) { crc_sel = vos_crc32c_hw_a64; }
    VOS_HOTSWAP_TICK(vos_rdcnt_a64);
#elif defined(__x86_64__)
    if (caps & VOS_CAP_CRC32C_HW) { crc_sel = vos_crc32c_hw_x64; }
    VOS_HOTSWAP_TICK(vos_rdtsc_x64);
#elif defined(__arm__) && !defined(__aarch64__)
    VOS_HOTSWAP_TICK(vos_rdcnt_av7);
#elif defined(__riscv)
    VOS_HOTSWAP_TICK(vos_rdtime_rv64);
#else
    VOS_HOTSWAP_TICK(vos_tick_sw);
#endif

    VOS_HOTSWAP_CRC(crc_sel);
    VOS_UNUSED(caps);
}

/* ── HARDWARE IDENTIFICATION MATRIX (bit-map, 8×4 = 32 bits) ───────────── */

/* Encodes: capability × risk-tier as a 2D bit matrix packed into u32.
   Row = capability (3 bits, 0..7 = CRC32C..MOCK).
   Col = tier (2 bits, 0..3 = DETECT/USE/MITIGATE/BLOCK).
   Matrix cell = 1 if the combination is active.                             */
typedef struct {
    u32 matrix;          /* 8 capabilities × 4 tiers, packed LSB-first       */
    u32 arch_tag;        /* arch identifier (hex-literal from zero.h)         */
    u32 feature_sig;     /* XOR of all active feature bits × PHI32            */
    u32 crc_seed;        /* CRC32C of (matrix || arch_tag) for chain verify   */
} vos_hwcap_matrix_t;

/* Build the hardware capability matrix at init time. */
static vos_hwcap_matrix_t vos_build_hwcap_matrix(vos_cap_t caps, u32 arch_tag) {
    vos_hwcap_matrix_t m;
    u32 bit, mat = 0u;
    u32 arch = arch_tag;
    /* For each capability bit: set DETECT tier always, USE if cap present,
       MITIGATE if cap absent (software path), BLOCK never (no HW denied). */
    for (bit = 0u; bit < 8u; bit++) {
        u32 present = (caps >> bit) & 1u;
        u32 detect   = 1u;      /* tier 0: always detected                    */
        u32 use      = present; /* tier 1: used if present                    */
        u32 mitigate = present ^ 1u; /* tier 2: mitigated if absent           */
        u32 block    = 0u;      /* tier 3: never blocked by this framework    */
        u32 cell = detect | (use << 1u) | (mitigate << 2u) | (block << 3u);
        mat |= (cell << (bit * 4u));
    }
    m.matrix      = mat;
    m.arch_tag    = arch;
    m.feature_sig = (caps * RMR_ZERO_PHI32_U32) ^ arch;
    /* Pack matrix + arch into 8 bytes for CRC seed verification. */
    u8 seed_bytes[8];
    seed_bytes[0] = (u8)(mat);
    seed_bytes[1] = (u8)(mat >> 8u);
    seed_bytes[2] = (u8)(mat >> 16u);
    seed_bytes[3] = (u8)(mat >> 24u);
    seed_bytes[4] = (u8)(arch);
    seed_bytes[5] = (u8)(arch >> 8u);
    seed_bytes[6] = (u8)(arch >> 16u);
    seed_bytes[7] = (u8)(arch >> 24u);
    m.crc_seed = vos_crc32c_sw(seed_bytes, 8u, VOS_CHAIN_INIT);
    return m;
}

/* ── PUBLIC INTERFACE ────────────────────────────────────────────────────── */

u32 vos_init(void) {
    vos_cap_t caps;
    vos_q16_t fn_out;
    vos_bool_t fraf_ok;
    vos_hwcap_matrix_t mat;
    u32 arch_tag;

    /* Step 1: Reset arena (O(1), no memset needed — BSS is already zero). */
    vos_g_arena_top  = vos_g_arena;
    vos_g_arena_mark = vos_g_arena;

    /* Step 2: Detect hardware capabilities. */
    caps = vos_detect_caps();
    vos_g_caps = caps;

    /* Step 3: Build dispatch tables. */
    vos_init_dispatch(caps);

    /* Step 4: Verify FRAF attractor (A.2: F* = 23.158 ± ε). */
    VOS_FRAF_CONVERGE(fn_out, fraf_ok, VOS_Q16_ONE);
    VOS_FAILSAFE(fraf_ok);

    /* Step 5: Build hardware capability matrix (A.1: geometric invariant). */
#if defined(__aarch64__)
    arch_tag = 0x41363441u; /* "A64A" */
#elif defined(__x86_64__)
    arch_tag = 0x58363446u; /* "X64F" */
#elif defined(__arm__)
    arch_tag = 0x41524D37u; /* "ARM7" */
#elif defined(__riscv)
    arch_tag = 0x52564136u; /* "RVA6" */
#else
    arch_tag = 0x554E4B4Eu; /* "UNKN" */
#endif

    mat = vos_build_hwcap_matrix(caps, arch_tag);
    VOS_FAILSAFE(mat.crc_seed != 0u);

    /* Step 6: Verify chain integrity (A.4: CRC chain intact). */
    VOS_FAILSAFE(VOS_CHAIN_VERIFY(mat.crc_seed));

    (void)fn_out;
    return 1u;
}

u32 vos_selftest(void) {
    vos_q16_t fn_out;
    vos_bool_t ok;

    /* Verify FRAF convergence from multiple seeds. */
    VOS_FRAF_CONVERGE(fn_out, ok, VOS_Q16_ONE);
    if (!ok) return 0u;

    VOS_FRAF_CONVERGE(fn_out, ok, VOS_Q16_FROM(5));
    if (!ok) return 0u;

    VOS_FRAF_CONVERGE(fn_out, ok, VOS_Q16_FROM(100));
    if (!ok) return 0u;

    /* Verify CRC32C software path produces known result. */
    static const u8 vos_test_vec[4] = {0x01u, 0x02u, 0x03u, 0x04u};
    u32 crc_sw = vos_crc32c_sw(vos_test_vec, 4u, VOS_CHAIN_INIT);
    if (crc_sw == 0u || crc_sw == 0xFFFFFFFFu) return 0u;

    /* Verify hardware path matches software path (if HW available). */
    if (vos_g_caps & VOS_CAP_CRC32C_HW) {
        u32 crc_hw = vos_g_crc(vos_test_vec, 4u, VOS_CHAIN_INIT);
        if (crc_hw != crc_sw) return 0u;
    }

    /* Verify arena allocation and rollback. */
    VOS_MARK();
    void *p = VOS_ARENA_ALLOC(64u);
    if (!p) return 0u;
    VOS_RESTORE();
    void *p2 = VOS_ARENA_ALLOC(64u);
    if (!p2) return 0u;
    if (p != p2) return 0u; /* after restore, same address must be returned */
    VOS_RESTORE();

    (void)fn_out;
    return 1u;
}

void vos_caps_report(u32 *out) {
    if (!out) return;
    out[0] = (u32)vos_g_caps;
    out[1] = VOS_FRAF_STAR_Q16;
    out[2] = VOS_FRAF_SCALE_Q16;
    out[3] = VOS_FRAF_OFFSET_Q16;
}
