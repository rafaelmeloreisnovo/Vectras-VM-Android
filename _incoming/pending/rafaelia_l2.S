@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600   @ 400KB - Força uso da L2
_C: .float 0.95
_D: .float 0.5
_S: .ascii "☯ [L2-FLUXO] Latência Estável\n"

.text
.global _start
_start:
    @ --- NÚCLEO L2 ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]
    ldr r0, =_D
    vld1.32 {d18[0]}, [r0]
    vdup.32 q9, d18[0]

    ldr r4, =_M
    movw r6, #25600     @ 25600 blocos de 16 bytes = 400KB
.L_L2:
    pld [r4, #256]      @ Prefetch agressivo para L2
    vld1.32 {q0}, [r4]
    vmul.f32 q0, q0, q8
    vrev64.32 q1, q0
    vadd.f32 q0, q0, q1
    vmul.f32 q0, q0, q9
    vst1.32 {q0}, [r4]!
    subs r6, r6, #1
    bne .L_L2

    @ --- FEEDBACK ---
    mov r0, #1          @ stdout
    ldr r1, =_S
    mov r2, #32         @ Tamanho exato para evitar truncamento
    mov r7, #4          @ sys_write
    svc #0

    mov r0, #0
    mov r7, #1          @ sys_exit
    svc #0
