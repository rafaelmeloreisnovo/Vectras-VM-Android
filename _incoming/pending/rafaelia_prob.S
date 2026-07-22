@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "☯ [PROB-JUMP] Salto de Paridade Ativo\n"

.text
.global _start
_start:
    @ --- SEED INICIAL ---
    movw r3, #0x6F41
    movt r3, #0x1EDC
    vdup.32 q15, r3

    ldr r4, =_M
    movw r6, #6400

.L_VORTEX:
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ Paridade Cruzada 4x4 -> 8x8
    veor q8, q0, q1
    veor q9, q2, q3
    vrev32.8 q10, q8     @ Rotação de fase
    veor q15, q15, q10   @ Acúmulo de probabilidade
    veor q15, q15, q9

    subs r6, r6, #1
    bne .L_VORTEX

    @ --- O SALTO DE PROBABILIDADE (BYPASS VIDEO BUFFER) ---
    vmov r0, r1, d30     @ Extrai o DNA da execução
    tst r0, #1           @ Testa o bit de paridade (LDR/STR probability)
    
    @ Se o bit 0 for 1, o próximo passo é uma "Invasão" de Campo
    @ Se for 0, é uma "Entrega" de Campo
    beq .L_INVASAO
    
.L_ENTREGA:
    @ Fluxo A: Estabilidade
    mov r2, #38
    b .L_EXIT

.L_INVASAO:
    @ Fluxo B: Mutação (Aqui você poderia alterar o comportamento do próximo ciclo)
    mov r2, #38

.L_EXIT:
    dmb ish
    mov r0, #1
    ldr r1, =_S
    mov r7, #4
    svc #0
    mov r0, #0
    mov r7, #1
    svc #0
