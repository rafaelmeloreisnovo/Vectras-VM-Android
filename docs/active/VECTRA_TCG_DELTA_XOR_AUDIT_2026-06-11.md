<!-- DOC_TAXONOMY_SYNC: 2026-06-11 | role: active-engine-audit -->

# Vectra TCG Delta XOR Audit — PR #1005

## Metadados canônicos

- Versão do documento: 1.0.
- Última revisão: 2026-06-11.
- Escopo: auditoria técnica do cache TCG após o PR #1005, com separação explícita entre fato, prova, lacuna e próximo experimento.
- Status: canônico ativo; não substitui benchmark real.
- Commit de referência: `6b54df0a021bccd95a51dd5dd154a9099b144cd0`.
- PR de referência: `#1005` — `feat(engine): mutação seletiva de bits no cache TCG — delta XOR em vez de substituição do conjunto`.
- Arquivos de código vinculados:
  - `engine/rmr/src/rmr_tcg_cache.c`
  - `engine/rmr/include/rmr_tcg_cache.h`
  - `engine/rmr/src/rmr_isorf.c`
  - `engine/rmr/include/rmr_isorf.h`
  - `demo_cli/src/rmr_tcg_cache_selftest.c`
- Arquivos de build/documentação vinculados:
  - `Makefile`
  - `CMakeLists.txt`
  - `CHANGELOG.md`
  - `demo_cli/FILES_MAP.md`
  - `docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`

## Regra de leitura

Este documento não afirma ganho de performance em Android, QEMU ou TCG real sem benchmark. Ele afirma apenas o que está codificado, testado por selftest e documentado no repositório no commit de referência.

Separação obrigatória:

| Classe | Regra |
|---|---|
| FATO | Existe no código ou documentação versionada. |
| PROVA | Há teste, selftest, commit, diff ou vínculo executável. |
| LACUNA | Ainda não foi medido em carga real ou não há artefato suficiente. |
| F_NEXT | Próximo experimento mínimo para transformar lacuna em evidência. |

## Síntese executiva

O PR #1005 transforma a reinserção do cache TCG de uma escrita integral de bloco em uma escrita seletiva por delta XOR.

Antes, `RmR_TCGCache_Insert` regravava cada byte do bloco. Mesmo quando a maioria dos bits já era igual, a operação caminhava por todo o conjunto lógico.

Agora, `rmr_isorf_write_byte_delta` lê o byte residente, calcula `current ^ value`, e toca apenas os bits divergentes. Bits iguais são preservados; bits zero em página ausente seguem a semântica esparsa do ISOraf e não forçam alocação física.

Em linguagem operacional RAFAELIA:

```text
miss observado -> estado, não falha silenciosa
byte antigo XOR byte novo -> delta mensurável
bits iguais -> verdade preservada
bits divergentes -> ruído convertido em instrução mínima
```

## Mudança técnica principal

### 1. Escrita seletiva por delta XOR

Função introduzida no caminho do cache TCG:

```c
static u8 rmr_isorf_write_byte_delta(RmR_ISOraf_Store *st,
                                     u64 byte_offset,
                                     u8 value,
                                     u32 *flipped_out)
```

Contrato lógico:

1. Ler o byte atual no ISOraf.
2. Calcular `delta = current ^ value`.
3. Iterar pelos 8 bits do byte.
4. Ignorar bit sem divergência.
5. Chamar `RmR_ISOraf_SetBit` apenas quando o bit diverge.
6. Contar quantos bits foram realmente alterados.

Consequência: a operação deixa de ser “substituir conjunto” e passa a ser “transmutar diferença”.

### 2. Preservação do físico esparso ISOraf

O ISOraf já possui uma regra essencial:

```text
se a página não existe e o valor escrito é 0, não alocar página
```

A mudança do PR #1005 respeita essa semântica ao evitar chamadas desnecessárias para bits sem divergência. Assim, o cache não cria presença física artificial para ausência lógica.

### 3. Métricas novas de mutação

Campos adicionados ao estado do cache:

```c
u64 delta_bits_flipped;
u64 delta_bits_preserved;
```

Acessores públicos:

```c
u64 RmR_TCGCache_DeltaBitsFlipped(const RmR_TCGCache *cache);
u32 RmR_TCGCache_DeltaPreservedPct(const RmR_TCGCache *cache);
```

Interpretação:

| Métrica | Interpretação operacional |
|---|---|
| `delta_bits_flipped` | Quantidade acumulada de bits realmente alterados. |
| `delta_bits_preserved` | Quantidade acumulada de bits que permaneceram iguais durante inserções. |
| `DeltaPreservedPct` | Percentual de preservação estrutural do fluxo de reinserção. |

Esta é a primeira ponte mensurável para tratar `ρ` como medida de recompilação: quanto do bloco anterior permaneceu verdade no bloco novo.

## Selftest vinculado

Arquivo novo:

```text
demo_cli/src/rmr_tcg_cache_selftest.c
```

Invariantes codificadas:

1. Miss inicial é estado explícito e incrementa contador.
2. Primeira inserção grava exatamente `popcount(payload)` bits.
3. Reinserção idêntica custa zero bits adicionais.
4. Mutação de 1 bit custa exatamente 1 bit gravado.
5. Bloco em colapso responde `MISS` por política, não por silêncio.
6. Replay da mesma sequência reproduz a mesma identidade ISOraf.
7. Conteúdo residente é validado por leitura byte/bit.

Resultado declarado no PR:

```text
OK tcg cache selftest flipped=126 preserved_pct=83 collapse=1 hit_ratio=33
```

## Matriz FATO / PROVA / LACUNA / F_NEXT

| Item | FATO | PROVA | LACUNA | F_NEXT |
|---|---|---|---|---|
| Delta XOR no cache TCG | Implementado em `rmr_tcg_cache.c`. | Função `rmr_isorf_write_byte_delta` e uso em `RmR_TCGCache_Insert`. | Não há benchmark comparativo de custo por byte/bit. | Criar benchmark `rewrite_full_vs_delta_xor`. |
| Físico esparso preservado | Bits zero em página ausente não alocam página. | Semântica de `RmR_ISOraf_SetBit` + escrita seletiva. | Falta medir páginas usadas em sequência longa. | Medir `pages_used`, `data_word_used`, `physical_bits`. |
| Métrica de preservação | `delta_bits_flipped` e `delta_bits_preserved`. | Header e implementação expõem acessores. | Métrica ainda não está em relatório CI/perf. | Exportar JSON/CSV em benchmark host. |
| Miss como estado | Lookup vazio incrementa `total_misses`. | Selftest valida miss inicial. | Falta rastrear miss por classe de causa em workload real. | Separar miss frio, miss por colapso, miss por mudança de arch/size. |
| Colapso pegajoso | Bloco em colapso responde MISS por política. | Selftest valida `collapse_count == 1`. | Falta medir impacto em churn de blocos reais. | Benchmarkar churn com sequência sintética e trace real. |
| Replay determinístico | Mesma sequência gera mesma identidade ISOraf. | Selftest compara `RmR_ISOraf_Identity`. | Ainda não há seed corpus/versionamento de traces. | Criar corpus mínimo `data/bench/tcg_cache_traces/`. |

## Riscos técnicos restantes

### Risco 1 — Métrica acumulada sem janela

`delta_bits_flipped` e `delta_bits_preserved` são acumuladores globais. Isso é bom para auditoria de sessão, mas ainda não separa janelas temporais, blocos, traces ou classes de miss.

Mitigação futura:

```text
adicionar snapshot por ciclo:
- before_flipped
- after_flipped
- before_preserved
- after_preserved
- block_crc32c
- host_size
- reason
```

### Risco 2 — Performance ainda não medida

Menos bits tocados não implica automaticamente menor tempo total em todos os cenários. O custo de leitura do byte residente, cálculo de delta e branches pode ganhar ou perder dependendo de densidade, cache real, arquitetura e padrão de mutação.

Mitigação futura:

```text
medir, não narrar:
- ns/op
- bits_flipped/op
- pages_used/op
- data_word_used/op
- host_size
- mutation_density
- arch host
```

### Risco 3 — Granularidade byte/bit pode não representar TCG real completo

O selftest usa payload controlado de 32 bytes. Ele prova invariantes locais, mas não representa sozinho fluxo real de blocos TCG em Android/QEMU.

Mitigação futura:

```text
adicionar 3 níveis de teste:
1. unit/selftest determinístico
2. benchmark sintético com densidades controladas
3. trace real capturado em execução VM
```

## Protocolo mínimo de benchmark

Nome sugerido:

```text
demo_cli/src/rmr_tcg_cache_delta_bench.c
```

Saída sugerida:

```json
{
  "engine": "rmr_tcg_cache",
  "mode": "delta_xor",
  "payload_bytes": 4096,
  "iterations": 10000,
  "mutation_density_pct": 1,
  "delta_bits_flipped": 0,
  "delta_bits_preserved": 0,
  "preserved_pct": 0,
  "pages_used": 0,
  "physical_bits": 0,
  "ns_per_insert": 0,
  "claim_allowed": false
}
```

Regras:

1. `claim_allowed=false` até comparação contra baseline integral.
2. Rodar densidades: `0%`, `1 bit`, `1%`, `12.5%`, `50%`, `100%`.
3. Rodar tamanhos: `32`, `256`, `4096`, `8192` bytes.
4. Exportar SHA/commit do binário e commit Git.
5. Registrar CPU/ABI quando o benchmark sair do host.

## Próxima escrita recomendada

1. Criar benchmark host mínimo para `delta XOR`.
2. Criar baseline local de reescrita integral isolada, sem substituir o código atual.
3. Gerar relatório `reports/TCG_DELTA_XOR_BENCHMARK_2026-06-11.md` somente após execução.
4. Atualizar `PROJECT_STATE.md` apenas se houver resultado executado.
5. Não declarar melhoria de performance antes do relatório.

## Frase canônica

```text
O PR #1005 não prova aceleração geral; ele prova uma mudança semântica e mensurável: reinserção idêntica não toca bits, mutação de 1 bit toca 1 bit, miss é estado e replay preserva identidade ISOraf.
```

## Retroalimentação RAFAELIA

```text
F_ok   = delta XOR implementado, métricas expostas, selftest codificado.
F_gap  = falta benchmark real comparativo em host/Android/QEMU.
F_next = transformar métrica local em relatório executável com baseline.
```
