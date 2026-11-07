/* SPDX-License-Identifier: MIT */
#include "raf_linux_abi.h"

raf_linux_word raf_linux_write(raf_linux_word fd, const void *p, raf_linux_u32 n) {
    /* Local invalid buffer rejection using Linux EFAULT; no kernel bypass. */
    if (!p && n) return -14;
#if defined(__x86_64__) && !defined(__ILP32__)
    register raf_linux_word ret __asm__("rax") = RAF_LINUX_NR_WRITE;
    register raf_linux_word a0 __asm__("rdi") = fd;
    register const void *a1 __asm__("rsi") = p;
    register raf_linux_word a2 __asm__("rdx") = (raf_linux_word)n;
    __asm__ volatile("syscall" : "+a"(ret)
                     : "D"(a0), "S"(a1), "d"(a2)
                     : "rcx", "r11", "cc", "memory");
    return ret;
#elif defined(__aarch64__)
    register raf_linux_word x8 __asm__("x8") = RAF_LINUX_NR_WRITE;
    register raf_linux_word x0 __asm__("x0") = fd;
    register const void *x1 __asm__("x1") = p;
    register raf_linux_word x2 __asm__("x2") = (raf_linux_word)n;
    __asm__ volatile("svc #0" : "+r"(x0)
                     : "r"(x8), "r"(x1), "r"(x2)
                     : "cc", "memory");
    return x0;
#elif defined(__arm__) && !defined(__thumb__)
    register raf_linux_word r7 __asm__("r7") = RAF_LINUX_NR_WRITE;
    register raf_linux_word r0 __asm__("r0") = fd;
    register const void *r1 __asm__("r1") = p;
    register raf_linux_word r2 __asm__("r2") = (raf_linux_word)n;
    __asm__ volatile("svc #0" : "+r"(r0)
                     : "r"(r7), "r"(r1), "r"(r2)
                     : "cc", "memory");
    return r0;
#endif
}
