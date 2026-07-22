@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "\n☯ [INVASÃO] Cruzamento de Campo 7D: OK\n"

.text
.global _start
_start:
    @ --- SEED DE INVASÃO ---
    movw r3, #0x777
    vdup.32 q15, r3

    ldr r4, =_M
    movw r6, #3200      @ Processamos blocos duplos para permitir a invasão

.L_INVASION:
    @ Carga de dois blocos adjacentes (Matriz 8x8 expandida)
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- O PROCESSO DE INVASÃO ---
    @ O Bloco A (q0) invade o espaço do Bloco B (q2) via XOR de paridade
    veor q10, q0, q15    @ Camada 1: DNA da semente
    vrev32.8 q11, q10    @ Camada 2: Rotação de fase
    
    @ Invasão física: O resultado do primeiro bloco altera o segundo
    vadd.i32 q2, q2, q11 
    veor q15, q11, q2    @ A semente evolui com a invasão

    @ --- RE-MONTAGEM CRUZADA ---
    sub r4, r4, #64
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_INVASION

    @ --- FREIO DE CAUSALIDADE ---
    dmb ish
    isb

    @ SAÍDA (Com padding para evitar truncamento)
    mov r0, #1
    ldr r1, =_S
    mov r2, #41
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
