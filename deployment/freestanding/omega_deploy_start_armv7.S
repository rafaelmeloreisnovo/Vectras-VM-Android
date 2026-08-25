.syntax unified
.arch armv7-a
.fpu neon
.arm

.section .text._start,"ax",%progbits
.align 2
.global _start
.type _start,%function
.hidden omega_deploy_main
_start:
    b omega_deploy_main
.size _start, .-_start

.section .note.GNU-stack,"",%progbits
