<!-- DOC_TAXONOMY_SYNC: 2026-06-08 | role: technical-hub -->

# Hub técnico — Vectras VM Android

Este diretório é o hub técnico canônico. Ele aprofunda a entrada curta do [`../README.md`](../README.md), complementa o índice global [`../DOC_INDEX.md`](../DOC_INDEX.md) e aponta para execução local/CI/release em [`../BUILDING.md`](../BUILDING.md).

## Responsabilidade deste hub

- Consolidar arquitetura, build, CI, release, segurança, low-level, governança e operação.
- Reduzir inferência silenciosa para humanos e IA: cada trilha deve indicar documento, status e vínculo com código/workflow.
- Separar o que é canônico, legado compatível, experimental/ingestão e histórico sem mover arquivos nesta etapa.
- Dar contexto suficiente para navegar com excelência operacional: o leitor deve saber onde começar, onde validar e onde não assumir estado atual.

## Taxonomia documental principal

| Entrada | Papel |
|---|---|
| [`../README.md`](../README.md) | Entrada institucional e operacional curta. |
| [`../DOC_INDEX.md`](../DOC_INDEX.md) | Índice global completo e tabela de status documental. |
| [`README.md`](README.md) | Hub técnico. |
| [`navigation/INDEX.md`](navigation/INDEX.md) | Navegação por audiência. |
| [`../PROJECT_STATE.md`](../PROJECT_STATE.md) | Estado real validado. |
| [`../BUILDING.md`](../BUILDING.md) | Execução local/CI/release. |

## Classificação de diretórios que afeta leitura técnica

| Classe | Diretórios | Implicação técnica |
|---|---|---|
| **Canônico** | [`../app/`](../app/), [`../engine/`](../engine/), [`../tools/ci/`](../tools/ci/), [`../.github/workflows/`](../.github/workflows/), [`./`](./) | Base para decisões técnicas, PRs, CI, release e documentação vigente. |
| **Legado compatível** | [`../android/`](../android/) | Consultar como compatibilidade/referência; não usar como entrypoint oficial de build/release. |
| **Experimental/ingestão** | [`../Incluir/`](../Incluir/), [`../addthis/`](../addthis/), [`../_incoming/`](../_incoming/) | Material pendente de triagem; exige revisão de licença, autoria, segurança, build e rollback antes de promoção. |
| **Histórico** | [`../archive/`](../archive/), [`../bug/archive/`](../bug/archive/) | Evidência e memória técnica; não substitui validação atual. |

## Trilhas técnicas canônicas

### 1. Estado e fonte de verdade
- Estado corrente: [`../PROJECT_STATE.md`](../PROJECT_STATE.md).
- Índice global e status documental: [`../DOC_INDEX.md`](../DOC_INDEX.md).
- Matriz de alinhamento de diretórios: [`active/DIRECTORY_ALIGNMENT_MATRIX.md`](active/DIRECTORY_ALIGNMENT_MATRIX.md).
- Plano de organização documental: [`organization/DOC_ORGANIZATION_PLAN_2026-06-02.md`](organization/DOC_ORGANIZATION_PLAN_2026-06-02.md).

### 2. Build, CI, release, ABI e assinatura
- Execução local/CI/release: [`../BUILDING.md`](../BUILDING.md).
- Índice IA build/release: [`AI_BUILD_RELEASE_INDEX.md`](AI_BUILD_RELEASE_INDEX.md).
- Matriz de workflows: [`ci/workflow-matrix.md`](ci/workflow-matrix.md).
- Workflows canônicos: [`../.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml), [`../.github/workflows/host-ci.yml`](../.github/workflows/host-ci.yml), [`../.github/workflows/pipeline-orchestrator.yml`](../.github/workflows/pipeline-orchestrator.yml), [`../.github/workflows/quality-gates.yml`](../.github/workflows/quality-gates.yml).
- Ferramentas CI/release: [`../tools/ci/`](../tools/ci/).

### 3. Arquitetura Android, runtime e engine
- Fluxo de execução VM: [`architecture/VM_EXECUTION_FLOW.md`](architecture/VM_EXECUTION_FLOW.md).
- Runtime/core: [`../VECTRA_CORE.md`](../VECTRA_CORE.md).
- App Android: [`../app/`](../app/).
- Engine nativo: [`../engine/`](../engine/).
- Guia low-level branchless/sem heap: [`active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`](active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md).
- Compilador/pré-compilador: [`active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`](active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md).

### 4. Segurança, compliance, autoria e privacidade
- Modelo de ameaças: [`THREAT_MODEL.md`](THREAT_MODEL.md).
- Segurança: [`../SECURITY.md`](../SECURITY.md).
- Privacidade: [`../PRIVACY.md`](../PRIVACY.md).
- Licenças e terceiros: [`../THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md), [`../LICENSES_REGISTER.md`](../LICENSES_REGISTER.md).
- Autoria clean-room: [`../AUTHORSHIP_CLEANROOM_PLAN.md`](../AUTHORSHIP_CLEANROOM_PLAN.md).
- Assets clean-room: [`../resources/compliance/ASSET_CLEANROOM_POLICY.md`](../resources/compliance/ASSET_CLEANROOM_POLICY.md).

### 5. Navegação por audiência e inclusão informacional
- Índice por audiência: [`navigation/INDEX.md`](navigation/INDEX.md).
- Operações/performance: [`navigation/PERFORMANCE_OPERATIONS.md`](navigation/PERFORMANCE_OPERATIONS.md).
- Empresas: [`navigation/ENTERPRISE_COMPANIES.md`](navigation/ENTERPRISE_COMPANIES.md).
- Pesquisa: [`navigation/SCIENTISTS_RESEARCH.md`](navigation/SCIENTISTS_RESEARCH.md).
- Universidades: [`navigation/UNIVERSITIES_ACADEMIC.md`](navigation/UNIVERSITIES_ACADEMIC.md).
- Benchmarking: [`navigation/BENCHMARK_COMPARISONS.md`](navigation/BENCHMARK_COMPARISONS.md).
- Governança/rastreabilidade: [`navigation/TRACEABILITY_GOVERNANCE.md`](navigation/TRACEABILITY_GOVERNANCE.md).

## Regras de leitura para humanos e IA

- Declare incerteza quando a evidência estiver em ingestão, legado ou histórico.
- Prefira links internos e arquivos canônicos antes de inferir contexto.
- Não promova material experimental por semântica forte; promova apenas por validação técnica, compliance e rollback.
- Não trate narrativa de performance como medição sem benchmark e relatório vinculados.
- Não enfraqueça o caminho de release assinado; use unsigned apenas em validação interna explícita.
