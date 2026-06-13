# VECTRA_OPERATIONAL_EXCELLENCE_WORKSPACE_PLAN

## Estado

`FATO_DOCUMENTADO`: plano de trabalho com tempo, espaço, sequência e critérios para canonizar a documentação antes de continuar a varredura profunda da Vectra.

Este documento existe para evitar trabalho reativo, pressa operacional e leituras rasas. A Vectra deve ser documentada como ecossistema vivo: app, engine, low-level, RAFAELIA, baremetal, ingestão, histórico, CI e governança.

---

## Frase canônica

```text
Excelência operacional é dar tempo e espaço para o código revelar sua função antes de chamar qualquer coisa de bug, lixo, duplicata ou ausência.
```

---

## Objetivo

Criar uma sequência de documentação que acompanhe o código vivo.

```text
1. canonizar contratos que impedem erro de leitura;
2. mapear camadas e diretórios;
3. classificar incubadora/ingestão/histórico;
4. só então promover, corrigir, apagar ou integrar.
```

---

## Princípios

| Princípio | Regra |
|---|---|
| Tempo | não fazer promoção em bloco por pressa |
| Espaço | separar canônico, incubadora, ingestão e histórico |
| Evidência | código, build, warning, comentário e manifesto contam |
| TOKEN_VAZIO | lacuna protegida vale mais que conclusão falsa |
| Rollback | toda promoção deve ter reversão conhecida |
| CI | build/performance não devem ser inferidos sem execução atual |
| Anti-óbvio | aparência não define função |

---

## Sequência operacional

### Fase 0 — Contratos de leitura

Estado: `EM_ANDAMENTO`

| Documento | Status |
|---|---|
| `VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT.md` | criado |
| `VECTRA_CODE_AHEAD_OF_DOCS_LEDGER.md` | criado |
| `LOWLEVEL_WARNING_INTENT_CONTRACT.md` | criado |
| `VECTRA_FREESTANDING_VOID_CONTRACT.md` | criado |
| `VECTRA_INCUBATOR_TO_CORE_PROMOTION_PROTOCOL.md` | criado |
| `VECTRA_RAFCODE_PHI_BRIDGE.md` | criado |

### Fase 1 — Matriz de triagem

Estado: `F_NEXT`

Criar/expandir:

- `VECTRA_INCOMING_TRIAGE_MATRIX.md`
- matriz de `_incoming/pending/*.S`;
- matriz de `Rafaelia/*.S`;
- matriz de `Incluir/*.zip`, `*.docx`, `*.py`;
- matriz de `addthis/*`;
- decisão: `CANONICO`, `INCUBADORA`, `INGESTAO`, `HISTORICO`, `TOKEN_VAZIO`.

### Fase 2 — Pontes técnicas

Estado: `F_NEXT`

- `VECTRA_APP_RUNTIME_TO_ENGINE_MAP.md` — Android/Kotlin/Java → JNI → RMR;
- `VECTRA_RMR_TCG_CACHE_SEMANTICS.md` — miss, delta XOR, bits preservados;
- `VECTRA_RAFAELIA_ASM_LAYER_MAP.md` — B1–B8 e pending ASM;
- `VECTRA_BITWALK_CORE_PROMOTION_NOTE.md` — BITWALK/BITGHOST → `engine/rmr/include`.

### Fase 3 — Promoção controlada

Estado: `TOKEN_VAZIO` até leitura por arquivo.

Critérios:

1. origem identificada;
2. licença/autoria/proveniência;
3. função técnica;
4. documento ativo;
5. header/API se virar core;
6. manifesto se houver `.c/.S`;
7. teste/selftest;
8. rollback.

### Fase 4 — Build e validação

Estado: `BETA_BLOCKED` até CI real.

- Não inferir build atual;
- Não inferir aceleração atual;
- Não tratar release unsigned como distribuição oficial;
- Registrar evidência em ledger.

---

## Espaço de trabalho por camada

| Camada | Diretório | Documento de trabalho |
|---|---|---|
| App/runtime | `app/` | `VECTRA_APP_RUNTIME_TO_ENGINE_MAP.md` |
| Engine core | `engine/rmr/` | docs low-level já existentes + próximos mapas |
| RAFAELIA incubadora | `Rafaelia/` | `VECTRA_RAFAELIA_ASM_LAYER_MAP.md` |
| Baremetal RAFCODEphi | `tools/baremetal/rafcode_phi/` | `VECTRA_RAFCODE_PHI_BRIDGE.md` |
| Incoming | `_incoming/` | `VECTRA_INCOMING_TRIAGE_MATRIX.md` |
| Incluir | `Incluir/` | `VECTRA_INCOMING_TRIAGE_MATRIX.md` |
| Addthis | `addthis/` | `VECTRA_INCOMING_TRIAGE_MATRIX.md` |
| Histórico | `archive/`, `bug/archive/` | usar como evidência, não estado atual |

---

## Critério de parada por rodada

Uma rodada de trabalho deve parar quando:

```text
- já criou documento canônico suficiente para impedir erro futuro;
- encontrou lacuna grande que precisa TOKEN_VAZIO;
- teria que promover código sem ter lido origem completa;
- teria que inferir build/CI sem execução;
- o risco de truncar ou cortar contexto ficou alto.
```

---

## Protocolo de resposta após cada rodada

Sempre relatar:

```text
o que foi criado;
o que foi alterado;
o que não foi mexido;
o que ainda é TOKEN_VAZIO;
qual é o próximo F_NEXT;
```

---

## Ledger inicial

| Estado | Objeto | Observação |
|---|---|---|
| `FATO` | documentação estava atrás de partes do código vivo | agora há ledger próprio |
| `FATO` | anti-óbvio foi canonizado | usar antes de nova varredura |
| `FATO` | void freestanding foi canonizado | proteger usos incomuns |
| `FATO` | incubadora → core tem protocolo | não promover por bloco |
| `FATO` | RAFCODEphi tem ponte | C→ASM→hex não é trivial |
| `F_NEXT` | triagem `_incoming`/`Rafaelia`/`Incluir` | criar matriz e preencher progressivamente |

---

## Frase final

```text
Primeiro dar forma ao campo; depois atravessar o campo.
```
