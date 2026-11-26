/* SPDX-License-Identifier: GPL-2.0-only */
#include "rafhv_a32.h"

static uint32_t ror_imm(uint32_t op) {
    uint32_t v = op & 255u;
    uint32_t shift = ((op >> 8) & 15u) * 2u;
    return shift == 0u ? v : (v >> shift) | (v << (32u - shift));
}

static int allow_exec(const struct rafhv_vm *vm, uint64_t pc) {
    uint32_t i;
    if (pc > UINT32_MAX - 4u || (pc & 3u) != 0u) return 0;
    for (i = 0u; i < vm->region_count; ++i) {
        const struct rafhv_region *r = &vm->regions[i];
        if (pc >= r->base && pc - r->base < r->length &&
            r->length - (pc - r->base) >= 4u)
            return (r->permissions & (RAFHV_MEM_R | RAFHV_MEM_X)) ==
                   (RAFHV_MEM_R | RAFHV_MEM_X);
    }
    return 0;
}

static int load_word(const struct rafhv_vm *vm, uint64_t a, uint32_t *out) {
    uint32_t i;
    uint32_t word = 0u;
    for (i = 0u; i < 4u; ++i) {
        uint8_t b;
        if (rafhv_read8(vm, a + i, &b) != RAFHV_OK) return 0;
        word |= ((uint32_t)b << (8u * i));
    }
    *out = word;
    return 1;
}

static int store_word(struct rafhv_vm *vm, uint64_t a, uint32_t word) {
    uint32_t i;
    /* Validate 4 bytes before any write: no partial modifications on fault. */
    const struct rafhv_region *r = (const struct rafhv_region *)0;
    for (i = 0u; i < vm->region_count; ++i) {
        const struct rafhv_region *candidate = &vm->regions[i];
        if (a >= candidate->base && a - candidate->base < candidate->length &&
            candidate->length - (a - candidate->base) >= 4u) {
            r = candidate;
            break;
        }
    }
    if (r == (const struct rafhv_region *)0 ||
        (r->permissions & RAFHV_MEM_W) == 0u) return 0;
    for (i = 0u; i < 4u; ++i)
        if (rafhv_write8(vm, a + i, (uint8_t)(word >> (8u * i))) != RAFHV_OK)
            return 0;
    return 1;
}

int rafhv_a32_enter(void *ctx, uint64_t *pc, struct rafhv_exit *event) {
    struct rafhv_a32_cpu *cpu = (struct rafhv_a32_cpu *)ctx;
    uint32_t inst, op, rd, rn, imm;
    uint64_t next;
    if (cpu == (struct rafhv_a32_cpu *)0 || cpu->vm == (struct rafhv_vm *)0 ||
        pc == (uint64_t *)0 || event == (struct rafhv_exit *)0) return -1;
    event->reason = RAFHV_EXIT_NONE;
    event->detail = 0u;
    if (*pc > UINT32_MAX - 4u || (*pc & 3u) != 0u) {
        event->reason = RAFHV_EXIT_FAULT;
        event->detail = RAFHV_A32_BAD_PC;
        return 0;
    }
    if (!allow_exec(cpu->vm, *pc) || !load_word(cpu->vm, *pc, &inst)) {
        event->reason = RAFHV_EXIT_FAULT;
        event->detail = RAFHV_A32_FETCH;
        return 0;
    }
    if ((inst >> 28) != 14u) { /* Only AL condition is implemented. */
        event->reason = RAFHV_EXIT_FAULT;
        event->detail = RAFHV_A32_UNSUPPORTED;
        return 0;
    }
    next = *pc + 4u;
    rd = (inst >> 12) & 15u;
    rn = (inst >> 16) & 15u;
    op = inst & UINT32_C(0x0FE00000);
    if (op == UINT32_C(0x03A00000) && rd != 15u) { /* MOV immediate */
        cpu->r[rd] = ror_imm(inst);
    } else if (op == UINT32_C(0x02800000) && rd != 15u && rn != 15u) { /* ADD immediate */
        cpu->r[rd] = cpu->r[rn] + ror_imm(inst);
    } else if (op == UINT32_C(0x02400000) && rd != 15u && rn != 15u) { /* SUB immediate */
        cpu->r[rd] = cpu->r[rn] - ror_imm(inst);
    } else if ((inst & UINT32_C(0x0FE00000)) == UINT32_C(0x05800000) &&
               rd != 15u && rn != 15u) { /* LDR/STR word, pre-index, +imm, no writeback */
        uint32_t address = cpu->r[rn] + (inst & 4095u);
        if ((address & 3u) != 0u) {
            event->reason = RAFHV_EXIT_FAULT;
            event->detail = RAFHV_A32_MEMORY;
            return 0;
        }
        if ((inst & (1u << 20)) != 0u) {
            if (!load_word(cpu->vm, (uint64_t)address, &imm)) goto mem_fault;
            cpu->r[rd] = imm;
        } else {
            if (!store_word(cpu->vm, (uint64_t)address, cpu->r[rd])) goto mem_fault;
        }
    } else if ((inst & UINT32_C(0x0F000000)) == UINT32_C(0x0A000000) &&
               (inst & (1u << 24)) == 0u) { /* B, not BL */
        uint32_t displacement = (inst & UINT32_C(0x00FFFFFF)) << 2;
        if ((inst & UINT32_C(0x00800000)) != 0u)
            displacement |= UINT32_C(0xFC000000); /* Unsigned sign extension. */
        next = (uint32_t)((uint32_t)*pc + 8u + displacement);
    } else if ((inst & UINT32_C(0x0F000000)) == UINT32_C(0x0F000000)) { /* SVC */
        event->reason = RAFHV_EXIT_IO;
        event->detail = inst & UINT32_C(0x00FFFFFF);
    } else {
        event->reason = RAFHV_EXIT_FAULT;
        event->detail = RAFHV_A32_UNSUPPORTED;
        return 0;
    }
    *pc = next;
    cpu->r[15] = (uint32_t)next; /* Snapshot, not architectural PC+8 read semantics. */
    return 0;
mem_fault:
    event->reason = RAFHV_EXIT_FAULT;
    event->detail = RAFHV_A32_MEMORY;
    return 0;
}
