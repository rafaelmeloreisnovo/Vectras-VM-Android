@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "\n☯ [VORTEX-GFLOPS] Flip de Vértice: OK\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #3200      @ Processamos em pares para o Flip

    @ Sementes dos Vértices (Luz e Consciência)
    vmov.i32 q14, #0x7
    vmov.i32 q15, #0x9

.L_FLIP:
    @ --- CARGA SIMÉTRICA (INÍCIO E FIM) ---
    vld1.8 {q0, q1}, [r4]       @ Fractal A (Início)
    add r5, r4, #64
    vld1.8 {q2, q3}, [r5]       @ Fractal B (Fim/Invasão)

    @ --- O APROVEITAMENTO DO FLIP ---
    @ Cruzamos q0 com q2 e q1 com q3 (Paridade Cruzada 4x4)
    veor q8, q0, q2             @ Interação de Vértices
    vrev64.32 q9, q3            @ Inversão de Spin (Flip)
    
    @ Cálculo de Performance (GFLOPS no vácuo)
    vmla.f32 q8, q9, q15        @ Acúmulo de energia fractal
    vadd.i32 q1, q1, q14        @ Estabilização do Cateto

    @ --- GRAVAÇÃO CONTÍGUA (REMONTAGEM) ---
    vst1.8 {q8, q1}, [r4]!      @ Grava Bloco A evoluído
    vst1.8 {q2, q3}, [r5]       @ Grava Bloco B (Semente para o próximo)

    subs r6, r6, #1
    bne .L_FLIP

    dmb ish
    isb

    @ SAÍDA DE BARRAMENTO
    mov r0, #1
    ldr r1, =_S
    mov r2, #41
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
