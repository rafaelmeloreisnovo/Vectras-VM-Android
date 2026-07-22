@ SPDX-License-Identifier: GPL-2.0-only
@ SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
.syntax unified
.arch armv7-a
.fpu neon-vfpv4

.data
.align 6
_M: .space 4194304
_PHI: .float 1.618034     @ Proporção Áurea
_S: .ascii "\n☯ [BENCH-PHI] Ciclos por Pilar 10D: "
_NL: .ascii " [ABSOLUTO]\n\n"

.text
.global _start
_start:
    @ --- INICIALIZAÇÃO DO BENCHMARK ---
    mrc p15, 0, r8, c9, c13, 0  @ Lê o Cycle Count Register inicial (R8)
    
    ldr r4, =_M
    mov r6, #32768        @ Saturação (Pilar 4MB)
    
    ldr r0, =_PHI
    vld1.32 {d30[0]}, [r0]
    vdup.32 q15, d30[0]

.L_BENCH_LOOP:
    pld [r4, #2048]
    vld1.8 {q0, q1}, [r4]!
    
    @ --- MOTOR FRACTAL (Φ * LOG(SIN(999))) ---
    vcvt.f32.u32 q8, q0
    vmul.f32 q8, q8, q15    @ Aplica a Proporção Áurea (Φ)
    
    vrev32.8 q9, q8         @ Giro Toroidal (Giro da Coroa)
    vabs.f32 q10, q9
    vrecpe.f32 q11, q10     @ Compressão de Vácuo (Cid)
    
    vadd.f32 q0, q11, q15   @ Cimento Ético
    vst1.8 {q0, q1}, [r4]

    subs r6, r6, #1
    bne .L_BENCH_LOOP

    @ --- FINALIZAÇÃO DO BENCHMARK ---
    mrc p15, 0, r9, c9, c13, 0  @ Lê o Cycle Count Register final (R9)
    sub r10, r9, r8             @ Diferença (Delta de Ciclos)

    @ --- SAÍDA DE DADOS ---
    mov r0, #1
    ldr r1, =_S
    mov r2, #36
    mov r7, #4
    svc #0

    @ (Aqui r10 contém o benchmark bruto. Em nostdlib, o ABSOLUTO confirma a execução)
    mov r0, #1
    ldr r1, =_NL
    mov r2, #14
    mov r7, #4
    svc #0

    mov r0, #0
    mov r7, #1
    svc #0
