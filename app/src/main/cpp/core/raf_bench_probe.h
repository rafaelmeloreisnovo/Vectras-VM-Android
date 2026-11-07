/* SPDX-License-Identifier: GPL-2.0-only */
/* Freestanding deterministic micro-workload; see docs/ABI_AUTOPROBE_FREESTANDING_BENCH.md */
#ifndef RAF_BENCH_PROBE_H
#define RAF_BENCH_PROBE_H
#include <stdint.h>
uint32_t raf_bench_xorshift32(uint32_t seed, uint32_t iterations);
#endif
