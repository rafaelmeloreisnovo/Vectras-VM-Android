# LOWLEVEL_WARNING_INTENT_CONTRACT

## Estado

`FATO_DOCUMENTADO`: contrato de revisão para warnings, comentários, fluxo branchless, tails/shadows e cortes do compilador.

Este documento impede um erro comum de auditoria: tratar todo warning como bug e apagar sinais que o pipeline usa para gerar binário menor, rota mais direta ou corte correto de símbolos.

---

## Regra canônica

```text
Warning não é automaticamente bug.
Warning primeiro é sinal.
Sinal precisa ser classificado antes de corrigir.
```

No Vectras/RMR, alguns warnings, comentários, guards e ausências aparentes fazem parte do desenho low-level.

---

## Por que isso existe

O engine usa compilador, pré-compilador e linker como parte da arquitetura:

```text
source
→ comentário/marker/guard
→ pré-compilador
→ compilador
→ warning/símbolo/seção
→ linker gc-sections
→ binário reduzido
```

Portanto, um warning pode ser:

| Tipo | Significado |
|---|---|
| `BUG_REAL` | erro que muda semântica ou quebra ABI |
| `WARNING_INTENCIONAL` | sinal usado pelo pipeline |
| `TOKEN_VAZIO` | precisa medir antes de decidir |
| `PRECOMPILER_MARKER` | comentário/guard/macro usado para cortar caminho |
| `DEAD_SECTION_SIGNAL` | seção morta que deve ser coletada pelo linker |
| `TAIL_OR_SHADOW_FLOW` | forma de evitar volta inútil no loop |

---

## Warning como instrução

O documento de compilador já registra o padrão:

```text
-ffunction-sections
-fvisibility=hidden
-Wunused-function
-Wl,--gc-sections
```

Nesse caso, `-Wunused-function` pode ser operando do pipeline de redução de símbolos, não defeito a silenciar.

Apagar o warning com `__attribute__((unused))` pode impedir que a seção morta seja sinalizada e coletada.

---

## Comentário/marker como parte do build

Comentários comuns são ignorados pelo compilador C.

Mas no Vectras alguns comentários funcionam como **marcadores de tooling**:

```text
DOC_ORG_SCAN
HOTFIX
source-scan
CASM marker
contrato de ABI
referência de arquivo canônico
```

Esses marcadores podem ser lidos por scripts, grep, auditorias, geradores de manifesto, CI ou revisão manual automatizada.

Regra:

```text
não remover comentário estrutural sem verificar quem consome esse comentário.
```

---

## Ausência de `if`, `for`, `while` também pode ser intenção

Em código low-level, a ausência de operadores condicionais ou loops explícitos pode indicar:

- branchless mask/select;
- loop unrolled;
- computed dispatch;
- salto por tabela;
- macro expandida;
- seção morta coletável;
- tail path;
- shadow path;
- caminho `ascender` do pipeline.

Não converter automaticamente para `if/for/while` “mais legível” sem medir assembly e binário final.

---

## Tail, shadow e ascender

### Tail

Tail é caminho final que evita retorno desnecessário ao topo do loop quando a próxima instrução já está determinada.

```text
não voltar para testar loop de novo
→ saltar direto para próxima instrução útil
```

### Shadow

Shadow é caminho paralelo/latente que pode ficar invisível para a execução normal, mas preserva forma, fallback, auditoria ou alinhamento.

```text
caminho existe
mas só aparece em certo guard, ABI, flag ou layer
```

### Ascender

Ascender é o padrão local para elevar a decisão de fluxo para o compilador/pipeline, em vez de expressar tudo como `if/for/while` direto.

```text
entrada pequena
→ macro/guard/máscara/tabela
→ compilador escolhe corte
→ binário final executa caminho direto
```

---

## Um C pode disparar mais de uma instrução

Uma linha C em low-level pode expandir para múltiplas operações úteis:

```text
load
mask
select
store
barrier
prefetch
```

ou, com flags certas, pode reduzir para uma instrução especializada:

```text
crc32cd
csel
cnt/ctz/clz
popcount
store indexado
```

Portanto, revisar só a aparência do C é insuficiente.

---

## Protocolo antes de corrigir warning

Antes de alterar código por causa de warning:

1. Identificar a flag que gerou o warning.
2. Verificar se a função/variável está em seção própria.
3. Verificar se `--gc-sections` ou equivalente consome o sinal.
4. Procurar comentário/marker/guard relacionado.
5. Checar se o caminho é baremetal, JNI ou host.
6. Checar se a ausência de `if/for/while` é branchless/tail/shadow.
7. Comparar assembly quando for hot path.
8. Classificar: `BUG_REAL`, `WARNING_INTENCIONAL`, `TOKEN_VAZIO`.
9. Só corrigir depois de provar que não é parte do pipeline.

---

## O que não fazer

```text
não apagar warning só para “limpar” log
não adicionar unused attribute sem provar necessidade
não trocar máscara por if sem medir
não trocar unroll por loop por estética
não remover comentário estrutural sem buscar consumo
não promover bug/core em bloco
não mexer em manifesto gerado manualmente
```

---

## Checklist de auditoria

| Pergunta | Se sim |
|---|---|
| O warning ajuda `gc-sections`? | manter ou documentar |
| O comentário é marker de tool/CI? | manter |
| A função parece unused mas preserva ABI/fallback? | classificar antes |
| O loop ausente é unroll/tabela/máscara? | medir assembly |
| A volta ao topo do loop foi evitada por tail? | manter se medido |
| O path é shadow por guard? | não remover sem teste de build matrix |
| É hot path? | comparar ciclos/binário |

---

## Relação com documentos existentes

- `docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`
- `docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`
- `engine/rmr/README.md`
- `engine/rmr/sources_rmr_core.cmake`
- `tools/baremetal/rafcode_phi/README.md`

---

## Frase canônica

```text
No low-level RAFAELIA/Vectras, warning é evidência primeiro.
Só vira erro depois de falhar no teste de intenção.
```
