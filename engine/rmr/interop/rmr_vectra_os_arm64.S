/* ═══════════════════════════════════════════════════════════════════════════
   rmr_vectra_os_arm64.S — VECTRA_OS AArch64 Hardware Primitives
   ─────────────────────────────────────────────────────────────────────────
   Zero-abstraction, register-direct, no stub, no libc.
   All primitives are leaf functions: no frame, no stack usage beyond args.

   Register contract (AAPCS64 — preserved by callee: x19..x28, x29, x30):
     x0..x7  = args / return values (caller-saved)
     Hot-path registers x0..x7 mirror the VECTRA_OS contract in the header.

   ISA requirements: ARMv8-A (base).  CRC ops gated by __ARM_FEATURE_CRC32.
   ═══════════════════════════════════════════════════════════════════════════ */

#if defined(__aarch64__)

/* Select .arch directive based on CRC32 availability. */
#if defined(__ARM_FEATURE_CRC32)
    .arch armv8-a+crc
#else
    .arch armv8-a
#endif
    .text
    .balign 16

/* ─────────────────────────────────────────────────────────────────────────
   vos_crc32c_hw_a64 — CRC32C hardware instruction (Castagnoli)
   Prototype : u32 vos_crc32c_hw_a64(const u8 *buf, u32 len, u32 init)
   Args      : x0 = buf ptr, w1 = len (bytes), w2 = init (seed)
   Returns   : w0 = CRC32C result
   Pipeline  : 8-byte chunks via CRC32CX (1 cycle/8B on Cortex-A55+)
               tail bytes via CRC32CB (1 cycle each)
   ───────────────────────────────────────────────────────────────────────── */
#if defined(__ARM_FEATURE_CRC32)

    /*
     * .hidden: STV_HIDDEN ELF visibility — symbol NOT exported to .dynsym.
     * Callable internally (static linkage), but --gc-sections removes it if
     * no translation unit references it (e.g. CRC32C_HW cap not detected).
     * This is the assembler-level gc-sections methodology: structured directives
     * embedded as code annotations, not documentation.
     */
    .global vos_crc32c_hw_a64
    .hidden vos_crc32c_hw_a64
    .type   vos_crc32c_hw_a64, %function
vos_crc32c_hw_a64:
    /* w2 = running crc; inverted as per CRC32C convention (~init). */
    mvn     w2, w2              /* w2 = ~init                               */

    /* 8-byte bulk loop: unrolled ×2 (MC-10: reduce loop overhead). */
    subs    w1, w1, #16         /* preflight: 16 bytes available?            */
    b.lt    .Lchunk8_a64
.Lchunk16_a64:
    ldr     x3, [x0], #8       /* load 8 bytes; post-index (T-post-idx)     */
    crc32cx w2, w2, x3          /* 1 cycle latency CRC32C on 64-bit word     */
    ldr     x3, [x0], #8
    crc32cx w2, w2, x3
    subs    w1, w1, #16
    b.ge    .Lchunk16_a64

    /* Recover residual: w1 = original_len mod 16, offset from -16. */
    adds    w1, w1, #8          /* probe: ≥8 bytes remaining after ×2 done? */
    b.lt    .Lbyte_loop_a64
    ldr     x3, [x0], #8
    crc32cx w2, w2, x3
    subs    w1, w1, #8
    b.ge    .Lchunk8_a64

    /* ≥8 bytes remaining from initial chunk8 path */
.Lchunk8_a64:
    adds    w1, w1, #8          /* recover: was len before -8 check         */
    b.le    .Ldone_a64

    /* 1-byte tail: process remaining bytes individually. */
.Lbyte_loop_a64:
    cbz     w1, .Ldone_a64
    ldrb    w3, [x0], #1        /* load 1 byte; post-index                   */
    crc32cb w2, w2, w3          /* CRC32C on 8-bit operand                   */
    sub     w1, w1, #1
    b       .Lbyte_loop_a64

.Ldone_a64:
    mvn     w0, w2              /* ~crc = final result                       */
    ret
    .size vos_crc32c_hw_a64, .-vos_crc32c_hw_a64

#endif /* __ARM_FEATURE_CRC32 */

/* ─────────────────────────────────────────────────────────────────────────
   vos_rdcnt_a64 — Read system counter without syscall (T25)
   Prototype : u64 vos_rdcnt_a64(void)
   Returns   : x0 = CNTVCT_EL0 (monotonic, 64-bit, no kernel involvement)
   Cost      : 1 MRS instruction ≈ 5 cycles on Cortex-A55
   Barrier   : ISB before read guarantees ordering with preceding stores.
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_rdcnt_a64
    .hidden vos_rdcnt_a64
    .type   vos_rdcnt_a64, %function
vos_rdcnt_a64:
    isb                         /* flush pipeline; fence before timer read   */
    mrs     x0, cntvct_el0      /* 0xDE00 = CNTVCT_EL0 system register       */
    ret
    .size vos_rdcnt_a64, .-vos_rdcnt_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_q16_mul_a64 — Q16.16 signed multiply (T09 + T21 FMA-class)
   Prototype : u32 vos_q16_mul_a64(u32 a, u32 b)
   Args      : w0 = a (Q16.16), w1 = b (Q16.16)
   Returns   : w0 = (a × b) >> 16, Q16.16 result
   Instruction: SMULL = signed multiply long (32×32→64), then SSHR 16.
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_q16_mul_a64
    .hidden vos_q16_mul_a64
    .type   vos_q16_mul_a64, %function
vos_q16_mul_a64:
    smull   x0, w0, w1          /* x0 = sign-extended(w0) × sign-extended(w1)*/
    asr     x0, x0, #16         /* arithmetic right shift 16 → Q16 result    */
    ret
    .size vos_q16_mul_a64, .-vos_q16_mul_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_csel_u32_a64 — Branchless 32-bit select (T11)
   Prototype : u32 vos_csel_u32_a64(u32 a, u32 b, u32 cond)
   Args      : w0 = a, w1 = b, w2 = cond (0 = false)
   Returns   : w0 = a if cond != 0, else b
   Maps to   : CSEL (1 cycle, no branch, no prediction unit involvement)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_csel_u32_a64
    .hidden vos_csel_u32_a64
    .type   vos_csel_u32_a64, %function
vos_csel_u32_a64:
    cmp     w2, #0              /* set flags: ZF=1 if cond==0                */
    csel    w0, w0, w1, ne      /* w0 = (cond≠0) ? a : b  — 1 cycle         */
    ret
    .size vos_csel_u32_a64, .-vos_csel_u32_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_memzero_n_a64 — NEON 128-bit zeroing (T08)
   Prototype : void vos_memzero_n_a64(void *dst, u32 len)
   Args      : x0 = dst (must be 16-byte aligned), w1 = len (multiple of 16)
   Throughput: 16 bytes/cycle via STR Q (128-bit NEON store)
   Use case  : arena reset, zero-init BSS pools T05
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_memzero_n_a64
    .hidden vos_memzero_n_a64
    .type   vos_memzero_n_a64, %function
vos_memzero_n_a64:
    cbz     w1, .Lzero_done_a64
    eor     v0.16b, v0.16b, v0.16b /* v0 = 0x00×16; zero NEON register       */
.Lzero_loop_a64:
    str     q0, [x0], #16      /* store 128 bits; advance pointer            */
    subs    w1, w1, #16
    b.gt    .Lzero_loop_a64
.Lzero_done_a64:
    ret
    .size vos_memzero_n_a64, .-vos_memzero_n_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_phi_hash_a64 — Knuth multiplicative hash (T36)
   Prototype : u32 vos_phi_hash_a64(u64 key, u32 bits)
   Args      : x0 = key (64-bit), w1 = bits (result width 1..31)
   Returns   : w0 = (key × PHI64) >> (64 − bits)
   PHI64     : 0x9E3779B97F4A7C15  (2^64 / φ)
   Dispersal : uniform over [0, 2^bits) without division or modulo.
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_phi_hash_a64
    .hidden vos_phi_hash_a64
    .type   vos_phi_hash_a64, %function
vos_phi_hash_a64:
    mov     x2, #0x7C15                    /* PHI64 low 16                  */
    movk    x2, #0x7F4A, lsl #16           /* PHI64 bits 31:16              */
    movk    x2, #0xB97F, lsl #32           /* PHI64 bits 47:32              */
    movk    x2, #0x9E37, lsl #48           /* PHI64 bits 63:48 = 0x9E3779B9 */
    mul     x0, x0, x2          /* x0 = key × PHI64 (lower 64 bits)         */
    mov     w2, #64
    sub     w1, w2, w1          /* shift = 64 - bits                         */
    lsr     x0, x0, x1          /* x0 >>= (64 - bits) → index in [0,2^bits) */
    ret
    .size vos_phi_hash_a64, .-vos_phi_hash_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_fraf_step_a64 — Single FRAF iteration in Q16 (hot path, no call)
   Prototype : u32 vos_fraf_step_a64(u32 fn_q16)
   Args      : w0 = F_n (Q16.16)
   Returns   : w0 = F_{n+1} (Q16.16)
   Formula   : F_{n+1} = F_n × (√3/2) + 3.102356
   Constants : SCALE=0xDDB4 (56756), OFFSET=0x31A1E (203294)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_fraf_step_a64
    .hidden vos_fraf_step_a64
    .type   vos_fraf_step_a64, %function
vos_fraf_step_a64:
    mov     w1, #0xDDB4         /* FRAF_SCALE_Q16 = 56756 = √3/2 × 2^16     */
    smull   x0, w0, w1          /* x0 = F_n × SCALE (signed 64-bit product)  */
    asr     x0, x0, #16         /* >> 16 → Q16 result                        */
    mov     w1, #0x1A1Eu        /* OFFSET low 16 bits: 0x31A1E = 203294      */
    movk    w1, #0x0003, lsl #16
    add     w0, w0, w1          /* + OFFSET_Q16                              */
    ret
    .size vos_fraf_step_a64, .-vos_fraf_step_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_lyapunov_verify_a64 — Verify Lyapunov exponent sign (A.3)
   Prototype : u32 vos_lyapunov_verify_a64(void)
   Returns   : w0 = 1 if λ < 0 (stable), 0 if ≥ 0 (unstable)
   Method    : |ln(FRAF_SCALE/Q16_ONE)| sign check via integer comparison.
   SCALE < ONE → |SCALE/ONE| < 1 → ln < 0 → stable.
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_lyapunov_verify_a64
    .hidden vos_lyapunov_verify_a64
    .type   vos_lyapunov_verify_a64, %function
vos_lyapunov_verify_a64:
    mov     w0, #0xDDB4         /* FRAF_SCALE_Q16 = 56756                    */
    mov     w1, #0x10000        /* VOS_Q16_ONE   = 65536                     */
    cmp     w0, w1              /* SCALE < ONE?                              */
    cset    w0, lo              /* w0 = 1 if SCALE < ONE (unsigned less)     */
    ret
    .size vos_lyapunov_verify_a64, .-vos_lyapunov_verify_a64

/* ─────────────────────────────────────────────────────────────────────────
   vos_gcd_probe_a64 — GCD check for toroidal invariant A.1
   Prototype : u32 vos_gcd_probe_a64(u32 a, u32 b)
   Args      : w0 = Δr, w1 = R
   Returns   : w0 = gcd(Δr, R): must equal 1 for geometric justice
   Algorithm : Binary GCD (Stein's), O(log²n), no division instruction.
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_gcd_probe_a64
    .hidden vos_gcd_probe_a64
    .type   vos_gcd_probe_a64, %function
vos_gcd_probe_a64:
    cbz     w0, .Lgcd_ret_b_a64
    cbz     w1, .Lgcd_ret_a_a64

    /* Factor out powers of 2 from each operand. */
    rbit    w2, w0              /* reverse bits to find trailing zeros       */
    clz     w2, w2              /* clz of reversed = ctz of original        */
    rbit    w3, w1
    clz     w3, w3
    lsr     w0, w0, w2          /* w0 = Δr >> ctz(Δr) (remove 2-factors)   */
    lsr     w1, w1, w3          /* w1 = R  >> ctz(R)                        */
    cmp     w2, w3
    csel    w4, w2, w3, lo      /* w4 = min(ctz_a, ctz_b)                   */

.Lgcd_loop_a64:
    cmp     w0, w1
    b.eq    .Lgcd_done_a64
    b.lt    .Lgcd_swap_a64
    sub     w0, w0, w1
    rbit    w2, w0
    clz     w2, w2
    lsr     w0, w0, w2
    b       .Lgcd_loop_a64
.Lgcd_swap_a64:
    sub     w1, w1, w0
    rbit    w2, w1
    clz     w2, w2
    lsr     w1, w1, w2
    b       .Lgcd_loop_a64

.Lgcd_done_a64:
    lsl     w0, w0, w4          /* restore common factor of 2               */
    ret
.Lgcd_ret_a_a64:
    ret
.Lgcd_ret_b_a64:
    mov     w0, w1
    ret
    .size vos_gcd_probe_a64, .-vos_gcd_probe_a64

#endif /* __aarch64__ */
