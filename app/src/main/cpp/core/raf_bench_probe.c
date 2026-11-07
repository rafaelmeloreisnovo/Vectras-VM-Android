/* SPDX-License-Identifier: GPL-2.0-only */
#include "raf_bench_probe.h"

/* Pure bounded ALU kernel: no libc, heap, filesystem, clock, Java or syscalls. */
uint32_t raf_bench_xorshift32(uint32_t seed, uint32_t iterations) {
    uint32_t state = seed;
    uint32_t i;
    for (i = 0U; i < iterations; ++i) {
        state ^= (uint32_t)(state << 13);
        state ^= (uint32_t)(state >> 17);
        state ^= (uint32_t)(state << 5);
    }
    return state;
}
