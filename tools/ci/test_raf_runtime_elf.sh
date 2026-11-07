#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE="$ROOT/app/src/main/cpp/core"
CC="${CC:-cc}"
NM="${NM:-nm}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
"$CC" -std=c11 -O2 -ffreestanding -fno-builtin -fno-stack-protector \
    -fno-unwind-tables -Wall -Wextra -Werror -c "$CORE/raf_runtime_elf.c" -o "$TMP/core.o"
if "$NM" -u "$TMP/core.o" | grep -q '[[:alnum:]_]'; then
    echo 'FAIL: external unresolved ELF core symbols' >&2
    "$NM" -u "$TMP/core.o"
    exit 1
fi
cat > "$TMP/test.c" <<'C'
#include <stdint.h>
#include <stdio.h>
#include "raf_runtime_elf.h"
static unsigned int count;
static void put16(uint8_t *b, unsigned int i, uint16_t v) {
    b[i] = (uint8_t)v; b[i+1] = (uint8_t)(v >> 8);
}
static void put32(uint8_t *b, unsigned int i, uint32_t v) {
    b[i] = (uint8_t)v; b[i+1] = (uint8_t)(v >> 8);
    b[i+2] = (uint8_t)(v >> 16); b[i+3] = (uint8_t)(v >> 24);
}
static void elf(uint8_t *b, unsigned int cls, unsigned int mach) {
    unsigned int i;
    for (i=0; i<64; ++i) b[i]=0;
    b[0]=0x7f; b[1]='E'; b[2]='L'; b[3]='F'; b[4]=(uint8_t)cls;
    b[5]=1; b[6]=1;
    put16(b,16,2); put16(b,18,(uint16_t)mach); put32(b,20,1);
    if (cls==1) {
        put32(b,28,52); put16(b,40,52); put16(b,42,32); put16(b,44,1);
        if(mach==40) put32(b,36,0x05000000U);
    } else {
        put32(b,32,64); put16(b,52,64); put16(b,54,56); put16(b,56,1);
    }
}
#define CHECK(expect, ptr, size, abi) do { \
    int32_t got = raf_runtime_elf_header_probe((ptr),(size),(abi)); \
    ++count; if (got != (expect)) { \
      fprintf(stderr,"FAIL test=%u expected=%d got=%d\n",count,(int)(expect),(int)got);return 1; \
    } \
} while (0)
int main(void) {
    uint8_t b[64];
    CHECK(RAF_ELF_NULL_INPUT,0,64,RAF_HOST_ARMV7);
    elf(b,1,40);
    CHECK(RAF_ELF_HEADER_MATCH_ONLY,b,52,RAF_HOST_ARMV7);
    CHECK(RAF_ELF_TRUNCATED,b,51,RAF_HOST_ARMV7);
    CHECK(RAF_ELF_HOST_UNKNOWN,b,52,RAF_HOST_UNKNOWN);
    CHECK(RAF_ELF_HOST_MISMATCH,b,52,RAF_HOST_X86);
    CHECK(RAF_ELF_HOST_MISMATCH,b,52,RAF_HOST_ARM64);
    b[0]=0; CHECK(RAF_ELF_BAD_MAGIC,b,52,RAF_HOST_ARMV7); b[0]=0x7f;
    b[5]=2; CHECK(RAF_ELF_UNSUPPORTED_CLASS_ENDIAN,b,52,RAF_HOST_ARMV7); b[5]=1;
    b[6]=0; CHECK(RAF_ELF_BAD_HEADER,b,52,RAF_HOST_ARMV7); b[6]=1;
    put16(b,16,1); CHECK(RAF_ELF_BAD_HEADER,b,52,RAF_HOST_ARMV7); put16(b,16,2);
    put32(b,36,0x04000000); CHECK(RAF_ELF_ARM_EABI_MISMATCH,b,52,RAF_HOST_ARMV7);
    put32(b,36,0x05000400); CHECK(RAF_ELF_ARM_HARDFLOAT_MISMATCH,b,52,RAF_HOST_ARMV7);
    put32(b,36,0x05000000);
    put16(b,18,0xffff); CHECK(RAF_ELF_UNSUPPORTED_MACHINE,b,52,RAF_HOST_ARMV7);
    elf(b,2,183);
    CHECK(RAF_ELF_HEADER_MATCH_ONLY,b,64,RAF_HOST_ARM64);
    CHECK(RAF_ELF_TRUNCATED,b,63,RAF_HOST_ARM64);
    CHECK(RAF_ELF_HOST_MISMATCH,b,64,RAF_HOST_X86_64);
    elf(b,1,3); CHECK(RAF_ELF_HEADER_MATCH_ONLY,b,52,RAF_HOST_X86);
    elf(b,2,62); CHECK(RAF_ELF_HEADER_MATCH_ONLY,b,64,RAF_HOST_X86_64);
    put16(b,56,0); CHECK(RAF_ELF_BAD_HEADER,b,64,RAF_HOST_X86_64);
    elf(b,1,40); put16(b,42,0); CHECK(RAF_ELF_BAD_HEADER,b,52,RAF_HOST_ARMV7);
    puts("PASS_SCOPED_HOST: 20 ELF header vectors; no external core symbols");
    return 0;
}
C
"$CC" -std=c11 -O2 -Wall -Wextra -Werror -I"$CORE" \
    "$TMP/test.c" "$TMP/core.o" -o "$TMP/test"
"$TMP/test"
