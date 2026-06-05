<!-- DOC_ORG_SCAN: 2026-04-17 | source-scan: ai-assisted-structural-review -->

# docs/active/

Documentação operacional vigente (fonte ativa para execução, build e release).

## Escopo
- Guias normativos e operacionais ainda em vigor.
- Relatórios vigentes para triagem técnica e decisão de engenharia.
- Modelos de sessão de IA para revisão sistêmica de build/CI/integração nativa.

## Fonte canônica
Consulte [`docs/INDEX_CANONICAL.md`](../INDEX_CANONICAL.md) para a classificação única entre normativo, ativo, auditoria e histórico.

## Documentos ativos prioritários
- [`VECTRAS_SOURCE_AUDIT_2026-05-05.md`](VECTRAS_SOURCE_AUDIT_2026-05-05.md): auditoria Vectras de build/release/CI/NDK/JNI, causas-raiz e bloqueios antes de implementação corretiva.
- [`VECTRAS_ORIGIN_DOCUMENTS_2026-05-05.md`](VECTRAS_ORIGIN_DOCUMENTS_2026-05-05.md): matriz de documentos de origem para alinhar Gradle, CMake, CI, ABI, assinatura e artefatos.
- [`AI_SESSION_SYSTEM_MODEL_2026-04-17.md`](AI_SESSION_SYSTEM_MODEL_2026-04-17.md): protocolo de sessão de IA orientado a causa-raiz, contrato de execução e fechamento verificável.
- [`UNIFIED_CODE_DOCUMENTATION_ALIGNMENT_2026-04-12.md`](UNIFIED_CODE_DOCUMENTATION_ALIGNMENT_2026-04-12.md): alinhamento unificado código↔documentação com catálogo técnico por arquivo/funções.
- [`DOCUMENTATION_COVERAGE_AUDIT_2026-04-07.md`](DOCUMENTATION_COVERAGE_AUDIT_2026-04-07.md): inventário metódico de `*.md`, cobertura por domínio e pendências estruturais.
- [`ALL_MARKDOWN_FILES_2026-04-07.md`](ALL_MARKDOWN_FILES_2026-04-07.md): catálogo completo de Markdown localizado na árvore do repositório.

## Metadados
- Versão do documento: 1.2
- Última atualização: 2026-05-05
- Commit de referência: `HEAD`

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.

## Documentos ativos
- `AI_SESSION_FACT_MAP_2026-06-05.md` — mapa factual desta sessão: o que muda antes/depois do contexto, sessão comum versus sessão semântica ampliada, token vazio, 7 direções qualitativas/quantitativas e reversas.
- `AI_SESSION_TRUTH_AND_EMPTY_TOKEN_PROTOCOL_2026-06-02.md` — protocolo para separar fato, hipótese, metáfora/parábola, lacuna, risco e ação em sessões de IA, com uso explícito de token vazio quando não há evidência suficiente.
