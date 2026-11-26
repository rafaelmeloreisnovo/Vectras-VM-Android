/* SPDX-License-Identifier: MIT */
#include "raf_core.h"

void raf_zero(void *dest, raf_u32 n) {
    raf_u8 *p = (raf_u8 *)dest;
    raf_u32 i;
    if (!p) return;
    for (i = 0u; i < n; ++i) p[i] = 0u;
}

raf_status raf_arena_take(raf_arena *a, raf_u32 n,
                          raf_u32 align, void **out) {
    raf_uptr addr, offset, pad;
    raf_u32 next;
    if (!out || !a || !a->base || a->cursor > a->capacity ||
        align == 0u || (align & (align - 1u)) != 0u || align > 4096u)
        return RAF_INVALID;
    if ((raf_uptr)a->base > ((raf_uptr)-1) - (raf_uptr)a->cursor)
        return RAF_NO_SPACE;
    addr = (raf_uptr)a->base + (raf_uptr)a->cursor;
    pad = (raf_uptr)(0u - addr) & (raf_uptr)(align - 1u);
    if (pad > (raf_uptr)(a->capacity - a->cursor) ||
        (raf_uptr)n > (raf_uptr)(a->capacity - a->cursor) - pad)
        return RAF_NO_SPACE;
    offset = (raf_uptr)a->cursor + pad;
    next = (raf_u32)(offset + (raf_uptr)n);
    *out = (void *)((raf_u8 *)a->base + (raf_u32)offset);
    a->cursor = next;
    return RAF_OK;
}

raf_u32 raf_crc32c(const void *bytes, raf_u32 n) {
    const raf_u8 *p = (const raf_u8 *)bytes;
    raf_u32 crc = 0xffffffffu;
    raf_u32 i;
#if !RAF_ENABLE_AARCH64_CRC32C
    raf_u32 j;
#endif
    if (!p && n) return 0u; /* invalid input is detected by dispatch */
    for (i = 0u; i < n; ++i) {
#if RAF_ENABLE_AARCH64_CRC32C
        raf_u32 v = (raf_u32)p[i];
        __asm__ volatile ("crc32cb %w0, %w0, %w1" : "+r"(crc) : "r"(v));
#else
        crc ^= (raf_u32)p[i];
        for (j = 0u; j < 8u; ++j) {
            raf_u32 mask = 0u - (crc & 1u);
            crc = (crc >> 1u) ^ (0x82f63b78u & mask);
        }
#endif
    }
    return ~crc;
}
