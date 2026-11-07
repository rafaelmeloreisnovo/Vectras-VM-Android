/* SPDX-License-Identifier: GPL-2.0-only */
#include "raf_runtime_elf.h"

/* Little-endian reads on bounded caller-provided bytes. No libc, heap, or I/O. */
static uint16_t le16(const uint8_t *p) {
    return (uint16_t)((uint16_t)p[0] | ((uint16_t)p[1] << 8));
}
static uint32_t le32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

int32_t raf_runtime_elf_header_probe(const uint8_t *b,
                                     uint32_t n,
                                     raf_runtime_host_abi host) {
    uint32_t expected_class;
    uint16_t expected_machine;
    uint16_t phentsize;
    uint16_t phnum;
    uint32_t flags;
    if (b == (const uint8_t *)0) return RAF_ELF_NULL_INPUT;
    if (n < 20U) return RAF_ELF_TRUNCATED;
    if (b[0] != 0x7fU || b[1] != 'E' || b[2] != 'L' || b[3] != 'F')
        return RAF_ELF_BAD_MAGIC;
    if ((b[4] != 1U && b[4] != 2U) || b[5] != 1U)
        return RAF_ELF_UNSUPPORTED_CLASS_ENDIAN;
    if (b[6] != 1U) return RAF_ELF_BAD_HEADER;
    if (host == RAF_HOST_ARMV7) {
        expected_class = 1U; expected_machine = 40U;
    } else if (host == RAF_HOST_ARM64) {
        expected_class = 2U; expected_machine = 183U;
    } else if (host == RAF_HOST_X86) {
        expected_class = 1U; expected_machine = 3U;
    } else if (host == RAF_HOST_X86_64) {
        expected_class = 2U; expected_machine = 62U;
    } else {
        return RAF_ELF_HOST_UNKNOWN;
    }
    if (n < (b[4] == 1U ? 52U : 64U)) return RAF_ELF_TRUNCATED;
    if (le16(b + 16U) != 2U && le16(b + 16U) != 3U)
        return RAF_ELF_BAD_HEADER; /* must be ET_EXEC or ET_DYN */
    if (le32(b + 20U) != 1U) return RAF_ELF_BAD_HEADER;
    if (b[4] == 1U) {
        if (le16(b + 40U) != 52U) return RAF_ELF_BAD_HEADER;
        phentsize = le16(b + 42U);
        phnum = le16(b + 44U);
        if (le32(b + 28U) == 0U || phentsize != 32U || phnum == 0U)
            return RAF_ELF_BAD_HEADER;
        flags = le32(b + 36U);
    } else {
        if (le16(b + 52U) != 64U) return RAF_ELF_BAD_HEADER;
        phentsize = le16(b + 54U);
        phnum = le16(b + 56U);
        /* p_offset is a 64-bit field; verify either word nonzero. */
        if ((le32(b + 32U) | le32(b + 36U)) == 0U ||
                phentsize != 56U || phnum == 0U)
            return RAF_ELF_BAD_HEADER;
        flags = le32(b + 48U);
    }
    if (le16(b + 18U) != 40U && le16(b + 18U) != 183U &&
        le16(b + 18U) != 3U && le16(b + 18U) != 62U)
        return RAF_ELF_UNSUPPORTED_MACHINE;
    if (b[4] != expected_class || le16(b + 18U) != expected_machine)
        return RAF_ELF_HOST_MISMATCH;
    if (host == RAF_HOST_ARMV7) {
        /* Linux/Android ARM EABI5. Unknown EABI is NOT a positive match. */
        if ((flags & 0xff000000U) != 0x05000000U)
            return RAF_ELF_ARM_EABI_MISMATCH;
        /* Android ARM32 user binaries use softfp calling convention. */
        if ((flags & 0x00000400U) != 0U)
            return RAF_ELF_ARM_HARDFLOAT_MISMATCH;
    }
    return RAF_ELF_HEADER_MATCH_ONLY;
}
