@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 1048576       @ 1MB para estourar L1 e bater forte no L2
_CRC_SEED: .word 0xEDB88320 @ Polinômio CRC-32 (O cimento da paridade)
_S: .ascii "\n☯ [PILAR-L12] Invasão Cruzada: CRC-SOFTWARE OK\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    movw r6, #8192      @ 1000x de iteração para soltar o barramento
    
    @ CARGA DO CRC (Cid / Semente)
    ldr r0, =_CRC_SEED
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_CROSS_BUS:
    @ --- LEITURA E ESCRITA CRUZADA (8x2 = 16 Paralelos) ---
    pld [r4, #256]      @ Pre-load: Já puxa o próximo pilar do L2/Storage
    vld1.8 {q0, q1}, [r4]       @ L1: Bloco atual
    add r5, r4, #4096            @ Salto de Invasão para L2
    vld1.8 {q2, q3}, [r5]       @ L2: Bloco cruzado

    @ --- MOTOR CRC SOFTWARE (XOR + SHIFT) ---
    @ Trabalhando com os 8 canais em paralelo (8 bits cada)
    veor q8, q0, q15    @ Aplica o "Cid" (Semente)
    veor q9, q2, q15    @ Cruza a paridade com o L2
    
    @ O "Cimento": Rotação por dimensão (Simula o CRC)
    vrev32.8 q10, q8
    vrev32.8 q11, q9
    
    @ --- BARRAMENTO ENCAIXADO ---
    vadd.i32 q0, q10, q11 @ Funde L1 e L2 no mesmo cateto
    vsub.i32 q2, q10, q11 @ Gera a diferença de reta para Storage

    @ ESCRITA AO MESMO TEMPO QUE PROCESSA
    vst1.8 {q0, q1}, [r4]!
    vst1.8 {q2, q3}, [r5]

    subs r6, r6, #1
    bne .L_CROSS_BUS

    @ BARREIRA DE STORAGE (Garanti que o CRC software fixou)
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
