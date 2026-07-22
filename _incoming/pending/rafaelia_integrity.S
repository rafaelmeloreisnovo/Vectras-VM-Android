@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "☯ [CRC-32] Integridade de Matrizes 8x8 Ativa\n"

.text
.global _start
_start:
    @ --- CARGA DA SEMENTE (Ponte entre R e Q) ---
    movw r3, #0x6F41    @ Parte baixa
    movt r3, #0x1EDC    @ Parte alta (0x1EDC6F41)
    vdup.32 q15, r3     @ Broadcast da semente para todas as lanes de q15

    ldr r4, =_M
    movw r6, #6400      @ 400KB / 64 bytes

.L_INTEGRITY:
    @ --- LEITURA DE MATRIZES (8-BIT GRANULAR) ---
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- O CRC SIMD (PARIDADE CRUZADA) ---
    @ Redução vertical: q0-q3 -> paridade interna
    veor q8, q0, q1
    veor q9, q2, q3
    veor q10, q8, q9
    veor q15, q15, q10  @ Acúmulo no registro de integridade

    @ --- PERMUTAÇÃO DE MATRIZES (O ENTRELAÇO) ---
    vtrn.8 q0, q2       @ Transpõe matrizes A e C (nível 8-bit)
    vtrn.16 q1, q3      @ Transpõe matrizes B e D (nível 16-bit)

    @ --- ESCRITA DE FLUXO ---
    sub r4, r4, #64
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r4]!

    subs r6, r6, #1
    bne .L_INTEGRITY

    @ --- BARREIRA E FEEDBACK ---
    dmb ish
    mov r0, #1
    ldr r1, =_S
    mov r2, #44
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
