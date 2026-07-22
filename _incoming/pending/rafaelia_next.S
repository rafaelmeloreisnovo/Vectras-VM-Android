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
_S: .ascii "☯ [NEXT-STEP] Fluxo de Intercalação Ativo\n"

.text
.global _start
_start:
    @ --- PREPARAÇÃO ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]  @ Constante de resfriamento
    ldr r0, =_D
    vld1.32 {d18[0]}, [r0]
    vdup.32 q9, d18[0]  @ Constante de dissipação

    ldr r4, =_M
    movw r6, #12800     @ Processamos de 2 em 2 blocos (32 bytes por ciclo)

    @ --- O SALTO: LOOP INTERCALADO (EVITANDO HIT/MISS) ---
.L_NEXT:
    @ Passo 1: Dispara o Preload para 512 bytes à frente (Deep Fetch)
    pld [r4, #512]
    
    @ Passo 2: Processa Bloco A (q0)
    vld1.32 {q0}, [r4]
    vmul.f32 q0, q0, q8
    vrev64.32 q1, q0
    vadd.f32 q0, q0, q1
    vmul.f32 q0, q0, q9
    vst1.32 {q0}, [r4]!

    @ Passo 3: Processa Bloco B (q2) - Enquanto o barramento busca o próximo
    vld1.32 {q2}, [r4]
    vmul.f32 q2, q2, q8
    vrev64.32 q3, q2
    vadd.f32 q2, q2, q3
    vmul.f32 q2, q2, q9
    vst1.32 {q2}, [r4]!

    subs r6, r6, #1
    bne .L_NEXT

    @ --- FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #41
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
