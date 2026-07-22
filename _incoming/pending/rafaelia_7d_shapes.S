@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4194304
_PHI: .float 1.618034
_SQRT2: .float 1.414213  @ A Reta Multiplicadora
_S: .ascii "\n☯ [7D-HYPERFORMA] Hexagonal/Octagonal: EM FASE\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    mov r6, #16384
    
    @ CARGA DA DIAGONAL (sqrt(2))
    ldr r0, =_SQRT2
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_HYPER_SHAPE:
    vld1.8 {q0, q1}, [r4]
    
    @ --- PROJEÇÃO DIAGONAL (Invasão do Campo) ---
    vcvt.f32.u32 q8, q0
    vmul.f32 q9, q8, q15    @ Multiplicação por sqrt(2) -> Novo Quadrado
    
    @ --- ROTAÇÃO POR DIMENSÃO (Giro dos Vértices) ---
    @ Usamos VTRN para cruzar os catetos na mediana
    vtrn.32 q0, q1          @ Cruzamento de paridade 4x4
    vrev64.16 q10, q9       @ Reflexão Hexagonal
    
    @ --- O TRIÂNGULO RETO (Divisão Diagonal) ---
    vadd.f32 q0, q0, q10    @ Fusão das Hiperformas
    
    vst1.8 {q0}, [r4]!

    @ Salto de Base 20 para manter o vácuo
    add r4, r4, #20
    and r4, r4, r5          @ r5 ainda é 0x3FFFFF (Pilar 4MB)

    subs r6, r6, #1
    bne .L_HYPER_SHAPE

    dmb ish
    isb

    mov r0, #1
    ldr r1, =_S
    mov r2, #49
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
