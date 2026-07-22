@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4096     @ Matriz Toroidal
_F: .asciz "rafaelia.bin"
_S: .ascii "☯ [CRISTALIZADO]"
_N: .ascii "\n"

.text
.global _start
_start:
    @ --- FASE 1: PROCESSAMENTO (ESPIRAL) ---
    ldr r4, =_M
    vmov.f32 d16, #1.0  @ Inicialização rápida
    mov r6, #1024       @ 1024 floats (4096 bytes)
.L_PROC:
    vld1.32 {q0}, [r4]
    vadd.f32 q0, q0, q0 @ Pulso de ativação
    vst1.32 {q0}, [r4]!
    subs r6, r6, #4
    bne .L_PROC

    @ --- FASE 2: PERSISTÊNCIA (SYSCALLS) ---
    @ 1. OPEN (r7=5): rafaelia.bin, O_WRONLY|O_CREAT, mode 0644
    ldr r0, =_F
    mov r1, #65         @ O_WRONLY(1) | O_CREAT(64)
    ldr r2, =0644       @ Permissões
    mov r7, #5          @ sys_open
    svc #0
    mov r8, r0          @ Salva File Descriptor (FD) em r8

    @ 2. WRITE (r7=4): Escreve a Matriz _M no arquivo
    mov r0, r8
    ldr r1, =_M
    mov r2, #4096       @ Tamanho total da matriz
    mov r7, #4          @ sys_write
    svc #0

    @ 3. CLOSE (r7=6)
    mov r0, r8
    mov r7, #6          @ sys_close
    svc #0

    @ --- FEEDBACK ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #19
    mov r7, #4
    svc #0
    
    mov r0, #1
    ldr r1, =_N
    mov r2, #1
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1          @ exit
    svc #0
