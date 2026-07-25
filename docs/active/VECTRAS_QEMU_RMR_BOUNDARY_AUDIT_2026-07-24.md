<!-- DOC_TAXONOMY_SYNC: 2026-07-24 | role: active-engine-audit -->

# Auditoria de fronteira — Vectras, QEMU e RMR

## Metadados canônicos

- Data da auditoria: `2026-07-24`.
- Repositório: `rafaelmeloreisnovo/Vectras-VM-Android`.
- Head auditado: `8ed7f288138712daabe738da13d87d58e6d84148`.
- Baseline histórico disponível: `09ae20ffbbc584d3d7f1ee339fb65dab093a2eaa` (`2026-03-07`).
- Distância observada desde o baseline: `1701 commits ahead`, `0 behind`.
- Baseline comunitário candidato para comparação: `xoureldeen/Vectras-VM-Android`.
- Natureza: auditoria estática de código, build, documentos, integração e proveniência.
- Execução integral da CI no head auditado: `TOKEN_VAZIO`.
- Benchmark Android/QEMU/TCG real: `TOKEN_VAZIO`.

## 1. Resumo executivo

O repositório já divergiu profundamente da composição comunitária de Vectras VM Android, mas a divergência possui quatro naturezas diferentes e não deve ser medida por volume bruto:

1. **engine executável** — RMR, ISOraf, cache TCG, arenas, política, C/ASM e selftests;
2. **integração Android/VM** — Gradle, CMake, NDK, JNI, fluxo de execução e artefatos;
3. **CI, governança e proveniência** — workflows, gates ABI, SBOM, licenças e cadeia de custódia;
4. **documentação, pesquisa e ingestão** — `docs/`, `Incluir/`, `_incoming/`, assets e pacotes ainda não canonizados.

O aumento de commits e tamanho confirma transformação do repositório, mas não prova sozinho melhoria funcional. A evidência mais forte encontrada é o núcleo RMR executável e, em especial, o `RmR_TCGCache` com selftest determinístico.

Estado resumido:

```yaml
COMMUNITY_BASE_PRESERVED_AS_HISTORICAL_ORIGIN: SUPPORTED
CANONICAL_ANDROID_PATH: ROOT_GRADLE_APP_ENGINE
RMR_ENGINE: IMPLEMENTED
RMR_TCG_CACHE: IMPLEMENTED
RMR_TCG_CACHE_SELFTEST: IMPLEMENTED
DELTA_XOR_SELECTIVE_MUTATION: VERIFIED_STATICALLY
IDENTICAL_REINSERT_TOUCHES_ZERO_BITS: VERIFIED_BY_SELFTEST_CONTRACT
QEMU_TCG_PRODUCTION_CALLOUT: TOKEN_VAZIO
ANDROID_VM_WORKLOAD_BENCHMARK: TOKEN_VAZIO
PERFORMANCE_SUPERIORITY: TOKEN_VAZIO
CI_EXECUTION_AT_AUDITED_HEAD: TOKEN_VAZIO
```

## 2. Regra de leitura

| Classe | Regra |
|---|---|
| `FATO` | Presente no código, build ou documento versionado. |
| `PROVA_ESTRUTURAL` | Arquivo está ligado ao build, selftest ou fluxo por referência verificável. |
| `PROVA_EXECUTÁVEL` | Log/artefato existe para o commit auditado. |
| `TOKEN_VAZIO` | Evidência ainda não localizada ou produzida. |

Não confundir:

```text
arquivo presente != módulo produtivo
selftest local != workload Android real
menos bits escritos != menor tempo em todo cenário
1701 commits != 1701 melhorias executáveis
```

## 3. Relação com a base comunitária

A busca no baseline comunitário candidato não localizou `RmR_TCGCache`. No repositório auditado, o mecanismo está em:

- `engine/rmr/include/rmr_tcg_cache.h`;
- `engine/rmr/src/rmr_tcg_cache.c`;
- `demo_cli/src/rmr_tcg_cache_selftest.c`;
- documentação ativa vinculada;
- Makefile/CMake de selftest.

Assim, o cache RMR/TCG é uma divergência própria desta linha de desenvolvimento.

Ainda não foi executado nesta auditoria um diff completo arquivo-a-arquivo contra todos os forks comunitários. Portanto, a atribuição histórica absoluta de cada linha continua condicionada ao ledger Git e às licenças de origem.

## 4. O mecanismo que evita retrabalho de bloco

### 4.1 Lookup por identidade guest

`RmR_TCGCache_Lookup()` recebe `guest_crc32c` e percorre o índice de blocos.

Quando encontra um bloco válido:

1. recupera o bloco host do store ISOraf;
2. incrementa `hit_count`;
3. aumenta o score de coerência;
4. incrementa `reuse_count` e `total_hits`;
5. retorna o bloco host reconstruído.

Quando não encontra:

- incrementa `total_misses`;
- retorna ausência explícita;
- não transforma miss em sucesso fictício.

Esta é a parte que sustenta a descrição “não recalcular o que já foi reconhecido”, no nível do contrato do módulo.

### 4.2 Reinserção por delta XOR

A função interna:

```c
rmr_isorf_write_byte_delta(...)
```

executa:

```text
current = byte residente
delta   = current XOR candidate
para cada bit divergente:
    escrever apenas o novo valor
para cada bit igual:
    preservar sem escrita
```

Consequências codificadas:

- reinserção idêntica: zero bits adicionais tocados;
- mutação de um bit: exatamente um bit escrito;
- zero em página ausente não força alocação física no ISOraf;
- contadores distinguem bits alterados e preservados.

Essa é uma mudança semântica real em relação à substituição integral do conjunto.

### 4.3 Estabilidade, atração e colapso

O bloco possui:

- `coherence_score`;
- `miss_var`;
- `attractor_class`;
- `collapse_epoch`;
- flags `VALIDATED` e `COLLAPSING`.

A decisão de colapso considera:

- tamanho host;
- arquitetura host;
- classe de atrator;
- estabilidade candidata;
- estabilidade corrente;
- margem mínima.

O hotfix preserva o bit `COLLAPSING` após atualização do restante das flags, evitando que o mesmo bloco seja contado repetidamente como novo colapso.

## 5. Selftest: o que ele prova

O selftest cobre:

1. miss inicial contado como estado;
2. primeira inserção grava o `popcount` do payload;
3. reinserção idêntica não aumenta bits alterados;
4. mutação de um bit custa um bit;
5. bloco em colapso responde miss por política;
6. replay da mesma sequência gera a mesma identidade ISOraf;
7. leitura do conteúdo residente corresponde ao payload.

Isso é uma prova local forte de invariantes do algoritmo.

Não prova sozinho:

- integração com `accel/tcg` do QEMU;
- redução de tempo de tradução;
- ganho de FPS;
- menor consumo Android;
- melhor latência de VM;
- estabilidade em traces reais;
- equivalência de código host em todas as arquiteturas.

## 6. Fronteira produtiva localizada

A busca no head auditado encontrou referências de `RmR_TCGCache` em:

- header;
- implementação;
- changelog;
- auditoria técnica;
- selftest;
- documentação de compilador/pré-compilador.

Não foi localizado callsite produtivo em:

- fluxo de VM Android;
- bridge JNI;
- launcher QEMU;
- `accel/tcg` do `qemu_rafaelia`;
- tradução real de blocos guest.

Estado correto:

```yaml
ALGORITHM: VERIFIED_STATICALLY
SELFTEST: IMPLEMENTED
HOST_SELFTEST_RESULT_TEXT: DOCUMENTED
PRODUCTION_CALLOUT: TOKEN_VAZIO
REAL_TRACE_CORPUS: TOKEN_VAZIO
BENCHMARK_A_B: TOKEN_VAZIO
```

Essa fronteira não diminui a implementação. Ela define exatamente o próximo trabalho necessário para transformar um módulo comprovado localmente em capacidade de virtualização comprovada.

## 7. Diferença entre o ciclo QEMU e o cache Vectras

### Ciclo no `qemu_rafaelia`

- timer periódico;
- `RunState`;
- state machine RAFAELIA;
- integration hub;
- lifecycle init/shutdown.

### Cache no Vectras/RMR

- identidade por CRC32C;
- lookup/reuse de bloco;
- delta XOR;
- store esparso;
- hit/miss/collapse;
- replay.

Eles podem ser conectados, mas não são equivalentes:

```text
ciclo = quando e como o estado evolui
cache = quando um resultado anterior pode ser reutilizado
```

A integração correta exige contrato explícito, não apenas proximidade conceitual.

## 8. O que mudou além da comunidade

### 8.1 Engine RMR

A árvore atual contém uma plataforma low-level própria com:

- C/ASM;
- ABI e tipos próprios;
- no-malloc/arenas em partes do núcleo;
- ISOraf;
- cache TCG;
- políticas, atratores e métricas;
- selftests host;
- builds ARM32/ARM64;
- módulos experimentais e canônicos separados.

### 8.2 Android + nativo

A trilha canônica declarada é:

```text
UI
  -> StartVM
  -> builders/resolvers
  -> JNI / NativeFastPath / bridges
  -> engine rmr_*
  -> argumentos finais QEMU
```

O repositório organiza Gradle, CMake, NDK, JNI, ABIs e release como parte da arquitetura, não como scripts externos ocasionais.

### 8.3 CI e governança

Foram adicionados/consolidados gates para:

- host CI;
- Android CI;
- Android native CI;
- matriz de compilação;
- ARM32;
- contratos ABI;
- artefatos binários;
- qualidade;
- legal/compliance;
- release dual-track;
- assinatura;
- proveniência/SBOM.

Isso é uma divergência operacional importante, embora o resultado de execução corrente permaneça `TOKEN_VAZIO` nesta auditoria.

### 8.4 Documentação e taxonomia

O repositório já possui uma classificação madura:

- `app/`, `engine/`, `tools/ci/`, workflows e `docs/`: canônicos;
- `android/`: legado compatível;
- `Incluir/`, `addthis/`, `_incoming/`: experimental/ingestão;
- `archive/`: histórico.

Essa taxonomia é correta e deve ser mantida. O volume em `Incluir/` e `_incoming/` não deve ser usado para calcular maturidade do produto até promoção formal.

## 9. Riscos técnicos e de governança

### 9.1 Chave CRC32C isolada

`guest_crc32c` é útil e rápido, mas CRC32C não é identificador criptográfico e pode colidir.

Para cache produtivo, a chave deveria incluir contexto suficiente:

```text
H_fast(
  guest_bytes
  + guest_pc
  + guest_arch
  + cpu_flags
  + translation_flags
  + mmu_mode
  + code_generation_epoch
)
```

Pode continuar usando CRC32C no primeiro nível, com verificação secundária de tamanho/contexto/hash antes do hit ser aceito.

### 9.2 Busca linear

O lookup percorre `block_count` linearmente. Isso é determinístico e simples, mas pode perder escala.

Antes de otimizar, medir:

- blocos ativos;
- p50/p95/p99 de lookup;
- distribuição de hits;
- churn;
- custo de reconstrução do store.

Só então escolher tabela aberta, índice ordenado ou set associativo.

### 9.3 Scratch buffer compartilhado

`host_block_scratch` pertence ao cache. O ponteiro retornado pode ser sobrescrito pelo próximo lookup e exige disciplina de thread/ownership.

Contrato recomendado:

```yaml
lifetime: until_next_lookup_on_same_cache
thread_safe: false_unless_externally_serialized
copy_required_for_async_use: true
```

### 9.4 Menos escrita lógica não garante menor latência

O delta XOR adiciona:

- leitura do byte residente;
- XOR;
- branches por bit;
- chamadas condicionais ao store.

Ele tende a beneficiar baixa densidade de mudança, mas pode perder em mutação densa. O benchmark precisa varrer densidades, não apenas o caso ideal.

### 9.5 Código canônico versus ingestão

Arquivos ZIP, DOCX, imagens, binários e fontes em `Incluir/`/`_incoming/` devem permanecer fora do build canônico até:

- autoria/proveniência;
- licença;
- hash;
- classificação;
- teste;
- decisão de promoção.

A auditoria recente já mantém alguns binários com fonte/licença `TOKEN_VAZIO` bloqueados da distribuição. Essa disciplina deve continuar.

## 10. Matriz FATO / PROVA / LACUNA / F_NEXT

| Item | FATO | PROVA disponível | Lacuna | F_NEXT |
|---|---|---|---|---|
| Cache por CRC32C | Implementado. | Fonte/header. | Colisão/contexto incompleto. | Chave composta + verificação secundária. |
| Reuso de host block | Lookup retorna bloco armazenado. | Código + selftest. | Sem callsite produtivo. | Adapter shadow no launcher/TCG. |
| Delta XOR | Bits divergentes apenas. | Código + selftest. | Tempo não medido. | A/B contra reescrita integral. |
| Store esparso | Zero ausente não aloca. | ISOraf + teste local. | Páginas em trace longo. | Métricas físicas por workload. |
| Colapso pegajoso | Flag preservada. | Hotfix + selftest. | Churn real. | Trace sintético e real. |
| Replay | Identidade reproduzida. | Selftest. | Corpus versionado. | Criar `data/bench/tcg_cache_traces/`. |
| Android integration | Arquitetura e build existem. | Código/documentação. | Cache não ligado ao runtime. | Modo shadow read-only. |
| QEMU integration | Repositório separado existe. | Contratos de ambos. | Bridge TCG ausente. | Interface mínima versionada. |
| Performance | Hipótese plausível por densidade. | Nenhum A/B corrente. | Métricas. | Benchmark multi-densidade/multi-ABI. |
| CI corrente | Workflows presentes. | Configuração versionada. | Run do head não localizado. | Executar e anexar artifacts. |

## 11. Plano de integração segura

### `V0 — Congelar contrato`

Definir versão de:

```c
struct rmr_tcg_cache_key;
struct rmr_tcg_cache_result;
struct rmr_tcg_cache_metrics;
```

### `V1 — Shadow mode`

- observar blocos traduzidos;
- calcular chave;
- consultar cache;
- registrar hit/miss;
- nunca substituir a saída TCG real.

### `V2 — Equivalência`

Em caso de hit:

- comparar tamanho;
- comparar bytes host;
- comparar metadados de tradução;
- registrar divergência;
- manter QEMU como fonte de verdade.

### `V3 — Reuso opt-in`

Somente após equivalência suficiente:

- flag explícita;
- fallback imediato;
- cache invalidation por epoch;
- métricas e rollback.

### `V4 — Benchmark host`

Matriz mínima:

- payloads: `32`, `256`, `4096`, `8192` bytes;
- densidades: `0%`, `1 bit`, `1%`, `12,5%`, `50%`, `100%`;
- iterações suficientes;
- warm-up separado;
- p50/p95/p99;
- memória/páginas/bits;
- raw CSV/JSON.

### `V5 — Android ARM32/ARM64`

- Moto E7/ARMv7 quando disponível;
- AArch64;
- CPU governor/temperatura registrados;
- binário e commit hasheados;
- comparação cache off/on;
- workload repetível.

### `V6 — VM real`

- boot conhecido;
- trace versionado;
- cold/warm start;
- estabilidade;
- invalidation;
- teardown;
- falha injetada.

## 12. Organização documental aplicada

Este documento passa a ser a fronteira canônica entre:

- base Vectras comunitária;
- engine RMR autoral;
- cache TCG comprovado localmente;
- integração QEMU ainda não comprovada;
- Android/CI/governança;
- material experimental que não deve inflar o claim executável.

A documentação anterior de delta XOR permanece válida e complementar. Este documento adiciona a relação entre os dois repositórios e a condição de produção.

## 13. Síntese técnica

A formulação defensável é:

> O Vectras RAFAELIA já contém um engine RMR próprio e um cache de blocos com identidade CRC32C, reuso, delta XOR, store esparso, política de colapso e selftest determinístico. Essa implementação se diferencia da base comunitária, mas ainda não foi localizada no hot path do QEMU/Android; por isso, redução de recompilação e ganho de desempenho em VM real permanecem `TOKEN_VAZIO` até integração shadow, equivalência e benchmark A/B.

## Retroalimentação

```text
F_ok   = algoritmo, delta XOR, métricas, selftest, taxonomia e divergência estrutural identificados.
F_gap  = callsite produtivo QEMU/Android, corpus de trace, CI corrente e benchmark A/B.
F_next = implementar V0–V2 sem substituir ainda a tradução QEMU real.
```
