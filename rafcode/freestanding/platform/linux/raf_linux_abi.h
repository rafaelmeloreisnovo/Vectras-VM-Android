/* SPDX-License-Identifier: MIT */
/* Optional Linux syscall boundary, separate from the portable RAFCODE core.
 * syscall numbers/argument registers are public Linux userspace ABI facts.
 * Requires a kernel with the matching Linux ELF ABI; no C library functions. */
#ifndef RAF_LINUX_ABI_H
#define RAF_LINUX_ABI_H
/* No include dependency, not even on the portable RAFCODE core. */
typedef unsigned int raf_linux_u32;
typedef signed long raf_linux_word;
_Static_assert(sizeof(raf_linux_word) == sizeof(void *), "Linux word ABI mismatch");
_Static_assert(sizeof(raf_linux_u32) == 4, "requires 32-bit unsigned int");

#if defined(__x86_64__) && !defined(__ILP32__)
#define RAF_LINUX_NR_WRITE 1
#define RAF_LINUX_NR_EXIT 60
#define RAF_LINUX_MACHINE "x86_64"
#elif defined(__aarch64__)
#define RAF_LINUX_NR_WRITE 64
#define RAF_LINUX_NR_EXIT 93
#define RAF_LINUX_MACHINE "aarch64"
#elif defined(__arm__) && !defined(__thumb__)
#define RAF_LINUX_NR_WRITE 4
#define RAF_LINUX_NR_EXIT 1
#define RAF_LINUX_MACHINE "armv7-arm"
#else
#error "Linux syscall boundary supports only x86_64 LP64, AArch64 LP64, ARMv7 ARM-state"
#endif

/* Returns raw Linux kernel value: number of bytes written >=0, or -errno.
 * Partial writes and EINTR are observable, not silently retried.
 * fd and buffer permissions remain exclusively kernel-controlled. */
raf_linux_word raf_linux_write(raf_linux_word fd, const void *p, raf_linux_u32 n);

#endif
