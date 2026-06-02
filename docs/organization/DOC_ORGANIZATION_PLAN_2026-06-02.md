# Plano de organização documental e dados — 2026-06-02

## Entrega executada nesta etapa

- Criado um inventário auditável de arquivos soltos e fragmentos até 7 níveis.
- Criada auditoria executável de condições necessárias, placeholders, gaps e sinais de bug até 5 níveis.
- Criado manifesto SHA-256 para entradas pendentes e overlays, permitindo rollback/failover por hash.
- Criado um mapa de sincronização entre código-fonte atual e documentação até 5 níveis de diretórios.
- Criada matriz enterprise de dados necessários por camada fullstack, com critérios de teste, promoção e rollback.
- Consolidado o vocabulário RAFAELIA/T7 de fórmulas, variáveis e invariantes em documento de referência.
- Atualizados os índices para que a documentação nova seja encontrável sem mover fontes nem apagar artefatos existentes.

## Decisão de não mover arquivos nesta primeira etapa

A árvore contém documentos canônicos, relatórios históricos, overlays ZIP, protótipos e imagens soltas. Mover tudo de uma vez aumentaria risco de links quebrados, perda de proveniência e regressão de CI. Portanto, esta etapa organiza por **classificação + documentação + rota de promoção**, mantendo rollback simples por Git.

## Ciclo 1 — saneamento seguro

| Passo | Ação | Critério PASS | Rollback |
|---|---|---|---|
| 1 | Inventariar arquivos até 7 níveis | Inventário versionado em `docs/organization/` | Remover documento novo |
| 2 | Mapear arquitetura até 5 níveis | Documento aponta módulos reais do build | Remover documento novo |
| 3 | Separar vocabulário conceitual | Fórmulas/variáveis em referência única | Remover documento novo |
| 4 | Auditar placeholders/gaps/bugs sinalizados | Relatório Markdown + JSON em `reports/` | Remover relatórios novos |
| 5 | Gerar manifesto SHA-256 de entradas | TSV em `reports/` + resumo em `docs/organization/` | Regerar manifesto ou reverter arquivo |
| 6 | Definir matriz fullstack de dados faltantes | Matriz versionada com teste/rollback por tipo de mudança | Reverter documento novo |
| 7 | Atualizar índices | Links novos em `docs/README.md` e `DOC_INDEX.md` | Reverter linhas adicionadas |

## Ciclo 2 — promoção com evidência

| Classe | Ação futura recomendada | Failsafe/failover |
|---|---|---|
| Notas Markdown soltas de raiz | `git mv` para `docs/rafaelia_reference/` ou `archive/root-history/` com manifesto | Se link quebrar, reverter `git mv` e manter alias no índice |
| Entradas `_incoming/` | Promover só após teste unitário/equivalência | Se teste falhar, manter como pendente e registrar motivo |
| Entradas `Incluir/` | Separar artigo, protótipo, imagem e overlay | Se hash divergir, não promover |
| ZIPs | Gerar SHA-256 e manifestos antes de extrair/remover | Se extração duplicar fonte, arquivar sem ativar |
| Documentos superados | Mover para `docs/archive/` com substituto explícito | Se documento ainda for citado, manter stub de redirecionamento |

## Arquitetura documental alvo

```text
README.md / START_HERE.md / DOC_INDEX.md  -> navegação de topo
docs/README.md                            -> hub técnico
docs/organization/                        -> inventário, saneamento, roteiros de promoção
docs/rafaelia_reference/                  -> fórmulas, invariantes e linguagem conceitual
docs/active/                              -> auditorias ainda acionáveis
docs/archive/                             -> material superado com referência histórica
reports/                                  -> resultados pontuais de execução/validação
```

## Riscos conhecidos que permanecem abertos

- `attractor_table` incompleta e VOID paradox #22 continuam bugs conhecidos, não resolvidos nesta etapa documental.
- Existem overlays ZIP e protótipos em `Incluir/` que podem estar à frente dos documentos, mas ainda sem integração segura.
- A documentação antiga pode misturar teoria, runtime real e relatórios históricos; a promoção futura deve separar esses papéis.
- Alguns testes de build podem depender de SDK/NDK/Gradle baixados no ambiente.

## Próximos passos recomendados

1. Usar o manifesto SHA-256 já criado para selecionar primeiro lote de promoção.
2. Fazer lote `git mv` só para notas conceituais de raiz, preservando links e aliases.
3. Atualizar `docs/ARCHITECTURE.md` com base no mapa de sincronização recém-criado.
4. Rodar `./build.sh` e `./run_tests.sh` em ambiente Android/NDK completo.
5. Criar relatório de documentos obsoletos com substituto canônico para cada um.
