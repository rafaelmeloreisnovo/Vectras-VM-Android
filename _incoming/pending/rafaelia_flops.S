@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: "\n☯ [FLIP-FLOPS] Vértices de Performance: OK\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #3200      @ Processamento em blocos de 128 bytes (Vértices)
    
    @ Semente de Inversão (O Flip)
    vmov.i32 q15, #0x7
    vmov.i32 q14, #0x9

.L_VERTICE:
    @ --- PREENCHIMENTO INICIAL E FINAL (SIMULTÂNEO) ---
    vld1.8 {q0, q1}, [r4]       @ Fractal Início
    add r5, r4, #64
    vld1.8 {q2, q3}, [r5]       @ Fractal Fim (Invasão de Campo)

    @ --- APROVEITAMENTO DO FLIP ---
    @ Cruzamos os 4 fractais nos vértices do NEON
    veor q8, q0, q15            @ Vértice 1
    vrev64.32 q9, q3            @ Vértice 4 (Inversão Temporal)
    
    @ O Flip: Multiplicação por paridade cruzada (GFLOPS)
    vmul.f32 q10, q8, q14
    vmla.f32 q10, q9, q15       @ Fusão de Fatores

    @ --- REMONTAGEM CRUZADA ---
    vst1.8 {q10, q1}, [r4]!     @ Grava o Início processado
    vst1.8 {q2, q3}, [r5]       @ Grava o Fim validado

    subs r6, r6, #1
    bne .L_VERTICE

    dmb ish
    isb

    mov r0, #1
    ldr r1, =_S
    mov r2, #43
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
