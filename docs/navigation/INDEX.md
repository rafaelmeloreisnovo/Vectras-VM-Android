<!-- DOC_TAXONOMY_SYNC: 2026-06-08 | role: audience-navigation -->

# Navegação por audiência — Vectras VM Android

## Metadados canônicos

- Versão do documento: 2.1.
- Última revisão: 2026-06-08.
- Escopo: navegação por audiência para humanos, agentes de IA, engenharia, pesquisa, produto, operação, release e compliance.
- Status: canônico vigente.
- Commit de referência: `HEAD`.
- Fonte de verdade relacionada: [`../../DOC_INDEX.md`](../../DOC_INDEX.md), [`../README.md`](../README.md), [`../DOCUMENTATION_STANDARDS.md`](../DOCUMENTATION_STANDARDS.md) e [`../ci/workflow-matrix.md`](../ci/workflow-matrix.md).

Este índice direciona pessoas e agentes de IA para o documento certo conforme intenção, responsabilidade e nível de evidência necessário. Ele complementa a entrada curta [`../../README.md`](../../README.md), o índice global [`../../DOC_INDEX.md`](../../DOC_INDEX.md), o hub técnico [`../README.md`](../README.md), o estado real [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md) e o guia de execução [`../../BUILDING.md`](../../BUILDING.md).

## Trilha universal de 5 passos

1. **Entrada institucional**: [`../../README.md`](../../README.md).
2. **Estado real validado**: [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md).
3. **Índice global completo**: [`../../DOC_INDEX.md`](../../DOC_INDEX.md).
4. **Hub técnico**: [`../README.md`](../README.md).
5. **Execução local/CI/release**: [`../../BUILDING.md`](../../BUILDING.md).

## Taxonomia de diretórios para qualquer audiência

| Classe | Diretórios | Como interpretar |
|---|---|---|
| **Canônico** | [`../../app/`](../../app/), [`../../engine/`](../../engine/), [`../../tools/ci/`](../../tools/ci/), [`../../.github/workflows/`](../../.github/workflows/), [`../`](../) | Base atual de decisão técnica, build, CI, release e documentação. |
| **Legado compatível** | [`../../android/`](../../android/) | Referência/compatibilidade; não é entrypoint oficial. |
| **Experimental/ingestão** | [`../../Incluir/`](../../Incluir/), [`../../addthis/`](../../addthis/), [`../../_incoming/`](../../_incoming/) | Material em triagem; exige validação antes de uso normativo. |
| **Histórico** | [`../../archive/`](../../archive/), [`../../bug/archive/`](../../bug/archive/) | Memória e evidência antiga; não substitui estado atual. |

## Audiências e caminhos recomendados

| Audiência | Primeira leitura | Segunda leitura | Critério de confiança |
|---|---|---|---|
| **Engenharia Android/NDK/JNI** | [`../README.md`](../README.md) | [`../../BUILDING.md`](../../BUILDING.md), [`../architecture/VM_EXECUTION_FLOW.md`](../architecture/VM_EXECUTION_FLOW.md) | Código em [`../../app/`](../../app/) + [`../../engine/`](../../engine/) e workflow verde. |
| **Build, release e CI** | [`../../BUILDING.md`](../../BUILDING.md) | [`../AI_BUILD_RELEASE_INDEX.md`](../AI_BUILD_RELEASE_INDEX.md), [`../ci/workflow-matrix.md`](../ci/workflow-matrix.md) | Artefatos, assinatura e ABI validados por [`.github/workflows/`](../../.github/workflows/). |
| **Operações/performance** | [`PERFORMANCE_OPERATIONS.md`](PERFORMANCE_OPERATIONS.md) | [`../active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`](../active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md) | Benchmark/relatório real, não promessa narrativa. |
| **Empresas/adoção operacional** | [`ENTERPRISE_COMPANIES.md`](ENTERPRISE_COMPANIES.md) | [`TRACEABILITY_GOVERNANCE.md`](TRACEABILITY_GOVERNANCE.md), [`../../SECURITY.md`](../../SECURITY.md) | Governança, segurança, compliance e release reproduzível. |
| **Pesquisa/cientistas** | [`SCIENTISTS_RESEARCH.md`](SCIENTISTS_RESEARCH.md) | [`BENCHMARK_COMPARISONS.md`](BENCHMARK_COMPARISONS.md), [`../architecture/VM_EXECUTION_FLOW.md`](../architecture/VM_EXECUTION_FLOW.md) | Método reprodutível, falsificação e dados versionados. |
| **Universidades/formação** | [`UNIVERSITIES_ACADEMIC.md`](UNIVERSITIES_ACADEMIC.md) | [`RUNTIME_ENGINE_SYSTEMS.md`](RUNTIME_ENGINE_SYSTEMS.md) | Separação clara entre teoria, implementação e experimento. |
| **Investidores/board/produto** | [`HIGH_LEVEL_INVESTORS.md`](HIGH_LEVEL_INVESTORS.md) | [`BIGTECH_REVOLUTION_ANNOUNCE.md`](BIGTECH_REVOLUTION_ANNOUNCE.md), [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md) | Estado real validado antes de narrativa estratégica. |
| **Compliance/licenças/autoria** | [`TRACEABILITY_GOVERNANCE.md`](TRACEABILITY_GOVERNANCE.md) | [`../../THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md), [`../../LICENSES_REGISTER.md`](../../LICENSES_REGISTER.md), [`../../AUTHORSHIP_CLEANROOM_PLAN.md`](../../AUTHORSHIP_CLEANROOM_PLAN.md) | Proveniência, licença, autoria e rollback. |
| **Agentes de IA** | [`../../DOC_INDEX.md`](../../DOC_INDEX.md) | [`../README.md`](../README.md), [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md) | Citar arquivos canônicos, marcar inferência e evitar promoção silenciosa de material experimental. |

## Documentos desta navegação

- [`BIGTECH_REVOLUTION_ANNOUNCE.md`](BIGTECH_REVOLUTION_ANNOUNCE.md) — narrativa estratégica para anúncio e alto nível.
- [`HIGH_LEVEL_INVESTORS.md`](HIGH_LEVEL_INVESTORS.md) — diligência técnica para investidores e VCs.
- [`SCIENTISTS_RESEARCH.md`](SCIENTISTS_RESEARCH.md) — protocolo reprodutível para pesquisa.
- [`UNIVERSITIES_ACADEMIC.md`](UNIVERSITIES_ACADEMIC.md) — uso didático e formação.
- [`ENTERPRISE_COMPANIES.md`](ENTERPRISE_COMPANIES.md) — adoção operacional empresarial.
- [`BENCHMARK_COMPARISONS.md`](BENCHMARK_COMPARISONS.md) — método de comparação e benchmark.
- [`PERFORMANCE_OPERATIONS.md`](PERFORMANCE_OPERATIONS.md) — runbook de operação/performance.
- [`RUNTIME_ENGINE_SYSTEMS.md`](RUNTIME_ENGINE_SYSTEMS.md) — ponte runtime/engine/sistemas.
- [`TRACEABILITY_GOVERNANCE.md`](TRACEABILITY_GOVERNANCE.md) — governança, rastreabilidade e compliance.
- [`TECHNOLOGY_INNOVATION_AUTHORSHIP.md`](TECHNOLOGY_INNOVATION_AUTHORSHIP.md) — inovação, autoria e subsistemas.

## Rapport técnico: inclusão informacional sem perder rigor

- **Humano**: comece pela intenção, valide o estado real e avance por links internos.
- **IA**: minimize inferências, cite a fonte, preserve distinção entre canônico/legado/experimental/histórico.
- **Operação**: uma trilha só é confiável quando conecta documento, código, workflow, comando e resultado.
- **Governança**: coerência vale mais que fluidez; material latente ou esquecido deve ser incluído como hipótese até ser validado.
