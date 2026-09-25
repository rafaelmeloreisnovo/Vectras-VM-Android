.arch armv8-a
.section .text._start,"ax",%progbits
.global _start
.hidden _start
.type _start,%function
_start:
    bl vectras_zipraf_phase_a_selftest
    mov x8, #93
    svc #0
    brk #0
.size _start, .-_start
.section .note.GNU-stack,"",%progbits
