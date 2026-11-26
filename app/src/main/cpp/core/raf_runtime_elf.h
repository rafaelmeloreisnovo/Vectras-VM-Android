/* SPDX-License-Identifier: GPL-2.0-only */
/* RAFAELIA: independent, freestanding ELF header compatibility classifier.
 * NOT an ELF loader, signature verifier, dependency resolver, or boot proof.
 */
#ifndef RAF_RUNTIME_ELF_H
#define RAF_RUNTIME_ELF_H

/* Freestanding compiler headers only; no C runtime linkage. */
#include <stdint.h>

typedef enum raf_runtime_host_abi {
    RAF_HOST_UNKNOWN = 0,
    RAF_HOST_ARMV7 = 1,
    RAF_HOST_ARM64 = 2,
    RAF_HOST_X86 = 3,
    RAF_HOST_X86_64 = 4
} raf_runtime_host_abi;

typedef enum raf_runtime_elf_result {
    RAF_ELF_HEADER_MATCH_ONLY = 1,
    RAF_ELF_NULL_INPUT = -1,
    RAF_ELF_TRUNCATED = -2,
    RAF_ELF_BAD_MAGIC = -3,
    RAF_ELF_UNSUPPORTED_CLASS_ENDIAN = -4,
    RAF_ELF_BAD_HEADER = -5,
    RAF_ELF_UNSUPPORTED_MACHINE = -6,
    RAF_ELF_HOST_MISMATCH = -7,
    RAF_ELF_ARM_EABI_MISMATCH = -8,
    RAF_ELF_ARM_HARDFLOAT_MISMATCH = -9,
    RAF_ELF_HOST_UNKNOWN = -10
} raf_runtime_elf_result;

/* At most the first 64 bytes are inspected. Caller owns all I/O and lengths.
 * Return 1 means ONLY the ELF header appears compatible with this host ABI.
 * Does NOT prove complete ELF, interpreter, DT_NEEDED, QEMU/PRoot presence,
 * filesystem permissions, process execution or guest VM boot.
 */
int32_t raf_runtime_elf_header_probe(const uint8_t *bytes,
                                     uint32_t available,
                                     raf_runtime_host_abi host);

#endif
