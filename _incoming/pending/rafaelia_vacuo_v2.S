@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 1048576
_CID: .word 0x96399963
_S: .ascii "\n☯ [VACUO-10D] Sincronia de Pilar: ABSOLUTA\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #10000
    
    ldr r0, =_CID
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_VACUO_FIX:
    pld [r4, #1024]
    vld1.8 {q0, q1}, [r4]       @ Bloco A (Contíguo)
    add r5, r4, #16384
    vld1.8 {q2, q3}, [r5]       @ Bloco B (Contíguo)

    @ --- PROCESSAMENTO SIMD (BARRAMENTO CRUZADO) ---
    veor q8, q0, q15            @ XOR Fractal A -> Temporário q8
    vrev32.8 q9, q2             @ Spin Fractal B -> Temporário q9
    
    @ Fusão de Hiperformas (Baskara)
    vadd.f32 q0, q8, q9         @ Devolve para q0 (Início do par de escrita)
    vsub.f32 q1, q8, q9         @ Devolve para q1 (Fim do par de escrita)
    
    @ --- ESCRITA CONTÍGUA (SEM ERRO) ---
    vst1.8 {q0, q1}, [r4]!      @ Grava par A
    vst1.8 {q2, q3}, [r5]       @ Grava par B (Original/Estabilizado)

    subs r6, r6, #1
    bne .L_VACUO_FIX

    dmb ish
    isb

    mov r0, #1
    ldr r1, =_S
    mov r2, #47
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
