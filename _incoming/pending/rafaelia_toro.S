@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_C: .float 0.95
_D: .float 0.5
_S: .ascii "☯ [TORO] Geometria Circular Ativa\n"

.text
.global _start
_start:
    @ --- PREPARAÇÃO ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]
    ldr r0, =_D
    vld1.32 {d18[0]}, [r0]
    vdup.32 q9, d18[0]

    ldr r4, =_M
    movw r6, #25600
    vmov.i32 q15, #0    @ Vetor de Semente (O elo do Toro)

    @ --- LOOP TOROIDAL ---
.L_TORO:
    vld1.32 {q0}, [r4]
    vadd.f32 q0, q0, q15 @ Funde o rastro do bloco anterior
    
    vmul.f32 q0, q0, q8
    vrev64.32 q1, q0
    vadd.f32 q0, q0, q1
    vmul.f32 q0, q0, q9
    
    vmov q15, q0        @ Preserva o estado para o próximo elo
    vst1.32 {q0}, [r4]!
    
    subs r6, r6, #1
    bne .L_TORO

    @ --- SINCRONIA FINAL ---
    dmb ish
    isb

    @ --- FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #34
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
