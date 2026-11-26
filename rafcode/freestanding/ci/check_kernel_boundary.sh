#!/bin/sh
# SPDX-License-Identifier: MIT — HOSTED verification script, not runtime.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
CC=${CC:-cc}
NM=${NM:-nm}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
FLAGS='-std=c11 -O2 -ffreestanding -fno-builtin -fno-pie -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -ffunction-sections -fdata-sections -fomit-frame-pointer -Wall -Wextra -Werror -pedantic'
MODULES='raf_memory raf_path raf_dispatch raf_port'
for f in $MODULES; do
  "$CC" $FLAGS -I"$ROOT/include" -c "$ROOT/src/$f.c" -o "$OUT/$f.o"
done
"$CC" $FLAGS -I"$ROOT/include" -I"$ROOT/platform/linux" -c "$ROOT/platform/linux/raf_linux_sys.c" -o "$OUT/sys.o"
if "$NM" -u "$OUT/sys.o" | grep -q '[[:alnum:]_]'; then
  echo 'FAIL standalone syscall adapter has external references' >&2; exit 1
fi
"$CC" $FLAGS -I"$ROOT/include" -I"$ROOT/platform/linux" -c "$ROOT/platform/linux/raf_linux_demo.c" -o "$OUT/demo.o"
"$CC" -c "$ROOT/platform/linux/start_x86_64.S" -o "$OUT/start.o"
"$CC" -nostdlib -nodefaultlibs -nostartfiles -static -Wl,-e,_start -Wl,--no-undefined -Wl,--gc-sections -Wl,--build-id=none \
  "$OUT/start.o" "$OUT/demo.o" "$OUT/sys.o" \
  "$OUT/raf_memory.o" "$OUT/raf_path.o" "$OUT/raf_dispatch.o" "$OUT/raf_port.o" -o "$OUT/linux_demo"
if "$NM" -u "$OUT/linux_demo" | grep -q '[[:alnum:]_]'; then
  echo 'FAIL external unresolved symbol' >&2; exit 1
fi
if readelf -d "$OUT/linux_demo" 2>/dev/null | grep -q 'NEEDED'; then
  echo 'FAIL shared library dependency' >&2; exit 1
fi
if readelf -l "$OUT/linux_demo" | grep -q 'INTERP'; then
  echo 'FAIL ELF interpreter dependency' >&2; exit 1
fi
if readelf -h "$OUT/linux_demo" | grep -q 'Type:.*DYN'; then
  echo 'FAIL runtime pie/dynamic type' >&2; exit 1
fi
printf '/alpha/gamma\n' > "$OUT/expected"
"$OUT/linux_demo" > "$OUT/actual"
cmp "$OUT/expected" "$OUT/actual"
readelf -h "$OUT/linux_demo" | grep -E 'Class:|Machine:|Type:'
wc -c "$OUT/linux_demo"
printf 'PASS_NATIVE_EXECUTION_NO_LIBC CC=%s\n' "$CC"
if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for pair in 'armv7a-none-eabi armv7' 'aarch64-none-elf aarch64'; do
    set -- $pair
    target=$1 arch=$2
    cross="$OUT/$arch"
    mkdir -p "$cross"
    for f in $MODULES; do
      clang --target="$target" $FLAGS -I"$ROOT/include" -c "$ROOT/src/$f.c" -o "$cross/$f.o"
    done
    clang --target="$target" $FLAGS -I"$ROOT/include" -I"$ROOT/platform/linux" -c "$ROOT/platform/linux/raf_linux_sys.c" -o "$cross/sys.o"
    if "$NM" -u "$cross/sys.o" | grep -q '[[:alnum:]_]'; then
      echo "FAIL standalone adapter unresolved $arch" >&2; exit 1
    fi
    clang --target="$target" $FLAGS -I"$ROOT/include" -I"$ROOT/platform/linux" -c "$ROOT/platform/linux/raf_linux_demo.c" -o "$cross/demo.o"
    clang --target="$target" -c "$ROOT/platform/linux/start_$arch.S" -o "$cross/start.o"
    clang --target="$target" -fuse-ld=lld -nostdlib -nodefaultlibs -static \
      -Wl,-e,_start -Wl,--no-undefined -Wl,--gc-sections -Wl,--build-id=none \
      "$cross/start.o" "$cross/demo.o" "$cross/sys.o" "$cross/raf_memory.o" \
      "$cross/raf_path.o" "$cross/raf_dispatch.o" "$cross/raf_port.o" -o "$cross/raf_linux_demo"
    if readelf -d "$cross/raf_linux_demo" 2>/dev/null | grep -q 'NEEDED'; then
      echo "FAIL shared deps $arch" >&2; exit 1
    fi
    if readelf -l "$cross/raf_linux_demo" | grep -q 'INTERP'; then
      echo "FAIL interpreter $arch" >&2; exit 1
    fi
    if "$NM" -u "$cross/raf_linux_demo" | grep -q '[[:alnum:]_]'; then
      echo "FAIL unresolved $arch" >&2; exit 1
    fi
    if [ "$arch" = armv7 ]; then
      readelf -h "$cross/raf_linux_demo" | grep -q 'Machine:.*ARM'
      readelf -h "$cross/raf_linux_demo" | grep -q 'Class:.*ELF32'
    else
      readelf -h "$cross/raf_linux_demo" | grep -q 'Machine:.*AArch64'
      readelf -h "$cross/raf_linux_demo" | grep -q 'Class:.*ELF64'
    fi
    printf 'PASS_CROSS_LINK_NO_LIBC %s\n' "$arch"
    readelf -h "$cross/raf_linux_demo" | grep -E 'Class:|Machine:|Type:'
  done
  # Deliberately reject unsupported Thumb-state for the ARM syscall register ABI.
  if clang --target=armv7a-none-eabi -mthumb $FLAGS -I"$ROOT/include" \
     -I"$ROOT/platform/linux" -c "$ROOT/platform/linux/raf_linux_sys.c" \
     -o "$OUT/forbidden_thumb.o" >"$OUT/thumb.log" 2>&1; then
    echo 'FAIL unsupported Thumb ABI accepted' >&2; exit 1
  fi
  grep -q 'supports only' "$OUT/thumb.log"
  echo 'PASS_NEGATIVE_THUMB_UNSUPPORTED'
fi
