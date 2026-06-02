<!-- DOC_ORG_SCAN: 2026-04-17 | source-scan: ai-assisted-structural-review -->

# Vectras VM Android — Technical Documentation Hub

Camada canônica de documentação técnica do repositório, organizada para engenharia de build/release, integração nativa (JNI/NDK/CMake) e operação de CI.

## Missão desta camada
- Consolidar **fonte de verdade** para arquitetura, build, CI, segurança e operação.
- Reduzir divergência entre código, workflow e documentos executáveis.
- Fornecer trilha auditável para revisão técnica, release e investigação de falhas.

## Cadeia de navegação oficial (5 níveis)
1. Institucional do repositório: [`../README.md`](../README.md)
2. Índice global: [`../DOC_INDEX.md`](../DOC_INDEX.md)
3. Hub técnico de documentação: [`README.md`](README.md)
4. Índice por audiência/domínio: [`navigation/INDEX.md`](navigation/INDEX.md)
5. Documento especializado por tema: arquivos em `docs/`, `docs/active/`, `docs/ci/` e `docs/navigation/`


## Organização documental e dados (2026-06-02)

- [`organization/DOC_ORGANIZATION_PLAN_2026-06-02.md`](organization/DOC_ORGANIZATION_PLAN_2026-06-02.md): plano em dois ciclos para organizar documentos, dados, fragmentos e entradas pendentes com rollback.
- [`organization/LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md`](organization/LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md): inventário de arquivos soltos/fragmentos até 7 níveis.
- [`organization/NECESSARY_CONDITIONS_AUDIT_2026-06-02.md`](organization/NECESSARY_CONDITIONS_AUDIT_2026-06-02.md): auditoria profissional de placeholders, bugs sinalizados, gaps de navegação e condições necessárias até 5 níveis.
- [`organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md`](organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md): manifesto SHA-256 de entradas pendentes e overlays para promoção com rollback.
- [`organization/SOURCE_ARCHITECTURE_SYNC_2026-06-02.md`](organization/SOURCE_ARCHITECTURE_SYNC_2026-06-02.md): mapa código-fonte ↔ documentação baseado na árvore real até 5 níveis.
- [`rafaelia_reference/RAFAELIA_T7_VARIABLES_FORMULAS.md`](rafaelia_reference/RAFAELIA_T7_VARIABLES_FORMULAS.md): dicionário RAFAELIA/T7 de fórmulas, variáveis e invariantes para promoção futura a código/testes.

## Fonte de verdade por domínio
| Domínio | Documento principal | Documentos de suporte |
|---|---|---|
| Arquitetura e runtime | [`ARCHITECTURE.md`](ARCHITECTURE.md) | [`API.md`](API.md), [`THREE_LAYER_ANALYSIS.md`](THREE_LAYER_ANALYSIS.md) |
| Build Android/NDK/JDK | [`BUILD_ENV_ALIGNMENT.md`](BUILD_ENV_ALIGNMENT.md) | [`SETUP_SDK_NDK.md`](SETUP_SDK_NDK.md), [`BUILD_REFACTOR_SCOPE.md`](BUILD_REFACTOR_SCOPE.md) |
| CI/CD e artefatos | [`OPERATIONS.md`](OPERATIONS.md) | [`ci/android-mode-matrix.md`](ci/android-mode-matrix.md), [`INDEX_CANONICAL.md`](INDEX_CANONICAL.md) |
| Segurança/compliance | [`SECURITY.md`](SECURITY.md) | [`LEGAL_AND_LICENSES.md`](LEGAL_AND_LICENSES.md), [`SOURCE_TRACEABILITY_MATRIX.md`](SOURCE_TRACEABILITY_MATRIX.md) |
| Estado e governança | [`../PROJECT_STATE.md`](../PROJECT_STATE.md) | [`DOCUMENTATION_STANDARDS.md`](DOCUMENTATION_STANDARDS.md), [`active/README.md`](active/README.md) |

## Fluxo operacional de manutenção documental
1. Identificar alteração real de código/build/workflow.
2. Atualizar documento primário do domínio impactado.
3. Sincronizar índices (`../README.md`, `../DOC_INDEX.md`, `navigation/INDEX.md`, `INDEX_CANONICAL.md`) quando houver impacto de navegação.
4. Executar validações automatizadas de referência e consistência.
5. Registrar metadados de versão/data/commit.

## Sessão de IA para compreensão sistêmica (padrão)
Use o protocolo abaixo em revisões profundas:
- **Mapeamento de causa-raiz**: localizar divergência estrutural (não só sintoma).
- **Contrato de execução**: validar ordem `toolchain -> configure -> build -> artifact -> upload`.
- **Conferência de artefatos**: garantir que CI e build local descrevam as mesmas saídas.
- **Rastreabilidade**: cada mudança documental deve apontar para scripts/workflows reais.

Documento de apoio para esse protocolo: [`active/AI_SESSION_SYSTEM_MODEL_2026-04-17.md`](active/AI_SESSION_SYSTEM_MODEL_2026-04-17.md).

## Validação mínima recomendada
```bash
./tools/check_docs_reference_commit.sh
python3 tools/verify_repo_file_dependencies.py
./tools/ci/validate_pipeline_directories.sh
```

## Metadados
- Versão do documento: 2.0
- Última atualização: 2026-04-17
- Commit de referência: `HEAD`
- Domínio de código coberto: documentação transversal para app, engine, tools, web, runtime e CI.

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
