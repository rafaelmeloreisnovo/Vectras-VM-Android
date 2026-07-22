@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "\n☯ [FIX-SINCRO] Sustentação Contígua: OK\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #6400
    
    @ TTL como Âncora (q15)
    movw r10, #0xFFFF
    vdup.32 q15, r10

.L_SANKOFA:
    @ O decremento do TTL é o nosso relógio passivo
    subs r10, r10, #1
    
    @ Carga contígua para evitar erro de range
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- GEOMETRIA DE SUSTENTAÇÃO (4x4 -> 8x8) ---
    @ Cruzamento de paridade usando o TTL (q15)
    veor q0, q0, q15     @ Matriz A absorve o tempo
    veor q1, q1, q2      @ Matriz B sustenta o cruzamento
    
    @ Permutação para fechar o Quadrado Perfeito
    vtrn.32 q0, q1       @ Intercalação contígua
    vrev32.8 q15, q15    @ Evolução da semente

    @ Gravação Contígua (Voltamos 64 bytes para sobrescrever com integridade)
    sub r4, r4, #64
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_SANKOFA

    @ --- BARREIRA DE INÉRCIA ---
    dmb ish
    isb

    @ Saída para o barramento
    mov r0, #1
    ldr r1, =_S
    mov r2, #41
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
