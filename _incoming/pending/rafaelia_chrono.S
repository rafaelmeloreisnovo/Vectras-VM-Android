@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096
_F: .asciz "rafaelia.bin"
_C: .float 0.95
_D: .float 0.5
_S: .ascii "☯ [SINCRO] Latência: "
_N: .ascii " ns\n"

.text
.global _start
_start:
    sub sp, sp, #16

    @ --- T1 ---
    mov r0, #1
    mov r1, sp
    mov r7, #263
    svc #0
    ldr r10, [sp, #4]

    @ --- NÚCLEO ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]
    ldr r0, =_D
    vld1.32 {d18[0]}, [r0]
    vdup.32 q9, d18[0]

    ldr r4, =_M
    mov r6, #256
.L_CORE:
    vld1.32 {q0}, [r4]
    vmul.f32 q0, q0, q8
    vrev64.32 q1, q0
    vadd.f32 q0, q0, q1
    vmul.f32 q0, q0, q9
    vst1.32 {q0}, [r4]!
    subs r6, r6, #1
    bne .L_CORE

    @ --- T2 ---
    mov r0, #1
    mov r1, sp
    mov r7, #263
    svc #0
    ldr r11, [sp, #4]

    @ Delta: r2 = r11 - r10
    subs r2, r11, r10

    @ --- FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #21
    mov r7, #4
    svc #0

    @ CORREÇÃO: Carregar 50000 usando movw (16-bit)
    movw r3, #50000
    cmp r2, r3
    
    @ Se r2 < 50000, imprimimos o sufixo de sucesso
    ldr r1, =_N
    mov r2, #4
    mov r0, #1
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
