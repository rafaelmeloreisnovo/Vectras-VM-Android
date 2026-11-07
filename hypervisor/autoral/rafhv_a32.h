/* SPDX-License-Identifier: GPL-2.0-only */
/* Experimental A32 interpreter: ONLY the explicitly supported instructions. */
#ifndef RAFHV_A32_H
#define RAFHV_A32_H
#include "rafhv.h"

#define RAFHV_A32_BAD_PC 1u
#define RAFHV_A32_FETCH 2u
#define RAFHV_A32_UNSUPPORTED 3u
#define RAFHV_A32_MEMORY 4u

struct rafhv_a32_cpu {
    struct rafhv_vm *vm;
    uint32_t r[16];
};
/* One guest A32 instruction per successful call. No privilege model / MMU. */
int rafhv_a32_enter(void *ctx, uint64_t *pc, struct rafhv_exit *event);
#endif
