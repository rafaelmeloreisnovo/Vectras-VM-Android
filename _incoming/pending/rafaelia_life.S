@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096     @ Espaço da Matriz (Toro)
_F: .asciz "rafaelia.bin"
_C: .float 1.618034 @ Constante PHI (Proporção Áurea)
_S: .ascii "☯ [EVOLUÇÃO CONCLUÍDA]"
_N: .ascii "\n"

.text
.global _start
_start:
    @ --- 1. INALAR (READ) ---
    @ Open rafaelia.bin (O_RDWR = 2)
    ldr r0, =_F
    mov r1, #2
    mov r7, #5          @ sys_open
    svc #0
    mov r8, r0          @ r8 = FD

    @ Read 4096 bytes para _M
    mov r0, r8
    ldr r1, =_M
    mov r2, #4096
    mov r7, #3          @ sys_read
    svc #0

    @ --- 2. PROCESSAR (EVOLUÇÃO PHI) ---
    ldr r4, =_M
    ldr r0, =_C
    vld1.32 {d16[0]}, [r0]
    vdup.32 q8, d16[0]  @ q8 = PHI
    mov r6, #256        @ 256 iterações de 16-bytes

.L_EVOLVE:
    vld1.32 {q0}, [r4]
    vmul.f32 q1, q0, q8 @ Expansão Áurea
    vrev64.32 q2, q1    @ Inversão de fase (Spiral)
    vadd.f32 q0, q1, q2 @ Novo estado sintrópico
    vst1.32 {q0}, [r4]! @ Grava no buffer e avança
    subs r6, r6, #1
    bne .L_EVOLVE

    @ --- 3. EXALAR (WRITE BACK) ---
    @ Reposiciona o ponteiro do arquivo para o início (lseek, r7=19)
    mov r0, r8
    mov r1, #0          @ offset 0
    mov r2, #0          @ SEEK_SET
    mov r7, #19
    svc #0

    @ Escreve a nova matriz evoluída
    mov r0, r8
    ldr r1, =_M
    mov r2, #4096
    mov r7, #4          @ sys_write
    svc #0

    @ Close (r7=6)
    mov r0, r8
    mov r7, #6
    svc #0

    @ --- FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #24
    mov r7, #4
    svc #0
    mov r0, #1
    ldr r1, =_N
    mov r2, #1
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
