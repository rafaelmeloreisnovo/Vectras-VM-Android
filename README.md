<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

# Vectras VM Android

> Plataforma Android de virtualização com base híbrida (Android + C/C++ + Rust), foco em determinismo operacional, rastreabilidade e governança documental.

## Abstract
Esta revisão consolida a documentação em três camadas por diretório (propósito, estrutura e arquivo-a-arquivo), conectando documentação raiz, mapas locais e cadeia de comandos de inspeção. O objetivo é eliminar lacunas entre arquivos soltos, módulos ativos e documentação técnica, com navegação formal e auditável.

## Atualização da revisão documental (2026-04-03)
- Fluxo de revisão contínua padronizado em `docs/README.md`.
- Navegação de `docs/` refatorada para estrutura numerada de 5 níveis.
- Metadados de governança documental atualizados para acompanhamento de ciclo.

## START HERE
- Entrada rápida profissional: [`START_HERE.md`](START_HERE.md)
- Arquitetura operacional (fonte primária): [`docs/architecture/VM_EXECUTION_FLOW.md`](docs/architecture/VM_EXECUTION_FLOW.md)

## Governança e estado — navegação rápida
- Estado do projeto: [`PROJECT_STATE.md`](PROJECT_STATE.md)
- Histórico de mudanças: [`CHANGELOG.md`](CHANGELOG.md)
- Notas de release: [`RELEASE_NOTES.md`](RELEASE_NOTES.md)
- Índice documental: [`DOC_INDEX.md`](DOC_INDEX.md)
- Segurança: [`SECURITY.md`](SECURITY.md)
- Guia de contribuição: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Privacidade: [`PRIVACY.md`](PRIVACY.md)
- Modelo de ameaças: [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md)
- Avisos de terceiros/licenciamento: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Plano clean-room de autoria e descontinuação de legado: [`AUTHORSHIP_CLEANROOM_PLAN.md`](AUTHORSHIP_CLEANROOM_PLAN.md)
- Registro formal de licenças e compliance: [`LICENSES_REGISTER.md`](LICENSES_REGISTER.md)
- Créditos por inspiração (sem derivação): [`CREDITS_INSPIRATION.md`](CREDITS_INSPIRATION.md)
- Política clean-room de assets: [`resources/compliance/ASSET_CLEANROOM_POLICY.md`](resources/compliance/ASSET_CLEANROOM_POLICY.md)
- Registro de proveniência de assets: [`resources/compliance/ASSET_PROVENANCE_REGISTER.csv`](resources/compliance/ASSET_PROVENANCE_REGISTER.csv)
- Referência do runtime: [`VECTRA_CORE.md`](VECTRA_CORE.md)
- Guia macro de documentação: [`VECTRAS_MEGAPROMPT_DOCS.md`](VECTRAS_MEGAPROMPT_DOCS.md)
- Auditoria de cobertura dos Markdown (2026-04-07): [`docs/active/DOCUMENTATION_COVERAGE_AUDIT_2026-04-07.md`](docs/active/DOCUMENTATION_COVERAGE_AUDIT_2026-04-07.md)
- Catálogo completo de Markdown (2026-04-07): [`docs/active/ALL_MARKDOWN_FILES_2026-04-07.md`](docs/active/ALL_MARKDOWN_FILES_2026-04-07.md)
- Guia operacional de build: [`BUILDING.md`](BUILDING.md)
- Índice operacional para IA (build/release/ABI/signing): [`docs/AI_BUILD_RELEASE_INDEX.md`](docs/AI_BUILD_RELEASE_INDEX.md)
- Matriz de alinhamento diretórios críticos: [`docs/active/DIRECTORY_ALIGNMENT_MATRIX.md`](docs/active/DIRECTORY_ALIGNMENT_MATRIX.md)
- Troubleshooting operacional: [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md)
- Sumário de correções: [`FIXES_SUMMARY.md`](FIXES_SUMMARY.md)
- Manifesto de estabilidade: [`VERSION_STABILITY.md`](VERSION_STABILITY.md)

## Política de overlays ZIP
- Arquivos `*.zip` de overlay na raiz são apenas artefatos transitórios de transporte e **não** são fonte de verdade.
- A fonte oficial de código e documentação é **exclusivamente** a árvore versionada no Git.
- O CI valida e falha quando detectar overlay ZIP na raiz contendo código-fonte duplicado da árvore ativa.

## Histórico arquivado (raiz)
- [`archive/root-history/1.md`](archive/root-history/1.md)
- [`archive/root-history/ADVANCED_OPTIMIZATIONS.md`](archive/root-history/ADVANCED_OPTIMIZATIONS.md)
- [`archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md`](archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md)
- [`archive/root-history/IMPLEMENTATION_COMPLETE.md`](archive/root-history/IMPLEMENTATION_COMPLETE.md)
- [`archive/root-history/IMPLEMENTATION_SUMMARY.md`](archive/root-history/IMPLEMENTATION_SUMMARY.md)
- [`archive/root-history/VECTRAS_ANALYSIS_COMPLETE.md`](archive/root-history/VECTRAS_ANALYSIS_COMPLETE.md)
- [`archive/root-history/VECTRAS_DEEP_EVIDENCE.md`](archive/root-history/VECTRAS_DEEP_EVIDENCE.md)

## Dissertação analítica (modelo de 3 camadas)
1. **Camada 1 — Diretório**: define responsabilidade técnica de cada domínio.
2. **Camada 2 — Estrutura**: explicita subdiretórios e fronteiras de módulo.
3. **Camada 3 — Arquivos**: descreve cada arquivo com papel, ligação e comando de inspeção.

Referências estruturais:
- [`docs/THREE_LAYER_ANALYSIS.md`](docs/THREE_LAYER_ANALYSIS.md)
- [`docs/ROOT_FILE_CHAIN.md`](docs/ROOT_FILE_CHAIN.md)

## Mapa de diretórios (com READMEs + FILES_MAP)
| Diretório | README | Mapa de Arquivos |
|---|---|---|
| `app/` | [app/README.md](app/README.md) | [app/FILES_MAP.md](app/FILES_MAP.md) |
| `engine/` | [engine/README.md](engine/README.md) | [engine/FILES_MAP.md](engine/FILES_MAP.md) |
| `terminal-emulator/` | [terminal-emulator/README.md](terminal-emulator/README.md) | [terminal-emulator/FILES_MAP.md](terminal-emulator/FILES_MAP.md) |
| `terminal-view/` | [terminal-view/README.md](terminal-view/README.md) | [terminal-view/FILES_MAP.md](terminal-view/FILES_MAP.md) |
| `shell-loader/` | [shell-loader/README.md](shell-loader/README.md) | [shell-loader/FILES_MAP.md](shell-loader/FILES_MAP.md) |
| `bench/` | [bench/README.md](bench/README.md) | [bench/FILES_MAP.md](bench/FILES_MAP.md) |
| `bug/` | [bug/README.md](bug/README.md) | [bug/FILES_MAP.md](bug/FILES_MAP.md) |
| `demo_cli/` | [demo_cli/README.md](demo_cli/README.md) | [demo_cli/FILES_MAP.md](demo_cli/FILES_MAP.md) |
| `tools/` | [tools/README.md](tools/README.md) | [tools/FILES_MAP.md](tools/FILES_MAP.md) |
| `docs/` | [docs/README.md](docs/README.md) | [docs/FILES_MAP.md](docs/FILES_MAP.md) |
| `reports/` | [reports/README.md](reports/README.md) | [reports/FILES_MAP.md](reports/FILES_MAP.md) |
| `resources/` | [resources/README.md](resources/README.md) | [resources/FILES_MAP.md](resources/FILES_MAP.md) |
| `runtime/` | [runtime/README.md](runtime/README.md) | [runtime/FILES_MAP.md](runtime/FILES_MAP.md) |
| `web/` | [web/README.md](web/README.md) | [web/FILES_MAP.md](web/FILES_MAP.md) |
| `archive/` | [archive/README.md](archive/README.md) | [archive/FILES_MAP.md](archive/FILES_MAP.md) |
| `fastlane/` | [fastlane/README.md](fastlane/README.md) | [fastlane/FILES_MAP.md](fastlane/FILES_MAP.md) |
| `gradle/` | [gradle/README.md](gradle/README.md) | [gradle/FILES_MAP.md](gradle/FILES_MAP.md) |
| `3dfx/` | [3dfx/README.md](3dfx/README.md) | [3dfx/FILES_MAP.md](3dfx/FILES_MAP.md) |

## Política de assinatura (`vectras.jks`)
- A chave `vectras.jks` **não deve permanecer versionada** no Git.
- A assinatura de **release** deve usar segredo de CI/cofre seguro (`VECTRAS_RELEASE_*` / `android.injected.signing.*`).
- Builds de `debug` usam apenas assinatura debug padrão do Android Gradle Plugin (não usar chave de release).
- Rotação recomendada: a cada 90 dias (ou imediatamente após incidente), com revogação e atualização de segredos no CI.
- Acesso mínimo: apenas mantenedores responsáveis por release e conta de automação do CI.

## Cadeia de comando recomendada
```bash
git ls-files
find . -maxdepth 2 -type d | sort
./tools/gradle_with_jdk21.sh verifyRepoFileDependencies verifyBootstrapAssets
```

## Índices
- [DOC_INDEX.md](DOC_INDEX.md)
- [docs/README.md](docs/README.md)
- [docs/AI_BUILD_RELEASE_INDEX.md](docs/AI_BUILD_RELEASE_INDEX.md)
- [docs/navigation/BIGTECH_REVOLUTION_ANNOUNCE.md](docs/navigation/BIGTECH_REVOLUTION_ANNOUNCE.md)

## Execução padrão de CI/CD
- **Decisão explícita adotada: Opção A (recomendada)** — manter apenas **3 workflows canônicos** ativos.
- Fluxos suportados (canônicos):
  - `.github/workflows/pipeline-orchestrator.yml` (ponto único de entrada para `push`/`pull_request`/manual).
  - `.github/workflows/host-ci.yml` (pipeline host canônico, reutilizado via `workflow_call`).
  - `.github/workflows/android-ci.yml` (pipeline Android canônico, reutilizado via `workflow_call`).
- Workflows legados/extras foram **descontinuados** e movidos para `.github/workflows/archive/` para eliminar disparos paralelos e drift operacional.
- Política de uso:
  - Eventos de branch/PR/manual devem iniciar em **Actions > Pipeline Orchestrator**.
  - Workflows canônicos filhos (`host-ci.yml` e `android-ci.yml`) são acionados pelo orquestrador; `android.yml` permanece apenas como wrapper de entrada/manual para delegação controlada.
  - Arquivos em `.github/workflows/archive/` são apenas histórico técnico, sem suporte operacional ativo.
- `pipeline-orchestrator.yml` seleciona o perfil com `pipeline_profile` (`host_only`, `android_only`, `full`) e encaminha `run_workfile`/`log_level`/`abi_profile` para o fluxo Android.

## Como rodar manualmente
- Fluxo recomendado: acesse **Actions > Pipeline Orchestrator > Run workflow** e selecione:
  - `pipeline_profile`: host-only, android-only ou full.
  - `run_workfile`: `smoke_debug`, `unit_loader`, `full_debug`, `release_gate`.
  - `log_level`: `lifecycle`, `info`, `debug`.
  - `abi_profile`: `official_arm64` (trilha oficial) ou `internal_arm32_arm64` (validação interna ARM v7/v8).
- Depuração pontual: execute o workflow **android-ci** diretamente quando quiser isolar build/test Android.
- `run_native_checks=true` também valida matriz CMake Android (`armeabi-v7a` + `arm64-v8a`) e faz upload do artefato `android-cmake-matrix-*` para rastrear compilação low-level por ABI.
- Inputs principais do workflow **android-ci**:
  - `run_workfile`: define o conjunto de tarefas Gradle.
  - `build_variant` (`debug`|`release`|`both`): define variante alvo.
  - `signing_mode` (`auto`|`signed`|`unsigned`): política de assinatura de release.
  - `abi_profile` (`official_arm64`|`internal_arm32_arm64`): seleciona trilha ABI oficial ou validação interna dual-ARM.
  - `run_lint` e `run_native_checks`: habilitam gates opcionais.
  - `log_level`: controla verbosidade e rastreabilidade dos logs.
- Para manter valores padrão por repositório em CI, configure variáveis em **Settings > Secrets and variables > Actions > Variables** (prefira canônicas: `compile.api`, `tools.version`, `ndk.version`, `cmake.version`, `java.language.version`; aliases legados como `COMPILE_API`, `TOOLS_VERSION`, `NDK_VERSION`, `CMAKE_VERSION`, `JAVA_VERSION` ficam como fallback de compatibilidade).

## Setup rápido de build
- Copie `local.properties.example` para `local.properties` e ajuste `sdk.dir`.
- Ajuste versões via `gradle.properties` com precedência explícita: canônicas (`compile.api`, `tools.version`, `java.language.version`, `cmake.version`, `ndk.version`) primeiro; aliases legados (`COMPILE_API`, `TOOLS_VERSION`, `JAVA_LANGUAGE_VERSION`, `CMAKE_VERSION`, `NDK_VERSION`) apenas como fallback retroativo com warning de depreciação.
- **Manutenção de upgrade de SDK:** altere primeiro `sdk.baseline.api` e `compile.api`/`target.api`/`release.min.target.api` em `gradle.properties`; aliases legados (`SDK_BASELINE_API`, `COMPILE_API`, `TARGET_API`, `RELEASE_MIN_TARGET_API`) ficam somente como fallback. O `build.gradle` raiz consome esse baseline como fallback único para todos os módulos.
- Baseline único de CMake para host + Android: `3.22.1`. O `CMakeLists.txt` da raiz e o CMake do app JNI compartilham esse baseline para evitar drift entre build local/CI e NDK.
- Política de JVM do Gradle: execute preferencialmente com **JDK 17** (alinhado com `JAVA_LANGUAGE_VERSION=17`).
- Defina explicitamente `JAVA_HOME` para o JDK 17/21 ou configure `org.gradle.java.home=<path-do-jdk17-ou-jdk21>` em `~/.gradle/gradle.properties`.
- Use sempre o wrapper `./tools/gradle_with_jdk21.sh` (local e CI) para evitar regressão com JDK 22+.
- O build agora valida em bootstrap `GRADLE_JAVA_RUNTIME_VERSION` (padrão 17) e falha se a JVM runtime exceder `GRADLE_MAX_RUNTIME_JAVA_VERSION` (padrão 21).
- Para override pontual, use `-P` no comando Gradle.

### Precedência oficial de propriedades Gradle
- Regra fixa: propriedade canônica (`lowercase.with.dots`) sempre vence.
- Alias legado (`UPPER_SNAKE_CASE`) é somente fallback para compatibilidade retroativa.
- Uso de alias legado gera warning de depreciação no bootstrap Gradle para facilitar migração sem quebra imediata.

### ABIs oficialmente suportadas
- Escopos ABI em `tools/qemu_launch.yml`:
  - `official_distribution`: padrão oficial de distribuição, com `arm64-v8a`.
  - `internal_validation`: matriz técnica interna dual-ARM (`arm64-v8a,armeabi-v7a`) para validação de compatibilidade ARM32/ARM64.
- **Política Gradle de distribuição oficial**:
  - `APP_ABI_POLICY=arm64-only` + `SUPPORTED_ABIS=arm64-v8a` (default oficial).
- **Política Gradle de validação interna dual-ARM (workflow canônico)**:
  - `APP_ABI_POLICY=arm32-arm64` + `SUPPORTED_ABIS=arm64-v8a,armeabi-v7a`.
  - Disponível no workflow `android-ci` via `abi_profile=internal_arm32_arm64`, restrita a trilha interna (release bloqueado para este perfil).
- **Política Gradle de validação técnica interna**:
  - `APP_ABI_POLICY=internal-5abi` + `SUPPORTED_ABIS=arm64-v8a,armeabi-v7a,x86,x86_64,riscv64` para validação interna unsigned em GitHub Actions (exige `CI_INTERNAL_VALIDATION=true` e `MIN_API>=35`).
  - a validação `internal-5abi` permanece para execução técnica interna explícita via Gradle local/diagnóstico, fora da trilha canônica de release no workflow principal.
- O CI executa `tools/check_abi_policy_alignment.py` para garantir alinhamento entre `gradle.properties` (`APP_ABI_POLICY`/`SUPPORTED_ABIS`) e os escopos ABI em `tools/qemu_launch.yml`.
- Entradas condicionais para ABIs fora dessa matriz no CMake (ex.: `riscv64`) são apenas roadmap e não representam ABI ativa no empacotamento Gradle.

### Exemplo de configuração de Java para build
```bash
source <(./tools/configure_java_home.sh --print)
./tools/gradle_with_jdk21.sh --version
./tools/gradle_with_jdk21.sh verifyGradleRuntimeJvm
```

### Validação canônica de setup/CI/build
```bash
./tools/gradle_with_jdk21.sh checkNativeAllMatrix
```

Essa task já encadeia `verifyMinApiAbiCompatibility`, `verifyArm64ToolchainCompatibility`, `verifyGradleRuntimeJvm` e executa `assembleDebug`, `assembleRelease`, `assemblePerfRelease` nas políticas de ABI suportadas.

### Caminho Android canônico (anti-drift)
- O caminho oficial de build Android é **somente o Gradle da raiz** (`./gradlew`, módulos `:app`, `:shell-loader`, `:terminal-*`).
- O diretório `android/` permanece legado para compatibilidade local, mas a fonte de verdade de build/release continua sendo apenas a raiz (`:app`).
- O CI executa `tools/ci/validate_android_sdk_alignment.sh` para falhar quando houver referência oficial ao caminho legado ou divergência de baseline SDK entre caminhos.
- O CI executa `tools/ci/check_java_contracts.py` para bloquear regressão de assinatura duplicada em `NativeFastPath` antes do `compileDebugJavaWithJavac`.
- O CI executa `tools/ci/verify_android_local_properties_contract.sh` para garantir `sdk.dir` válido, `ndk.dir` ausente (deprecado) e presença da `ndk.version` canônica no SDK instalado.
- Para preparar ambiente Android local sem drift, execute `./tools/ci/prepare_android_env.sh`; o script prioriza `ANDROID_SDK_ROOT`/`ANDROID_HOME`, faz fallback em `${REPO_ROOT}/.android-sdk`, `/workspace/android-sdk`, `/usr/lib/android-sdk`, `/opt/android-sdk`, `/opt/android-sdk-linux` e `$HOME/Android/Sdk`, sincroniza apenas `sdk.dir` em `local.properties` e valida `ndk.version` sem usar `ndk.dir` (deprecado no AGP).
- Para publicação de artefatos Android no CI sem lacunas silenciosas, o workflow materializa manifestos determinísticos via `tools/ci/materialize_android_ci_artifacts.sh` antes do upload.

Para fixar por usuário (sem depender de shell):
```properties
# ~/.gradle/gradle.properties
org.gradle.java.home=/usr/lib/jvm/java-21-openjdk
```

## Referência rápida de bugs
- Escopo e relação com os demais domínios: [`bug/README.md`](bug/README.md)
- Mapa arquivo-a-arquivo do domínio de bugs: [`bug/FILES_MAP.md`](bug/FILES_MAP.md)

## Canal oficial de comunidade e suporte
- Canal oficial neutro: https://vectras.vercel.app/community.html
- Para notícias, suporte e feedback, use sempre o canal oficial acima.

## CI canonical reference (Android/Host)

- Canonical Android pipeline: `.github/workflows/android-ci.yml`.
- Android wrapper entrypoint: `.github/workflows/android.yml`.
- Auxiliary Android ABI compatibility matrix: `.github/workflows/compile-matrix.yml`.
- Canonical host pipeline: `.github/workflows/host-ci.yml`.
- Orchestration and final gates: `.github/workflows/pipeline-orchestrator.yml` and `.github/workflows/quality-gates.yml`.
- Canonical matrix documentation: `docs/ci/workflow-matrix.md`.

## Native sector deterministic gate

- Host CI now enforces `make run-sector-selftest` as a mandatory gate.
- The test fixture validates fixed expected outputs for `hash64`, `crc32`, `coherence_q16`, `entropy_q16`, `last_entropy_milli`, and `last_invariant_milli`.
- The gate also executes consecutive and parallel calls to detect shared global state regressions in `run_sector`.

## Native deterministic snapshot + benchmark smoke
- Snapshot determinístico do core: `make run-sector-snapshot-42` (input fixo de 42 bytes em `run_sector`).
- Benchmark nativo simples (smoke, sem promessa de performance): `make run-core-bench-smoke`.
- Scripts auxiliares:
  - `scripts/native/build.sh`
  - `scripts/native/test.sh`
  - `scripts/native/benchmark.sh`
- Limite atual: benchmark smoke roda no host CI (não representa benchmark Android real por ABI).


## Coerência operacional de release
- Branch padrão operacional inclui `master` no orquestrador, mantendo `main`, `develop` e `feature/**`.
- `release-unsigned-internal` é exclusivo para validação interna dual ARM (`internal_arm32_arm64`) sem assinatura.
- `release-signed-official` é exclusivo para distribuição oficial `official_arm64` com assinatura.
- `VECTRA_CORE_ENABLED` permanece ativo em release com gates de validação determinística.
- Status canônico de build só é atualizado após CI real concluída.

- `external_sources.manifest` mantém `androidx_RmR` e `qemu_rafaelia` em branch-tracked (sem commit pin), com validação remota de integridade no CI.

## Certification and audit claim notice
This repository does not claim ISO certification, formal ISO compliance, or accredited external audit status. Any ISO/IEC references are internal checklist references or methodological alignment notes only. Certification requires an external accredited audit process and is outside the scope of this repository.

Este repositório não declara certificação ISO, conformidade ISO formal nem auditoria externa acreditada. Qualquer referência a ISO/IEC é apenas checklist interno, referência metodológica ou alinhamento preliminar de boas práticas. Certificação exige processo externo acreditado e está fora do escopo deste repositório.

### Audit/Benchmark trail
- docs/AUDIT_CLAIMS_POLICY.md
- reports/vectra_grade_benchmarks.md
- reports/device_runtime_smoke.md
- reports/rmr_equivalence.md

## RAFAELIA Termux Bootstrap (escopo atual)
- Target atual: **Termux userland** com `./build_termux.sh`.
- Não é ainda target freestanding com `_raf_start`.
- Não é ainda entrega `.so` JNI.
- Próximos targets planejados: `freestanding` e `librmr.so`.
