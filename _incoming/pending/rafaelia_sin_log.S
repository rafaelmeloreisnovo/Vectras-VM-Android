@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4194304
_LOG_BASE: .float 0.693147  @ ln(2) para normalização
_S: .ascii "\n☯ [LOG-SENO] Compressão Harmônica: CONCLUÍDA\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    mov r6, #16384
    
    @ CARGA DA BASE LOGARÍTMICA
    ldr r0, =_LOG_BASE
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_LOG_SINE_LOOP:
    pld [r4, #1024]
    vld1.8 {q0, q1}, [r4]
    
    @ --- SISTEMA LOGARÍTMICO (Aproximação por deslocamento) ---
    @ O logaritmo reduz a magnitude, o seno estabiliza a fase
    vcvt.f32.u32 q8, q0    @ Converte para Float para o Seno
    vmul.f32 q8, q8, q15    @ Aplica a escala logarítmica
    
    @ --- O SENO DO LOGARITMO (Simulado por oscilação de paridade) ---
    vrev32.8 q9, q8         @ Inversão senoidal (Fase 180)
    vsub.f32 q10, q8, q9    @ Diferencial de amplitude
    
    @ Fator de Baskara na Coroa Circular
    vadd.f32 q0, q10, q15   @ O "Cimento" comprimido
    
    vst1.8 {q0, q1}, [r4]!

    subs r6, r6, #1
    bne .L_LOG_SINE_LOOP

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
