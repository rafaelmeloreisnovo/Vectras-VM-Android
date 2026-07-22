@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096     @ Matriz Principal
_C: .float 0.866025 @ Constante Spiral_{sqrt(3)/2}
_S: .ascii "☯"      @ Coerência
_N: .ascii "\n"

.text
.global _start
_start:
    ldr r4, =_M
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]  @ Carrega constante no registrador 'oculto' d16
    vdup.32 q8, d16[0]      @ Espalha para q8 (todas as 4 dimensões)
    
    mov r6, #10             @ Z-Axis
.L_Z:
    mov r5, #10             @ Y-Axis
.L_Y:
    pld [r4, #128]
    
    @ --- DIMENSÃO OCULTA (d17-d31) ---
    vld1.32 {q0}, [r4]      @ q0 = d0,d1 (Visível)
    vmul.f32 q9, q0, q8     @ Processa em q9 (Invisível/Oculto)
    vrev64.32 q10, q9       @ Simetria de espelho
    vadd.f32 q0, q9, q10    @ Fusão de retorno para o Visível
    
    vst1.32 {q0}, [r4]!     @ Grava e avança

    subs r5, r5, #1
    bne .L_Y
    
    subs r6, r6, #1
    bne .L_Z

    @ --- SAÍDA ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #3
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
