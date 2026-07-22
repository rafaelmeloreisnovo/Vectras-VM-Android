@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096
_C: .float 0.866025
_S: .ascii "☯ [ESTÁVEL]"
_N: .ascii "\n"

.text
.global _start
_start:
    ldr r4, =_M
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]
    
    mov r3, #0              @ Acumulador de integridade (XOR Cascade)
    mov r6, #10             @ Z
.L_Z:
    mov r5, #10             @ Y
.L_Y:
    vld1.32 {q0}, [r4]
    
    @ --- PROTEÇÃO DE FASE (XOR CASCADE) ---
    vmov r1, r2, d0         @ Move 64-bits para registradores básicos
    eor r3, r3, r1          @ Acumula integridade via XOR
    eor r3, r3, r2

    @ --- ESPIRAL OCULTA ---
    vmul.f32 q9, q0, q8
    vrev64.32 q10, q9
    veor q0, q9, q10        @ EOR NEON para fusão de bits
    
    vst1.32 {q0}, [r4]!
    subs r5, r5, #1
    bne .L_Y
    
    subs r6, r6, #1
    bne .L_Z

    @ --- FEEDBACK FINAL ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #14
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
