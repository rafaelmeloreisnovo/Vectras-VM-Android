@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 819200
_SEED: .word 0xDEADC0DE
_S: .ascii "\n☯ [8-PARALELO] XOR Integrado: ESTÁVEL\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #6400
    
    ldr r0, =_SEED
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]     @ Semente nos 8 caminhos (16-bit) ou 4 (32-bit)

.L_8WAY_LOOP:
    @ CARGA PARALELA 128-bit
    vld1.8 {q0, q1}, [r4]

    @ --- O XOR NOS 8 CAMINHOS ---
    @ Aqui o SIMD age: 1 instrução, 16 resultados de 8-bits
    veor q8, q0, q15        @ O XOR "atravessa" o dado
    vrev32.8 q9, q8         @ Inversão de paridade nos 8 blocos
    
    @ Reintegração para evitar a bifurcação de erro
    vadd.i32 q0, q8, q9     @ Fusão dos caminhos paralelos
    
    vst1.8 {q0, q1}, [r4]!

    subs r6, r6, #1
    bne .L_8WAY_LOOP

    @ SINCRONIA DE FLUXO (O Freio)
    dmb ish
    isb

    mov r0, #1
    ldr r1, =_S
    mov r2, #41
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
