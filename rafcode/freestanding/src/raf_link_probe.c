/* SPDX-License-Identifier: MIT */
/* Link-only function, NOT a user-space executable entry; never execute. */
#include "raf_core.h"
volatile raf_u32 raf_link_probe_sink;
void raf_link_probe(void) {
    static const raf_u8 bytes[9] = { '1','2','3','4','5','6','7','8','9' };
    raf_link_probe_sink = raf_crc32c(bytes, 9u);
    for (;;) { __asm__ volatile ("" ::: "memory"); }
}
