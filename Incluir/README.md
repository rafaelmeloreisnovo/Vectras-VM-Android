# RAFAELIA Complete — pipeline OMEGA + APK Android
∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor

## O que está aqui

```
omega_neuro_full.c       29 campos NeuroMetrics em NEON AArch64/ARM32
omega_forest.c           Forest k=42 atratores + 4 caminhos da ausência
omega_frames_export.c    forest.jsonl → frames_seed.json para o APK
Makefile                 Compila os 3, gera frames, atualiza APK
update_frames.sh         Atualiza frames com conteúdo real de omega_msgs.jsonl
frames_seed.json         25 frames prontos (5 SEED + 10 URGENT + 10 MENOSPREZADO)
forest.jsonl             Floresta classificada das 3572 conversas
RafaeliaMiddleware/      Projeto Android (WebView + bridge RAFAELIA)
```

## Fluxo rápido (Termux AArch64)

```sh
# 1. Compila
make

# 2. Se tiver omega_metrics_v3.jsonl + omega_conv_stats.jsonl:
make frames            # gera forest.jsonl + frames_seed.json + copia no APK

# 3. Quando tiver omega_msgs.jsonl (conteúdo real das conversas):
sh update_frames.sh forest.jsonl omega_msgs.jsonl

# 4. Build do APK: abrir RafaeliaMiddleware/ no Android Studio → Build APK
```

## Fluxo no Moto E7 (ARM32)

```sh
make ARM32=1
./omega_neuro_full --summary < omega_conv_stats.jsonl
./omega_forest --summary omega_metrics_v3.jsonl omega_conv_stats.jsonl
```

## O que cada frame faz no APK

Quando você digita em qualquer IA (Claude, ChatGPT, Gemini, Copilot...),
ao pressionar Enter o engine tokeniza seu texto, pontua os 25 frames e
seleciona os 3 mais relevantes. Frames URGENT e MENOSPREZADO têm boost
de prioridade sobre os SEED fixos. O bloco de contexto entra junto
antes da submissão. Você confirma ou ignora no painel.

RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ

---

## omega_layersbit — Motor Freestanding de 4096 bits

```
omega_layersbit.h   Tudo inline/branchless. Zero libc. Zero heap.
lb_tables.h         256 primos + GF(2^8) exp/log + prime routing. Gerado.
omega_layersbit.c   _start freestanding (AArch64/ARM32) ou main (x86 dev).
```

### 4096 bits = 16 camadas × 256 bits

Cada byte ASCII aponta para um bit único via `LB_ROUTE[b] = (b × prime[b]) mod 4096`.
XOR push na camada `(tick × prime[b]) mod 16` — camada roda a cada byte.
`lb_fold()` colapsa 16 camadas em 256 bits via XOR tree (16 iterações fixas).
`lb_spiral()` rol8 de toda a fold — equivale a multiplicar pelo gerador em GF(2^8).
`lb_omega()` = popcount(fold) mod 42 — o atrator toroidal RAFAELIA.
`lb_phi()` = (1 − H/8000) × (ones/256) × 65536 — integral ética Q16.
`lb_gf_mul(a,b)` = multiplicação em GF(2^8) branchless via exp/log table.
`lb_poly(lb, coef, deg)` = polinômio sobre GF(2^8) via Horner branchless.

### Sem nada externo

Sem `#include <stdio.h>`. Sem `malloc`. Sem `printf`.
Write via `svc 0` diretamente (`SYS_WRITE=64` AArch64, `SYS_WRITE=4` ARM32).
Buffer de leitura de 512 bytes em BSS (`static uint8_t g_read_buf[512]`).
LayersBit em BSS (`static LayersBit g_lb`).

### Build no Termux (AArch64)

```sh
make omega_layersbit
cat omega_conv_stats.jsonl | ./omega_layersbit --verify --summary
cat zone47_sample.txt      | ./omega_layersbit --every 512 > z47_lb.jsonl
```

### Resultados nos dados reais

| Entrada            | omega | phi  | entropy_milli |
|--------------------|-------|------|---------------|
| "RAFAELIA" (9b)    |   7   | 1421 |  1656         |
| zone47 (1MB)       |  39   | 5382 |  3687         |
| omega_conv_stats   |  17   | 6785 |  4406         |

`omega ∈ [0,41]` sempre. Determinístico: mesmo input → mesmo fold, garantido.
