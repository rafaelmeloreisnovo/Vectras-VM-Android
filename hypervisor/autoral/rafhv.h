/* SPDX-License-Identifier: GPL-2.0-only */
/* RAFAELIA Vectra: independent, bounded VM control kernel contract v0.1.
 * No assertion of CPU virtualization or guest boot is made by this interface.
 */
#ifndef RAFHV_H
#define RAFHV_H

#include <stdint.h> /* compiler-provided freestanding C header; no libc linkage */

#define RAFHV_ABI 1u
#define RAFHV_MAX_REGIONS 8u
#define RAFHV_MAX_STEPS 4096u
#define RAFHV_MAGIC UINT32_C(0x52485631)
#define RAFHV_MEM_R 1u
#define RAFHV_MEM_W 2u
#define RAFHV_MEM_X 4u

enum rafhv_result {
    RAFHV_OK = 0,
    RAFHV_INVALID = 1,
    RAFHV_STATE = 2,
    RAFHV_OVERLAP = 3,
    RAFHV_RANGE = 4,
    RAFHV_PERMISSION = 5,
    RAFHV_BACKEND = 6
};

enum rafhv_state {
    RAFHV_COLD = 0,
    RAFHV_READY = 1,
    RAFHV_RUNNING = 2,
    RAFHV_PAUSED = 3,
    RAFHV_HALTED = 4,
    RAFHV_FAULTED = 5
};

enum rafhv_exit_reason {
    RAFHV_EXIT_NONE = 0,
    RAFHV_EXIT_IO = 1,
    RAFHV_EXIT_HALT = 2,
    RAFHV_EXIT_FAULT = 3,
    RAFHV_EXIT_BUDGET = 4
};

struct rafhv_region {
    uint64_t base;
    uint64_t length;
    uint8_t *bytes;          /* Borrowed caller memory; never allocated/freed here. */
    uint32_t permissions;
};

struct rafhv_exit {
    uint32_t reason;
    uint32_t detail;         /* Backend-defined bounded code; not proof of guest boot. */
};

struct rafhv_vm {
    uint32_t magic;
    uint32_t abi;
    uint32_t state;
    uint32_t region_count;
    uint64_t pc;
    uint64_t steps;
    struct rafhv_exit last_exit;
    struct rafhv_region regions[RAFHV_MAX_REGIONS];
};

/* External backend owns CPU translation/entry and all privileged/OS operations.
 * 0 => exit event valid; nonzero => backend failure. No callback is trusted
 * to establish guest boot, native acceleration, or hardware capabilities.
 */
typedef int (*rafhv_enter_fn)(void *ctx, uint64_t *pc, struct rafhv_exit *event);

int rafhv_init(struct rafhv_vm *vm);
int rafhv_map(struct rafhv_vm *vm, uint64_t base, uint64_t length,
              uint8_t *borrowed_bytes, uint32_t permissions);
int rafhv_read8(const struct rafhv_vm *vm, uint64_t guest_addr, uint8_t *out);
int rafhv_write8(struct rafhv_vm *vm, uint64_t guest_addr, uint8_t value);
int rafhv_run(struct rafhv_vm *vm, rafhv_enter_fn enter, void *ctx,
              uint32_t step_budget);
int rafhv_reset(struct rafhv_vm *vm); /* Metadata only; never wipes borrowed RAM. */

#endif
