# RAFAELIA — Índice de contratos e esquemas Vectras-VM-Android (27/10/2026)

> **SOURCE OBSERVED / DRAFT / HOLD.** Censo da árvore Git `a879abafe6c8b77c2499644d94d4a0fa371896c5`, `truncated=false`. NÃO afirmar execução, segurança, identidade humana, certificação, migração de schema ou prova acadêmica por presença de arquivo. Preservar documentos e licenças anteriores.

## Escopo

- Produtor: `rafaelmeloreisnovo/Vectras-VM-Android`.
- Quantidade de superfícies contratuais selecionadas: **11** arquivos, entre 2797 arquivos da árvore. O escopo é por caminho `qualquer segmento contracts/ ou schemas/`, incluindo contratos em subárvores relevantes; **não inclui automaticamente todo arquivo Java/Kotlin/C chamado Contract em outros caminhos**.
- Cada SHA abaixo é o SHA-1 de objeto Git (blob), suficiente para identificar a versão e não para provar a propriedade intelectual ou execução.

## Papéis e limites observados

Contrato consumidor IPC V3 vincula Termux App e preserva refresh_required=true; hardware/guest boot não provados. tools_runtime_connectivity controla marcadores de fonte, não execução física.

## Registro por arquivo

| Nº | Arquivo (base Git) | Bytes | Blob Git | Classe inicial | Evidência |
|---:|---|---:|---|---|---|
| 1 | [`contracts/t7/RAFAELIA_T7_WORKSPACE_VM_GATE.v1.json`](../contracts/t7/RAFAELIA_T7_WORKSPACE_VM_GATE.v1.json) | 1166 | `06539482b9096b3c585678f25c6c7194432fe44b` | MANIFESTO/POLITICA_JSON | `SOURCE_ONLY` |
| 2 | [`contracts/tools_runtime_connectivity.v1.json`](../contracts/tools_runtime_connectivity.v1.json) | 5929 | `bb18fd21823d2ef6fa2bc5f4804da784a2734207` | MANIFESTO/POLITICA_JSON | `SOURCE_ONLY` |
| 3 | [`docs/contracts/MISSION_EXECUTION_RUNTIME_BINDING_V1.md`](../docs/contracts/MISSION_EXECUTION_RUNTIME_BINDING_V1.md) | 1418 | `12354dd54c2e32a9994a2098df5284d72f3bcd82` | DESCRICAO | `SOURCE_ONLY` |
| 4 | [`docs/contracts/TRI_REPO_MISSION_COHESION_BINDING_V1.md`](../docs/contracts/TRI_REPO_MISSION_COHESION_BINDING_V1.md) | 2169 | `34aaa3f11e3784ee5194a40417bff2d98ded0267` | DESCRICAO | `SOURCE_ONLY` |
| 5 | [`docs/contracts/VECTRAS_TERMUX_IPC_V2.md`](../docs/contracts/VECTRAS_TERMUX_IPC_V2.md) | 1806 | `d5f6c65e44f5e0edc8463014ce4f80c32cfd5fe7` | DESCRICAO | `SOURCE_ONLY` |
| 6 | [`docs/contracts/VECTRAS_TERMUX_IPC_V3.json`](../docs/contracts/VECTRAS_TERMUX_IPC_V3.json) | 3088 | `8992d5b3ae69685358f307a7b213b28f425983f3` | MANIFESTO/POLITICA_JSON | `SOURCE_ONLY` |
| 7 | [`docs/contracts/VECTRAS_TERMUX_IPC_V3.md`](../docs/contracts/VECTRAS_TERMUX_IPC_V3.md) | 4393 | `3fd80a9721e41ef396e0ffa5c021dd281b298420` | DESCRICAO | `SOURCE_ONLY` |
| 8 | [`docs/contracts/VECTRAS_X3264_SQUARE_MEDIAN_POC_V1.json`](../docs/contracts/VECTRAS_X3264_SQUARE_MEDIAN_POC_V1.json) | 1908 | `e7eea7bee9cecb0998ceb7648583326463b2ee7d` | MANIFESTO/POLITICA_JSON | `SOURCE_ONLY` |
| 9 | [`docs/contracts/VECTRAS_X3264_SQUARE_MEDIAN_POC_V1.md`](../docs/contracts/VECTRAS_X3264_SQUARE_MEDIAN_POC_V1.md) | 2806 | `a61089b669fc95c313ad909ef75536aee69c8c68` | DESCRICAO | `SOURCE_ONLY` |
| 10 | [`docs/contracts/vectras.runtime-evidence.v1.schema.json`](../docs/contracts/vectras.runtime-evidence.v1.schema.json) | 3722 | `249cd8aa5a624ac5e1adf4d6386ab9a4bf7a4625` | SCHEMA_JSON | `SOURCE_ONLY` |
| 11 | [`schemas/guest_boot_evidence.v1.schema.json`](../schemas/guest_boot_evidence.v1.schema.json) | 2249 | `9c6d3c42e62ee328872f0b0dbac8914b8ea506b1` | SCHEMA_JSON | `SOURCE_ONLY` |

## Riscos / falsificadores

1. IPC provider vs consumer: comparação de keys, runner, extras e limites.
2. schemas guest_boot_evidence e runtime-evidence: ensaio de falsificação com recibo real.
3. T7 workspace: preservar rollback antes de mutação de imagens.

## Ciclo de refatoração segura

1. **Fonte**: produtor, HEAD, blob SHA e licença por arquivo.
2. **Semântica**: diferenciar esquema de dados, política, exemplo, implementação C/Java e documento histórico.
3. **Consumidor**: localizar import/chamada real e versão compatível (não inferir por nome).
4. **Teste negativo**: rejeição de campo obrigatório ausente, tipo incorreto, privilégio não autorizado e versão divergente.
5. **Receipts**: BUILD != LINK != RUNTIME != DEVICE != CLAIM; `TOKEN_VAZIO` permanece estado válido fora de gates fechados.
6. **Privacidade e direitos**: não publicar conteúdo confidencial; preservar upstream e controle humano.
7. **Rollback**: reverter somente adições do PR de documentação, sem alterações nos esquemas originais.

**F_ok:** arquivos e blobs enumerados em HEAD observado; **F_gap:** parser/validator/runtime não executados, consumidor completo não fechado; **F_next:** adaptadores/versionamento, testes negativos e revisão de segurança por responsável. `claim_allowed=false`.
