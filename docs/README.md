<!-- DOC_TAXONOMY_SYNC: 2026-06-13 | role: technical-hub -->

# Hub técnico — Vectras VM Android

## Metadados canônicos

- Versão do documento: 2.7.
- Última revisão: 2026-06-13.
- Escopo: hub técnico de arquitetura, build/release, CI, segurança, documentação, operação e pesquisa.
- Status: canônico vigente.
- Commit de referência: `HEAD`.
- Fonte de verdade relacionada: [`../DOC_INDEX.md`](../DOC_INDEX.md), [`navigation/INDEX.md`](navigation/INDEX.md), [`DOCUMENTATION_STANDARDS.md`](DOCUMENTATION_STANDARDS.md) e [`ci/workflow-matrix.md`](ci/workflow-matrix.md).

Este diretório é o hub técnico canônico. Ele aprofunda a entrada curta do [`../README.md`](../README.md), complementa o índice global [`../DOC_INDEX.md`](../DOC_INDEX.md) e aponta para execução local/CI/release em [`../BUILDING.md`](../BUILDING.md).

## Responsabilidade deste hub

- Consolidar arquitetura, build, CI, release, segurança, low-level, governança e operação.
- Reduzir inferência silenciosa para humanos e IA: cada trilha deve indicar documento, status e vínculo com código/workflow.
- Separar o que é canônico, legado compatível, experimental/ingestão e histórico sem mover arquivos nesta etapa.
- Dar contexto suficiente para navegar com excelência operacional: o leitor deve saber onde começar, onde validar e onde não assumir estado atual.
- Registrar quando o código executando está à frente da documentação, sem confundir documentação atrasada com ausência técnica.
- Preservar continuidade de atividade: cada rodada deve deixar trilha para humanos e IA continuarem sem recomeçar do zero.
- Verificar o todo antes de mexer na parte, usando fluxograma holístico quando o caminho não estiver claro.

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
- Auditoria TCG delta XOR / ISOraf: [`active/VECTRA_TCG_DELTA_XOR_AUDIT_2026-06-11.md`](active/VECTRA_TCG_DELTA_XOR_AUDIT_2026-06-11.md).
- Contrato de warning como intenção: [`active/LOWLEVEL_WARNING_INTENT_CONTRACT.md`](active/LOWLEVEL_WARNING_INTENT_CONTRACT.md).
- Contrato freestanding `void`: [`active/VECTRA_FREESTANDING_VOID_CONTRACT.md`](active/VECTRA_FREESTANDING_VOID_CONTRACT.md).
- Ponte RAFCODEphi C→ASM→hex: [`active/VECTRA_RAFCODE_PHI_BRIDGE.md`](active/VECTRA_RAFCODE_PHI_BRIDGE.md).

### 4. Canonização anti-óbvio e documentação atrasada
- Mapa holístico global e fluxograma: [`active/VECTRA_GLOBAL_HOLISTIC_FLOWMAP.md`](active/VECTRA_GLOBAL_HOLISTIC_FLOWMAP.md).
- Guia de continuidade da execução e leitura: [`active/VECTRA_EXECUTION_CONTINUITY_READING_GUIDE.md`](active/VECTRA_EXECUTION_CONTINUITY_READING_GUIDE.md).
- Cânone de conceitos da obra: [`active/VECTRA_OBRA_CONCEPTS_CANON.md`](active/VECTRA_OBRA_CONCEPTS_CANON.md).
- Plano de excelência operacional e espaço de trabalho: [`active/VECTRA_OPERATIONAL_EXCELLENCE_WORKSPACE_PLAN.md`](active/VECTRA_OPERATIONAL_EXCELLENCE_WORKSPACE_PLAN.md).
- Contrato anti-óbvio de revisão: [`active/VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT.md`](active/VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT.md).
- Ledger código à frente da documentação: [`active/VECTRA_CODE_AHEAD_OF_DOCS_LEDGER.md`](active/VECTRA_CODE_AHEAD_OF_DOCS_LEDGER.md).
- Protocolo de refatoração por fricção determinística: [`active/VECTRA_FRICTION_DETERMINISTIC_REFACTOR_PROTOCOL.md`](active/VECTRA_FRICTION_DETERMINISTIC_REFACTOR_PROTOCOL.md).
- Protocolo incubadora → core: [`active/VECTRA_INCUBATOR_TO_CORE_PROMOTION_PROTOCOL.md`](active/VECTRA_INCUBATOR_TO_CORE_PROMOTION_PROTOCOL.md).
- Matriz de triagem de incoming/incubadora: [`active/VECTRA_INCOMING_TRIAGE_MATRIX.md`](active/VECTRA_INCOMING_TRIAGE_MATRIX.md).
- Ledger Lote A RAFAELIA: [`active/VECTRA_RAFAELIA_LOTE_A_TRIAGE_LEDGER.md`](active/VECTRA_RAFAELIA_LOTE_A_TRIAGE_LEDGER.md).
- Nota de continuidade B3: [`active/VECTRA_RAFAELIA_B3_CONTINUITY_NOTE.md`](active/VECTRA_RAFAELIA_B3_CONTINUITY_NOTE.md).

### 5. Segurança, compliance, autoria e privacidade
- Modelo de ameaças: [`THREAT_MODEL.md`](THREAT_MODEL.md).
- Segurança: [`../SECURITY.md`](../SECURITY.md).
- Privacidade: [`../PRIVACY.md`](../PRIVACY.md).
- Licenças e terceiros: [`../THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md), [`../LICENSES_REGISTER.md`](../LICENSES_REGISTER.md).
- Autoria clean-room: [`../AUTHORSHIP_CLEANROOM_PLAN.md`](../AUTHORSHIP_CLEANROOM_PLAN.md).
- Assets clean-room: [`../resources/compliance/ASSET_CLEANROOM_POLICY.md`](../resources/compliance/ASSET_CLEANROOM_POLICY.md).

### 6. Navegação por audiência e inclusão informacional
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
- Não trate a auditoria TCG delta XOR como prova de aceleração geral; ela é evidência de mudança semântica, selftest e métrica local até existir benchmark comparativo.
- Não enfraqueça o caminho de release assinado; use unsigned apenas em validação interna explícita.
- Não trate documentação atrasada como ausência de conceito quando há evidência em código, build, comentário, warning, manifesto ou artefato.
- Não chame item estranho de lixo/bug/duplicata antes de aplicar o contrato anti-óbvio.
- Não avance para promoção de incubadora sem usar o plano de excelência operacional e a matriz de triagem.
- Não refatore por estética: use o protocolo de fricção determinística para separar atrito útil de desperdício real.
- Não continue uma atividade sem declarar camada, arquivos lidos, lacuna protegida e próximo `F_NEXT`.
- Quando a melhor rota não estiver clara, subir para o mapa holístico global antes de atuar localmente.
