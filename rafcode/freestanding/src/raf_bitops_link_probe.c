/* SPDX-License-Identifier: MIT */
/* Link-only probe: intentionally has no process exit and MUST NOT be run. */
#include "raf_bitops.h"
volatile raf_u32 raf_bitops_link_sink;

void raf_bitops_link_probe(void) {
    raf_u8 bytes[4];
    raf_u32 loaded = 0u;
    raf_u32 v = raf_reverse_bits32(0x01234567u);
    v ^= raf_bswap32(0x01020304u);
    v ^= raf_rotl32(v, 5u);
    if (raf_store_be32(bytes, 4u, v) == RAF_OK &&
        raf_load_be32(bytes, 4u, &loaded) == RAF_OK)
        raf_bitops_link_sink = loaded;
    for (;;) { }
}
