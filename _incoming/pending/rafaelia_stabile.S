@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096
_F: .asciz "rafaelia.bin"
_C: .float 1.618034     @ Constante PHI
_L: .float 1.0          @ Limite Superior (Freio)
_S: .ascii "☯ [ESTÁVEL] ∆: "
_N: .ascii "\n"

.text
.global _start
_start:
    @ --- 1. CARGA DE CONSTANTES ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]  @ q8 = PHI
    ldr r0, =_L
    vld1.32 {d17[0]}, [r0]
    vdup.32 q9, d17[0]  @ q9 = 1.0 (Freio)

    @ --- 2. INALAR ---
    ldr r0, =_F
    mov r1, #2
    mov r7, #5
    svc #0
    mov r8, r0
    ldr r1, =_M
    mov r2, #4096
    mov r7, #3
    svc #0

    @ --- 3. EVOLUÇÃO COM FREIO (VMIN) ---
    ldr r4, =_M
    mov r6, #256
.L_FRAME:
    vld1.32 {q0}, [r4]
    vmul.f32 q1, q0, q8  @ Tenta evoluir
    vmin.f32 q1, q1, q9  @ FREIO: Trunca em 1.0 se ultrapassar
    
    vst1.32 {q1}, [r4]!
    subs r6, r6, #1
    bne .L_FRAME

    @ --- 4. EXALAR (CRISTALIZAÇÃO) ---
    mov r0, r8
    mov r1, #0
    mov r2, #0
    mov r7, #19
    svc #0
    mov r0, r8
    ldr r1, =_M
    mov r2, #4096
    mov r7, #4
    svc #0
    mov r0, r8
    mov r7, #6
    svc #0

    @ --- 5. FEEDBACK LIMPO ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #17
    mov r7, #4
    svc #0
    mov r0, #1
    ldr r1, =_N
    mov r2, #1
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
