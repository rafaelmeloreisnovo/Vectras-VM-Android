@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "☯ [FRACTAL-4x4] Paridade Cruzada Ativa\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #6400      @ Processamos em chunks de 64 bytes (4x q-regs)

.L_FRACTAL:
    @ --- CARGA DA MATRIZ ENTRELAÇADA ---
    vld1.32 {q0, q1}, [r4]!  @ Bloco A e B
    vld1.32 {q2, q3}, [r4]!  @ Bloco C e D (Paridade)

    @ --- PERMUTAÇÃO CRUZADA (O CROSSOVER) ---
    vtrn.32 q0, q2           @ Cruza elementos entre A e C
    vtrn.32 q1, q3           @ Cruza elementos entre B e D
    
    @ --- PROCESSAMENTO DOS FRACTAIS ---
    @ Alguns ficam (acumulam), outros saem (stale)
    veor q0, q0, q3          @ Paridade cruzada entre extremos
    vadd.f32 q1, q1, q2      @ Fusão dos meios
    
    @ --- RE-MONTAGEM (BACK TO RAM) ---
    sub r4, r4, #64          @ Volta o ponteiro para re-gravar no lugar
    vst1.32 {q0, q1}, [r4]!
    vst1.32 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_FRACTAL

    @ --- SINCRONIA E SAÍDA ---
    dmb ish
    mov r0, #1
    ldr r1, =_S
    mov r2, #39
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
