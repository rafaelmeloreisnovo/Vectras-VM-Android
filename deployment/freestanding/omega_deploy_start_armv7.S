/* SPDX-License-Identifier: GPL-2.0-only */
.syntax unified
.arch armv7-a
.fpu neon
.arm

.section .text._start,"ax",%progbits
.align 2
.global _start
.type _start,%function
.extern omega_deploy_main

/*
 * Linux/Android ARM EABI boundary.
 * r7=4 -> write(2), r7=1 -> exit(2).
 * No dynamic linker, libc, JNI, loader helper or heap is involved.
 */
_start:
    bl      omega_deploy_main
    mov     r4, r0
    cmp     r4, #0
    bne     .Lreport_fail

.Lreport_ok:
    mov     r0, #1
    ldr     r1, =omega_ok_message
    mov     r2, #15
    mov     r7, #4
    svc     #0
    b       .Lexit

.Lreport_fail:
    mov     r0, #2
    ldr     r1, =omega_fail_message
    mov     r2, #17
    mov     r7, #4
    svc     #0

.Lexit:
    mov     r0, r4
    mov     r7, #1
    svc     #0

/* exit() is noreturn. If a hostile/invalid host returns from SVC, trap locally. */
.Lunexpected_return:
    udf     #0
    b       .Lunexpected_return
.size _start, .-_start

.section .rodata.omega_deploy,"a",%progbits
.align 2
omega_ok_message:
    .ascii  "OMEGA_ARMV7_OK\n"
omega_fail_message:
    .ascii  "OMEGA_ARMV7_FAIL\n"

.section .note.GNU-stack,"",%progbits
