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
_S: .ascii "☯ [FLASH-SYNC] Broadcast Ativo\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #25600     @ 400KB

    @ --- O FLASH (BROADCAST DE PLAY) ---
    @ Este loop apenas puxa as linhas de cache para o silício
.L_FLASH:
    pld [r4]            @ Pre-load (Broadcast elétrico)
    add r4, r4, #128    @ Pula 2 linhas de cache por vez
    subs r6, r6, #8     @ Avança o contador em bloco
    bne .L_FLASH

    @ --- RESET DO PONTEIRO ---
    ldr r4, =_M
    movw r6, #25600

    @ --- NÚCLEO DE PROCESSAMENTO (SINCRO) ---
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]
    ldr r0, =_D
    vld1.32 {d18[0]}, [r0]
    vdup.32 q9, d18[0]

.L_PLAY:
    vld1.32 {q0}, [r4]
    vmul.f32 q0, q0, q8
    vrev64.32 q1, q0
    vadd.f32 q0, q0, q1
    vmul.f32 q0, q0, q9
    vst1.32 {q0}, [r4]!
    subs r6, r6, #1
    bne .L_PLAY

    @ --- FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #32
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
