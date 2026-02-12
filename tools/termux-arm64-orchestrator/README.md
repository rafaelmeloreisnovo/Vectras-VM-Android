# Termux ARM64 Android 15 Orchestrator

Pipeline real para compilar **dentro do terminal** (Termux/ambiente similar), preparando SDK/NDK/CMake localmente e executando build `arm64-v8a`.

## Diretriz principal

Este módulo é para **build local no terminal**, sem depender de GitHub Actions para compilar.

## O que este módulo resolve

- Bootstrap automático de componentes Android necessários quando faltam no ambiente de terminal.
- Build release `arm64-v8a` com foco em flags de performance e redução de falhas por memória.
- Assinatura obrigatória com `vectras.jks` do repositório (sem publicação em loja).
- Gate mínimo de conformidade legal/documental antes da compilação.

## Arquivos

- `bootstrap-termux-android15.sh`: instala/prepara cmdline-tools + SDK + NDK + CMake local (`.android-sdk`) e gera `local.properties`.
- `orchestrate-build.sh`: orquestrador principal (detecção de arquitetura/NEON, spill de storage, bootstrap, build e verificação de assinatura).
- `legal-compliance-check.sh`: valida pré-requisitos legais e metadados de release + keystore.
- `run-local-termux-build.sh`: entrypoint único para execução local no terminal.

## Execução local (recomendada)

```bash
bash tools/termux-arm64-orchestrator/run-local-termux-build.sh
```

## Execução por etapas (manual)

```bash
bash tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh
bash tools/termux-arm64-orchestrator/legal-compliance-check.sh
bash tools/termux-arm64-orchestrator/orchestrate-build.sh
```

## Variáveis úteis

- `ANDROID_API_LEVEL` (default `35`)
- `ANDROID_BUILD_TOOLS` (default `35.0.0`)
- `ANDROID_NDK_VERSION` (default `27.2.12479018`)
- `ANDROID_CMAKE_VERSION` (default `3.22.1`)
- `BUILD_SPILL_DIR` (default `.build-spill`)
- `VECTRAS_KEYSTORE` (default `./vectras.jks`)
- `VECTRAS_KEY_ALIAS` (default `vectras`)
- `VECTRAS_STORE_PASSWORD` (default `856856`)
- `VECTRAS_KEY_PASSWORD` (default `856856`)
- `BOOTSTRAP_ANDROID=0|1`
- `CI_DRY_RUN=0|1`
