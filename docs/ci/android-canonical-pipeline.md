<!-- DOC_ORG_SCAN: 2026-04-20 | source-scan: android-ci-canonicalization -->

# Android pipeline canônica

## Decisão

A pipeline Android canônica do repositório é **`.github/workflows/android-ci.yml`**.

## Topologia atual (fonte de verdade)

1. **`android-ci.yml` (workflow_call)**
   - Contém o contrato completo de build Android: resolução de `run_workfile`, seleção de ABIs, política de assinatura release, validações de diretório/SDK/JDK/NDK/CMake, build Gradle, lint opcional e upload de artefatos.
2. **`android.yml` (push/pull_request/workflow_dispatch)**
   - É o ponto de entrada acionável e **wrapper** para `android-ci.yml`.
   - Resolve inputs e dispara um gate adaptativo para `compile-matrix.yml` somente em execuções internas específicas.
3. **`compile-matrix.yml` (workflow_call)**
   - Workflow especializado de compatibilidade por ABI/variant em matriz explícita.
   - Mantém lógica própria de lanes Android (não substitui `android-ci.yml`, nem é o fluxo oficial de release).

## Regras operacionais

- Toda mudança de contrato Android oficial (SDK/NDK/JDK/CMake, política de ABI, assinatura release, tasks Gradle oficiais) deve acontecer em `android-ci.yml`.
- `android.yml` deve permanecer fino: entrada, resolução de parâmetros e delegação para `android-ci.yml`.
- `compile-matrix.yml` é trilha auxiliar de compatibilidade e regressão; não deve ser tratada como fonte primária de release.
- Evoluções de ABI low-level continuam obrigatórias via `tools/ci/lowlevel_abi_contract.json` + `tools/ci/validate_lowlevel_abi.sh`.

## Trilhas explícitas (workflow_dispatch / workflow_call)

- `debug-local` → executa `run_workfile=upstream-debug-compat`, `build_variant=debug`, `signing_mode=unsigned`, `abi_profile=official_arm32_arm64`.
- `debug-internal-arm32-arm64` → debug interno dual-ABI (`arm64-v8a` + `armeabi-v7a`) unsigned.
- `release-unsigned-internal` → release interno dual-ABI unsigned (aceito somente na trilha interna com `ci_internal_validation=true`).
- `release-signed-official` → release oficial arm64-v8a com assinatura obrigatória.

As trilhas de release mantêm validações rígidas de side-by-side ABI e assinatura; degradações são permitidas apenas em trilhas de debug/local.

## Evidências e manifesto de artefatos

- A task `:app:verifyDeliveredCompiledArtifacts` gera `app/build/reports/artifacts/artifact-manifest.json` com `sha256`, `size` e ABI de cada `lib/<abi>/*.so`.
- O manifesto agrega evidências com `file` e `readelf -h` por biblioteca quando as ferramentas estão disponíveis no runner.
- Em trilhas internas com política dual-ABI, a ausência de `armeabi-v7a` ou `arm64-v8a` falha a execução.

## Divergências corrigidas nesta revisão

- Removida afirmação anterior de que `compile-matrix.yml` delega totalmente para `android-ci.yml`.
- Alinhada a definição de pipeline oficial com a implementação real: `android-ci.yml` como canônica e `android.yml` como wrapper.

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Alias legado host ainda ativo: `.github/workflows/ci.yml` (wrapper para `host-ci.yml`, com gatilhos `push`/`pull_request` alinhados em `main`, `master`, `dev`, `release/**`).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
