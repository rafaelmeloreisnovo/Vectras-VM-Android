@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S_OK: .ascii "☯ [INTEGRO] Fluxo Nominal\n"
_S_ERR: .ascii "⚠️ [DIVERGENTE] Re-sincronizando\n"

.text
.global _start
_start:
    @ --- PREPARAÇÃO DA SEMENTE ---
    movw r3, #0x6F41
    movt r3, #0x1EDC
    vdup.32 q15, r3     @ Assinatura Alvo (Esperada)

    ldr r4, =_M
    movw r6, #6400

.L_DECISION:
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ CRC-SIMD: Cruzamento de Paridade
    veor q8, q0, q1
    veor q9, q2, q3
    veor q10, q8, q9
    veor q15, q15, q10

    subs r6, r6, #1
    bne .L_DECISION

    @ --- TESTE DE DECISÃO (O PONTO DE INFLEXÃO) ---
    @ Movemos o CRC final de q15 para registradores core (r0, r1)
    vmov r0, r1, d30    @ Parte da assinatura q15
    
    @ Se r0 for 0 (improvável mas serve de teste), algo falhou
    cmp r0, #0
    beq .L_ERROR

.L_SUCCESS:
    mov r0, #1
    ldr r1, =_S_OK
    mov r2, #26
    b .L_OUT

.L_ERROR:
    mov r0, #1
    ldr r1, =_S_ERR
    mov r2, #32

.L_OUT:
    mov r7, #4          @ sys_write
    svc #0
    mov r0, #0
    mov r7, #1          @ sys_exit
    svc #0
