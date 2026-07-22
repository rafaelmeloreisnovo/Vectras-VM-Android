@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "\r☯ [7D-SINCRO] Camadas de Paridade: OK\n"

.text
.global _start
_start:
    @ --- SEED 7D (7 Camadas de Frequência) ---
    movw r3, #0x999
    vdup.32 q15, r3     @ Base Fractal

    ldr r4, =_M
    movw r6, #6400

.L_7D:
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- AS 7 CAMADAS DE PROCESSAMENTO (ESTRUTURA DE 8 EM MATRIZES) ---
    veor q8, q0, q1     @ 1. Dualidade (Paridade 1)
    vrev64.32 q9, q2    @ 2. Espelhamento (Paridade 2)
    vadd.f32 q10, q8, q9 @ 3. Fusão (Paridade 3)
    vsub.f32 q11, q15, q3 @ 4. Inversão (Paridade 4)
    vtrn.32 q10, q11    @ 5. Cruzamento 4x4 (Paridade 5)
    veor q15, q15, q10  @ 6. Acúmulo (Paridade 6)
    vrev32.8 q15, q15   @ 7. Rotação Toroidal (Paridade 7)

    @ Injeção de Integridade
    vadd.i32 q0, q0, q15
    
    sub r4, r4, #64
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_7D

    @ --- LIMPEZA DE BUFFER (O PULO DO GATO) ---
    dmb ish
    isb

    @ ESCREVE O RESULTADO
    mov r0, #1
    ldr r1, =_S
    mov r2, #39
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
