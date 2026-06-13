# SPDX-License-Identifier: GPL-2.0-only
# conjunto_de_conceitos / Makefile
# Autoria: Rafael Melo Reis. Clean-room.
# Build freestanding (sem libc) com clang+lld. Alvos: termux(arm64 nativo),
# arm64/arm32 (cross), host(x86_64 porta de referencia que roda e testa a logica).

CC      ?= clang
SRC      = src/disc.c src/prim.c src/net.c
COMMON   = -ffreestanding -fno-builtin -nostdlib -fno-stack-protector -Wall -Wextra -O2
LDF      = -nostdlib -static -fuse-ld=lld -Wl,-e,_start

.PHONY: all termux arm64 arm32 host verify clean

all: host

# Em Termux/Android (aarch64) o clang nativo ja mira aarch64-linux-android.
termux:
	$(CC) $(COMMON) $(LDF) $(SRC) -o discador

arm64:
	$(CC) --target=aarch64-linux-android $(COMMON) $(LDF) $(SRC) -o discador.arm64

arm32:
	$(CC) --target=armv7-linux-gnueabihf -mfpu=neon $(COMMON) $(LDF) $(SRC) -o discador.arm32

# Porta de referencia x86_64: roda no host para validar a logica (sem NEON).
host:
	$(CC) $(COMMON) $(LDF) $(SRC) -o discador.host

verify:
	sh tools/verify_codegen.sh

clean:
	rm -f discador discador.arm64 discador.arm32 discador.host *.o
