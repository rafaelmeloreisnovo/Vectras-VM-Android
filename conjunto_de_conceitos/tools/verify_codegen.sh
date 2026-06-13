#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
# conjunto_de_conceitos / verify_codegen.sh
# Autoria: Rafael Melo Reis. Clean-room.
# Prova reproduzivel SEM qemu: cross-compila, linka freestanding e confirma
# por disassembly que (a) nao ha simbolos indefinidos (sem libc) e
# (b) o kernel SIMD emite instrucoes NEON reais. Runtime ARM = no dispositivo.
set -e

CC=${CC:-clang}
COMMON="-ffreestanding -fno-builtin -nostdlib -fno-stack-protector -O2"
SRC="src/disc.c src/prim.c src/net.c"
OD=${OBJDUMP:-llvm-objdump}
NM=${NM:-llvm-nm}

echo "== [1/3] aarch64: link freestanding =="
$CC --target=aarch64-linux-android $COMMON -nostdlib -static -fuse-ld=lld \
    -Wl,-e,_start $SRC -o /tmp/cdc.arm64.elf
U=$($NM -u /tmp/cdc.arm64.elf | wc -l)
echo "   simbolos indefinidos: $U  (esperado 0)"
test "$U" -eq 0 || { echo "FALHA: ha simbolos indefinidos"; exit 1; }

echo "== [2/3] aarch64: NEON presente no kernel SIMD =="
N=$($OD -d /tmp/cdc.arm64.elf | sed -n '/<cdc_sum8_simd>:/,/^$/p' \
     | grep -cE "uaddlp|uadalp|ldr[[:space:]]+q[0-9]+")
echo "   instrucoes NEON no cdc_sum8_simd: $N  (esperado > 0)"
test "$N" -gt 0 || { echo "FALHA: NEON ausente"; exit 1; }

echo "== [3/3] armv7: compila com NEON =="
$CC --target=armv7-linux-gnueabihf -mfpu=neon $COMMON -c src/prim.c -o /tmp/cdc.prim7.o
echo "   armv7 OK"

echo "OK: codegen verificado (aarch64 link + NEON, armv7 compile)."
