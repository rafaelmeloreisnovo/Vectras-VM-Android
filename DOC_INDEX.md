<!-- DOC_TAXONOMY_SYNC: 2026-06-08 | role: global-complete-index -->

# Índice global de documentação — Vectras VM Android

Este é o índice global completo e a fonte de navegação documental do repositório. Ele organiza os documentos por papel, status, dono lógico e vínculo com código/workflow para reduzir drift entre humanos, IA, CI, build Android/NDK e release.

## Taxonomia única de entrada

| Documento | Papel único | Escopo |
|---|---|---|
| [`README.md`](README.md) | Entrada institucional e operacional curta. | Visão rápida, classificação de diretórios e sequência inicial. |
| [`DOC_INDEX.md`](DOC_INDEX.md) | Índice global completo. | Catálogo documental, status, dono lógico e vínculos executáveis. |
| [`docs/README.md`](docs/README.md) | Hub técnico. | Arquitetura, CI, build/release, low-level, segurança e governança. |
| [`docs/navigation/INDEX.md`](docs/navigation/INDEX.md) | Navegação por audiência. | Trilhas por público humano/IA, engenharia, pesquisa, operação, produto e compliance. |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | Estado real validado. | Estado corrente, bloqueios, limitações e última sincronização. |
| [`BUILDING.md`](BUILDING.md) | Execução local/CI/release. | Comandos canônicos de build, signing, ABI, artefatos e validações. |

## Classificação oficial dos diretórios

| Classe | Diretórios | Uso permitido | Regra de promoção/migração |
|---|---|---|---|
| **Canônico** | [`app/`](app/), [`engine/`](engine/), [`tools/ci/`](tools/ci/), [`.github/workflows/`](.github/workflows/), [`docs/`](docs/) | Fonte vigente para app, engine, automação, CI/release e documentação ativa. | Alterações exigem validação por build/teste/documentação aplicáveis. |
| **Legado compatível** | [`android/`](android/) | Compatibilidade, referência e comparação histórica controlada. | Não promover a entrypoint oficial sem plano de convergência e rollback. |
| **Experimental/ingestão** | [`Incluir/`](Incluir/), [`addthis/`](addthis/), [`_incoming/`](_incoming/) | Triagem, ingestão, pesquisa e material ainda não canonizado. | Promover somente após revisão de licença, autoria, segurança, build e links. |
| **Histórico** | [`archive/`](archive/), [`bug/archive/`](bug/archive/) | Registro de decisões, correções antigas e evidências arquivadas. | Restaurar conteúdo apenas com justificativa, teste e rollback explícitos. |

## Política de migração documental e física

1. Primeiro criar a camada de classificação, links e status.
2. Depois abrir plano de migração física com escopo, owners, riscos e rollback.
3. Não mover arquivos inicialmente para não quebrar links, scripts, workflows, histórico ou evidências.
4. Qualquer promoção de experimental para canônico deve declarar origem, licença, vínculo com código, validação e critério de reversão.
5. Qualquer uso de histórico como base atual deve citar a evidência e revalidar no commit corrente.

## Status documental

| Caminho | Papel | Status | Dono lógico | Última revisão | Vínculo com código/workflow |
|---|---|---|---|---|---|
| [`README.md`](README.md) | Entrada institucional e operacional curta. | Canônico vigente | Governança documental / release engineering | 2026-06-08 | Ponte para [`PROJECT_STATE.md`](PROJECT_STATE.md), [`DOC_INDEX.md`](DOC_INDEX.md), [`BUILDING.md`](BUILDING.md) e diretórios canônicos. |
| [`DOC_INDEX.md`](DOC_INDEX.md) | Índice global completo. | Canônico vigente | Governança documental / technical writing | 2026-06-08 | Referencia app, engine, CI, docs, estado e workflows. |
| [`docs/README.md`](docs/README.md) | Hub técnico. | Canônico vigente | Arquitetura / engenharia Android-NDK | 2026-06-08 | Vínculo com [`docs/architecture/VM_EXECUTION_FLOW.md`](docs/architecture/VM_EXECUTION_FLOW.md), [`docs/AI_BUILD_RELEASE_INDEX.md`](docs/AI_BUILD_RELEASE_INDEX.md) e [`docs/ci/workflow-matrix.md`](docs/ci/workflow-matrix.md). |
| [`docs/navigation/INDEX.md`](docs/navigation/INDEX.md) | Navegação por audiência. | Canônico vigente | Enablement / documentação técnica | 2026-06-08 | Direciona leitura para arquitetura, operação, benchmark, pesquisa, auditoria e governança. |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | Estado real validado. | Canônico vigente; estado declarado `BETA_BLOCKED` | Release management / CI owners | 2026-05-24 registrada; taxonomia vinculada em 2026-06-08 | Vínculo com [`reports/CANONICAL_BUILD_STATUS.md`](reports/CANONICAL_BUILD_STATUS.md), workflows Android/Host e políticas ABI. |
| [`BUILDING.md`](BUILDING.md) | Execução local/CI/release. | Canônico vigente | Build/release engineering | 2026-06-08 por vínculo documental | Vínculo com [`tools/gradle_with_jdk21.sh`](tools/gradle_with_jdk21.sh), [`tools/ci/`](tools/ci/), [`app/build.gradle`](app/build.gradle) e [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml). |
| [`docs/AI_BUILD_RELEASE_INDEX.md`](docs/AI_BUILD_RELEASE_INDEX.md) | Índice operacional para IA em build/release/ABI/signing. | Canônico técnico | Build/release engineering + IA operacional | 2026-06-08 por vínculo documental | Complementa [`BUILDING.md`](BUILDING.md) e workflows Android. |
| [`docs/ci/workflow-matrix.md`](docs/ci/workflow-matrix.md) | Matriz canônica de workflows. | Canônico técnico | CI owners | 2026-06-08 por vínculo documental | Vínculo direto com [`.github/workflows/`](.github/workflows/). |
| [`docs/architecture/VM_EXECUTION_FLOW.md`](docs/architecture/VM_EXECUTION_FLOW.md) | Fluxo de execução da VM. | Canônico técnico | Arquitetura/runtime | 2026-06-08 por vínculo documental | Vínculo com [`app/`](app/) e [`engine/`](engine/). |
| [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) | Modelo de ameaças. | Canônico técnico | Segurança / compliance | 2026-06-08 por vínculo documental | Vínculo com release, signing, CI e privacidade. |
| [`SECURITY.md`](SECURITY.md) | Política de segurança. | Canônico vigente | Segurança | 2026-06-08 por vínculo documental | Vínculo com triagem de vulnerabilidades e artefatos sensíveis. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Guia de contribuição. | Canônico vigente | Maintainers | 2026-06-08 por vínculo documental | Vínculo com fluxo Git, validação local e revisão. |
| [`CHANGELOG.md`](CHANGELOG.md) | Histórico de mudanças. | Registro ativo | Release management | 2026-06-08 por vínculo documental | Vínculo com releases e PRs. |
| [`RELEASE_NOTES.md`](RELEASE_NOTES.md) | Notas de release. | Registro temporal | Release management / produto | 2026-06-08 por vínculo documental | Vínculo com artefatos publicados e changelog. |
| [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) | Avisos de terceiros/licenças. | Canônico compliance | Jurídico/compliance | 2026-06-08 por vínculo documental | Vínculo com código importado, forks e assets. |
| [`LICENSES_REGISTER.md`](LICENSES_REGISTER.md) | Registro formal de licenças. | Canônico compliance | Jurídico/compliance | 2026-06-08 por vínculo documental | Vínculo com dependências, forks e distribuição. |
| [`AUTHORSHIP_CLEANROOM_PLAN.md`](AUTHORSHIP_CLEANROOM_PLAN.md) | Plano clean-room de autoria. | Canônico compliance | Compliance / arquitetura | 2026-06-08 por vínculo documental | Vínculo com ingestão, legado e remoção de risco. |
| [`resources/compliance/ASSET_CLEANROOM_POLICY.md`](resources/compliance/ASSET_CLEANROOM_POLICY.md) | Política clean-room de assets. | Canônico compliance | Compliance / design assets | 2026-06-08 por vínculo documental | Vínculo com [`resources/`](resources/) e proveniência de assets. |
| [`resources/compliance/ASSET_PROVENANCE_REGISTER.csv`](resources/compliance/ASSET_PROVENANCE_REGISTER.csv) | Registro de proveniência de assets. | Canônico compliance | Compliance / assets | 2026-06-08 por vínculo documental | Vínculo com assets versionados. |
| [`VECTRA_CORE.md`](VECTRA_CORE.md) | Referência conceitual do runtime/core. | Canônico técnico | Runtime/engine | 2026-06-08 por vínculo documental | Vínculo com [`engine/`](engine/) e contratos low-level. |
| [`docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`](docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md) | Guia low-level branchless/sem heap. | Canônico técnico especializado | Engine/native performance | 2026-06-08 por vínculo documental | Vínculo com [`engine/`](engine/) e validações low-level. |
| [`tools/compliance/check_lowlevel_constraints.py`](tools/compliance/check_lowlevel_constraints.py) | Verificador estático low-level. | Canônico operacional | Engine/native performance | 2026-06-08 por vínculo documental | Executável para checagem de restrições low-level. |
| [`docs/active/DIRECTORY_ALIGNMENT_MATRIX.md`](docs/active/DIRECTORY_ALIGNMENT_MATRIX.md) | Matriz de alinhamento de diretórios críticos. | Apoio canônico | Governança documental / arquitetura | 2026-06-08 por vínculo documental | Complementa esta classificação de diretórios. |
| [`docs/organization/DOC_ORGANIZATION_PLAN_2026-06-02.md`](docs/organization/DOC_ORGANIZATION_PLAN_2026-06-02.md) | Plano de saneamento documental. | Plano ativo de organização | Governança documental | 2026-06-02 | Base para migração planejada, failsafe/failover/rollback. |
| [`docs/organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md`](docs/organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md) | Manifesto de ingressos/overlays. | Apoio de ingestão | Governança documental / compliance | 2026-06-02 | Vínculo com experimental/ingestão e rollback. |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | Diagnóstico operacional. | Canônico operacional | Operações / build engineering | 2026-06-08 por vínculo documental | Vínculo com falhas de build, CI, SDK/NDK e execução. |
| [`FIXES_SUMMARY.md`](FIXES_SUMMARY.md) | Sumário de correções. | Registro ativo | Maintainers / release | 2026-06-08 por vínculo documental | Vínculo com estado e histórico de correções. |
| [`VERSION_STABILITY.md`](VERSION_STABILITY.md) | Manifesto/checklist de estabilidade. | Governança ativa | Release management / QA | 2026-06-08 por vínculo documental | Vínculo com gates e critérios de release. |
| [`app/README.md`](app/README.md) | Mapa local do app Android. | Canônico por diretório | Android app owners | 2026-06-08 por vínculo documental | Vínculo com [`app/build.gradle`](app/build.gradle) e código Android/JNI. |
| [`engine/README.md`](engine/README.md) | Mapa local do engine. | Canônico por diretório | Engine/native owners | 2026-06-08 por vínculo documental | Vínculo com C/C++/ASM/RMR. |
| [`tools/README.md`](tools/README.md) | Mapa local de ferramentas. | Canônico por diretório | Build/release/tooling owners | 2026-06-08 por vínculo documental | Vínculo com scripts de validação, CI e compliance. |
| [`android/README.md`](android/README.md) | Mapa de legado compatível. | Legado compatível | Maintainers de compatibilidade | 2026-06-08 por vínculo documental | Não é entrypoint oficial; comparar com raiz antes de usar. |
| [`_incoming/README.md`](_incoming/README.md) | Área de ingestão pendente. | Experimental/ingestão | Triagem documental/compliance | 2026-06-08 por vínculo documental | Promover somente com revisão e rollback. |
| [`addthis/README.md`](addthis/README.md) | Área experimental/ingestão. | Experimental/ingestão | Triagem documental/compliance | 2026-06-08 por vínculo documental | Promover somente com revisão e rollback. |
| [`archive/README.md`](archive/README.md) | Entrada do histórico arquivado. | Histórico | Governança documental | 2026-06-08 por vínculo documental | Evidência antiga; não substitui estado atual. |
| [`bug/archive/README.md`](bug/archive/README.md) | Arquivo histórico de bugs. | Histórico | QA / manutenção | 2026-06-08 por vínculo documental | Consultar apenas como evidência histórica. |

## Índice técnico por domínio

### Build, CI, release e artefatos
- Guia de execução: [`BUILDING.md`](BUILDING.md).
- Índice IA build/release: [`docs/AI_BUILD_RELEASE_INDEX.md`](docs/AI_BUILD_RELEASE_INDEX.md).
- Matriz de workflows: [`docs/ci/workflow-matrix.md`](docs/ci/workflow-matrix.md).
- Workflows: [`.github/workflows/android.yml`](.github/workflows/android.yml), [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml), [`.github/workflows/host-ci.yml`](.github/workflows/host-ci.yml), [`.github/workflows/pipeline-orchestrator.yml`](.github/workflows/pipeline-orchestrator.yml), [`.github/workflows/quality-gates.yml`](.github/workflows/quality-gates.yml), [`.github/workflows/compile-matrix.yml`](.github/workflows/compile-matrix.yml).
- Ferramentas CI: [`tools/ci/`](tools/ci/).

### Android, JNI, NDK e engine
- App Android: [`app/`](app/) e [`app/README.md`](app/README.md).
- Engine: [`engine/`](engine/) e [`engine/README.md`](engine/README.md).
- Runtime/core: [`VECTRA_CORE.md`](VECTRA_CORE.md).
- Arquitetura de execução: [`docs/architecture/VM_EXECUTION_FLOW.md`](docs/architecture/VM_EXECUTION_FLOW.md).
- Guia low-level: [`docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`](docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md).

### Governança, segurança, compliance e autoria
- Segurança: [`SECURITY.md`](SECURITY.md), [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md).
- Privacidade: [`PRIVACY.md`](PRIVACY.md).
- Contribuição: [`CONTRIBUTING.md`](CONTRIBUTING.md).
- Licenças: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), [`LICENSES_REGISTER.md`](LICENSES_REGISTER.md).
- Autoria/clean-room: [`AUTHORSHIP_CLEANROOM_PLAN.md`](AUTHORSHIP_CLEANROOM_PLAN.md), [`CREDITS_INSPIRATION.md`](CREDITS_INSPIRATION.md).
- Assets: [`resources/compliance/ASSET_CLEANROOM_POLICY.md`](resources/compliance/ASSET_CLEANROOM_POLICY.md), [`resources/compliance/ASSET_PROVENANCE_REGISTER.csv`](resources/compliance/ASSET_PROVENANCE_REGISTER.csv).

### Estado, histórico e manutenção
- Estado atual: [`PROJECT_STATE.md`](PROJECT_STATE.md).
- Changelog: [`CHANGELOG.md`](CHANGELOG.md).
- Notas de release: [`RELEASE_NOTES.md`](RELEASE_NOTES.md).
- Troubleshooting: [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md).
- Correções: [`FIXES_SUMMARY.md`](FIXES_SUMMARY.md).
- Estabilidade: [`VERSION_STABILITY.md`](VERSION_STABILITY.md).
- Histórico: [`archive/`](archive/), [`bug/archive/`](bug/archive/).

### Organização documental e ingestão
- Hub técnico: [`docs/README.md`](docs/README.md).
- Navegação por audiência: [`docs/navigation/INDEX.md`](docs/navigation/INDEX.md).
- Plano de organização: [`docs/organization/DOC_ORGANIZATION_PLAN_2026-06-02.md`](docs/organization/DOC_ORGANIZATION_PLAN_2026-06-02.md).
- Inventário de fragmentos: [`docs/organization/LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md`](docs/organization/LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md).
- Auditoria de condições necessárias: [`docs/organization/NECESSARY_CONDITIONS_AUDIT_2026-06-02.md`](docs/organization/NECESSARY_CONDITIONS_AUDIT_2026-06-02.md).
- Matriz de entrega de dados: [`docs/organization/NECESSARY_DATA_DELIVERY_MATRIX_2026-06-02.md`](docs/organization/NECESSARY_DATA_DELIVERY_MATRIX_2026-06-02.md).
- Manifesto de ingressos: [`docs/organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md`](docs/organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md).
- Sincronização código/documentação: [`docs/organization/SOURCE_ARCHITECTURE_SYNC_2026-06-02.md`](docs/organization/SOURCE_ARCHITECTURE_SYNC_2026-06-02.md).

## Critérios para IA e humanos

- Se a informação for estado atual, confirme em [`PROJECT_STATE.md`](PROJECT_STATE.md) e nos relatórios/workflows vinculados.
- Se a informação for execução, use [`BUILDING.md`](BUILDING.md) e não o diretório legado [`android/`](android/).
- Se a informação for localização documental, use este índice antes de inferir.
- Se a informação vier de [`Incluir/`](Incluir/), [`addthis/`](addthis/) ou [`_incoming/`](_incoming/), trate como ingestão/experimental até revisão.
- Se a informação vier de [`archive/`](archive/) ou [`bug/archive/`](bug/archive/), trate como histórico até revalidação no commit corrente.
