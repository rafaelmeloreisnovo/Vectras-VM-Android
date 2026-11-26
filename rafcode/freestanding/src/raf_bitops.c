/* SPDX-License-Identifier: MIT */
#include "raf_bitops.h"

raf_u32 raf_reverse_bits32(raf_u32 value) {
#if RAF_ENABLE_ARM_BITOPS
    raf_u32 result;
#if defined(__aarch64__)
    __asm__("rbit %w0, %w1" : "=r"(result) : "r"(value));
#else
    __asm__("rbit %0, %1" : "=r"(result) : "r"(value));
#endif
    return result;
#else
    value = ((value & 0x55555555u) << 1u) | ((value >> 1u) & 0x55555555u);
    value = ((value & 0x33333333u) << 2u) | ((value >> 2u) & 0x33333333u);
    value = ((value & 0x0f0f0f0fu) << 4u) | ((value >> 4u) & 0x0f0f0f0fu);
    value = ((value & 0x00ff00ffu) << 8u) | ((value >> 8u) & 0x00ff00ffu);
    return (value << 16u) | (value >> 16u);
#endif
}

raf_u32 raf_bswap32(raf_u32 value) {
#if RAF_ENABLE_ARM_BITOPS
    raf_u32 result;
#if defined(__aarch64__)
    __asm__("rev %w0, %w1" : "=r"(result) : "r"(value));
#else
    __asm__("rev %0, %1" : "=r"(result) : "r"(value));
#endif
    return result;
#else
    return ((value & 0x000000ffu) << 24u) |
           ((value & 0x0000ff00u) <<  8u) |
           ((value & 0x00ff0000u) >>  8u) |
           ((value & 0xff000000u) >> 24u);
#endif
}

raf_u32 raf_rotl32(raf_u32 value, raf_u32 count) {
    raf_u32 shift = count & 31u;
    return (value << shift) | (value >> ((32u - shift) & 31u));
}

raf_status raf_load_be32(const raf_u8 *bytes, raf_u32 available, raf_u32 *value) {
    if (!bytes || !value) return RAF_INVALID;
    if (available < 4u) return RAF_NO_SPACE;
    *value = ((raf_u32)bytes[0] << 24u) |
             ((raf_u32)bytes[1] << 16u) |
             ((raf_u32)bytes[2] <<  8u) | (raf_u32)bytes[3];
    return RAF_OK;
}

raf_status raf_store_be32(raf_u8 *bytes, raf_u32 available, raf_u32 value) {
    if (!bytes) return RAF_INVALID;
    if (available < 4u) return RAF_NO_SPACE;
    bytes[0] = (raf_u8)(value >> 24u);
    bytes[1] = (raf_u8)(value >> 16u);
    bytes[2] = (raf_u8)(value >>  8u);
    bytes[3] = (raf_u8)value;
    return RAF_OK;
}
