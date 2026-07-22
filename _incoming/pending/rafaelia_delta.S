@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096
_F: .asciz "rafaelia.bin"
_C: .float 1.618034
_S: .ascii "☯ ∆Σ: "
_P: .ascii "+"
_N: .ascii "\n"

.text
.global _start
_start:
    @ --- 1. INALAR ---
    ldr r0, =_F
    mov r1, #2
    mov r7, #5
    svc #0
    mov r8, r0

    ldr r1, =_M
    mov r2, #4096
    mov r7, #3
    svc #0

    @ --- 2. AUDITORIA E EVOLUÇÃO ---
    ldr r4, =_M
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]  @ PHI
    vmov.i32 q15, #0    @ Zerar acumulador Delta Sigma
    
    mov r6, #256
.L_DELTA:
    vld1.32 {q0}, [r4]  @ Estado Atual (Ontem)
    vmul.f32 q1, q0, q8 @ Evolução (Agora)
    
    @ Calcular Diferença (Distância Vetorial)
    vsub.f32 q2, q1, q0
    vadd.f32 q15, q15, q2 @ Acumula o Delta em q15
    
    vst1.32 {q1}, [r4]!
    subs r6, r6, #1
    bne .L_DELTA

    @ --- 3. EXALAR ---
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

    @ --- 4. FEEDBACK DO PULSO ---
    @ Imprime o prefixo
    mov r0, #1
    ldr r1, =_S
    mov r2, #8
    mov r7, #4
    svc #0

    @ Imprime um '+' para cada nível de crescimento detectado em q15
    vmov r1, r2, d30    @ Move parte do Delta para r1/r2
    lsr r1, r1, #24     @ Normaliza para escala visível
    mov r0, #1
    ldr r1, =_P
    mov r2, #1
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
