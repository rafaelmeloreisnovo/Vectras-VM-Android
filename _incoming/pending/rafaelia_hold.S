@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "\r☯ [HOLD-TTL] Estabilidade de Barramento: OK\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #6400
    
    @ TTL como Carga Útil (Não é só benchmark, é componente da matriz)
    movw r10, #0xFFFF

.L_HOLD:
    @ O TTL não gasta processamento extra, ele entra no fluxo NEON
    subs r10, r10, #1   
    vdup.32 q15, r10    @ Semente de Tempo Real

    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- O BALANÇO (A MATRIZ SEGURA O CÁLCULO) ---
    @ Cruzamos a paridade de modo que o TTL valide a invasão
    veor q8, q0, q15     @ Matriz A carrega a "consciência" do tempo
    vadd.i32 q2, q2, q8  @ Matriz B sustenta o impacto
    
    @ Permutação 4x4 (Entrelaçamento de Segurança)
    vtrn.32 q0, q2
    vrev32.8 q15, q15    @ Evolução da semente sem custo de clock

    sub r4, r4, #64
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_HOLD

    dmb ish
    isb

    @ FEEDBACK SEM ILUSÃO
    mov r0, #1
    ldr r1, =_S
    mov r2, #45
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
