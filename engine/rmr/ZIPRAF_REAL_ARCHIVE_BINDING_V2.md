# ZIPRAF Real Archive Binding, mmap Host e Plano Multicore V2

Status: `REFERENCE_IMPLEMENTATION / HOST_KAT_PASS_LOCAL`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`

## 1. Decisão arquitetural

O ZIP continua sendo a camada de compatibilidade. A identidade autoral permanece no grafo ZIPRAF.

```text
arquivo ZIP real
→ EOCD
→ central directory
→ local header
→ payload span
→ política STORE/DEFLATE/rejeição
→ bloco ZIPRAF por digest externo verificado
→ aresta de módulo
→ fase + core mask + época
```

Não há extração obrigatória para um payload `STORE` apenas porque ele está dentro do ZIP. Quando o payload está armazenado sem compressão, alinhado, imutável e posteriormente verificado por digest, seus bytes podem ser referenciados diretamente no mapeamento do arquivo. `DEFLATE` continua exigindo materialização lógica.

## 2. O que foi implementado

### Parser sem heap do envelope ZIP clássico

O parser usa buffers fornecidos pelo chamador e limites fixos. Ele vincula:

```text
central directory entry
↔ local file header
↔ nome
↔ método
↔ CRC declarado
↔ tamanhos
↔ payload offset
↔ data descriptor, quando presente
```

São rejeitados:

- EOCD, central directory ou local header truncados;
- ZIP64 nesta versão;
- arquivos multidisco;
- offsets fora dos limites;
- mismatch entre central e local header;
- descriptor incompatível;
- payloads ou registros locais sobrepostos;
- local headers duplicados;
- nomes duplicados após normalização ASCII portátil;
- nomes absolutos, `..`, segmentos vazios, ponto final, espaço final e dispositivos reservados;
- symlinks Unix para fins de binding;
- criptografia e métodos não suportados para execução do plano.

ZIP64 não é tratado como inválido universalmente; ele está apenas fora do perfil V2 e retorna um estado explícito.

## 3. Quatro ações distintas

Cada entrada recebe exatamente uma ação lógica:

```text
DIRECT_MAP_LAYOUT
COPY_STORE
DECOMPRESS
REJECT
```

`DIRECT_MAP_LAYOUT` significa somente que a geometria do payload permite acesso direto dentro de um mapeamento do arquivo. Não significa automaticamente:

- digest verificado;
- página executável;
- autorização do loader;
- pinning físico;
- DMA;
- ausência de cópias internas do kernel;
- execução Android comprovada.

A promoção para `RMR_ZIPRAF_BLOCK_DIRECT_MAP` exige ainda imutabilidade, digest não vazio e `digest_verified=true`.

## 4. Binding com o grafo existente

`RmR_ZiprafArchive_BindEntry` transforma uma entrada verificada em `RmR_ZiprafPageBlock`.

Invariantes:

```text
magic MZ/ELF não define EXEC_CANDIDATE
DEFLATE não recebe DIRECT_MAP
DMA_CANDIDATE exige DIRECT_MAP
entrada insegura não pode virar bloco
estado mutável e imutável não coexistem
```

O digest continua externo ao parser do ZIP porque o CRC32 do formato não substitui SHA-256/BLAKE3 para identidade de conteúdo.

## 5. Permutação por cores e direções

O plano lógico recebe uma máscara de cores e distribui tarefas deterministicamente:

```text
core(entry_i) = nth_set_bit(core_mask, i mod popcount(core_mask))
phase(entry_i) = floor(i / popcount(core_mask))
direction(phase) = forward, reverse, forward, reverse...
```

A alternância de direção é uma permutação de leitura auditável. Ela não é alegada como harmônica física do clock. O scheduler real poderá substituir essa política após benchmark, preservando o mesmo manifesto de tarefas.

## 6. Prova host mmap delimitada

O probe cria um ZIP real com um payload `STORE` alinhado à página, grava em arquivo temporário, mapeia com `mmap(MAP_PRIVATE)`, analisa o ZIP e lê o payload diretamente do endereço:

```text
mapping_base + payload_offset
```

O receipt registra:

```text
page_size
archive_bytes
payload_offset
direct_map_bytes
explicit_user_copy_bytes
elapsed_ns
minor_fault_delta
major_fault_delta
layout_fingerprint64
```

O campo `explicit_user_copy_bytes=0` descreve apenas o código do probe. Não afirma zero movimentação física em cache, page cache, controlador ou barramento.

## 7. Fingerprint estrutural

O `layout_fingerprint64` usa FNV-1a para detectar mudança do layout observado durante testes. Ele não é assinatura, MAC nem identidade criptográfica. A identidade autorizada permanece:

```text
digest_kind + digest + logical_size
```

## 8. Gate adversarial

O KAT local cobre 37 verificações, incluindo:

- ZIP construído byte a byte; compatibilidade adicional validada localmente com ZIP produzido por biblioteca externa;
- STORE alinhado e não alinhado;
- DEFLATE;
- criptografia, symlink e método desconhecido;
- path traversal e colisão portátil;
- EOCD, central e local corrompidos;
- ZIP64 e multidisco;
- overlap, duplicidade e descriptor adulterado;
- binding real para o page graph;
- plano determinístico com dois cores e três fases;
- receipt mmap host.

## 9. Matriz de urgência

| Ordem | Caminho | Estado V2 | Gate de promoção |
|---|---|---|---|
| U0 | ZIP real → payload spans | implementado em host | KAT remoto verde |
| U1 | digest SHA-256/BLAKE3 do payload real | interface pronta | KAT com vetores conhecidos |
| U2 | corpus real ZIP/APK | pendente | relatório de compatibilidade e rejeições |
| U3 | mmap Android/Termux | pendente | bytes copiados, faults, p50/p95/p99 |
| U4 | materializador DEFLATE | pendente | digest do resultado e limites de memória |
| U5 | scheduler octa-core | plano lógico pronto | single/2/4/8 cores, afinidade e cache misses |
| U6 | BitRafa/FEC 35–45% | não autorizado | modelo de erro + prova + fault injection |
| U7 | DMA/IOMMU/IRQ | contrato de época pronto | driver/hypervisor e stale IRQ real |
| U8 | ledger/Merkle | hash chain permitido | autoridade, replicação e política explícitas |

## 10. Limites honestos

```yaml
zip_classic_parser: HOST_REFERENCE
zip64: EXPLICITLY_UNSUPPORTED_V2
real_payload_binding: HOST_KAT
host_mmap_store: VERIFIED_LIMITED
explicit_user_copy_for_mapped_store_probe: 0
kernel_or_hardware_zero_copy: NOT_CLAIMED
android_mmap: TOKEN_VAZIO
deflate_materializer: TOKEN_VAZIO
payload_crypto_digest_runtime: TOKEN_VAZIO
multicore_physical_harmonic: TOKEN_VAZIO
dma_iommu_irq_runtime: TOKEN_VAZIO
bitflip_35_45_recovery: NOT_AUTHORIZED
claim_allowed: false
```

## R3

```text
F_ok:
  ZIP clássico real é ligado a spans limitados, ações explícitas,
  page graph, plano por cores e mmap host sem cópia explícita do payload STORE.

F_gap:
  digest criptográfico do payload, corpus APK, ZIP64, Android mmap,
  DEFLATE controlado, scheduler medido, FEC externo e DMA físico.

F_next:
  executar o scanner sobre um corpus real de ZIP/APK e produzir
  receipts por entrada, depois portar o probe para Termux/Android.
```
