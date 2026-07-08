# Makefile — RAFAELIA OMEGA tools
# ∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor
#
# Build AArch64 (Termux):   make
# Build ARM32 (Moto E7):    make ARM32=1
# Build x86_64:             make X86=1

CC     = cc
CFLAGS = -O2 -std=c11 -Wall -Wextra -Wno-unused-result -Wno-stringop-truncation

ifdef ARM32
  CFLAGS += -march=armv7-a -mfpu=neon-vfpv4 -mfloat-abi=softfp
else ifdef X86
  CFLAGS += -march=native
else
  CFLAGS += -march=armv8-a
endif

BINS = omega_neuro_full omega_forest omega_frames_export

.PHONY: all clean frames update_apk

all: $(BINS)
	@echo ""
	@echo "✅ Binários prontos: $(BINS)"

omega_neuro_full: omega_neuro_full.c
	$(CC) $(CFLAGS) $< -o $@ -lm
	@echo "  omega_neuro_full OK (29 campos NeuroMetrics)"

omega_forest: omega_forest.c
	$(CC) $(CFLAGS) $< -o $@ -lm
	@echo "  omega_forest OK (k=42 clusters, 4 caminhos)"

omega_frames_export: omega_frames_export.c
	$(CC) $(CFLAGS) $< -o $@
	@echo "  omega_frames_export OK (forest.jsonl → frames_seed.json)"

# Gera os frames reais a partir dos dados processados
frames: omega_forest omega_frames_export
	@echo "Gerando forest.jsonl..."
	./omega_forest omega_metrics_v3.jsonl omega_conv_stats.jsonl > forest.jsonl
	@echo "Exportando frames..."
	sh update_frames.sh forest.jsonl
	@echo "✅ frames_seed.json com $$(python3 -c \"import json; d=json.load(open('frames_seed.json')); print(len(d))\") frames"

# Copia frames_seed.json para o APK e faz zip
update_apk: frames
	@if [ -d "RafaeliaMiddleware/app/src/main/assets" ]; then \
		cp frames_seed.json RafaeliaMiddleware/app/src/main/assets/frames_seed.json; \
		echo "✅ APK assets atualizado"; \
	else \
		echo "⚠️ RafaeliaMiddleware/ não encontrado — copie frames_seed.json manualmente"; \
	fi

clean:
	rm -f $(BINS) forest.jsonl frames_seed.json frames_seed_exported.json frames_seed_real.json

# ── LayersBit freestanding ──────────────────────────────────────────
omega_layersbit: omega_layersbit.c omega_layersbit.h lb_tables.h
	$(CC) $(CFLAGS) -ffreestanding -nostdlib -nostartfiles \
	    $< -o $@
	@echo "  omega_layersbit OK (freestanding, branchless, 4096 bits)"
	@ls -lh $@ | awk '{print "  tamanho: " $$5}'

# Target freestanding para x86 dev (com libc write mas ffreestanding)
omega_layersbit_dev: omega_layersbit.c omega_layersbit.h lb_tables.h
	cc -O2 -std=c11 -ffreestanding -Wno-unused-function $< -o $@
	@echo "  omega_layersbit_dev OK (x86 dev build)"

all: $(BINS) omega_layersbit_dev
