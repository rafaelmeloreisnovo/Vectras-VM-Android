@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096
_F: .asciz "rafaelia.bin"
_C: .float 0.95        @ Fator de Resfriamento (Cooling)
_D: .float 0.5         @ Fator de Dissipação (Halving)
_S: .ascii "☯ [SINCRO] "
_N: .ascii "\n"

.text
.global _start
_start:
    @ --- 1. CONSTANTES DE CAMPO ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]  @ q8 = 0.95
    ldr r0, =_D
    vld1.32 {d18[0]}, [r0]
    vdup.32 q9, d18[0]  @ q9 = 0.5 (Para o Halving manual)

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

    @ --- 3. EQUILÍBRIO TÉRMICO ---
    ldr r4, =_M
    mov r6, #256
.L_EQUITAS:
    vld1.32 {q0}, [r4]
    vmul.f32 q0, q0, q8  @ Resfriamento inicial
    vrev64.32 q1, q0     @ Permutação de calor (L1 High <-> Low)
    
    @ Halving Add Manual: (q0 + q1) * 0.5
    vadd.f32 q0, q0, q1  
    vmul.f32 q0, q0, q9  
    
    vst1.32 {q0}, [r4]!
    subs r6, r6, #1
    bne .L_EQUITAS

    @ --- 4. EXALAR ---
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

    @ --- 5. FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #11
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
