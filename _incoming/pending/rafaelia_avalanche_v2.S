@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 819200
_SEED1: .word 0xDEADC0DE
_SEED2: .word 0xFEEDFACE
_S: .ascii "\n☯ [AVALANCHE-ABS] Teste de Estresse: OK\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #6400      @ Densidade de avalanche
    
    @ CARGA DAS SEMENTES (O VENENO)
    ldr r0, =_SEED1
    vld1.32 {d28[0]}, [r0]
    vdup.32 q14, d28[0]
    
    ldr r0, =_SEED2
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]
    
    @ TTL (Benchmark Passivo)
    movw r10, #0xFFFF

.L_AVALANCHE_V2:
    subs r10, r10, #1
    vdup.32 q13, r10

    @ CARGA CONTÍGUA
    vld1.8 {q0, q1}, [r4]
    add r5, r4, #2048
    vld1.8 {q2, q3}, [r5]

    @ --- AVALANCHE DE FEEDBACK ---
    veor q8, q0, q15        @ Invasão de Ruído
    vmla.f32 q8, q2, q13    @ Acúmulo de tempo real
    vrev32.8 q9, q8         @ Flip de paridade 
    veor q15, q9, q14       @ Retroalimentação da semente (Cadeia Crítica)
    
    @ Estabilização 7D (O Quadrado que não quebra)
    vtrn.32 q0, q9
    vadd.i32 q2, q2, q15

    @ GRAVAÇÃO
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r5]

    subs r6, r6, #1
    bne .L_AVALANCHE_V2

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
