# ============================================================
# RAFAELIA ARM32 — Makefile
# ∆RafaelVerboΩ | ΣΩΔΦBITRAF | FIAT LUX
#
# USO NO TERMUX (Android ARM32/ARM64):
#   pkg install clang make
#   make termux-safe
#
# USO CROSS (Linux → ARM32):
#   apt install gcc-arm-linux-gnueabihf
#   make cross
#
# LIMPAR:
#   make clean
# ============================================================

TARGET   = rafaelia_arm32
SRC      = rafaelia_arm32.c

# Flags comuns (zero libc, zero builtin, zero stack-protector)
CFLAGS_COMMON = -O2 -nostdlib -static \
                -fno-stack-protector \
                -fno-builtin \
                -e _start

# ---- Termux (clang nativo no dispositivo) ----
CLANG_TERMUX = clang
ARCH_TERMUX  = -target armv7a-linux-androideabi -march=armv7-a -mfloat-abi=softfp

termux: termux-safe

termux-safe:
	$(CLANG_TERMUX) $(CFLAGS_COMMON) $(ARCH_TERMUX) -o $(TARGET) $(SRC)
	@echo ""
	@echo "✔ Compilado seguro: ./$(TARGET)"
	@echo "  Executar: ./$(TARGET)"
	@echo "  MIDR    : fallback seguro (sem MRC p15)"
	@echo "  Tamanho : $$(ls -lh $(TARGET) | awk '{print $$5}')"

termux-midr:
	$(CLANG_TERMUX) $(CFLAGS_COMMON) $(ARCH_TERMUX) -DRAFAELIA_ENABLE_MIDR -o $(TARGET) $(SRC)
	@echo ""
	@echo "✔ Compilado com MIDR probe: ./$(TARGET)"
	@echo "  Atenção : pode gerar SIGILL em alguns Android/userland"
	@echo "  Tamanho : $$(ls -lh $(TARGET) | awk '{print $$5}')"

# ---- Cross-compile (Linux host → ARM32) ----
CC_CROSS  = arm-linux-gnueabihf-gcc
ARCH_CROSS = -march=armv7-a -mfloat-abi=soft

cross:
	$(CC_CROSS) $(CFLAGS_COMMON) $(ARCH_CROSS) -o $(TARGET) $(SRC)
	@echo ""
	@echo "✔ Cross-compilado: ./$(TARGET)"
	@file $(TARGET)
	@ls -lh $(TARGET)

# ---- Inspecionar binário ----
info:
	file $(TARGET)
	size $(TARGET)

# ---- Disassembly parcial ----
asm:
	arm-linux-gnueabihf-objdump -d $(TARGET) | head -120

clean:
	rm -f $(TARGET)

.PHONY: termux termux-safe termux-midr cross info asm clean
