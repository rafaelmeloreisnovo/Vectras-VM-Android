@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "☯ [CRC-SIMD-FIX] Sincronia de Barramento Ativa\n"

.text
.global _start
_start:
    @ --- CARGA DA SEMENTE ---
    movw r3, #0x6F41
    movt r3, #0x1EDC
    vdup.32 q15, r3     @ q15 = Semente Estável

    ldr r4, =_M
    movw r6, #6400

.L_SYNC:
    @ --- LEITURA 8x8 ---
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- EMULAÇÃO DE CRC HW VIA NEON (BIT-REFLECT) ---
    @ Cruzamos a paridade para simular a dispersão do polinômio
    veor q8, q0, q15     @ O estado anterior (q15) ataca o novo bloco
    veor q9, q1, q2
    veor q10, q3, q8
    
    @ Rotação de Paridade (Simula o Shift do CRC)
    vrev32.8 q11, q10    @ Inverte a ordem dos bytes para dispersão
    veor q15, q11, q9    @ Nova assinatura consolidada

    @ --- REMONTAGEM ---
    sub r4, r4, #64
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_SYNC

    @ --- FINALIZAÇÃO ---
    dmb ish
    mov r0, #1
    ldr r1, =_S
    mov r2, #47
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
