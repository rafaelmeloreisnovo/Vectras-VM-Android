/* SPDX-License-Identifier: MIT */
.text
.global _start
.type _start,@function
_start:
    xor %ebp,%ebp
    andq $-16,%rsp
    call raf_linux_demo
    mov %eax,%edi
    mov $60,%eax
    syscall
1:  jmp 1b
.size _start,.-_start
.section .note.GNU-stack,"",@progbits
