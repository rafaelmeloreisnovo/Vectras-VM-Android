@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4194304
_PHI: .float 1.618034
_S: .ascii "\n☯ [PARIDADE-CENTRAL] Elo 4-4: ESTABILIZADO NO MEIO\n\n"

.text
.global _start
_start:
    ldr r4, =_M
    mov r6, #16384

    @ MÁSCARA DE PROTEÇÃO 4MB
    movw r5, #0xFFFF
    movt r5, #0x003F

    ldr r0, =_PHI
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_MEDIAN_LINK:
    vld1.8 {q0, q1}, [r4]   @ Carga dos blocos
    
    @ --- A LIGAÇÃO DOS 4 (O Elo Central) ---
    @ Extraímos os 4 bytes centrais de cada lado para formar a ponte
    vext.8 q2, q0, q1, #4   @ Deslocamento para alinhar o "meio"
    vrev32.8 q3, q2         @ Giro da paridade central
    
    @ --- MATEMÁTICA SEM PERDA ---
    @ O que sobe (vadd) e o que desce (vsub) se equilibram no q3
    vadd.f32 q10, q0, q15   @ Sobe para a Hiperforma
    vsub.f32 q11, q1, q15   @ Desce para o Silício
    
    @ Ligação final: A paridade q3 cimenta o q10 e q11
    veor q0, q10, q3
    veor q1, q11, q3
    
    vst1.8 {q0, q1}, [r4]!

    @ Ciclo Vigesimal (Base 20) com a máscara
    add r4, r4, #20
    and r4, r4, r5

    subs r6, r6, #1
    bne .L_MEDIAN_LINK

    dmb ish
    isb

    mov r0, #1
    ldr r1, =_S
    mov r2, #53
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
