@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

@ --- O VAZIO (DADOS SEM NOME) ---
.data
.align 6
_0: .space 64       @ Bloco alinhado de 64-bytes (Cache Line L1)
_1: .ascii "."      @ Byte de Coerencia
_2: .ascii "!"      @ Byte de Entropia (Jitter)
_3: .ascii "\n"     @ Byte de Quebra de Fase

@ --- A AÇÃO (SEM FUNÇÕES) ---
.text
.global _start
_start:
    sub sp, sp, #16     @ Recuo no Stack para buffer temporario (16 bytes)
    mov r6, #14         @ Registrador 6: Contador de 14 ciclos

.L0:
    @ T1: Interrupção 263 (clock_gettime)
    mov r0, #1
    mov r1, sp
    ldr r7, =263
    svc #0
    ldr r8, [sp, #4]    @ Pega apenas nanossegundos -> r8

    @ --- NÚCLEO GEOMÉTRICO (SIMD / PREFETCH) ---
    ldr r4, =_0         @ Aponta para memória L1
    pld [r4, #64]       @ PREFETCH: Puxa o próximo bloco antes de precisar
    vld1.32 {q0, q1}, [r4] @ Carrega 256 bits para NEON
    vadd.f32 q2, q0, q1    @ Transformação (Métrica)
    vst1.32 {q2}, [r4]     @ Descarrega

    @ T2: Interrupção 263 (clock_gettime)
    mov r0, #1
    mov r1, sp
    ldr r7, =263
    svc #0
    ldr r9, [sp, #4]    @ Nanossegundos -> r9

    @ Delta: r2 = r9 - r8
    subs r2, r9, r8
    ldrmi r3, =1000000000
    addmi r2, r2, r3    @ Corrige virada de segundo

    @ Julgamento Ético (< 20000 ns ?)
    ldr r3, =20000
    cmp r2, r3
    ldr r1, =_1         @ Aponta para "."
    ldrgt r1, =_2       @ Se maior, aponta para "!"
    
    @ I/O: Interrupção 4 (write)
    mov r0, #1          @ fd=1 (Terminal)
    mov r2, #1          @ Tamanho=1 byte
    mov r7, #4
    svc #0

    @ Fim do Ciclo
    subs r6, r6, #1
    bne .L0             @ Pula de volta para .L0 se não for zero

    @ Quebra de Linha
    mov r0, #1
    ldr r1, =_3
    mov r2, #1
    mov r7, #4
    svc #0

    @ Morte Limpa: Interrupção 1 (exit)
    mov r0, #0
    mov r7, #1
    svc #0
