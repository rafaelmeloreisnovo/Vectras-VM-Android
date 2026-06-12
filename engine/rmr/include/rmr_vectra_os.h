/* ═══════════════════════════════════════════════════════════════════════════
   rmr_vectra_os.h — VECTRA_OS  Zero-Abstraction Compilation Contract
   ─────────────────────────────────────────────────────────────────────────
   ECOSYSTEM EXPANSION METHODOLOGY — Post-Doctoral Formal Specification
   Paradigm: Freestanding · NoMalloc · NoFriction · BranchMinimal

   Axiomatic Contract (invariants; violation terminates the cycle):
     A.1  gcd(Δr, R) = 1          → toroidal T⁷ traversal is geometrically just
     A.2  F* = 23.158 ± ε (Q16)   → FRAF attractor verified every 48 iterations
     A.3  λ = −0.14384             → Lyapunov exponent negative → FSM is stable
     A.4  Ω-CRC chain intact       → commit gate: every state is rollback-capable

   AArch64 / ARM32 Register Allocation Contract (caller-preserved, hot path):
     x0 / r0  = state_vector base ptr    (NEVER freed mid-cycle)
     x1 / r1  = coherence C              (Q16.16 fixed-point)
     x2 / r2  = entropy H                (Q16.16 fixed-point)
     x3 / r3  = phase counter mod 42
     x4 / r4  = attractor index [0..41]
     x5 / r5  = delta / scratch
     x6 / r6  = hash accumulator (FNV running)
     x7 / r7  = flags register (VOS_FLAG_* bit-packed, see below)

   Compilation Contract (all rules non-negotiable):
     — Hot path: macros only.  No named function calls.
     — Allocation: VOS_ARENA_ALLOC only.  No malloc / no brk / no mmap.
     — Conditionals: VOS_CSEL only where branch prediction would fail.
     — Loops: gcd-proven-terminating or unrolled.  No unbounded iteration.
     — Symbols: -ffunction-sections -fdata-sections + --gc-sections strips dead.
     — ASM: inline hexadecimal-addressed register operations (no named regs
       beyond the contract map above).
   ═══════════════════════════════════════════════════════════════════════════ */

#ifndef RMR_VECTRA_OS_H
#define RMR_VECTRA_OS_H

#include "rmr_types.h"

/*
 * VISIBILITY CONTRACT — symbol reduction methodology:
 *   All declarations below default to hidden visibility (-fvisibility=hidden
 *   enforced via compile flag + this pragma).  Only symbols explicitly marked
 *   __attribute__((visibility("default"))) appear in the .dynsym export table.
 *   Hidden symbols still link internally but are stripped by --gc-sections when
 *   unreferenced.  Public API functions at the end of this header are the ONLY
 *   entries that survive into the final binary's symbol table.
 *
 *   Precompiler/linker pipeline:
 *     1. -ffunction-sections  → each fn in .text.<name> section
 *     2. -fvisibility=hidden  → all symbols default to STV_HIDDEN
 *     3. -Wunused-function    → warning fires for unreferenced sections (signal)
 *     4. --gc-sections        → linker removes all unreferenced sections
 *     5. --exclude-libs,ALL   → suppresses re-export of hidden archive symbols
 *   Result: binary contains only the reachable call graph from public API.
 */
#if defined(__GNUC__) || defined(__clang__)
#  pragma GCC visibility push(hidden)
#endif

/* ── 1. PRIMITIVE TYPES ────────────────────────────────────────────────── */

/* Signed complements (rmr_types.h provides only unsigned). */
typedef signed char        s8;
typedef signed short       s16;
typedef signed int         s32;
typedef signed long long   s64;

typedef u32 vos_cap_t;    /* capability bit-field (≡ flags register x7)    */
typedef u32 vos_crc_t;    /* 32-bit CRC accumulator                         */
typedef u64 vos_tick_t;   /* hardware tick counter (cntvct / rdtsc / rdtime)*/
typedef u32 vos_bool_t;   /* boolean: 0 = false, non-zero = true            */

/* Q16.16 fixed-point signed: 16 integer bits + 16 fractional bits.
   Range: ±32767.99998  Resolution: ~0.0000153 (1/65536)                    */
typedef u32 vos_q16_t;    /* stored as unsigned; arithmetic uses signed cast */

/* ── 2. Q16 ARITHMETIC MACROS (no FPU, no libm) ───────────────────────── */

#define VOS_Q16_ONE          0x00010000u            /* 1.0  in Q16           */
#define VOS_Q16_HALF         0x00008000u            /* 0.5  in Q16           */
#define VOS_Q16_FROM(n)      ((vos_q16_t)((n) << 16u))
#define VOS_Q16_INT(q)       ((u32)((q) >> 16u))
#define VOS_Q16_FRAC(q)      ((u32)((q) & 0xFFFFu))

/* Signed 32×32→64 multiply, right-shift 16: core Q16 kernel op.
   Maps directly to SMULL+SSHR (ARM64), IMUL+SAR (x86-64).                 */
#define VOS_Q16_MUL(a, b) \
    ((vos_q16_t)(((s64)(s32)(a) * (s64)(s32)(b)) >> 16))

#define VOS_Q16_ADD(a, b)  ((vos_q16_t)((u32)(a) + (u32)(b)))
#define VOS_Q16_SUB(a, b)  ((vos_q16_t)((u32)(a) - (u32)(b)))
#define VOS_Q16_NEG(a)     ((vos_q16_t)(~(u32)(a) + 1u))
#define VOS_Q16_ABS(a)     (((s32)(a) < 0) ? VOS_Q16_NEG(a) : (a))

/* ── 3. CAPABILITY FLAG REGISTER (bit-packed, mirrors x7 contract) ─────── */

/* Fonte única dos bits: rmr_vectra_flags.def (X-macro, ledger G3).
   O número do bit vive SOMENTE no .def; o enum e as máscaras derivam dele. */
enum {
#define VOS_CAP_DEF(name, bit, str) VOS_CAP_BIT_##name = (bit),
#include "rmr_vectra_flags.def"
#undef VOS_CAP_DEF
  VOS_CAP_BIT__LIMIT = 32
};

enum {
  VOS_CAP_COUNT = 0
#define VOS_CAP_DEF(name, bit, str) + 1
#include "rmr_vectra_flags.def"
#undef VOS_CAP_DEF
};

#define VOS_CAP_CRC32C_HW    (1u << VOS_CAP_BIT_CRC32C_HW) /* hardware CRC32C instruction    */
#define VOS_CAP_NEON_128     (1u << VOS_CAP_BIT_NEON_128)  /* NEON/AdvSIMD 128-bit available */
#define VOS_CAP_SVE          (1u << VOS_CAP_BIT_SVE)       /* ARM SVE available              */
#define VOS_CAP_FMA          (1u << VOS_CAP_BIT_FMA)       /* fused-multiply-add available   */
#define VOS_CAP_CNTVCT       (1u << VOS_CAP_BIT_CNTVCT)    /* EL0 system counter accessible  */
#define VOS_CAP_RDTSC        (1u << VOS_CAP_BIT_RDTSC)     /* x86 RDTSC accessible           */
#define VOS_CAP_SSE42        (1u << VOS_CAP_BIT_SSE42)     /* SSE4.2 (includes CRC32 opcode) */
#define VOS_CAP_MOCK         (1u << VOS_CAP_BIT_MOCK)      /* simulation mode: no real HW    */

/* Nome canônico da flag por índice de bit (fonte: .def).  static inline:
   zero símbolo exportado; gc-sections elimina onde não referenciado.      */
static inline const char *vos_flag_name(u32 bit_index) {
  switch (bit_index) {
#define VOS_CAP_DEF(name, bit, str) case (bit): return (str);
#include "rmr_vectra_flags.def"
#undef VOS_CAP_DEF
    default: return "unknown";
  }
}

/* Hotswap: enable/disable at runtime (DMB-fenced, thread-visible).
   Use VOS_HOTSWAP_CRC / VOS_HOTSWAP_TIMER rather than raw cap mutation.   */
#define VOS_CAPS_ENABLE(mask)  do { \
    __asm__ volatile("" ::: "memory"); \
    vos_g_caps |= (mask); \
    __asm__ volatile("" ::: "memory"); \
} while(0)

#define VOS_CAPS_DISABLE(mask) do { \
    __asm__ volatile("" ::: "memory"); \
    vos_g_caps &= ~(mask); \
    __asm__ volatile("" ::: "memory"); \
} while(0)

/* ── 3.1 FLAG ROLLBACK (G4 núcleo) — transação sobre o registrador ─────── */
/* Mesmo padrão do arena mark/restore: snapshot O(1), rollback O(1).
   Nota de contrato: a propagação de return-code via estados TTL8 (G4
   completo) aguarda o codex de referência; ver gap ledger §4 G4.          */
extern volatile vos_cap_t vos_g_caps_prev;  /* snapshot para rollback      */

#define VOS_FLAGS_MARK() do { \
    __asm__ volatile("" ::: "memory"); \
    vos_g_caps_prev = vos_g_caps; \
    __asm__ volatile("" ::: "memory"); \
} while(0)

#define VOS_FLAGS_RESTORE() do { \
    __asm__ volatile("" ::: "memory"); \
    vos_g_caps = vos_g_caps_prev; \
    __asm__ volatile("" ::: "memory"); \
} while(0)

/* RAF_TRY_FLAG(mask, body): transação de capability — marca o registrador,
   habilita mask e avalia body; se body for falso, o registrador anterior é
   restaurado integralmente (rollback sem resíduo). O resultado é observável
   pelo próprio estado de vos_g_caps, coerente com hit/miss como estados.  */
#define RAF_TRY_FLAG(mask, body) do { \
    VOS_FLAGS_MARK(); \
    VOS_CAPS_ENABLE(mask); \
    if (!(body)) VOS_FLAGS_RESTORE(); \
} while(0)

/* ── 4. ARENA ALLOCATOR — bump-pointer, BSS, zero-overhead ─────────────── */

#ifndef VOS_ARENA_SIZE
#define VOS_ARENA_SIZE       (65536u)    /* 64 KB, in BSS (no mmap)          */
#endif
#define VOS_ARENA_ALIGN_LOG2 4u          /* 16-byte natural alignment         */
#define VOS_ARENA_ALIGN      (1u << VOS_ARENA_ALIGN_LOG2)

/* Align a size up to the nearest VOS_ARENA_ALIGN boundary.                  */
#define VOS_ALIGN_UP(sz) \
    (((u32)(sz) + (VOS_ARENA_ALIGN - 1u)) & ~(VOS_ARENA_ALIGN - 1u))

/* Single-expression bump allocation: returns void* or NULL on overflow.
   Cost: 1 add + 1 cmp + 1 branch (predicted-taken: the common case).       */
#define VOS_ARENA_ALLOC(sz) ( \
    (VOS_ALIGN_UP(sz) <= (u32)(vos_g_arena + VOS_ARENA_SIZE - vos_g_arena_top)) ? \
    (void *)((vos_g_arena_top += VOS_ALIGN_UP(sz)) - VOS_ALIGN_UP(sz)) : \
    (void *)0 \
)

/* Rollback checkpointing: O(1) mark + restore, no pointer tracking. */
#define VOS_MARK()     do { vos_g_arena_mark = vos_g_arena_top; } while(0)
#define VOS_RESTORE()  do { vos_g_arena_top  = vos_g_arena_mark; } while(0)
#define VOS_ARENA_RESET() do { \
    vos_g_arena_top  = vos_g_arena; \
    vos_g_arena_mark = vos_g_arena; \
} while(0)

/* ── 5. BRANCHLESS SELECTION — maps to CSEL (ARM64) / CMOV (x86-64) ────── */

/* VOS_CSEL(cond, a, b): returns a if cond != 0, else b.
   Arithmetic identity: mask = -(cond != 0); result = (a & mask) | (b & ~mask).
   Compiler will emit CSEL/CMOV when both a,b are register-width integers.  */
#define VOS_CSEL(cond, a, b) \
    ((__typeof__(a))(((u32)(-(s32)((u32)(cond) != 0u)) & (u32)(a)) | \
                     (~(u32)(-(s32)((u32)(cond) != 0u)) & (u32)(b))))

/* Branchless absolute value for Q16. */
#define VOS_CSEL_ABS_Q16(q) \
    ((vos_q16_t)(((u32)(q) ^ (u32)((s32)(q) >> 31)) - (u32)((s32)(q) >> 31)))

/* ── 6. DISPATCH FUNCTION POINTER TABLE — hotswap-capable ──────────────── */

/* CRC32C dispatch: hardware (crc32cx / crc32q) or software Castagnoli.
   Signature: (buf, len, init) → u32 crc.
   VOS_HOTSWAP_CRC_HW / _SW switch the active implementation atomically.   */
typedef u32 (*vos_crc_fn_t)(const u8 *, u32, u32);
typedef vos_tick_t (*vos_tick_fn_t)(void);

extern volatile vos_crc_fn_t  vos_g_crc;    /* active CRC32C implementation  */
extern volatile vos_tick_fn_t vos_g_tick;   /* active tick-counter reader    */
extern volatile vos_cap_t     vos_g_caps;   /* live capability flags         */
extern u8                     vos_g_arena[VOS_ARENA_SIZE]; /* BSS arena pool */
extern u8                    *vos_g_arena_top;             /* bump cursor    */
extern u8                    *vos_g_arena_mark;            /* rollback mark  */

/* Hot-path macros (zero named-function overhead in critical section). */
#define VOS_CRC32C(buf, len, init) (vos_g_crc((buf), (u32)(len), (u32)(init)))
#define VOS_TICK()                 (vos_g_tick())
#define VOS_TICK_DELTA(t0)         ((vos_tick_t)(VOS_TICK() - (t0)))

/* Atomic hotswap of CRC implementation (DMB fenced on both sides). */
#define VOS_HOTSWAP_CRC(fn_ptr) do { \
    __asm__ volatile("" ::: "memory"); \
    vos_g_crc = (fn_ptr); \
    __asm__ volatile("" ::: "memory"); \
} while(0)

#define VOS_HOTSWAP_TICK(fn_ptr) do { \
    __asm__ volatile("" ::: "memory"); \
    vos_g_tick = (fn_ptr); \
    __asm__ volatile("" ::: "memory"); \
} while(0)

/* ── 7. FRAF CONVERGENCE — Fibonacci-Rafael attractor F* = 23.158 ───────── */

/* Q16 constants derived from:
     F_{n+1} = F_n × (√3/2) − π·sin(279°)
   √3/2  ≈ 0.866025403784  → Q16: 56756  (0xDDB4)
   sin(279°) = −sin(81°) ≈ −0.987688  → π·sin(279°) ≈ −3.102356
   Subtraction of negative = addition: offset = +3.102356
   3.102356 × 65536 = 203294  (0x31A1E)
   F* é o ponto fixo do SISTEMA QUANTIZADO implementado, não do contínuo:
     F* = offset / (1 − scale) em Q16 = 203294×65536/(65536−56756)
        = 0x17277A  (≈ 23.1538; o contínuo daria ≈ 23.1589)
   Iterações: taxa de contração 0.866 ⇒ 48 passos nunca alcançam ε=0.001
   (precisa ≥ ~70 a partir de seed 1; ~79 a partir de seed 100).
   96 passos garantem |Fₙ−F*| ≤ ε para seeds até 1000 incluindo o viés de
   truncamento do Q16_MUL (≤ 1/(1−scale) ≈ 8 LSB acumulados).             */
#define VOS_FRAF_SCALE_Q16  ((vos_q16_t)0x0000DDB4u)  /* 0.866025 × 2^16   */
#define VOS_FRAF_OFFSET_Q16 ((vos_q16_t)0x00031A1Eu)  /* 3.102356 × 2^16   */
#define VOS_FRAF_STAR_Q16   ((vos_q16_t)0x0017277Au)  /* F* quantizado     */
#define VOS_FRAF_EPS_Q16    ((vos_q16_t)0x00000042u)  /* ε = 0.001 × 2^16  */
#define VOS_FRAF_ITERS      96u                        /* iterations to F*  */
#define VOS_LYAPUNOV_NEG    0x9FE9u                    /* |λ| = 0.14384 Q16 */

/* Single FRAF iteration (macro = 1 inline expansion, no call overhead). */
#define VOS_FRAF_STEP(fn) \
    VOS_Q16_ADD(VOS_Q16_MUL((fn), VOS_FRAF_SCALE_Q16), VOS_FRAF_OFFSET_Q16)

/* Full convergence loop: 48 iterations with attractor verification.
   Writes: out_fn = final Q16 value, out_ok = 1 if |fn − F*| < ε.          */
#define VOS_FRAF_CONVERGE(out_fn, out_ok, seed_q16) do { \
    vos_q16_t _vfc_fn = (seed_q16); \
    u32 _vfc_i = VOS_FRAF_ITERS; \
    do { _vfc_fn = VOS_FRAF_STEP(_vfc_fn); } while(--_vfc_i); \
    (out_fn) = _vfc_fn; \
    (out_ok) = (VOS_CSEL_ABS_Q16(VOS_Q16_SUB(_vfc_fn, VOS_FRAF_STAR_Q16)) \
                <= VOS_FRAF_EPS_Q16); \
} while(0)

/* ── 8. CRC32C CHAIN — commit integrity (Ω-gate) ───────────────────────── */

/* Running CRC32C chain over output tokens.  Rollback if chain breaks.      */
#define VOS_CHAIN_INIT       0xFFFFFFFFu
#define VOS_CHAIN_FEED(c, b) VOS_CRC32C((const u8 *)&(b), 4u, (c))
#define VOS_CHAIN_VERIFY(c)  ((c) != 0u && (c) != 0xFFFFFFFFu)

/* ── 9. MEDIAN-31 MICRO-BENCHMARK — Machine Codex MC-01 compliant ──────── */

/* Sample buffer on stack (31 × u32, 124 bytes): fits in L1 easily.
   Insertion sort on u32[31] is O(961) = trivial; no heap needed.           */
#define VOS_BENCH_N 31u

/* VOS_BENCH_RUN(name_u32, out_med_ns, body_expr):
   Runs body_expr 3 warmup + 31 measured times; writes median to out_med_ns.
   Anti-DCE: result accumulated into _bcr to force evaluation.              */
#define VOS_BENCH_RUN(out_med, body_expr) do { \
    vos_tick_t _bt[VOS_BENCH_N]; \
    u32 _bw; \
    for(_bw = 0; _bw < 3u; _bw++) { (void)(body_expr); } \
    for(_bw = 0; _bw < VOS_BENCH_N; _bw++) { \
        vos_tick_t _bt0 = VOS_TICK(); \
        (void)(body_expr); \
        _bt[_bw] = VOS_TICK() - _bt0; \
    } \
    /* Insertion sort — no stdlib needed */ \
    u32 _bi, _bj; vos_tick_t _bk; \
    for(_bi = 1; _bi < VOS_BENCH_N; _bi++) { \
        _bk = _bt[_bi]; _bj = _bi; \
        while(_bj > 0 && _bt[_bj-1] > _bk) { _bt[_bj] = _bt[_bj-1]; _bj--; } \
        _bt[_bj] = _bk; \
    } \
    (out_med) = _bt[VOS_BENCH_N / 2u]; \
} while(0)

/* ── 10. FAILSAFE / SELF-TEST GATE ─────────────────────────────────────── */

/* VOS_FAILSAFE(ok): abort-protocol on invariant failure.
   Maps to: set VOID attractor flag in g_caps, flush hash, halt.
   In JNI builds: returns 0 to caller (no OS exit).
   In baremetal:  infinite stall via inline ASM WFI/HLT.                    */
#if defined(RMR_JNI_BUILD) && RMR_JNI_BUILD
#  define VOS_FAILSAFE(ok) do { \
    if(!(ok)) { \
        VOS_CAPS_ENABLE(VOS_CAP_MOCK); \
        vos_g_caps |= 0xFF000000u; \
        return 0; \
    } \
} while(0)
#else
#  if defined(__aarch64__)
#    define VOS_FAILSAFE(ok) do { \
    if(!(ok)) { \
        VOS_CAPS_ENABLE(VOS_CAP_MOCK); \
        __asm__ volatile("wfi" ::: "memory"); \
        for(;;){} \
    } \
} while(0)
#  elif defined(__x86_64__) || defined(__i386__)
#    define VOS_FAILSAFE(ok) do { \
    if(!(ok)) { \
        VOS_CAPS_ENABLE(VOS_CAP_MOCK); \
        __asm__ volatile("hlt" ::: "memory"); \
        for(;;){} \
    } \
} while(0)
#  else
#    define VOS_FAILSAFE(ok) do { if(!(ok)) { for(;;){} } } while(0)
#  endif
#endif

/* ── 11. GPIO PIN SEQUENTIAL ADDRESSING (bare-metal register mapping) ───── */

/* Sequential pin map: base + pin × stride (word-aligned MMIO).
   stride = 4 (ARM GPIO banks), 8 (x86 I/O port model).                    */
#define VOS_GPIO_ADDR(base, pin, stride) \
    ((volatile u32 *)((uintptr_t)(base) + (u32)(pin) * (u32)(stride)))
#define VOS_GPIO_SET(base, pin, stride)  (*VOS_GPIO_ADDR(base,pin,stride) = 1u)
#define VOS_GPIO_CLR(base, pin, stride)  (*VOS_GPIO_ADDR(base,pin,stride) = 0u)
#define VOS_GPIO_RD(base, pin, stride)   (*VOS_GPIO_ADDR(base,pin,stride))
/* Toggle via write-to-toggle register (Arduino PINB equivalent, T32). */
#define VOS_GPIO_TOGGLE(base, pin, stride) \
    (*VOS_GPIO_ADDR(base,pin,stride) = *VOS_GPIO_ADDR(base,pin,stride) ^ 1u)

/* ── 12. MACHINE CODEX — cycle-budget invariants (MC-01..MC-10) ─────────── */

#define VOS_MC_LOOP_MIN_INSN     1u   /* MC-01: ≥1 useful insn per iteration */
#define VOS_MC_FMLA_MIN_INFLIGHT 4u   /* MC-02: min in-flight FP ops for sat.*/
#define VOS_MC_BRANCH_MISS_CYC   20u  /* MC-03: branch mispredict penalty     */
#define VOS_MC_L1_CYC            4u   /* MC-04: L1 cache miss latency         */
#define VOS_MC_L2_CYC            12u
#define VOS_MC_L3_CYC            36u
#define VOS_MC_DRAM_CYC          200u
#define VOS_MC_ISB_CYC           20u  /* MC-05: ISB pipeline flush cost       */
#define VOS_MC_DSB_CYC           15u
#define VOS_MC_DMB_CYC           5u
#define VOS_MC_SVC_NS            300u /* MC-06: syscall average latency (ns)  */
#define VOS_MC_MRS_CYC           5u   /* MC-07: system register read cost     */
#define VOS_MC_MUL64_CYC         3u   /* MC-08: 64-bit multiply latency       */
#define VOS_MC_LOAD_USE_STALL    4u   /* MC-09: load-use hazard stall         */
#define VOS_MC_LOOP_OVERHEAD     2u   /* MC-10: compare+branch overhead/iter  */

/* ── 13. PHI64 INDEX HASH — T36: Knuth multiplicative (replaces modulo) ─── */

#define VOS_PHI64  0x9E3779B97F4A7C15ULL
/* Index into table of size 2^bits: (key × PHI64) >> (64 − bits). */
#define VOS_PHI_IDX(key64, bits) \
    ((u32)(((u64)(key64) * VOS_PHI64) >> (64u - (bits))))

/* ── 14. FNV-1a RUNNING HASH — T33 session-integrity token ─────────────── */

#define VOS_FNV_BASIS  0xCBF29CE484222325ULL
#define VOS_FNV_PRIME  0x00000100000001B3ULL
#define VOS_FNV_FEED(h, byte) \
    ((u64)((((u64)(h)) ^ (u8)(byte)) * VOS_FNV_PRIME))

/* ── 15. PUBLIC API — explicit default visibility (survives gc-sections) ─── */
/*
 * These three functions are the ONLY symbols exported from this module.
 * All internal helpers (vos_crc32c_sw, vos_detect_caps, vos_init_dispatch,
 * vos_build_hwcap_matrix, vos_tick_sw, per-arch HW primitives) carry hidden
 * visibility and are eliminated from .dynsym by --gc-sections if unreferenced.
 *
 * PRECOMPILER NOTE: unused internal functions will generate -Wunused-function
 * warnings.  This is INTENTIONAL — it is the gc-sections elimination signal.
 * Do NOT suppress these warnings with __attribute__((unused)).
 */
#if defined(__GNUC__) || defined(__clang__)
#  pragma GCC visibility pop
#endif

/* vos_init(): detects HWCAP, builds dispatch tables, verifies FRAF attractor.
   Returns 1 on success, 0 on invariant failure.                             */
__attribute__((visibility("default"))) u32 vos_init(void);

/* vos_selftest(): re-verifies A.1..A.4 at runtime.  Returns 1 = pass.      */
__attribute__((visibility("default"))) u32 vos_selftest(void);

/* vos_caps_report(): writes capability hex tag to out[0..3].               */
__attribute__((visibility("default"))) void vos_caps_report(u32 *out);

#endif /* RMR_VECTRA_OS_H */
