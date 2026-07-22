@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4194304
_PHI: .float 1.618034
_T_START: .space 8          @ Estrutura para timespec (sec, nsec)
_T_END: .space 8
_S: .ascii "\n☯ [BENCH-FINAL] Pilar 4MB (Φ + 999) - Sincronia: "
_AB: .ascii " [ABSOLUTO]\n\n"

.text
.global _start
_start:
    @ --- MARCA TEMPO INICIAL ---
    mov r0, #1              @ CLOCK_MONOTONIC
    ldr r1, =_T_START
    mov r7, #263            @ syscall clock_gettime
    svc #0

    ldr r4, =_M
    mov r6, #32768          @ 32k iterações no pilar
    ldr r0, =_PHI
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_PHI_AVALANCHE:
    pld [r4, #2048]
    vld1.8 {q0, q1}, [r4]!
    
    @ --- MOTOR LOG-SENO Φ ---
    vcvt.f32.u32 q8, q0
    vmul.f32 q8, q8, q15    @ Escala de Ouro
    vrev32.8 q9, q8         @ Giro Toroidal
    vabs.f32 q10, q9
    vrecpe.f32 q11, q10     @ Compressão de Vácuo
    vadd.f32 q0, q11, q15   @ Cimento Ético
    
    vst1.8 {q0, q1}, [r4]
    subs r6, r6, #1
    bne .L_PHI_AVALANCHE

    @ --- MARCA TEMPO FINAL ---
    mov r0, #1
    ldr r1, =_T_END
    mov r7, #263
    svc #0

    @ SAÍDA DE STATUS
    mov r0, #1
    ldr r1, =_S
    mov r2, #47
    mov r7, #4
    svc #0

    mov r0, #1
    ldr r1, =_AB
    mov r2, #13
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
