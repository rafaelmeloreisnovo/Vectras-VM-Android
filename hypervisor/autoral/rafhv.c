/* SPDX-License-Identifier: GPL-2.0-only */
#include "rafhv.h"

static int rafhv_valid(const struct rafhv_vm *vm) {
    return vm != (const struct rafhv_vm *)0 &&
           vm->magic == RAFHV_MAGIC && vm->abi == RAFHV_ABI &&
           vm->region_count <= RAFHV_MAX_REGIONS;
}

int rafhv_init(struct rafhv_vm *vm) {
    uint32_t i;
    if (vm == (struct rafhv_vm *)0) return RAFHV_INVALID;
    vm->magic = RAFHV_MAGIC;
    vm->abi = RAFHV_ABI;
    vm->state = RAFHV_READY;
    vm->region_count = 0u;
    vm->pc = UINT64_C(0);
    vm->steps = UINT64_C(0);
    vm->last_exit.reason = RAFHV_EXIT_NONE;
    vm->last_exit.detail = 0u;
    for (i = 0; i < RAFHV_MAX_REGIONS; ++i) {
        vm->regions[i].base = UINT64_C(0);
        vm->regions[i].length = UINT64_C(0);
        vm->regions[i].bytes = (uint8_t *)0;
        vm->regions[i].permissions = 0u;
    }
    return RAFHV_OK;
}

int rafhv_reset(struct rafhv_vm *vm) {
    if (!rafhv_valid(vm)) return RAFHV_INVALID;
    return rafhv_init(vm);
}

int rafhv_map(struct rafhv_vm *vm, uint64_t base, uint64_t length,
              uint8_t *borrowed_bytes, uint32_t permissions) {
    uint32_t i;
    uint64_t end;
    if (!rafhv_valid(vm) || borrowed_bytes == (uint8_t *)0 || length == 0u ||
        (permissions & RAFHV_MEM_R) == 0u ||
        (permissions & ~(RAFHV_MEM_R | RAFHV_MEM_W | RAFHV_MEM_X)) != 0u)
        return RAFHV_INVALID;
    if (vm->state != RAFHV_READY) return RAFHV_STATE;
    if (length > UINT64_MAX - base || length > (uint64_t)UINTPTR_MAX)
        return RAFHV_RANGE;
    if (vm->region_count == RAFHV_MAX_REGIONS) return RAFHV_RANGE;
    end = base + length; /* Exclusive bound; address UINT64_MAX is not mapped. */
    for (i = 0; i < vm->region_count; ++i) {
        const struct rafhv_region *r = &vm->regions[i];
        if (base < r->base + r->length && r->base < end)
            return RAFHV_OVERLAP;
    }
    i = vm->region_count;
    vm->regions[i].base = base;
    vm->regions[i].length = length;
    vm->regions[i].bytes = borrowed_bytes;
    vm->regions[i].permissions = permissions;
    vm->region_count = i + 1u;
    return RAFHV_OK;
}

static const struct rafhv_region *rafhv_locate(const struct rafhv_vm *vm,
                                               uint64_t address) {
    uint32_t i;
    for (i = 0u; i < vm->region_count; ++i) {
        const struct rafhv_region *r = &vm->regions[i];
        if (address >= r->base && address - r->base < r->length)
            return r;
    }
    return (const struct rafhv_region *)0;
}

int rafhv_read8(const struct rafhv_vm *vm, uint64_t guest_addr, uint8_t *out) {
    const struct rafhv_region *r;
    if (!rafhv_valid(vm) || out == (uint8_t *)0) return RAFHV_INVALID;
    r = rafhv_locate(vm, guest_addr);
    if (r == (const struct rafhv_region *)0) return RAFHV_RANGE;
    if ((r->permissions & RAFHV_MEM_R) == 0u) return RAFHV_PERMISSION;
    *out = r->bytes[(uintptr_t)(guest_addr - r->base)];
    return RAFHV_OK;
}

int rafhv_write8(struct rafhv_vm *vm, uint64_t guest_addr, uint8_t value) {
    const struct rafhv_region *r;
    if (!rafhv_valid(vm)) return RAFHV_INVALID;
    r = rafhv_locate(vm, guest_addr);
    if (r == (const struct rafhv_region *)0) return RAFHV_RANGE;
    if ((r->permissions & RAFHV_MEM_W) == 0u) return RAFHV_PERMISSION;
    r->bytes[(uintptr_t)(guest_addr - r->base)] = value;
    return RAFHV_OK;
}

int rafhv_run(struct rafhv_vm *vm, rafhv_enter_fn enter, void *ctx,
              uint32_t step_budget) {
    uint32_t i;
    if (!rafhv_valid(vm) || enter == (rafhv_enter_fn)0 ||
        step_budget == 0u || step_budget > RAFHV_MAX_STEPS)
        return RAFHV_INVALID;
    if (vm->state != RAFHV_READY && vm->state != RAFHV_PAUSED)
        return RAFHV_STATE;
    vm->state = RAFHV_RUNNING;
    for (i = 0u; i < step_budget; ++i) {
        struct rafhv_exit event = { RAFHV_EXIT_NONE, 0u };
        uint64_t next_pc = vm->pc;
        if (enter(ctx, &next_pc, &event) != 0 ||
            event.reason > RAFHV_EXIT_FAULT || vm->steps == UINT64_MAX) {
            vm->state = RAFHV_FAULTED;
            vm->last_exit.reason = RAFHV_EXIT_FAULT;
            vm->last_exit.detail = 0u;
            return RAFHV_BACKEND;
        }
        vm->pc = next_pc;
        vm->steps += UINT64_C(1);
        if (event.reason == RAFHV_EXIT_NONE) continue;
        vm->last_exit = event;
        vm->state = event.reason == RAFHV_EXIT_HALT ? RAFHV_HALTED :
                    event.reason == RAFHV_EXIT_IO ? RAFHV_PAUSED : RAFHV_FAULTED;
        return RAFHV_OK;
    }
    vm->last_exit.reason = RAFHV_EXIT_BUDGET;
    vm->last_exit.detail = 0u;
    vm->state = RAFHV_PAUSED;
    return RAFHV_OK;
}
