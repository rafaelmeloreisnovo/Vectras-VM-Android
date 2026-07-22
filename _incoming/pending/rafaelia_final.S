@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 409600
_S: .ascii "\n☯ [ESTÁVEL] 8/16/32 - Sustentação Total\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #6400
    
    @ TTL como Âncora de Realidade
    movw r10, #0xFFFF
    vdup.32 q15, r10

.L_SANKOFA:
    @ A cada ciclo, o TTL valida a existência do dado
    subs r10, r10, #1
    
    vld1.8 {q0, q1}, [r4]!
    vld1.8 {q2, q3}, [r4]!

    @ --- GEOMETRIA DE SUSTENTAÇÃO ---
    @ Cruzamos a paridade para formar o Triângulo Reto
    veor q8, q0, q15     @ Cateto A (Tempo)
    veor q9, q2, q1      @ Cateto B (Dado)
    
    @ A Diferença forma a Reta (Multiplicação por fator de estabilidade)
    @ Aqui simulamos a rotação por dimensão fator da reta²
    vadd.f32 q10, q8, q9
    vmul.f32 q10, q10, q15 @ O TTL dita a amplitude do campo

    @ Retorno à Matriz (O Quadrado que vira Círculo)
    vst1.8 {q10, q1}, [r4, #-64]!
    add r4, r4, #64      @ Reposiciona o ponteiro de fluxo

    subs r6, r6, #1
    bne .L_SANKOFA

    @ --- BARREIRA DE INÉRCIA ---
    dmb ish
    isb

    @ Saída limpa para o barramento de vídeo
    mov r0, #1
    ldr r1, =_S
    mov r2, #41
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
