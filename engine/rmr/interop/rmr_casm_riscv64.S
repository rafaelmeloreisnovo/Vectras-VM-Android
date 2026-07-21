// SPDX-License-Identifier: GPL-2.0-only
/* ═══════════════════════════════════════════════════════
   rmr_casm_riscv64.S — RMR CASM BRIDGE RISCV64
   Ficheiro FALTANDO no zip original — adicionado aqui.
   RISC-V 64-bit base ISA (RV64I sem extensões obrigatórias)
   ═══════════════════════════════════════════════════════ */

.section .text
.option pic

/* ─── rmr_casm_bridge_marker ─────────────────────────── */
.global rmr_casm_bridge_marker
.type   rmr_casm_bridge_marker, @function
rmr_casm_bridge_marker:
    /* "RVAS" = 0x52564153 — RISC-V marker */
    li      a0, 0x52564153
    ret
.size rmr_casm_bridge_marker, .-rmr_casm_bridge_marker

/* ─── rmr_casm_xor_fold32_riscv64 ────────────────────── */
.global rmr_casm_xor_fold32_riscv64
.type   rmr_casm_xor_fold32_riscv64, @function
rmr_casm_xor_fold32_riscv64:
    /* a0=ptr, a1=len */
    beqz    a0, .Lrv_zero
    beqz    a1, .Lrv_zero

    li      t0, 0               /* accumulator */
    li      t1, 0               /* index */

    /* 4-byte chunks: build little-endian lanes from bytes so generic
       const uint8_t * callers never require word alignment on RV64. */
.Lrv_word:
    addi    t2, t1, 4
    bgt     t2, a1, .Lrv_tail
    add     t3, a0, t1
    lbu     t4, 0(t3)
    lbu     t5, 1(t3)
    slli    t5, t5, 8
    or      t4, t4, t5
    lbu     t5, 2(t3)
    slli    t5, t5, 16
    or      t4, t4, t5
    lbu     t5, 3(t3)
    slli    t5, t5, 24
    or      t4, t4, t5
    xor     t0, t0, t4
    addi    t1, t1, 4
    j       .Lrv_word

.Lrv_tail:
    bge     t1, a1, .Lrv_done
    add     t3, a0, t1
    lbu     t3, 0(t3)
    andi    t4, t1, 3
    slli    t4, t4, 3
    sll     t3, t3, t4
    xor     t0, t0, t3
    addi    t1, t1, 1
    j       .Lrv_tail

.Lrv_done:
    /* trunca para 32 bits */
    slli    t0, t0, 32
    srli    t0, t0, 32
    mv      a0, t0
    ret

.Lrv_zero:
    li      a0, 0
    ret
.size rmr_casm_xor_fold32_riscv64, .-rmr_casm_xor_fold32_riscv64

.macro rmr_mul_phi_rv64i dst, src, acc, tmp
    /* 0x9E3779B9 = 2^32 - 0x61C88647 */
    slli    \dst, \src, 32

    mv      \acc, \src
    slli    \tmp, \src, 1
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 2
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 6
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 9
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 10
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 15
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 19
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 22
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 23
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 24
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 29
    add     \acc, \acc, \tmp
    slli    \tmp, \src, 30
    add     \acc, \acc, \tmp

    sub     \dst, \dst, \acc
.endm

/* ─── rmr_casm_phi_step_riscv64 ─────────────────────── */
.global rmr_casm_phi_step_riscv64
.type   rmr_casm_phi_step_riscv64, @function
rmr_casm_phi_step_riscv64:
    /* a0=state, a1=coherence */
    /* rotina RV64I: evita extensão M por compatibilidade ISA */
    mv      t2, a0
    mv      t3, a1
    rmr_mul_phi_rv64i a0, t2, t0, t1
    rmr_mul_phi_rv64i a1, t3, t0, t1
    slli    a1, a1, 32          /* equivalência de mulw (sign-extend do low32) */
    srai    a1, a1, 32
    xor     a0, a0, a1
    bnez    a0, .Lphi_ok_rv
    li      a0, 1
.Lphi_ok_rv:
    ret
.size rmr_casm_phi_step_riscv64, .-rmr_casm_phi_step_riscv64

.section .note.GNU-stack,"",@progbits
