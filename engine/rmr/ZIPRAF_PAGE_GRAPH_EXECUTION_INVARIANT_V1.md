# ZIPRAF Page Graph, BitRafa, DMA e Execução por Módulos V1

Status: `REFERENCE_IMPLEMENTATION / HOST_KAT_PENDING_REMOTE`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`

## 1. Escopo

O ZIP é mantido como envelope de compatibilidade. A organização autoral fica em um grafo interno de páginas, módulos, offsets, digests, fases e épocas.

```text
ZIP compatibility shell
└── ZIPRAF page graph
    ├── content-addressed immutable blocks
    ├── module-to-block edges
    ├── core masks and phases
    ├── BitRafa redundancy metadata
    ├── DMA leases
    └── append-only mapping epochs
```

Esta versão é um modelo C verificável em host. Não é driver DMA, filesystem, loader PE/ELF, hypervisor nem prova de zero-copy integral.

## 2. Regra de acesso sem extração

Uma entrada pode ser candidata a mapeamento direto quando:

```text
compression_method = STORE
stored_size = logical_size
offset aligned
block immutable
digest verified
```

Entradas DEFLATE continuam compatíveis com ZIP, porém exigem materialização/descompressão antes do consumo lógico.

```text
ZIP_COMPATIBLE != DIRECTLY_MAPPABLE
```

## 3. Bloco como identidade de conteúdo

Cada bloco possui:

```text
block_id
archive_offset
stored_size
logical_size
alignment
compression_method
flags
digest_kind + digest
redundancy profile
```

A reutilização é autorizada por identidade de conteúdo e imutabilidade:

```text
reuse(block) :=
  immutable(before)
  AND immutable(after)
  AND digest(before) = digest(after)
  AND logical_size(before) = logical_size(after)
```

Se apenas uma região mudou, somente ela precisa receber novo digest e nova referência. Regiões imutáveis iguais podem ser compartilhadas por múltiplos módulos.

## 4. Permutações computacionais relacionadas

O modelo combina ideias conhecidas sem tratá-las como equivalentes:

| Conceito | Papel permitido |
|---|---|
| content-addressed storage | identidade e deduplicação de blocos imutáveis |
| Merkle/append-only ledger | lastro de versões e relações |
| mmap | acesso virtual a bytes armazenados e alinhados |
| copy-on-write | separar mutação de compartilhamento imutável |
| scatter/gather | representar múltiplos spans sem exigir contiguidade física |
| work stealing / DAG scheduling | distribuir módulos prontos por cores |
| DMA lease | vínculo temporário entre dispositivo, endereço e época |
| ECC/FEC | detectar ou reconstruir somente dentro do perfil provado |
| overlays | compor base imutável com deltas mutáveis |

## 5. Multicore e fases

Uma aresta liga um módulo a uma faixa de bloco:

```text
module_id
block_id
local_offset
length
access_flags
core_mask
phase
```

Leituras imutáveis podem coexistir em vários cores. Escritas sobre a mesma faixa e fase são rejeitadas. Isso modela cadência e dependências, mas não prova uma harmônica física do clock.

```text
phase = ordem lógica
core_mask = conjunto permitido
clock/cycles = medição posterior
```

Frequência, amplitude térmica e latência não fazem parte do digest do conteúdo. Devem entrar em telemetria/receipt separado.

## 6. MZ, ELF e ZIP

A assinatura `MZ` classifica um candidato PE. A assinatura não concede execução.

```text
magic detected
→ format parser
→ bounds and section validation
→ architecture match
→ relocation/import policy
→ signature/trust policy
→ executable mapping authorized by platform loader
```

O mesmo vale para ELF. O modelo oferece `EXEC_CANDIDATE`; a autorização real permanece externa.

## 7. BitRafa e limite de perda

Os perfis atuais `PARITY2_OBSERVE` e `ECC32_MASKED_OBSERVE` são tratados como observação/síndrome. Eles não recebem capacidade de recuperação positiva nesta versão.

Uma alegação como 35–45% exige distinguir:

```text
known erasures
unknown bit errors
omissions
permutations
mapping errors
```

Para FEC externo, o manifesto exige posições de erasure conhecidas, razão de shards compatível e prova externa explícita. Sem isso:

```text
recovery_claim_ppm = 0
claim_allowed = false
```

## 8. DMA, IRQ e época

O endereço DMA é uma concessão temporária:

```text
transaction_id
block_id
owner_core_mask
mapping_epoch
dma_address
length
expires_tick
state
```

Uma conclusão IRQ é aceita apenas quando:

```text
state = IN_FLIGHT
transaction_id matches
mapping_epoch matches
now <= expires_tick
```

Remapeamento exige:

```text
IN_FLIGHT → QUIESCED → REMAP(new epoch) → ARMED
```

Assim, uma IRQ antiga não pode validar o endereço novo.

## 9. Blockchain, hash e lastro

Hash identifica conteúdo ou manifesto; não representa sozinho o clock, o resultado físico ou a causalidade. Um ledger encadeado local pode preservar histórico, mas só deve ser chamado de blockchain quando o protocolo de blocos, consenso/autoridade e verificação estiverem explicitamente definidos.

Nesta etapa:

```text
append-only hash chain = permitido
blockchain claim = TOKEN_VAZIO
```

## 10. Invariantes do gate

O KAT rejeita:

1. DEFLATE marcado como direct-map;
2. execução concedida apenas por `MZ`;
3. alegação de 45% para paridade/ECC observacional;
4. escrita concorrente na mesma faixa/fase;
5. reutilização após mudança de digest;
6. IRQ com época antiga;
7. remapeamento sem quiescência.

E confirma:

1. grafo ZIPRAF válido;
2. compartilhamento de bloco imutável;
3. autorização de execução separada do formato;
4. IRQ atual aceita;
5. remapeamento após `QUIESCED`.

## 11. Artefatos

```text
engine/rmr/include/rmr_zipraf_page_graph.h
engine/rmr/src/rmr_zipraf_page_graph.c
demo_cli/src/zipraf_page_graph_selftest.c
tools/zipraf/test_zipraf_page_graph.sh
.github/workflows/zipraf-page-graph.yml
```

## 12. Limites

```yaml
zip_compatibility: IMPLEMENTED_AS_MODEL
store_direct_map_contract: HOST_VERIFIED_PENDING_REMOTE
compressed_zero_copy: false
pe_or_elf_direct_execution: TOKEN_VAZIO
android_mmap_runtime: TOKEN_VAZIO
dma_irq_hardware_runtime: TOKEN_VAZIO
multicore_scheduler_runtime: TOKEN_VAZIO
bitflip_35_45_recovery: NOT_AUTHORIZED
blockchain_consensus: TOKEN_VAZIO
claim_allowed: false
```

## R3

```text
F_ok:
  ZIP compatibility, immutable page graph, digest reuse,
  phase/core edges and DMA epoch gates were materialized.

F_gap:
  real archive parser integration, mmap measurements, Android loader,
  DMA/IOMMU driver, external FEC proof and multicore benchmark.

F_next:
  bind the page graph to real ZIP local headers and central directory,
  then measure copied bytes, page faults, cache misses and p50/p95/p99.
```
