<!-- DOC_ORG_SCAN: 2026-04-17 | source-scan: ai-assisted-structural-review -->

# INDEX_CANONICAL

Índice canônico único para documentação técnica e evidências de execução.

## 1) Normativo (fonte de verdade obrigatória)
- `README.md`
- `BUILDING.md`
- `TROUBLESHOOTING.md`
- `PROJECT_STATE.md`
- `docs/README.md`
- `docs/ARCHITECTURE.md`
- `docs/OPERATIONS.md`
- `docs/BUILD_ENV_ALIGNMENT.md`
- `docs/SETUP_SDK_NDK.md`
- `docs/SECURITY.md`
- `docs/LEGAL_AND_LICENSES.md`
- `docs/DOCUMENTATION_STANDARDS.md`
- `docs/METODOLOGIA_ORGANIZACAO_7_DIRECOES.md`
- `docs/RAFAELIA_T7_MULTILINGUAL_MAPPING.md`
- `reports/CANONICAL_BUILD_STATUS.md`

## 2) Ativo (estado operacional vigente)
- `docs/active/AI_SESSION_FACT_MAP_2026-06-05.md`
- `docs/rafaelia_reference/RAFAELIA_SEED_BLOCK_CATALOG_2026-06-05.md`
- `docs/active/README.md`
- `docs/active/AI_SESSION_SYSTEM_MODEL_2026-04-17.md`
- `docs/active/UNIFIED_CODE_DOCUMENTATION_ALIGNMENT_2026-04-12.md`
- `docs/ci/android-mode-matrix.md`
- `reports/BUILD_VALIDATION_2026-04-03.md`
- `reports/build-verification-2026-04-03-fix.md`
- `reports/build-verification-2026-04-03.md`

## 3) Auditoria e inventário
- `reports/FULL_REPO_AUDIT_REPORT.md`
- `reports/NON_MD_AUDIT_REPORT.md`
- `reports/non_md_inventory.tsv`
- `reports/full_repo_audit.tsv`
- `reports/EXTERNAL_FORKS_BUILD_INPUTS.md`
- `reports/NDK_DEPENDENCY_OVERHEAD_AUDIT.md`

## 4) Histórico superseded
- `docs/archive/README.md`
- `docs/archive/2026-04-06_status-superseded_BUILD_FAILURE_AUDIT.md`
- `docs/archive/2026-04-06_status-superseded_VM_SUPERVISION_AUDIT_EVIDENCE.md`
- `archive/root-history/`

## 5) Evidências brutas
- `evidence/2026-04-06_status-raw_bugs_vectras_completo.txt`

## Governança
- Este arquivo é o único índice transversal canônico.
- Todo novo documento deve ser classificado em um dos blocos acima.
- Conteúdo superseded deve migrar para `docs/archive/` com marcador temporal no nome.

## Metadados
- Versão do documento: 2.2
- Última atualização: 2026-05-04
- Commit de referência: `HEAD`

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
