/* SPDX-License-Identifier: MIT */
.text
.global _start
.type _start,%function
_start:
    bl raf_linux_demo
    mov x8,#93
    svc #0
1:  b 1b
.size _start,.-_start
.section .note.GNU-stack,"",%progbits
