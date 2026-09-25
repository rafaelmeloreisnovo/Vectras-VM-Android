.syntax unified
.arch armv7-a
.arm
.section .text._start,"ax",%progbits
.global _start
.hidden _start
.type _start,%function
_start:
    bic sp, sp, #15
    bl vectras_zipraf_phase_a_selftest
    mov r7, #1
    svc #0
    udf #0
.size _start, .-_start
.section .note.GNU-stack,"",%progbits
