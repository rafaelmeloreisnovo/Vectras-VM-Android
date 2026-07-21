// SPDX-License-Identifier: GPL-2.0-only
/* ═══════════════════════════════════════════════════════════════════════════
   rmr_vectra_os_armv7.S — VECTRA_OS ARMv7 Hardware Primitives
   ─────────────────────────────────────────────────────────────────────────
   Zero-abstraction, register-direct, no stub, no libc.
   All primitives: AAPCS32 leaf functions (r0..r3 args, r0 return).
   ARMv7-A: NEON/VFP available on Cortex-A7/A15/A53-32 etc.
   ═══════════════════════════════════════════════════════════════════════════ */

#if defined(__arm__) && !defined(__aarch64__)
    .arch armv7-a
    .fpu neon
    .text
    .balign 4

/* ─────────────────────────────────────────────────────────────────────────
   vos_rdcnt_av7 — Read PMCCNTR (performance monitor cycle counter) (T25)
   Prototype : u64 vos_rdcnt_av7(void)
   Returns   : r1:r0 = 64-bit cycle count (high:low — AAPCS32 u64 return)
   Requirement: Linux userspace (PMUSERENR.EN = 1 set by kernel).
   ───────────────────────────────────────────────────────────────────────── */
    /* .hidden: gc-sections eliminates this section if pmccntr path unused. */
    .global vos_rdcnt_av7
    .hidden vos_rdcnt_av7
    .type   vos_rdcnt_av7, %function
vos_rdcnt_av7:
    /* PMCCNTR: MRC p15, 0, Rd, c9, c13, 0 */
    mrc     p15, 0, r0, c9, c13, 0   /* r0 = PMCCNTR (32-bit cycle counter) */
    mov     r1, #0                    /* high 32 bits = 0 (32-bit counter)  */
    bx      lr
    .size vos_rdcnt_av7, .-vos_rdcnt_av7

/* ─────────────────────────────────────────────────────────────────────────
   vos_csel_u32_av7 — Branchless 32-bit select (T11)
   Prototype : u32 vos_csel_u32_av7(u32 a, u32 b, u32 cond)
   Args      : r0 = a, r1 = b, r2 = cond
   Returns   : r0 = a if cond != 0, else b
   Instruction: MOVcc (conditional move, 1 cycle, no prediction cost)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_csel_u32_av7
    .hidden vos_csel_u32_av7
    .type   vos_csel_u32_av7, %function
vos_csel_u32_av7:
    cmp     r2, #0              /* set ZF if cond == 0                       */
    movne   r0, r0              /* if cond≠0: r0 = a (already in r0)        */
    moveq   r0, r1              /* if cond==0: r0 = b                        */
    bx      lr
    .size vos_csel_u32_av7, .-vos_csel_u32_av7

/* ─────────────────────────────────────────────────────────────────────────
   vos_q16_mul_av7 — Q16.16 signed multiply (T09)
   Prototype : u32 vos_q16_mul_av7(u32 a, u32 b)
   Args      : r0 = a (Q16.16), r1 = b (Q16.16)
   Returns   : r0 = (a × b) >> 16 (Q16.16)
   Instruction: SMULL (signed long multiply) → r1:r0, shift right 16.
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_q16_mul_av7
    .hidden vos_q16_mul_av7
    .type   vos_q16_mul_av7, %function
vos_q16_mul_av7:
    smull   r0, r1, r0, r1      /* r1:r0 = sign(r0) × sign(r1) (64-bit)    */
    /* Right-shift 64-bit result by 16 into r0. */
    lsr     r0, r0, #16         /* r0 >>= 16 (low bits)                     */
    orr     r0, r0, r1, lsl #16 /* r0 |= r1 << 16 (high bits fill in)       */
    bx      lr
    .size vos_q16_mul_av7, .-vos_q16_mul_av7

/* ─────────────────────────────────────────────────────────────────────────
   vos_memzero_n_av7 — NEON 64-bit zero fill (T08)
   Prototype : void vos_memzero_n_av7(void *dst, u32 len)
   Args      : r0 = dst (8-byte aligned), r1 = len (multiple of 8)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_memzero_n_av7
    .hidden vos_memzero_n_av7
    .type   vos_memzero_n_av7, %function
vos_memzero_n_av7:
    cmp     r1, #0
    bxeq    lr
    vmov.i8 d0, #0              /* d0 = 0x00 × 8 (64-bit NEON register)     */
.Lzero_loop_av7:
    vst1.8  {d0}, [r0]!         /* store 8 bytes, advance r0                */
    subs    r1, r1, #8
    bgt     .Lzero_loop_av7
    bx      lr
    .size vos_memzero_n_av7, .-vos_memzero_n_av7

/* ─────────────────────────────────────────────────────────────────────────
   vos_fraf_step_av7 — Single FRAF iteration in Q16 (T37)
   Prototype : u32 vos_fraf_step_av7(u32 fn_q16)
   Args      : r0 = F_n (Q16.16)
   Returns   : r0 = F_{n+1} (Q16.16)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_fraf_step_av7
    .hidden vos_fraf_step_av7
    .type   vos_fraf_step_av7, %function
vos_fraf_step_av7:
    /* r1 = FRAF_SCALE_Q16 = 0xDDB4 = 56756 */
    mov     r1, #0xDB00
    orr     r1, r1, #0xB4       /* r1 = 0xDDB4                              */
    smull   r0, r2, r0, r1      /* r2:r0 = F_n × SCALE                      */
    lsr     r0, r0, #16
    orr     r0, r0, r2, lsl #16 /* r0 = (F_n × SCALE) >> 16 (Q16 result)   */
    /* r1 = FRAF_OFFSET_Q16 = 0x31A1E = 203294 */
    mov     r1, #0x1E00
    orr     r1, r1, #0x1A       /* 0x1A1E */
    orr     r1, r1, #0x30000    /* 0x31A1E */
    add     r0, r0, r1          /* + OFFSET_Q16                             */
    bx      lr
    .size vos_fraf_step_av7, .-vos_fraf_step_av7

#endif /* __arm__ && !__aarch64__ */
