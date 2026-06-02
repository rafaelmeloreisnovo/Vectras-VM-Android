<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

# Índice de Documentação do Repositório

## Entradas principais

- [`README.md`](README.md): visão institucional + navegação por camadas.
- [`docs/README.md`](docs/README.md): curadoria técnica.
- [`docs/THREE_LAYER_ANALYSIS.md`](docs/THREE_LAYER_ANALYSIS.md): análise estrutural em 3 camadas.
- [`docs/ROOT_FILE_CHAIN.md`](docs/ROOT_FILE_CHAIN.md): reflexão arquivo-a-arquivo da raiz.
- [`docs/AI_BUILD_RELEASE_INDEX.md`](docs/AI_BUILD_RELEASE_INDEX.md): índice operacional IA para build/release/ABI/signing.

## Organização documental e dados — 2026-06-02
- [`docs/organization/DOC_ORGANIZATION_PLAN_2026-06-02.md`](docs/organization/DOC_ORGANIZATION_PLAN_2026-06-02.md): plano de saneamento em ciclos, com failsafe/failover/rollback.
- [`docs/organization/LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md`](docs/organization/LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md): inventário de arquivos soltos, fragmentos e entradas pendentes até 7 níveis.
- [`docs/organization/SOURCE_ARCHITECTURE_SYNC_2026-06-02.md`](docs/organization/SOURCE_ARCHITECTURE_SYNC_2026-06-02.md): sincronização entre arquitetura de código atual e documentação até 5 níveis.
- [`docs/rafaelia_reference/RAFAELIA_T7_VARIABLES_FORMULAS.md`](docs/rafaelia_reference/RAFAELIA_T7_VARIABLES_FORMULAS.md): vocabulário consolidado de fórmulas, variáveis e invariantes RAFAELIA/T7.

## Documentos de raiz
| Documento | Finalidade | Público-alvo | Classificação | Localização atual | Inbound link |
|---|---|---|---|---|---|
| `README.md` | Porta de entrada do repositório e mapa de navegação geral. | Novos contribuidores, engenharia, auditoria técnica. | **Ativo** | Raiz | [`DOC_INDEX.md`](DOC_INDEX.md) |
| `DOC_INDEX.md` | Índice canônico da documentação e governança documental. | Engenharia, technical writers, manutenção de docs. | **Ativo** | Raiz | [`README.md`](README.md) |
| `PROJECT_STATE.md` | Estado corrente do ciclo de vida (stable/experimental/refactoring). | Liderança técnica, release managers, PM. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `CHANGELOG.md` | Histórico contínuo de mudanças por versão. | Desenvolvimento, QA, release managers. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `RELEASE_NOTES.md` | Notas executivas da release mais recente (registro temporal). | Produto, operação, stakeholders externos. | **Histórico** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `THIRD_PARTY_NOTICES.md` | Registro legal/licenças de terceiros. | Compliance, jurídico, segurança. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `VECTRA_CORE.md` | Referência conceitual do runtime Vectra Core MVP. | Engenharia de runtime e arquitetura. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `VECTRAS_MEGAPROMPT_DOCS.md` | Guia estratégico de documentação técnica ampla do projeto. | Documentação técnica, enablement e coordenação. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `BUILDING.md` | Guia operacional de pré-requisitos e comandos de build (CLI/Gradle/ABI). | Engenharia, integração contínua, mantenedores de build. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `TROUBLESHOOTING.md` | Runbook de diagnóstico para falhas de setup/build/bootstrap. | Engenharia, QA, suporte técnico. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `FIXES_SUMMARY.md` | Sumário operacional das correções aplicadas e impactos técnicos. | Engenharia, auditoria técnica, manutenção corretiva. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `VERSION_STABILITY.md` | Manifesto e checklist de estabilidade por versão/metodologia. | Arquitetura, release managers, governança técnica. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `archive/root-history/1.md` | Registro pontual de ajuste de compatibilidade/chaves. | Auditoria histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/ADVANCED_OPTIMIZATIONS.md` | Catálogo histórico de módulos avançados de otimização. | Engenharia/performance (consulta histórica). | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md` | Sumário histórico da etapa de refatoração de benchmark. | Performance, engenharia de validação. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/IMPLEMENTATION_COMPLETE.md` | Registro histórico de implementação consolidada. | Auditoria técnica e rastreabilidade histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/IMPLEMENTATION_SUMMARY.md` | Resumo histórico de implementação por evidências. | Auditoria técnica e documentação histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/VECTRAS_ANALYSIS_COMPLETE.md` | Relato histórico de análise consolidada. | Pesquisa histórica e auditoria. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/VECTRAS_DEEP_EVIDENCE.md` | Evidências históricas detalhadas de revisão/validação. | Auditoria e governança histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |

## Regra de manutenção do índice
- Todo novo arquivo Markdown na raiz (`./*.md`) deve ser incluído na tabela **Documentos de raiz** no mesmo commit/PR em que for criado ou movido.
- Antes de concluir mudanças na raiz, validar consistência com [`docs/ROOT_FILES_CATALOG.md`](docs/ROOT_FILES_CATALOG.md) e [`docs/ROOT_FILE_CHAIN.md`](docs/ROOT_FILE_CHAIN.md).
- Para revisão/refatoração documental, seguir o fluxo em [`docs/README.md#fluxo-de-revisão-documental-refatorado`](docs/README.md#fluxo-de-revisão-documental-refatorado).

## Diretórios com documentação completa
Cada diretório tem:
1) `README.md` (camadas 1 e 2)
2) `FILES_MAP.md` (camada 3)

- `app/`, `engine/`, `terminal-emulator/`, `terminal-view/`, `shell-loader/`, `bench/`, `bug/`, `demo_cli/`, `tools/`, `docs/`, `reports/`, `resources/`, `runtime/`, `web/`, `archive/`, `fastlane/`, `gradle/`, `3dfx/`.

- `bug/`: [`bug/README.md`](bug/README.md) e [`bug/FILES_MAP.md`](bug/FILES_MAP.md).


## Domínio de bugs (`bug/`)
- Escopo, objetivo e relação com `docs/`, `reports/` e raiz: [`bug/README.md`](bug/README.md).
- Mapa arquivo-a-arquivo com função e status: [`bug/FILES_MAP.md`](bug/FILES_MAP.md).
- Nomenclatura alinhada ao padrão (`README.md` + `FILES_MAP.md`) usado em `engine/`, `tools/` e `docs/`.

## Documentos técnicos especializados
- Narrativa em 5 níveis (executivo/técnico/operacional/verificabilidade/adoção): [`docs/navigation/BIGTECH_REVOLUTION_ANNOUNCE.md`](docs/navigation/BIGTECH_REVOLUTION_ANNOUNCE.md)
- Auditoria de cobertura Markdown por domínio (2026-04-07): [`docs/active/DOCUMENTATION_COVERAGE_AUDIT_2026-04-07.md`](docs/active/DOCUMENTATION_COVERAGE_AUDIT_2026-04-07.md)
- Catálogo completo de arquivos Markdown (2026-04-07): [`docs/active/ALL_MARKDOWN_FILES_2026-04-07.md`](docs/active/ALL_MARKDOWN_FILES_2026-04-07.md)
- Arquitetura: `docs/ARCHITECTURE.md`, `docs/RAFAELIA_COHESION_ENTERPRISE_STACK.md`
- Operação: `docs/OPERATIONS.md`, `docs/BLUEPRINT_FLUXOS_VM.md`
- Performance: `docs/PERFORMANCE_INTEGRITY.md`, `docs/BENCHMARKS.md`
- Conformidade: `docs/LEGAL_AND_LICENSES.md`, `docs/IP_MAP.md`, `THIRD_PARTY_NOTICES.md`
- Build/release para IA: `docs/AI_BUILD_RELEASE_INDEX.md`


## Governança operacional (CI e segurança)
- CI Android (wrapper de entrada): [`.github/workflows/android.yml`](.github/workflows/android.yml).
- CI Android canônico: [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml).
- CI Engine/host canônico: [`.github/workflows/host-ci.yml`](.github/workflows/host-ci.yml).
- Automação de dependências: [`.github/dependabot.yml`](.github/dependabot.yml).
- Security allowlist de artefatos sensíveis: [`security/sensitive-artifacts-allowlist.txt`](security/sensitive-artifacts-allowlist.txt).
- Verificador de artefatos sensíveis: [`tools/check_sensitive_artifacts.sh`](tools/check_sensitive_artifacts.sh).

## Núcleo Engine RMR
- Cabeçalhos low-level: [`engine/rmr/include/rmr_corelib.h`](engine/rmr/include/rmr_corelib.h), [`engine/rmr/include/rmr_ll_ops.h`](engine/rmr/include/rmr_ll_ops.h), [`engine/rmr/include/rmr_ll_tuning.h`](engine/rmr/include/rmr_ll_tuning.h), [`engine/rmr/include/rmr_math_fabric.h`](engine/rmr/include/rmr_math_fabric.h).
- Implementações C: [`engine/rmr/src/rmr_corelib.c`](engine/rmr/src/rmr_corelib.c), [`engine/rmr/src/rmr_ll_ops.c`](engine/rmr/src/rmr_ll_ops.c), [`engine/rmr/src/rmr_ll_tuning.c`](engine/rmr/src/rmr_ll_tuning.c), [`engine/rmr/src/rmr_math_fabric.c`](engine/rmr/src/rmr_math_fabric.c).

## Matriz de testes (app)
- Suite unitária de VM/runtime: [`app/src/test/java/com/vectras/vm/`](app/src/test/java/com/vectras/vm/).
- Suite unitária de terminal: [`app/src/test/java/com/vectras/vterm/`](app/src/test/java/com/vectras/vterm/).
- Contratos utilitários/QMP: [`app/src/test/java/com/vectras/qemu/utils/`](app/src/test/java/com/vectras/qemu/utils/).

## Relatórios e bugfix
- Relatório consolidado de correções: [`docs/BUGFIX_REPORT.md`](docs/BUGFIX_REPORT.md).

## RAFAELIA — 8 Caminhos Metodológicos (v1.8)
- Manifesto de estabilidade: [`VERSION_STABILITY.md`](VERSION_STABILITY.md) — checklist completo, 8 blocos.
- 8 Esferas Metodológicas: [`docs/ESFERAS_METODOLOGICAS_RAFAELIA.md`](docs/ESFERAS_METODOLOGICAS_RAFAELIA.md) — expandido de 5→8.
- Constantes de caminhos: [`app/src/main/java/com/vectras/vm/rafaelia/RafaeliaMethodPaths.java`](app/src/main/java/com/vectras/vm/rafaelia/RafaeliaMethodPaths.java).
- Validador runtime: [`app/src/main/java/com/vectras/vm/rafaelia/RafaeliaPathValidator.java`](app/src/main/java/com/vectras/vm/rafaelia/RafaeliaPathValidator.java).
- Testes unitários: [`app/src/test/java/com/vectras/vm/rafaelia/RafaeliaPathValidatorTest.java`](app/src/test/java/com/vectras/vm/rafaelia/RafaeliaPathValidatorTest.java).

## CI canonical reference (Android/Host)

- Canonical Android pipeline: `.github/workflows/android-ci.yml`.
- Android wrapper entrypoint: `.github/workflows/android.yml`.
- Auxiliary Android ABI compatibility matrix: `.github/workflows/compile-matrix.yml`.
- Canonical host pipeline: `.github/workflows/host-ci.yml`.
- Orchestration and final gates: `.github/workflows/pipeline-orchestrator.yml` and `.github/workflows/quality-gates.yml`.
- Canonical matrix documentation: `docs/ci/workflow-matrix.md`.

- reports/UNIFIED_COHERENCE_TECHNICAL_MAP.md — consolidação de coerência operacional técnica em camadas.
- reports/CORE_LOWLEVEL_IMPLEMENTATION_DOC.md — finalização da documentação do código low-level em core/.
