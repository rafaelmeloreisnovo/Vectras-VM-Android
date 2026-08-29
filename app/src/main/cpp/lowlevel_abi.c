#include "lowlevel_abi.h"

static const vectra_lowlevel_abi_descriptor_t kVectraLowlevelAbiDescriptors[VECTRA_LL_ARCH_COUNT] = {
    {
        VECTRA_LL_ARCH_ARM64_V8A,
        "arm64-v8a",
        1,
        0,
        16,
        VECTRA_LL_FRAME_LEAF_OPTIONAL,
        "stp x29, x30, [sp, #-16]!; mov x29, sp",
        "ldp x29, x30, [sp], #16; ret",
        VECTRA_LL_SYSCALL_HOST_ADAPTER_ONLY,
        {"x0-x7", "x0-x1", "x0-x18,v0-v31", "x19-x28,x29(fp),sp"}
    },
    {
        VECTRA_LL_ARCH_ARMEABI_V7A,
        "armeabi-v7a",
        1,
        0,
        8,
        VECTRA_LL_FRAME_LEAF_OPTIONAL,
        "push {r7, lr}; add r7, sp, #0",
        "pop {r7, pc}",
        VECTRA_LL_SYSCALL_HOST_ADAPTER_ONLY,
        {"r0-r3", "r0-r1", "r0-r3,r12", "r4-r11,sp"}
    },
    {
        VECTRA_LL_ARCH_X86_64,
        "x86_64",
        1,
        0,
        16,
        VECTRA_LL_FRAME_LEAF_OPTIONAL,
        "push rbp; mov rbp, rsp",
        "leave; ret",
        VECTRA_LL_SYSCALL_ALLOWLISTED_GATE,
        {"rdi,rsi,rdx,rcx,r8,r9", "rax,rdx", "rax,rcx,rdx,rsi,rdi,r8-r11,xmm0-xmm15", "rbx,rbp,r12-r15,rsp"}
    },
    {
        VECTRA_LL_ARCH_X86,
        "x86",
        1,
        0,
        16,
        VECTRA_LL_FRAME_REQUIRED,
        "push ebp; mov ebp, esp",
        "leave; ret",
        VECTRA_LL_SYSCALL_ALLOWLISTED_GATE,
        {"stack(cdecl)", "eax,edx", "eax,ecx,edx", "ebx,esi,edi,ebp,esp"}
    },
    {
        VECTRA_LL_ARCH_RISCV64,
        "riscv64",
        1,
        0,
        16,
        VECTRA_LL_FRAME_LEAF_OPTIONAL,
        "addi sp, sp, -16; sd ra, 8(sp); sd s0, 0(sp); mv s0, sp",
        "ld s0, 0(sp); ld ra, 8(sp); addi sp, sp, 16; ret",
        VECTRA_LL_SYSCALL_HOST_ADAPTER_ONLY,
        {"a0-a7", "a0-a1", "a0-a7,t0-t6", "s0-s11,sp"}
    },
    {
        VECTRA_LL_ARCH_RISCV32,
        "riscv32",
        1,
        0,
        16,
        VECTRA_LL_FRAME_LEAF_OPTIONAL,
        "addi sp, sp, -16; sw ra, 12(sp); sw s0, 8(sp); mv s0, sp",
        "lw s0, 8(sp); lw ra, 12(sp); addi sp, sp, 16; ret",
        VECTRA_LL_SYSCALL_HOST_ADAPTER_ONLY,
        {"a0-a7", "a0-a1", "a0-a7,t0-t6", "s0-s11,sp"}
    },
    {
        VECTRA_LL_ARCH_MIPS64,
        "mips64",
        1,
        0,
        16,
        VECTRA_LL_FRAME_REQUIRED,
        "daddiu $sp, $sp, -32; sd $ra, 24($sp); sd $fp, 16($sp); move $fp, $sp",
        "move $sp, $fp; ld $fp, 16($sp); ld $ra, 24($sp); daddiu $sp, $sp, 32; jr $ra",
        VECTRA_LL_SYSCALL_ALLOWLISTED_GATE,
        {"$a0-$a7", "$v0-$v1", "$at,$v0-$v1,$a0-$a7,$t0-$t9", "$s0-$s7,$sp,$fp,$ra"}
    }
};

static const vectra_lowlevel_interop_rule_t kVectraLowlevelInteropRules[] = {
    {1, 0, 0, 1, 0, 0, 0, "strict: same minor only"},
    {1, 1, 3, 1, 0, 4, 1, "adaptive bridge: v1.x stable call frame"},
    {2, 0, 1, 1, 2, 9, 1, "backport bridge: shim for extended metadata"},
    {2, 0, 1, 2, 0, 1, 0, "strict: same major v2"}
};

const vectra_lowlevel_abi_descriptor_t* vectra_lowlevel_abi_descriptors(uint32_t* count) {
    if (count != (void*)0) {
        *count = (uint32_t)VECTRA_LL_ARCH_COUNT;
    }
    return kVectraLowlevelAbiDescriptors;
}

const vectra_lowlevel_interop_rule_t* vectra_lowlevel_abi_interop_rules(uint32_t* count) {
    if (count != (void*)0) {
        *count = (uint32_t)(sizeof(kVectraLowlevelInteropRules) / sizeof(kVectraLowlevelInteropRules[0]));
    }
    return kVectraLowlevelInteropRules;
}

int vectra_lowlevel_validate_interop(uint16_t producer_major,
                                     uint16_t producer_minor,
                                     uint16_t consumer_major,
                                     uint16_t consumer_minor,
                                     uint8_t* adaptive_bridge_enabled) {
    uint32_t i;
    uint32_t count = (uint32_t)(sizeof(kVectraLowlevelInteropRules) / sizeof(kVectraLowlevelInteropRules[0]));

    if (adaptive_bridge_enabled != (void*)0) {
        *adaptive_bridge_enabled = 0;
    }

    for (i = 0; i < count; ++i) {
        const vectra_lowlevel_interop_rule_t* rule = &kVectraLowlevelInteropRules[i];
        if (producer_major != rule->producer_major || consumer_major != rule->consumer_major) {
            continue;
        }
        if (producer_minor < rule->producer_minor_min || producer_minor > rule->producer_minor_max) {
            continue;
        }
        if (consumer_minor < rule->consumer_minor_min || consumer_minor > rule->consumer_minor_max) {
            continue;
        }

        if (adaptive_bridge_enabled != (void*)0) {
            *adaptive_bridge_enabled = rule->adaptive_bridge;
        }
        return VECTRA_LL_ERR_OK;
    }

    return VECTRA_LL_ERR_UNSUPPORTED_ABI_VERSION;
}


const char* abi_entry_get_contract_version(void) {
    return VECTRA_LOWLEVEL_ABI_CONTRACT_VERSION;
}

const vectra_lowlevel_abi_descriptor_t* abi_entry_get_arch_descriptors(uint32_t* count) {
    return vectra_lowlevel_abi_descriptors(count);
}

const vectra_lowlevel_interop_rule_t* abi_entry_get_interop_rules(uint32_t* count) {
    return vectra_lowlevel_abi_interop_rules(count);
}

int abi_entry_validate_interop(uint16_t producer_major,
                               uint16_t producer_minor,
                               uint16_t consumer_major,
                               uint16_t consumer_minor,
                               uint8_t* adaptive_bridge_enabled) {
    return vectra_lowlevel_validate_interop(
        producer_major,
        producer_minor,
        consumer_major,
        consumer_minor,
        adaptive_bridge_enabled);
}

/*
 * VCAT is intentionally amalgamated into the existing freestanding archive.
 * This adds no hosted runtime target and keeps its libc/syscall boundary equal
 * to abi_core_freestanding. The specialist module itself contains no loops.
 */
#include "catalytic/vcat_core.inc"
