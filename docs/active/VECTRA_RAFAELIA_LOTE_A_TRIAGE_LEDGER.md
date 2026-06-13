# VECTRA_RAFAELIA_LOTE_A_TRIAGE_LEDGER

## Estado

`FATO_DOCUMENTADO`: ledger inicial do Lote A da matriz de triagem, focado em `Rafaelia/`.

Este arquivo não conclui a leitura de todos os itens. Ele inicia a classificação com evidência lida e protege `TOKEN_VAZIO` onde ainda não houve leitura completa.

---

## Frase canônica

```text
Rafaelia/ é incubadora técnica: não é lixo, não é automaticamente core, e não deve ser promovida em bloco.
```

---

## Fonte de contexto lida

| Arquivo | Evidência |
|---|---|
| `Rafaelia/README.md` | descreve B1–B4, hardware alvo, ARM32, NEON/VFPv4, cache, syscalls, Q16.16, build Termux |
| `Rafaelia/baremetal_nomalloc.h` | declara arena, matrix, vector ops, fast math, mem/string, arch/HW profile |
| `Rafaelia/baremetal_nomalloc.c` | implementa arena estática, HWCAP via auxv, fast math, mem/string, matrix sem malloc |
| `Rafaelia/rafaelia_b1.S` | implementa fundação ARM32 pura com syscalls, arena `mmap2`, CRC32C, T^7, NEON selftest e 42 ciclos |

---

## Leitura global de `Rafaelia/README.md`

Estado: `FATO_DOCUMENTADO`

O README declara `RAFAELIA ARM32 — Assembly Puro Sem Abstração`, com alvo Motorola E7 Power, Cortex-A53, ARM32, NEON/VFPv4, cache L1/L2 e Android sem root.

A arquitetura declarada organiza blocos:

| Bloco | Função declarada |
|---|---|
| `rafaelia_b1.S` | fundação: arena via `mmap2`, Toro T^7, 42 atratores, CRC32C SW, NEON mat4x4, EMA |
| `rafaelia_b2.S` | 7 direções: jump table, direções, pesos adaptativos |
| `rafaelia_b3.S` | multicore/throughput: `clone`, workers, CRC paralelo, `wait4` |
| `rafaelia_b4.S` | senoides, camadas, sobreposição, Taylor Q16.16, XOR acumulado |

### Classificação

| Campo | Valor |
|---|---|
| estado | `INCUBADORA` |
| valor técnico | alto: descreve arquitetura ARM32/NEON/syscall/Q16.16 |
| risco se apagar | perder mapa de intenção da incubadora |
| risco se promover em bloco | alto: docs declaram uso acadêmico/licença própria e alvo específico |
| próxima ação | ler B1–B8 individualmente antes de qualquer promoção |

---

## Item: `Rafaelia/baremetal_nomalloc.h`

Estado inicial: `INCUBADORA`

### O que existe

- Detecção de arquitetura (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`, `generic`).
- Flag NEON com inclusão de `arm_neon.h` quando disponível.
- HWCAP bits.
- Arena `mx_arena_t` com `base`, `cap`, `off`.
- Matrix `mx_t` e operações matriciais.
- Vector ops.
- Fast math sem libm no hot path.
- Mem/string bare-metal.
- Perfil de hardware com ABI, HWCAP, CPUs, page size, cache line e flags de acesso.

### Valor técnico

```text
ponte entre matemática/matriz/vetor e runtime sem malloc;
perfil HW Android/Termux;
contrato de arena;
base para comparar com engine/rmr baremetal compat.
```

### Riscos

| Risco | Observação |
|---|---|
| se apagar | perde contrato de incubadora nomalloc |
| se promover direto | pode conflitar com `engine/rmr/include/rmr_baremetal_compat.h` |
| se normalizar por estética | pode mascarar intenção de drop-in replacement |

### Status final

`INCUBADORA_COM_VALOR`

### Próxima ação

Comparar com:

- `engine/rmr/include/rmr_baremetal_compat.h`;
- `engine/rmr/src/rmr_baremetal_compat.c`;
- `engine/rmr/include/rmr_ll_tuning.h`;
- `engine/rmr/include/rmr_hw_detect.h`.

---

## Item: `Rafaelia/baremetal_nomalloc.c`

Estado inicial: `INCUBADORA`

### O que existe

- Comentário declara `ZERO malloc/free` e menor fricção por ausência de heap/fragmentação.
- Arena estática global de 512 KB alinhada a 64 bytes.
- HWCAP via `/proc/self/auxv`, sem `getauxval` pesado.
- Fast math: `rsqrt`, `sqrt`, `pow2`, `exp`, `log` aproximado.
- Memória/string próprias: `bmem_cpy`, `bmem_set`, `bmem_zero`, `bmem_cmp`, `bstr_*`.
- Arena pública: `arena_create`, `arena_alloc`, `arena_reset`, `arena_destroy` no-op/reset.
- Matrix sem malloc individual, com scratch stack para det/inv/solve.
- Uso de NEON em operações quando guard disponível.

### Atrito útil identificado

| Atrito aparente | Leitura correta |
|---|---|
| `mx_free` no-op | preserva API sem free individual |
| arena estática | evita heap/fragmentação |
| stack scratch limitada | evita heap no hot path |
| HWCAP via auxv | evita libc pesada |
| fast math aproximado | remove libm do caminho quente |

### TOKEN_VAZIO detectado

O `.c` inclui:

```c
#include "baremetal.h"
```

mas o arquivo lido é `baremetal_nomalloc.h`.

Busca por `baremetal.h baremetal_nomalloc` encontrou `Rafaelia/baremetal_nomalloc.*` e `_incoming/pending/baremetal_nomalloc.*`, mas não confirmou `Rafaelia/baremetal.h`.

Classificação atualizada:

| Estado | Motivo |
|---|---|
| `TOKEN_VAZIO_INCLUDE` | pode ser drop-in replacement proposital, compatibilidade com header anterior, ou fricção real de include |

Não corrigir sem buscar build/manifesto que consome esse arquivo e sem comparar a cópia em `_incoming/pending`.

### Status final

`INCUBADORA_COM_VALOR` + `TOKEN_VAZIO_INCLUDE`

---

## Item: `Rafaelia/rafaelia_b1.S`

Estado inicial: `INCUBADORA`

### O que existe

`rafaelia_b1.S` se declara como ARM32/AArch32 GNU Assembly para Cortex-A53/Helio G25/NEON, com zero dependência externa e comando de compilação por `as`/`ld`.

Implementa:

- syscalls Linux ARM32 (`exit`, `write`, `mmap2`, `munmap`, `clone`, `sched_yield`, `nanosleep`, `prctl`);
- constantes Q16.16 (`sqrt(3)/2`, `phi`, `pi`, `alpha`, período 42, dimensão toroidal 7);
- arena de 8 MB por `mmap2`;
- estado global `g_state`, `g_coherence`, `g_entropy`, `g_phase`, `g_step_count`;
- tabela de 42 atratores em T^7;
- tabela CRC32C gerada em runtime;
- boot `_start` com mensagens de selftest;
- `arena_init` e `arena_alloc` alinhado a 16 bytes;
- `crc32c_gentable` e `crc32c_sw`;
- `torus_init`, `torus_step`, `torus_collapse`;
- `neon_mat4x4_mul` Q16.16;
- `neon_selftest` com CRC do resultado;
- `rafaelia_run_42` como ciclo principal;
- `print_hex_word` e `sys_write_stdout`.

### Valor técnico

```text
fundação ARM32 sem libc;
boot executável direto;
arena mmap2;
CRC32C runtime;
estado toroidal 7D;
NEON Q16.16;
42 ciclos determinísticos;
mensagens de selftest;
```

### Atrito útil identificado

| Atrito aparente | Leitura correta |
|---|---|
| `_start` manual | executável freestanding/sem runtime C |
| syscalls numéricas | reduz dependência de libc |
| arena `mmap2` | memória controlada sem malloc |
| CRC table runtime | evita tabela estática pesada ou dependência externa |
| Q16.16 | evita FP/libm no núcleo |
| `print_hex_word` próprio | saída mínima sem printf |
| mensagens `.ascii` + len por `.equ` | saída determinística sem string runtime |

### TOKEN_VAZIO / pontos que exigem medição

| Item | Estado | Motivo |
|---|---|---|
| Loop principal usa `b .Rmain_cycle` incondicional após decremento | `TOKEN_VAZIO_TAIL` | pode ser fricção aceitável, forma ASM clara, ou candidato a tail path; precisa medir/validar antes de alterar |
| `torus_collapse` copia só quatro palavras do atrator para `g_state` | `TOKEN_VAZIO_SEMANTICA` | pode ser parcial/intencional, mas T^7 tem sete dimensões; não corrigir sem entender B2/B4/estado completo |
| Uso de `clone`, `sched_yield`, `nanosleep`, `prctl` equates não usados em B1 | `TOKEN_VAZIO_WARNING_INTENT` | podem preparar continuidade B3/futuro ou gerar warning/corte; não apagar por estética |
| `SYS_MUNMAP` definido mas não usado | `TOKEN_VAZIO_WARNING_INTENT` | pode ser sombra/futuro/compat |

### Classificação

| Campo | Valor |
|---|---|
| estado | `INCUBADORA_COM_VALOR` |
| promover em bloco? | não |
| apagar? | não |
| refatorar agora? | não |
| próxima ação | comparar B1 com B2/B3/B4 e mapear se os `TOKEN_VAZIO` são continuidade ou fricção real |

---

## Itens ainda não lidos nesta rodada

| Item | Estado |
|---|---|
| `Rafaelia/rafaelia_b2.S`–`b8.S` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/rafaelia_bitraf.c` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/rafaelia_orchestrator.c` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/rafaelia_jni_direct.c` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/termux_arm32_build.sh` | `TOKEN_VAZIO` até leitura |
| `_incoming/pending/baremetal_nomalloc.*` | `TOKEN_VAZIO` até comparação |

---

## Próximo F_NEXT

1. Comparar `Rafaelia/baremetal_nomalloc.*` com `_incoming/pending/baremetal_nomalloc.*`.
2. Ler `Rafaelia/rafaelia_b2.S` e mapear jump table/direções.
3. Ler `Rafaelia/rafaelia_b3.S` para verificar se syscalls equates de B1 são continuidade multicore.
4. Ler `Rafaelia/rafaelia_bitraf.c` e comparar com engine/rmr.
5. Atualizar este ledger progressivamente, sem promover nada ainda.

---

## Frase final

```text
A leitura progressiva do Lote A confirma incubadora técnica real: B1 não é só ASM solto; é fundação executável ARM32 com estado, memória, CRC, NEON e ciclo determinístico. Os atritos encontrados viram TOKEN_VAZIO antes de qualquer refatoração.
```
