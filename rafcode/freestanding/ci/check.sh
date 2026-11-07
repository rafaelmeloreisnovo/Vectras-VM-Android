#!/bin/sh
# SPDX-License-Identifier: MIT — CI tooling, not part of freestanding core.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
CC=${CC:-cc}
NM=${NM:-nm}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
FLAGS='-std=c11 -O2 -ffreestanding -fno-builtin -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -fdata-sections -ffunction-sections -Wall -Wextra -Werror -pedantic'
for f in raf_memory raf_path raf_dispatch raf_port; do
  "$CC" $FLAGS -I"$ROOT/include" -c "$ROOT/src/$f.c" -o "$OUT/$f.o"
done
# Validate closed module graph before any dead-section elimination.
"$CC" -nostdlib -r "$OUT/raf_memory.o" "$OUT/raf_path.o" "$OUT/raf_dispatch.o" "$OUT/raf_port.o" -o "$OUT/raf_all.o"
if "$NM" -u "$OUT/raf_all.o" | grep -q '[[:alnum:]_]'; then
  echo 'FAIL unresolved core symbol graph' >&2
  "$NM" -u "$OUT/raf_all.o"
  exit 1
fi
"$CC" -std=c11 -O2 -Wall -Wextra -Werror -pedantic -I"$ROOT/include" \
  "$ROOT/tests/test_raf_core.c" "$OUT/raf_memory.o" "$OUT/raf_path.o" "$OUT/raf_dispatch.o" "$OUT/raf_port.o" -o "$OUT/tests"
"$OUT/tests"
# A separate link-only ELF proves no libc at the FINAL LINK (not runnable).
"$CC" $FLAGS -I"$ROOT/include" -c "$ROOT/src/raf_link_probe.c" -o "$OUT/entry.o"
"$CC" -nostdlib -nostartfiles -nodefaultlibs -Wl,-e,raf_link_probe -Wl,--no-undefined \
  -Wl,--build-id=none -Wl,--gc-sections -no-pie \
  "$OUT/entry.o" "$OUT/raf_memory.o" "$OUT/raf_path.o" "$OUT/raf_dispatch.o" "$OUT/raf_port.o" \
  -o "$OUT/raf_link_probe"
if "$NM" -u "$OUT/raf_link_probe" | grep -q '[[:alnum:]_]'; then
  echo 'FAIL unresolved symbols final link' >&2; exit 1
fi
if readelf -d "$OUT/raf_link_probe" 2>/dev/null | grep -q NEEDED; then
  echo 'FAIL dynamic dependencies final link' >&2; exit 1
fi
printf 'PASS_SCOPED: isolated core; no unresolved symbols; no ELF DT_NEEDED; compiler=%s\n' "$CC"
# Optional cross-compiler smoke. Skipped if the configured compiler lacks targets.
if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for target in armv7a-none-eabi aarch64-none-elf; do
    for f in raf_memory raf_path raf_dispatch raf_port; do
      clang --target="$target" $FLAGS -I"$ROOT/include" -c "$ROOT/src/$f.c" -o "$OUT/$f-$target.o"
    done
  done
  clang --target=aarch64-none-elf -march=armv8-a+crc \
    -DRAF_ENABLE_AARCH64_CRC32C=1 $FLAGS -I"$ROOT/include" \
    -c "$ROOT/src/raf_memory.c" -o "$OUT/raf_memory-crc32c.o"
  echo 'PASS_SCOPED_CROSS_OBJECTS: ARMv7 EABI5, AArch64, AArch64 CRC32C opt-in'
fi
size "$OUT/raf_memory.o" "$OUT/raf_path.o" "$OUT/raf_dispatch.o" "$OUT/raf_port.o" || true
