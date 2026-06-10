/* ═══════════════════════════════════════════════════════════════════════════
   rmr_vectra_os_riscv64.S — VECTRA_OS RISC-V 64 Hardware Primitives
   ─────────────────────────────────────────────────────────────────────────
   Zero-abstraction, register-direct, no stub, no libc.
   All primitives: RISC-V psABI leaf functions (a0..a7 args, a0 return).
   Base ISA: RV64I.  Timer: rdtime CSR (available at U-mode on Linux).
   ═══════════════════════════════════════════════════════════════════════════ */

#if defined(__riscv) && (__riscv_xlen == 64)
    .text
    .balign 4

/* ─────────────────────────────────────────────────────────────────────────
   vos_rdtime_rv64 — Read TIME CSR (T28 zero-syscall timer)
   Prototype : u64 vos_rdtime_rv64(void)
   Returns   : a0 = time CSR (monotonic 64-bit counter)
   Requirement: Linux userspace (time CSR exposed at U-mode via vDSO).
   ───────────────────────────────────────────────────────────────────────── */
    /* .hidden: gc-sections removes RISC-V section when not target arch. */
    .global vos_rdtime_rv64
    .hidden vos_rdtime_rv64
    .type   vos_rdtime_rv64, @function
vos_rdtime_rv64:
    rdtime  a0                  /* CSR 0xC01: userspace-accessible time      */
    ret
    .size vos_rdtime_rv64, .-vos_rdtime_rv64

/* ─────────────────────────────────────────────────────────────────────────
   vos_csel_u32_rv64 — Branchless 32-bit select (T11)
   Prototype : u32 vos_csel_u32_rv64(u32 a, u32 b, u32 cond)
   Args      : a0 = a, a1 = b, a2 = cond
   Returns   : a0 = a if cond != 0, else b
   Method    : arithmetic mask: mask = -(cond != 0)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_csel_u32_rv64
    .hidden vos_csel_u32_rv64
    .type   vos_csel_u32_rv64, @function
vos_csel_u32_rv64:
    /* mask = -(a2 != 0): snez gives 0 or 1; negation gives 0 or -1 (0xFFF...) */
    snez    t0, a2              /* t0 = (a2 != 0) ? 1 : 0                   */
    neg     t0, t0              /* t0 = 0xFFFFFFFFFFFFFFFF if cond, else 0   */
    and     t1, a0, t0          /* t1 = a & mask                             */
    not     t0, t0              /* t0 = ~mask                                */
    and     t0, a1, t0          /* t0 = b & ~mask                            */
    or      a0, t1, t0          /* a0 = (a & mask) | (b & ~mask)            */
    ret
    .size vos_csel_u32_rv64, .-vos_csel_u32_rv64

/* ─────────────────────────────────────────────────────────────────────────
   vos_q16_mul_rv64 — Q16.16 signed multiply (T09)
   Prototype : u32 vos_q16_mul_rv64(u32 a, u32 b)
   Args      : a0 = a (Q16.16, sign-extended), a1 = b (Q16.16)
   Returns   : a0 = (a × b) >> 16 (Q16.16)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_q16_mul_rv64
    .hidden vos_q16_mul_rv64
    .type   vos_q16_mul_rv64, @function
vos_q16_mul_rv64:
    /* Sign-extend 32-bit inputs to 64-bit for correct signed multiply. */
    sext.w  a0, a0              /* a0 = sign_extend_32(a0)                  */
    sext.w  a1, a1              /* a1 = sign_extend_32(a1)                  */
    mul     a0, a0, a1          /* a0 = a × b (64-bit product)              */
    srai    a0, a0, 16          /* arithmetic right shift 16 → Q16 result   */
    ret
    .size vos_q16_mul_rv64, .-vos_q16_mul_rv64

/* ─────────────────────────────────────────────────────────────────────────
   vos_phi_hash_rv64 — Knuth multiplicative hash (T36)
   Prototype : u32 vos_phi_hash_rv64(u64 key, u32 bits)
   Args      : a0 = key (64-bit), a1 = bits
   Returns   : a0 = (key × PHI64) >> (64 - bits)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_phi_hash_rv64
    .hidden vos_phi_hash_rv64
    .type   vos_phi_hash_rv64, @function
vos_phi_hash_rv64:
    /* Load PHI64 = 0x9E3779B97F4A7C15 in two 32-bit halves + shift. */
    li      t0, 0x9E3779B9          /* upper 32 bits of PHI64               */
    slli    t0, t0, 32
    li      t1, 0x7F4A7C15          /* lower 32 bits of PHI64               */
    or      t0, t0, t1              /* t0 = PHI64                            */
    mul     a0, a0, t0              /* a0 = key × PHI64 (lower 64 bits)     */
    li      t1, 64
    sub     t1, t1, a1              /* t1 = 64 - bits                        */
    srl     a0, a0, t1              /* a0 >>= (64 - bits) → index            */
    ret
    .size vos_phi_hash_rv64, .-vos_phi_hash_rv64

/* ─────────────────────────────────────────────────────────────────────────
   vos_fraf_step_rv64 — Single FRAF iteration in Q16 (T37)
   Prototype : u32 vos_fraf_step_rv64(u32 fn_q16)
   Args      : a0 = F_n (Q16.16)
   Returns   : a0 = F_{n+1} (Q16.16)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_fraf_step_rv64
    .hidden vos_fraf_step_rv64
    .type   vos_fraf_step_rv64, @function
vos_fraf_step_rv64:
    li      t0, 0xDDB4          /* FRAF_SCALE_Q16 = 56756                   */
    sext.w  a0, a0              /* sign-extend a0 to 64-bit                 */
    mul     a0, a0, t0          /* a0 = F_n × SCALE (64-bit product)        */
    srai    a0, a0, 16          /* >> 16 → Q16 result                       */
    li      t0, 0x31A1E         /* FRAF_OFFSET_Q16 = 203294                 */
    add     a0, a0, t0          /* + OFFSET_Q16                             */
    ret
    .size vos_fraf_step_rv64, .-vos_fraf_step_rv64

#endif /* __riscv && __riscv_xlen == 64 */
