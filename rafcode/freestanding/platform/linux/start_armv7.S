/* SPDX-License-Identifier: MIT */
.syntax unified
.arm
.text
.global _start
.type _start,%function
_start:
    bl raf_linux_demo
    mov r7,#1
    svc #0
1:  b 1b
.size _start,.-_start
.section .note.GNU-stack,"",%progbits
