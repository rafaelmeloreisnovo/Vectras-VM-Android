#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-only
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CC="${CC:-cc}"
NM="${NM:-nm}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
"$CC" -std=c11 -O2 -ffreestanding -fno-builtin -fno-stack-protector -Wall -Wextra -Werror \
  -c "$ROOT/app/src/main/cpp/core/raf_bench_probe.c" -o "$TMP/core.o"
if "$NM" -u "$TMP/core.o" | grep -q '[[:alnum:]_]'; then
    echo 'FAIL: freestanding object has undefined symbols' >&2
    "$NM" -u "$TMP/core.o"
    exit 1
fi
cat > "$TMP/test.c" <<'C'
#include "raf_bench_probe.h"
#include <stdio.h>
int main(void) {
    if (raf_bench_xorshift32(0x12345678u, 0u) != 0x12345678u) return 1;
    if (raf_bench_xorshift32(0x12345678u, 1u) != 0x87985aa5u) return 2;
    if (raf_bench_xorshift32(0x12345678u, 100u) != 0xc95a1a08u) return 3;
    if (raf_bench_xorshift32(0u, 100u) != 0u) return 4;
    puts("PASS_SCOPED_HOST: 4 vectors; pure core object has no undefined symbols");
    return 0;
}
C
"$CC" -std=c11 -O2 -Wall -Wextra -Werror -I"$ROOT/app/src/main/cpp/core" \
  "$TMP/test.c" "$TMP/core.o" -o "$TMP/test"
"$TMP/test"
