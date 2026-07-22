@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
@ Matriz 10x10x10 (1000 elementos * 4 bytes = 4000 bytes)
@ Perfeitamente encaixada em 63 cache lines de 64 bytes
_M: .space 4096 
_B: .ascii "."
_E: .ascii "!"
_N: .ascii "\n"

.text
.global _start
_start:
    @ R4 = Base da Matriz
    ldr r4, =_M
    @ R6 = Contador de Camadas (10)
    mov r6, #10
    
.L_Z: @ Loop de Profundidade (Z)
    @ R5 = Contador de Linhas (10)
    mov r5, #10
    
.L_Y: @ Loop de Projeção (Y)
    @ PREFETCH do próximo plano para evitar stall de RAM
    pld [r4, #64]
    
    @ --- ENCAIXE SIMD NEON ---
    @ Carrega 4 floats (X0-X3), Transforma, Descarrega
    vld1.32 {q0}, [r4]
    vdup.32 q1, d0[0]    @ Espelhamento de fase
    vadd.f32 q2, q0, q1   @ Interação Fractal
    vst1.32 {q2}, [r4]!   @ Auto-incremento de endereço (Encaixe Contínuo)
    
    subs r5, r5, #1
    bne .L_Y
    
    subs r6, r6, #1
    bne .L_Z

    @ --- FEEDBACK DE COERÊNCIA ---
    mov r0, #1
    ldr r1, =_B
    mov r2, #1
    mov r7, #4
    svc #0
    
    mov r0, #1
    ldr r1, =_N
    mov r2, #1
    mov r7, #4
    svc #0

    @ Saída
    mov r0, #0
    mov r7, #1
    svc #0
