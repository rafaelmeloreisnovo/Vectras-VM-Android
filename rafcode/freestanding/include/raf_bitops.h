/* SPDX-License-Identifier: MIT */
/* RAFCODE-Phi: freestanding bit operations; no OS, libc, heap or I/O. */
#ifndef RAF_FS_BITOPS_H
#define RAF_FS_BITOPS_H
#include "raf_core.h"

#ifndef RAF_ENABLE_ARM_BITOPS
#define RAF_ENABLE_ARM_BITOPS 0
#endif

/* Explicit opt-in: rbit/rev exist on ARMv7-A and AArch64, not all ARM. */
#if RAF_ENABLE_ARM_BITOPS && !(defined(__aarch64__) || \
    (defined(__arm__) && defined(__ARM_ARCH) && (__ARM_ARCH >= 7)))
#error "RAF_ENABLE_ARM_BITOPS requires ARMv7+ or AArch64 target"
#endif

/* Binary bit order reversal, independent of host byte order. */
raf_u32 raf_reverse_bits32(raf_u32 value);
/* Byte order reversal, e.g. 0x01020304 -> 0x04030201. */
raf_u32 raf_bswap32(raf_u32 value);
/* Count is reduced modulo 32, including 0 and multiples of 32. */
raf_u32 raf_rotl32(raf_u32 value, raf_u32 count);
/* Exact four-byte BE wire format; no unaligned or out-of-bounds loads. */
raf_status raf_load_be32(const raf_u8 *bytes, raf_u32 available, raf_u32 *value);
raf_status raf_store_be32(raf_u8 *bytes, raf_u32 available, raf_u32 value);

#endif
