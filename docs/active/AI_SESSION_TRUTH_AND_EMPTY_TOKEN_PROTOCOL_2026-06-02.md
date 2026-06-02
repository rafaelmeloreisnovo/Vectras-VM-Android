<!-- DOC_ORG_SCAN: 2026-06-02 | source-scan: ai-assisted-session-protocol -->

# Protocolo de Sessão IA — Verdade, Token Vazio e Expansão Semântica

## Resumo
Este documento transforma o pedido de organização semântica em um protocolo operacional para sessões de IA no repositório. Ele separa fatos verificáveis, metáforas didáticas, hipóteses, lacunas e próximos passos, evitando que a sessão tente parecer útil quando não há base real suficiente.

## 1) O fato da situação
Uma sessão de usuário não é um tema por si só: ela é um **contexto temporário de trabalho** composto por mensagens, instruções, arquivos visíveis, ferramentas disponíveis, limites de execução e evidências coletadas. Antes de existir um tema técnico validado, a resposta correta deve declarar o vazio informacional em vez de inventar direção.

No contexto deste repositório, o fato operacional é:

1. O usuário forneceu uma intenção ampla: organizar, expandir, catalogar e criar navegação para arquivos soltos, fórmulas, metáforas, variáveis e trilhas de auditoria.
2. A árvore já contém documentação ativa de organização, navegação e referência RAFAELIA/T7.
3. A sessão precisa preservar a diferença entre **linguagem inspiradora** e **evidência executável**.
4. O token vazio é permitido e útil quando representa uma lacuna honesta: `sem evidência`, `não verificado`, `não há tema definido`, `não há teste executado`.
5. A resposta deve ser melhor depois da instrução do usuário porque passa a usar uma disciplina explícita de classificação, não porque passa a aceitar afirmações sem prova.

## 2) O que muda nas respostas antes e depois desta instrução

| Dimensão | Antes da instrução | Depois da instrução |
|---|---|---|
| Escopo | Responder ao pedido imediato. | Primeiro classificar o pedido em fato, hipótese, metáfora, risco, ação e lacuna. |
| Vazio | Tentar preencher ambiguidades com suposições moderadas. | Declarar vazio útil quando não há ponto real suficiente. |
| Metáfora | Tratar metáforas como estilo ou ruído. | Tratar metáforas como parábolas didáticas que precisam de tradução técnica. |
| Organização | Produzir resposta textual isolada. | Criar navegação, catálogo, critérios de promoção e rollback documental quando aplicável. |
| Verdade | Usar coerência interna da conversa. | Exigir vínculo com arquivo, teste, comando, hash, linha, workflow ou limitação explícita. |
| Enterprise | Dar recomendações gerais. | Separar leitura executiva, auditoria, engenharia, segurança, pesquisa e operação. |
| Segurança | Evitar dano óbvio. | Além disso, separar ciência verificável de metáfora e impedir claims técnicos não testados. |

## 3) O token vazio

**Definição:** token vazio é uma declaração mínima de ausência de evidência suficiente. Ele é melhor que uma resposta inventada porque mantém a cadeia de verdade intacta.

Use token vazio quando:

- não existe tema técnico definido;
- não há arquivo apontado;
- não há teste executado;
- não há evidência de runtime;
- a afirmação é filosófica/metafórica e ainda não foi traduzida para requisito;
- a hipótese exigiria dados externos, medição, corpus linguístico, áudio, osciloscópio, benchmark ou prova formal.

Exemplos seguros:

- `Tema técnico ainda não definido.`
- `Sem evidência de código para esta afirmação.`
- `Hipótese conceitual; não promovida a requisito.`
- `Metáfora preservada como parábola didática, sem claim científico.`
- `Teste não executado; resultado desconhecido.`

## 4) Metáforas como parábolas didáticas

Metáforas devem ser preservadas quando ajudam a ensinar, mas não devem substituir validação. A forma correta é converter cada imagem em uma parábola técnica:

| Metáfora/parábola | Tradução operacional | Critério de verdade |
|---|---|---|
| `Plancks/yactos de sentido` | granularidade máxima de inventário e rastreabilidade | arquivo catalogado, hash, linha, teste ou lacuna registrada |
| `Toro/T7` | mapeamento multidimensional de estado | fórmula documentada e invariantes preservados |
| `Omega/fractal` | ciclos recursivos de revisão | ciclo com entrada, saída, métrica e rollback |
| `Coerência × Amor^∞ × Prova` | cuidado com usuário + integridade + validação | não mentir, não ocultar falhas, citar evidências |
| `Fissura nuclear de potencial` | detectar requisito latente não pedido | só promover se houver benefício, teste e risco controlado |
| `Dicionário como som escrito` | camada fonética/multilíngue separada da semântica | corpus, método e métrica antes de claim linguístico |
| `Crise/oportunidade multilingue` | risco de perda semântica em traduções | matriz de idiomas, variantes, entonação e limites |

## 5) Sessão comum versus sessão qualificada

| Mecanismo | Sessão comum | Sessão qualificada para este repositório |
|---|---|---|
| Contexto | Mensagens recentes. | Mensagens + AGENTS.md + árvore Git + comandos + documentos canônicos. |
| Tema | Pode ser inferido. | Só é promovido quando há objetivo, arquivo ou evidência. |
| Quantificação | Baixa ou narrativa. | Profundidade, cobertura, hashes, linhas, testes, PASS/FAIL/SKIPPED. |
| Qualificação | Tom e intenção. | Estado: fato, hipótese, metáfora, lacuna, risco, ação, rollback. |
| Memória | Conversacional. | Rastreável por arquivos versionados e PR. |
| Falha | Pode ser resumida. | Deve ser exposta com causa, impacto e próximo passo. |
| Ação | Pode sugerir. | Deve preservar funcionalidades existentes e não fechar bugs sem fix. |

## 6) Sete direções de classificação

1. **Fato verificável:** existe em arquivo, comando, log, teste, hash ou workflow.
2. **Hipótese:** ideia plausível ainda sem prova.
3. **Metáfora/parábola:** linguagem de ensino que precisa de tradução técnica.
4. **Requisito:** comportamento esperado com critério de aceitação.
5. **Risco:** modo de falha, ambiguidade, segurança, regressão ou claim sem evidência.
6. **Experimento:** método para transformar hipótese em evidência.
7. **Rollback/failsafe:** caminho de retorno se a promoção quebrar coerência.

## 7) Sete reversas/antiderivadas para evitar autoengano

1. **O que não está provado?**
2. **Que dado faria a afirmação ser falsa?**
3. **Qual arquivo ou teste deveria existir e não existe?**
4. **Qual metáfora pode estar escondendo uma lacuna técnica?**
5. **Qual otimização pode aumentar risco ou acoplamento?**
6. **Qual tradução perde entonação, cadência ou sentido?**
7. **Qual rollback preserva o usuário se a ideia falhar?**

## 8) Dois ciclos multifuncionais de trabalho

### Ciclo 1 — Inventário e verdade mínima

1. Catalogar arquivos soltos e entradas pendentes.
2. Separar documentação ativa, histórica, experimental e fragmentária.
3. Marcar cada item como fato, hipótese, metáfora, risco ou lacuna.
4. Criar navegação de 5 níveis.
5. Registrar comandos executados e resultados.
6. Não mover nem apagar artefatos sem manifesto.
7. Entregar lista PASS/FAIL/SKIPPED.

### Ciclo 2 — Promoção e expansão controlada

1. Converter metáforas úteis em requisitos testáveis.
2. Consolidar fórmulas e variáveis em dicionário técnico.
3. Criar testes para invariantes promovidos.
4. Adicionar failsafe/failover/rollback por domínio.
5. Validar impacto em build, CI, segurança e runtime.
6. Rebaixar claims não comprovados para seção de hipótese.
7. Atualizar navegação e PR com evidências.

## 9) Aplicação direta às fórmulas e variáveis RAFAELIA/T7

As fórmulas e variáveis fornecidas devem ser tratadas em quatro camadas:

1. **Vocabulário:** nome, símbolo, unidade, domínio e significado.
2. **Invariante:** relação que não pode quebrar, como `|A| = 42`, `period(BitOmega)=42`, `gcd(Δr,R)=1` e `φ=(1-H)·C`.
3. **Implementação:** arquivo, função, script ou módulo que concretiza a fórmula.
4. **Validação:** teste, benchmark, comparação upstream, falsificação ou limitação.

Se uma fórmula não tiver implementação ou teste, ela permanece como referência conceitual, não como claim de produção.

## 10) Navegação recomendada nesta sessão

1. Começar por `START_HERE.md` para entrada geral.
2. Usar `DOC_INDEX.md` para localizar documentação ativa.
3. Usar `docs/organization/README.md` para saneamento de arquivos soltos e fragmentos.
4. Usar `docs/rafaelia_reference/RAFAELIA_T7_VARIABLES_FORMULAS.md` para vocabulário técnico de fórmulas/variáveis.
5. Usar este protocolo para decidir quando responder, quando declarar vazio e quando promover uma ideia a requisito.

## 11) Limite ético-operacional

A resposta de IA não deve fingir certeza. O padrão correto para este repositório é:

> **coerência + cuidado + prova**: ajudar sem ocultar lacunas, expandir sem inventar fatos, e transformar metáforas em trilhas auditáveis quando houver evidência suficiente.

## Metadados

- Versão do documento: 1.0
- Data: 2026-06-02
- Escopo: protocolo de sessão, organização semântica, token vazio, metáforas como parábolas e promoção controlada de hipóteses.
- Impacto em código: nenhum; documentação ativa.
