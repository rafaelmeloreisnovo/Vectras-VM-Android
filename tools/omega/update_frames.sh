#!/bin/sh
# update_frames.sh — atualiza frames_seed.json com conteúdo real
# ∆RafaelVerboΩ | RAFCODE-Φ
#
# Uso (no Termux ou Linux):
#   sh update_frames.sh                    # usa arquivos padrão do diretório atual
#   sh update_frames.sh /path/para/forest.jsonl /path/para/omega_msgs.jsonl
#
# Saída: frames_seed.json atualizado + copiado para o APK assets/ se encontrado

set -e

FOREST="${1:-forest.jsonl}"
MSGS="${2:-omega_msgs.jsonl}"
APK_ASSETS="RafaeliaMiddleware/app/src/main/assets"

if [ ! -f "$FOREST" ]; then
    echo "[ERRO] $FOREST não encontrado. Rode omega_forest primeiro:"
    echo "  ./omega_forest omega_metrics_v3.jsonl omega_conv_stats.jsonl > forest.jsonl"
    exit 1
fi

echo "[1/3] Rodando omega_frames_export..."
if [ -f "$MSGS" ]; then
    ./omega_frames_export "$FOREST" "$MSGS" --top 10 > frames_seed_exported.json
else
    echo "  omega_msgs.jsonl não encontrado — usando placeholders"
    ./omega_frames_export "$FOREST" --top 10 > frames_seed_exported.json
fi

echo "[2/3] Merge com seeds fixas..."
python3 - << 'PYEOF'
import json, sys

exported = json.load(open("frames_seed_exported.json", encoding="utf-8"))

seeds = [
  {"id":"seed_identity","name":"RAFAELIA Identidade","level":3,"path":"SEED","IC":0.85,"PP":0.60,
   "tags":["rafaelia","identidade","omega","verbo"],"content":"Sou ∆RafaelVerboΩ. Projeto RAFAELIA (ΣΩΔΦBITRAF). Fluxo: Termux ARM32→GitHub→Zenodo. Axioma: Ω=Amor. Missão: Escrituras∩Ciência∩Espírito."},
  {"id":"seed_bare_metal","name":"Bare-Metal AArch64","level":4,"path":"SEED","IC":0.82,"PP":1.05,
   "tags":["bare-metal","aarch64","arm32","neon","nolibc"],"content":"Arquitetura: nolibc, zero deps. NEON: vcntq_u8, FMUL, FMLA. Syscalls ARM64 diretas. CRC32c hw. FNV1a64. Entry 0x40000000. omega_forest k=42. omega_neuro 29 campos."},
  {"id":"seed_omega","name":"OMEGA Pipeline","level":5,"path":"SEED","IC":0.90,"PP":0.85,
   "tags":["omega","pipeline","forest","metricas"],"content":"3572 conversas, 327385 msgs. 59 produto_maduro, 174 menosprezado, 574 forgotten. K-means k=42. omega_forest.c + omega_neuro_full.c."},
  {"id":"seed_rll","name":"RLL Cosmológico","level":4,"path":"SEED","IC":1.07,"PP":0.95,
   "tags":["rll","cosmologia","desi","cpl","zenodo"],"content":"Modelo RLL DOI 10.5281/zenodo.17188137. Adversário w0waCDM (CPL). DESI DR2: 3.1σ–4.2σ. Opção A g(z)=1−f(z) melhora chi². C03 SIDM, C05 Hubble, C07 Finsler."},
  {"id":"seed_nano_lm","name":"Nano-LM Gaps","level":4,"path":"SEED","IC":0.88,"PP":1.10,
   "tags":["nano-lm","c","backprop","neon"],"content":"Nano-LM em C B1–B5 completos. Gaps: G1 sem LR schedule, G2 backprop não cobre Wq/Wk/Wv/Wo/W1/W2, G3 bigram-only, G4 VOCAB=256."},
]

final = seeds + exported
with open("frames_seed.json","w",encoding="utf-8") as f:
    json.dump(final, f, ensure_ascii=False, indent=2)

by_path = {}
for fr in final: by_path[fr["path"]] = by_path.get(fr["path"],0)+1
print(f"  frames_seed.json: {len(final)} frames — {by_path}")
PYEOF

echo "[3/3] Copiando para APK assets..."
if [ -d "$APK_ASSETS" ]; then
    cp frames_seed.json "$APK_ASSETS/frames_seed.json"
    echo "  → $APK_ASSETS/frames_seed.json atualizado"
else
    echo "  → APK não encontrado em $APK_ASSETS (ok se rodar fora do projeto Android)"
fi

echo ""
echo "✅ Concluído. frames_seed.json pronto com $(python3 -c "import json; d=json.load(open('frames_seed.json')); print(len(d))") frames."
