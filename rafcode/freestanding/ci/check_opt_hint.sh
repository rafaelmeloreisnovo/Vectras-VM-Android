#!/bin/sh
# SPDX-License-Identifier: MIT — host CI adapter, NOT a runtime dependency.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
CC=${CC:-cc}
NM=${NM:-nm}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
base='-std=c11 -ffreestanding -fno-builtin -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -ffunction-sections -fdata-sections -Wall -Wextra -Werror -pedantic'
"$CC" $base -O2 -I"$ROOT/include" -c "$ROOT/src/raf_opt_hint.c" -o "$OUT/hint.o"
if "$NM" -u "$OUT/hint.o" | grep -q '[[:alnum:]_]'; then
  echo 'FAIL: hints runtime has undefined imports' >&2; exit 1
fi
"$CC" -std=c11 -O2 -Wall -Wextra -Werror -pedantic -I"$ROOT/include" \
  "$ROOT/ci/raf_opt_cli.c" "$OUT/hint.o" -o "$OUT/cli"
# The source's own comment determines this build's allowlisted flags.
selected=$("$OUT/cli" < "$ROOT/src/raf_opt_kernel.c")
case "$selected" in
  '-O2 -DRAF_OPT_UNROLL=1'|'-O2 -DRAF_OPT_UNROLL=4'|\
  '-Os -DRAF_OPT_UNROLL=1'|'-Os -DRAF_OPT_UNROLL=4'|\
  '-O3 -DRAF_OPT_UNROLL=1'|'-O3 -DRAF_OPT_UNROLL=4') ;;
  *) echo 'FAIL: emitted nonallowlisted compiler flags' >&2; exit 1 ;;
esac
printf '%s\n' "SOURCE_COMMENT_FLAGS=$selected"
if printf '%s' '/* RAF_OPT:PROFILE=size;UNROLL=4;CMD=sh */' | "$OUT/cli" >/dev/null 2>&1; then
  echo 'FAIL: injected annotation accepted' >&2; exit 1
fi
if printf '%s' '/* RAF_OPT:PROFILE=stable;UNROLL=4 */ /* RAF_OPT:PROFILE=stable;UNROLL=4 */' | "$OUT/cli" >/dev/null 2>&1; then
  echo 'FAIL: duplicate annotation accepted' >&2; exit 1
fi
for profile in stable size speed; do
  case "$profile" in stable) opt=-O2;; size) opt=-Os;; speed) opt=-O3;; esac
  "$CC" $base "$opt" -DRAF_OPT_UNROLL=1 -I"$ROOT/include" \
    -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/kern_base.o"
  "$CC" $base "$opt" -DRAF_OPT_UNROLL=4 -I"$ROOT/include" \
    -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/kern_hint.o"
  for variant in base hint; do
    "$CC" -std=c11 "$opt" -Wall -Wextra -Werror -pedantic -I"$ROOT/include" \
      "$ROOT/tests/test_raf_opt_hint.c" "$OUT/hint.o" "$OUT/kern_$variant.o" \
      -o "$OUT/test"
    "$OUT/test"
    "$CC" -nostdlib -nostartfiles -nodefaultlibs -no-pie \
      -Wl,--no-undefined -Wl,--build-id=none \
      -Wl,-e,raf_opt_link_probe -Wl,--gc-sections \
      "$OUT/hint.o" "$OUT/kern_$variant.o" -o "$OUT/link_only"
    if "$NM" -u "$OUT/link_only" | grep -q '[[:alnum:]_]'; then
      echo 'FAIL: undefined in final ELF' >&2; exit 1
    fi
    if readelf -d "$OUT/link_only" 2>/dev/null | grep -q NEEDED; then
      echo 'FAIL: runtime dependency in final ELF' >&2; exit 1
    fi
    echo "PASS_SCOPED_OPT compiler=$CC profile=$profile variant=$variant"
  done
done
"$CC" $base $selected -I"$ROOT/include" -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/annotated.o"
# The kernel object intentionally references the parser module. Reject only
# unresolved symbols outside that explicitly allowlisted module boundary.
if "$NM" -u "$OUT/annotated.o" | grep -Ev '^[[:space:]]+U (raf_opt_parse|raf_opt_emit_flags)
if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for target in armv7a-none-eabi aarch64-none-elf; do
    clang --target="$target" $base -O2 -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_hint.c" -o "$OUT/$target-parser.o"
    clang --target="$target" $base $selected -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$target-kernel.o"
    if "$NM" -u "$OUT/$target-parser.o" | grep -q '[[:alnum:]_]'; then
      echo "FAIL cross parser has external symbols $target" >&2; exit 1
    fi
    if "$NM" -u "$OUT/$target-kernel.o" | grep -Ev '^[[:space:]]+U (raf_opt_parse|raf_opt_emit_flags)
  done
  if clang --target=armv7a-none-eabi $base -DRAF_OPT_UNROLL=3 \
     -I"$ROOT/include" -c "$ROOT/src/raf_opt_kernel.c" \
     -o "$OUT/negative.o" >/dev/null 2>&1; then
    echo 'FAIL invalid unroll factor accepted' >&2; exit 1
  fi
  echo 'PASS_SCOPED_CROSS_OPT ARMv7 EABI5+AArch64; invalid factor rejected'
fi
 | grep -q '[[:alnum:]_]'; then
  echo 'FAIL: annotated kernel has imports beyond the parser contract' >&2; exit 1
fi
if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for target in armv7a-none-eabi aarch64-none-elf; do
    clang --target="$target" $base -O2 -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_hint.c" -o "$OUT/$target-parser.o"
    clang --target="$target" $base $selected -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$target-kernel.o"
    if "$NM" -u "$OUT/$target-parser.o" "$OUT/$target-kernel.o" | grep -q '[[:alnum:]_]'; then
      echo "FAIL cross unresolved symbols $target" >&2; exit 1
    fi
  done
  if clang --target=armv7a-none-eabi $base -DRAF_OPT_UNROLL=3 \
     -I"$ROOT/include" -c "$ROOT/src/raf_opt_kernel.c" \
     -o "$OUT/negative.o" >/dev/null 2>&1; then
    echo 'FAIL invalid unroll factor accepted' >&2; exit 1
  fi
  echo 'PASS_SCOPED_CROSS_OPT ARMv7 EABI5+AArch64; invalid factor rejected'
fi
 | grep -q '[[:alnum:]_]'; then
      echo "FAIL cross kernel imports beyond parser $target" >&2; exit 1
    fi
  done
  if clang --target=armv7a-none-eabi $base -DRAF_OPT_UNROLL=3 \
     -I"$ROOT/include" -c "$ROOT/src/raf_opt_kernel.c" \
     -o "$OUT/negative.o" >/dev/null 2>&1; then
    echo 'FAIL invalid unroll factor accepted' >&2; exit 1
  fi
  echo 'PASS_SCOPED_CROSS_OPT ARMv7 EABI5+AArch64; invalid factor rejected'
fi
 | grep -q '[[:alnum:]_]'; then
  echo 'FAIL: annotated kernel has imports beyond the parser contract' >&2; exit 1
fi
if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for target in armv7a-none-eabi aarch64-none-elf; do
    clang --target="$target" $base -O2 -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_hint.c" -o "$OUT/$target-parser.o"
    clang --target="$target" $base $selected -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$target-kernel.o"
    if "$NM" -u "$OUT/$target-parser.o" "$OUT/$target-kernel.o" | grep -q '[[:alnum:]_]'; then
      echo "FAIL cross unresolved symbols $target" >&2; exit 1
    fi
  done
  if clang --target=armv7a-none-eabi $base -DRAF_OPT_UNROLL=3 \
     -I"$ROOT/include" -c "$ROOT/src/raf_opt_kernel.c" \
     -o "$OUT/negative.o" >/dev/null 2>&1; then
    echo 'FAIL invalid unroll factor accepted' >&2; exit 1
  fi
  echo 'PASS_SCOPED_CROSS_OPT ARMv7 EABI5+AArch64; invalid factor rejected'
fi
