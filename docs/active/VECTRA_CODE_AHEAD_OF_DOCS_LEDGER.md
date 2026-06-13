# VECTRA_CODE_AHEAD_OF_DOCS_LEDGER

## Estado

`FATO_DOCUMENTADO`: ledger para registrar situações em que o código, os scripts, os comentários, os warnings ou os artefatos executáveis já carregam semântica que a documentação ainda não explicou adequadamente.

Este arquivo existe para reduzir a repetição de pedidos e evitar que a ausência documental seja confundida com ausência técnica.

---

## Frase canônica

```text
Documentação atrasada não prova ausência do conceito.
Código executando, build script, warning, comentário, manifesto e artefato também podem ser evidência.
```

---

## Problema observado

A Vectra possui camadas onde o comportamento real aparece primeiro em:

```text
C
ASM
Kotlin/Java
shell scripts
CMake/Make
GitHub Actions
comments/markers
warnings
inventários
zips de ingestão
arquivos pending
```

A documentação nem sempre acompanha imediatamente. Isso cria risco de leitura rasa:

```text
não está documentado → não existe
parece duplicado      → remover
parece warning        → corrigir
parece pendente       → ignorar
parece histórico      → descartar
```

Esse raciocínio fica proibido sem classificação.

---

## Fontes de evidência aceitas

| Fonte | Valor |
|---|---|
| Código canônico | prova de implementação ou contrato |
| Header público | contrato ABI/API |
| ASM | intenção de instrução, register path ou hot path |
| CMake/Make | inclusão real no build |
| GitHub Actions | gate, release, CI, ABI ou policy |
| Comentário estrutural | marker consumível por tooling/auditoria |
| Warning | possível sinal de pipeline |
| Inventário `reports/` | mapa da árvore e issues visíveis |
| `_incoming/` | evidência de ingestão/semente |
| `archive/` | evidência histórica/anterioridade |
| `docs/active/` | documentação vigente |

---

## Estados do ledger

| Estado | Significado |
|---|---|
| `FATO_IMPLEMENTADO` | código existe e tem caminho identificável |
| `FATO_DOCUMENTADO` | documentação vigente cobre a ideia |
| `DOC_ATRASADA` | código existe, doc ainda insuficiente |
| `INCUBADORA` | implementação/semente existe fora do canônico |
| `INGESTAO` | artefato precisa triagem antes de virar base |
| `HISTORICO` | registro preservado, não estado atual |
| `TOKEN_VAZIO` | não classificado ainda |
| `F_NEXT` | próxima ação de documentação/canonização |

---

## Casos já canonizados nesta rodada

| Objeto | Evidência | Estado | Documento |
|---|---|---|---|
| Warning como sinal | `-Wunused` + `gc-sections` + visibilidade hidden | `FATO_DOCUMENTADO` | `LOWLEVEL_WARNING_INTENT_CONTRACT.md` |
| Comentário como marker | `DOC_ORG_SCAN`, `HOTFIX`, `source-scan`, ABI refs | `FATO_DOCUMENTADO` | `LOWLEVEL_WARNING_INTENT_CONTRACT.md` |
| Void freestanding | `void*`, `(void)x`, `typedef void` em compat/baremetal | `DOC_ATRASADA` | `F_NEXT` |
| Anti-óbvio | classificação antes de corrigir/promover/apagar | `FATO_DOCUMENTADO` | `VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT.md` |
| BITWALK/BITGHOST | docs e header experimental em `Rafaelia/` | `INCUBADORA` | docs RAFAELIA correspondentes |
| Container molecular | headers/pages/CRC/rota/BITWALK | `FATO_DOCUMENTADO` | `RAFAELIA_CONTAINER_MOLECULAR_STORAGE.md` |
| TCG delta XOR | `rmr_tcg_cache.*` | `FATO_DOCUMENTADO` parcial | `VECTRA_TCG_DELTA_XOR_AUDIT_2026-06-11.md` |
| RAFCODEphi | `tools/baremetal/rafcode_phi/` | `DOC_ATRASADA` para relação com BITWALK | `F_NEXT` |
| `_incoming/pending` ASM | múltiplos `.S` e `.c` | `INGESTAO`/`INCUBADORA` | `F_NEXT` |

---

## Padrão para novas entradas

```text
objeto:
local:
evidencia:
estado:
o_que_existe:
o_que_nao_existe:
risco_se_ler_como_obvio:
valor_da_lacuna:
proxima_acao:
```

---

## Prioridade de documentação

1. Contratos que impedem erro de auditoria.
2. Núcleos low-level já executando.
3. Relações entre incubadora e engine canônico.
4. Pontes Android/Kotlin/Java → JNI → engine.
5. Inventário de `_incoming` e `Incluir` sem promover em bloco.
6. Lacunas de build/CI sem inferência silenciosa.

---

## Regras

```text
Se o código existe e a doc não explica, marcar DOC_ATRASADA.
Se a doc existe mas o código ainda não entrou no build, marcar INCUBADORA.
Se há artefato mas não há triagem, marcar INGESTAO.
Se há hipótese sem evidência, marcar TOKEN_VAZIO.
Se há execução comprovada, marcar FATO_IMPLEMENTADO.
```

---

## Próximas canonizações sugeridas

| Prioridade | Documento sugerido | Escopo |
|---|---|---|
| P0 | `VECTRA_FREESTANDING_VOID_CONTRACT.md` | void como fronteira freestanding |
| P0 | `VECTRA_INCUBATOR_TO_CORE_PROMOTION_PROTOCOL.md` | Rafaelia/_incoming → engine/rmr |
| P1 | `VECTRA_RAFCODE_PHI_BRIDGE.md` | C casca, ASM núcleo, hex, VecBit |
| P1 | `VECTRA_RMR_TCG_CACHE_SEMANTICS.md` | miss, delta XOR, bits preservados |
| P2 | `VECTRA_APP_RUNTIME_TO_ENGINE_MAP.md` | Android/Kotlin/Java → JNI → RMR |
| P2 | `VECTRA_INCOMING_TRIAGE_MATRIX.md` | classificação de zips, ASM e scripts |

---

## Frase final

```text
A documentação deve seguir o código vivo, não apagar o código vivo para caber na documentação antiga.
```
