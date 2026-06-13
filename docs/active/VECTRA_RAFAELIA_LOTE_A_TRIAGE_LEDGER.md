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

Classificação provisória:

| Estado | Motivo |
|---|---|
| `TOKEN_VAZIO` | pode ser drop-in replacement proposital, compatibilidade com header anterior, ou fricção real de include |

Não corrigir sem buscar `baremetal.h` e o build que consome esse arquivo.

### Status final

`INCUBADORA_COM_VALOR` + `TOKEN_VAZIO_INCLUDE`

---

## Itens ainda não lidos nesta rodada

| Item | Estado |
|---|---|
| `Rafaelia/rafaelia_b1.S`–`b8.S` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/rafaelia_bitraf.c` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/rafaelia_orchestrator.c` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/rafaelia_jni_direct.c` | `TOKEN_VAZIO` até leitura |
| `Rafaelia/termux_arm32_build.sh` | `TOKEN_VAZIO` até leitura |

---

## Próximo F_NEXT

1. Buscar se existe `Rafaelia/baremetal.h` ou outro include compatível.
2. Ler `Rafaelia/rafaelia_b1.S` e mapear ABI/syscalls/registradores.
3. Ler `Rafaelia/rafaelia_b2.S` e mapear jump table/direções.
4. Ler `Rafaelia/rafaelia_bitraf.c` e comparar com engine/rmr.
5. Atualizar este ledger progressivamente, sem promover nada ainda.

---

## Frase final

```text
A primeira leitura do Lote A mostra incubadora real: há fricção útil, há potência técnica, e há pelo menos um TOKEN_VAZIO que exige investigação antes de qualquer refatoração.
```
