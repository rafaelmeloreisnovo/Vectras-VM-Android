#!/bin/sh
# SPDX-License-Identifier: MIT — hosted static evidence only; NEVER a device benchmark.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
CC=${CC:-cc}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
base='-std=c11 -ffreestanding -fno-builtin -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables -ffunction-sections -fdata-sections -Wall -Wextra -Werror -pedantic'
command -v objdump >/dev/null
command -v size >/dev/null
command -v readelf >/dev/null
command -v sha256sum >/dev/null
echo "CODEGEN_TOOLCHAIN=$("$CC" --version | head -n 1)"
echo 'CODEGEN_SCOPE=STATIC_ONLY NOT_DEVICE NOT_PERFORMANCE NOT_EQUIVALENCE_PROOF'
for profile in stable size speed; do
  case "$profile" in stable) opt=-O2;; size) opt=-Os;; speed) opt=-O3;; esac
  for unroll in 1 4; do
    stamp="$profile-$unroll"
    "$CC" $base "$opt" -DRAF_OPT_UNROLL="$unroll" -I"$ROOT/include" \
      -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$stamp.o"
    "$CC" $base "$opt" -DRAF_OPT_UNROLL="$unroll" -I"$ROOT/include" \
      -S "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$stamp.s"
    objdump -dr "$OUT/$stamp.o" > "$OUT/$stamp.dis"
    if ! grep -q '<raf_opt_xor_u32>:' "$OUT/$stamp.dis"; then
      echo "FAIL missing target function in disassembly $stamp" >&2; exit 1
    fi
    text_bytes=$(size -A "$OUT/$stamp.o" | awk '$1 == ".text.raf_opt_xor_u32" { print $2 }')
    case "$text_bytes" in ''|0|*[!0-9]*) echo "FAIL missing/nonpositive text section $stamp" >&2; exit 1;; esac
    asm_digest=$(sha256sum "$OUT/$stamp.s" | awk '{print $1}')
    dis_digest=$(sha256sum "$OUT/$stamp.dis" | awk '{print $1}')
    echo "CODEGEN_EVIDENCE compiler=$CC profile=$profile unroll=$unroll text_bytes=$text_bytes asm_sha256=$asm_digest disasm_sha256=$dis_digest"
  done
done
if [ "${RAF_CROSS_CHECK:-0}" = 1 ]; then
  for target in armv7a-none-eabi aarch64-none-elf; do
    for profile in stable size speed; do
      case "$profile" in stable) opt=-O2;; size) opt=-Os;; speed) opt=-O3;; esac
      for unroll in 1 4; do
        stamp="$target-$profile-$unroll"
        clang --target="$target" $base "$opt" -DRAF_OPT_UNROLL="$unroll" \
          -I"$ROOT/include" -c "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$stamp.o"
        clang --target="$target" $base "$opt" -DRAF_OPT_UNROLL="$unroll" \
          -I"$ROOT/include" -S "$ROOT/src/raf_opt_kernel.c" -o "$OUT/$stamp.s"
        readelf -Ws "$OUT/$stamp.o" | grep -q 'raf_opt_xor_u32'
        case "$target" in
          armv7a-none-eabi) readelf -h "$OUT/$stamp.o" | grep -q 'Machine:.*ARM';;
          aarch64-none-elf) readelf -h "$OUT/$stamp.o" | grep -q 'Machine:.*AArch64';;
        esac
        asm_digest=$(sha256sum "$OUT/$stamp.s" | awk '{print $1}')
        asm_bytes=$(wc -c < "$OUT/$stamp.s" | tr -d ' ')
        echo "CODEGEN_CROSS_EVIDENCE target=$target profile=$profile unroll=$unroll asm_bytes=$asm_bytes asm_sha256=$asm_digest"
      done
    done
  done
fi
echo 'PASS_SCOPED_CODEGEN_EVIDENCE no runtime performance claims'
