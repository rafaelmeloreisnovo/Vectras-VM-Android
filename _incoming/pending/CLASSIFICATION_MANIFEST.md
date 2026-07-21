# _incoming/pending — Manifesto de Classificacao

**Data:** 2026-07-21
**Gap:** G16 — 51+ arquivos `.S` assembly em `_incoming/pending/` sem classificacao

## Decisao

Todos os arquivos `.S` neste diretorio sao **programas ARM32/ARM64 standalone** para
benchmarking e experimentacao da plataforma Rafaelia. Nenhum deles e um modulo JNI
ou biblioteca compativel com o sistema de build CMake/NDK do Vectras-VM-Android.

**Status:** ARQUIVADOS_PENDING — aguardando decisao do owner (Rafael) para:
a) mover para `engine/rmr/benchmarks/` se forem benchmarks a preservar, ou
b) mover para `_incoming/archived/` se forem experimentos descartaveis.

## Categorias identificadas

### Categoria A — Benchmarks Rafaelia (ARM32 Termux, standalone)
Compilavel com `as -march=armv7-a` ou `clang` no Termux. Usam `SYS_EXIT_GROUP=252`.

| Arquivo | Descricao | Destino sugerido |
|---------|-----------|-----------------|
| `raf_asm_b1.S` | B1 ARM32 — CRC32C + EMA + 42 atratores | benchmarks/ |
| `rafaelia_b1.S` - `rafaelia_b8.S` | Bloco B1-B8 sequenciais | benchmarks/ |
| `rafaelia_bench_phi.S` | Benchmark phi/golden-ratio | benchmarks/ |
| `rafaelia_936_fast.S` | Pipeline 936 fast-path | benchmarks/ |
| `rafaelia_999_logsin.S` | Log/sin benchmark | benchmarks/ |
| `rafaelia_final.S` | Versao final de benchmark | benchmarks/ |
| `rafaelia_final_bench.S` | Benchmark final variant | benchmarks/ |
| `rafaelia_final_seal.S` | Sealed final version | benchmarks/ |

### Categoria B — Algoritmos matematicos (NEON/AArch64)
Algoritmos geometricos, fractal, torus. Candidatos a promocao em `engine/rmr/`.

| Arquivo | Descricao | Destino sugerido |
|---------|-----------|-----------------|
| `rafaelia_7d.S` | Geometria 7D | engine/rmr/math/ |
| `rafaelia_7d_gyro.S` | Geometria 7D com giroscopio | engine/rmr/math/ |
| `rafaelia_7d_shapes.S` | Formas 7D | engine/rmr/math/ |
| `rafaelia_10x10.S` | Matriz 10x10 | engine/rmr/math/ |
| `rafaelia_8way.S` | 8-way split | engine/rmr/math/ |
| `rafaelia_torus*.S` (multiplos) | Fluxo torus | engine/rmr/math/ |

### Categoria C — Utilitarios especificos (decision/delta/chrono)

| Arquivo | Descricao | Destino sugerido |
|---------|-----------|-----------------|
| `rafaelia_decision.S` | Motor de decisao | engine/rmr/core/ ou archived/ |
| `rafaelia_delta.S` | Delta encoder | engine/rmr/core/ ou archived/ |
| `rafaelia_chrono.S` | Timer de alta precisao | engine/rmr/core/ ou archived/ |
| `rafaelia_central_link.S` | Link central | archived/ |
| `rafaelia_abs.S` | Valor absoluto | archived/ (duplica MathUtils) |

### Categoria D — Experimentos/variantes (arquivar)

| Arquivo | Descricao | Destino sugerido |
|---------|-----------|-----------------|
| `r.S` | Experimento generico | archived/ |
| `rafaelia_fix.S` | Fix de experimento | archived/ |
| `rafaelia_avalanche.S` | Avalanche hash v1 | archived/ |
| `rafaelia_avalanche_v2.S` | Avalanche hash v2 | archived/ |
| `rafaelia_equitas.S` | Modulo equitas | archived/ |
| Demais `rafaelia_*.S` | Variantes e experimentos | archived/ |

## Arquivos nao-.S neste diretorio (tambem pendentes)

| Arquivo | Tipo | Acao recomendada |
|---------|------|-----------------|
| `Android_nomalloc.mk` | Android.mk template | archived/ |
| `Application.mk` | NDK App.mk | archived/ (configuracao legacy) |
| `RAFAELIA_MATH_FORMULAS.md` | Documentacao de formulas | docs/formulas/ |
| `RafaeliaCore.java` | Classe Java bridge | avaliar integracao ou archived/ |
| `baremetal_nomalloc.c` / `.h` | C bare-metal | engine/rmr/ ou archived/ |
| `bitraf64_prototype_Version4.py` | Prototipo Python | tools/research/ ou archived/ |
| `bitstack.c` / `.h` | Pilha bit-level | engine/rmr/ ou archived/ |
| `build_all.sh`, `diagnose*.sh` | Scripts de build/debug | tools/ ou archived/ |
| `hyperforms.json` | Config hyperforms | archived/ |

## Criterio de saida para G16

G16 sera FECHADO quando:
1. Todos os arquivos `.S` tiverem sido movidos para destino definitivo, OU
2. Owner (Rafael) confirmar que os arquivos sao descartaveis e o diretorio for limpo.

**Owner responsavel:** Rafael
**Acao minima aceitavel:** Mover para `_incoming/archived/` todos os arquivos que
nao forem promovidos ao build principal dentro de 30 dias.
