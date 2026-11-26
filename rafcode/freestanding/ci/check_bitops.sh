#!/bin/sh
# SPDX-License-Identifier: MIT — hosted CI tooling; excluded from freestanding core.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
CC=${CC:-cc}
NM=${NM:-nm}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM

for profile in stable size speed; do
  case "$profile" in
    stable) opt='-O2' ;;
    size)   opt='-Os' ;;
    speed)  opt='-O3' ;;
  esac
  flags="-std=c11 $opt -ffreestanding -fno-builtin -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -fdata-sections -ffunction-sections -Wall -Wextra -Werror -pedantic"
  "$CC" $flags -I"$ROOT/include" -c "$ROOT/src/raf_bitops.c" -o "$OUT/bits.o"
  "$CC" $flags -I"$ROOT/include" -c "$ROOT/src/raf_bitops_link_probe.c" -o "$OUT/probe.o"
  if "$NM" -u "$OUT/bits.o" | grep -q '[[:alnum:]_]'; then
    echo "FAIL unresolved bitops symbols ($CC $profile)" >&2
    exit 1
  fi
  "$CC" -std=c11 "$opt" -Wall -Wextra -Werror -pedantic -I"$ROOT/include" \
     "$ROOT/tests/test_raf_bitops.c" "$OUT/bits.o" -o "$OUT/tests"
  "$OUT/tests"
  "$CC" -nostdlib -nostartfiles -nodefaultlibs -Wl,-e,raf_bitops_link_probe \
    -Wl,--no-undefined -Wl,--build-id=none -Wl,--gc-sections -no-pie \
    "$OUT/probe.o" "$OUT/bits.o" -o "$OUT/link-probe"
  if "$NM" -u "$OUT/link-probe" | grep -q '[[:alnum:]_]'; then
    echo "FAIL final-link undefined symbols ($CC $profile)" >&2
    exit 1
  fi
  if readelf -d "$OUT/link-probe" 2>/dev/null | grep -q NEEDED; then
    echo "FAIL final-link dynamic dependencies ($CC $profile)" >&2
    exit 1
  fi
  echo "PASS_SCOPED_BITOPS profile=$profile compiler=$CC; no libc/no undefined"
done

if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for target in armv7a-none-eabi aarch64-none-elf; do
    clang --target="$target" -std=c11 -O2 -ffreestanding -fno-builtin \
      -fno-stack-protector -Wall -Wextra -Werror -pedantic \
      -DRAF_ENABLE_ARM_BITOPS=1 -I"$ROOT/include" \
      -c "$ROOT/src/raf_bitops.c" -o "$OUT/$target.o"
    if "$NM" -u "$OUT/$target.o" | grep -q '[[:alnum:]_]'; then
      echo "FAIL cross bitops undefined symbols $target" >&2
      exit 1
    fi
    clang --target="$target" -std=c11 -O2 -ffreestanding \
      -DRAF_ENABLE_ARM_BITOPS=1 -I"$ROOT/include" \
      -S "$ROOT/src/raf_bitops.c" -o "$OUT/$target.s"
    grep -Eq '^[[:space:]]*rbit[[:space:]]' "$OUT/$target.s" || {
      echo "FAIL cross $target rbit instruction missing" >&2; exit 1; }
    grep -Eq '^[[:space:]]*rev[[:space:]]' "$OUT/$target.s" || {
      echo "FAIL cross $target rev instruction missing" >&2; exit 1; }
  done
  if clang --target=x86_64-none-elf -std=c11 -ffreestanding -Werror \
       -DRAF_ENABLE_ARM_BITOPS=1 -I"$ROOT/include" \
       -c "$ROOT/src/raf_bitops.c" -o "$OUT/negative.o" 2>/dev/null; then
    echo 'FAIL wrong-architecture ASM opt-in was accepted' >&2
    exit 1
  fi
  echo 'PASS_SCOPED_CROSS_ARM_BITOPS: ARMv7 EABI5/AArch64 rbit+rev, wrong-arch reject'
fi
