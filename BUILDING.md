<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

# BUILDING

## CLI prerequisites
- JDK 17 (baseline runtime)
- Android SDK Platform 35 + Build Tools 35.0.0 (target/compile obrigatórios para publicação)
- NDK 27.2.12479018
- CMake 3.22.1

> Baseline único de CMake: host (raiz) e Android JNI usam 3.22.1 para manter paridade de toolchain.

## Setup environment (example)
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_SDK_ROOT=/workspace/android-sdk
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
```

If `local.properties` is missing, `./tools/gradle_with_jdk21.sh` auto-writes `sdk.dir`
from `ANDROID_SDK_ROOT`/`ANDROID_HOME`; when env vars are not defined it also tries
the canonical fallback locations used by the build (`/usr/lib/android-sdk`,
`/opt/android-sdk`, `/opt/android-sdk-linux`, `$HOME/Android/Sdk`).
For metadata-only Gradle invocations (`--version`, `help`, `tasks`, `properties`,
`projects`, `dependencies`, `dependencyInsight`), the wrapper does not enforce SDK
materialization, which allows diagnostics in clean environments before Android SDK setup.

## Build commands

## Não usar `android/`
O subdiretório `android/` é **legado** e não é entrypoint oficial de build/release.

Rationale técnico:
- contratos de AGP, SDK/Build Tools, NDK, CMake, ABI e signing são centralizados no projeto Gradle da **raiz**;
- manter `android/` como fonte de verdade cria drift de versão e ambiguidades de entrypoint;
- CI/release oficial valida e publica artefatos exclusivamente a partir da raiz (`:app:*`).
- A orquestração em `.github/workflows/pipeline-orchestrator.yml` compila release **unsigned e signed** em `arm64-v8a+armeabi-v7a` via `android-ci.yml` e publica artefatos por lane.

Comandos canônicos (raiz):
```bash
./tools/gradle_with_jdk21.sh :app:assembleDebug
./tools/gradle_with_jdk21.sh :app:assembleRelease
./tools/gradle_with_jdk21.sh :app:bundleRelease
```

```bash
./tools/gradle_with_jdk21.sh --version
./tools/gradle_with_jdk21.sh clean
./tools/gradle_with_jdk21.sh :app:assembleDebug --stacktrace
./tools/gradle_with_jdk21.sh :app:assembleRelease --stacktrace
./tools/gradle_with_jdk21.sh :app:assemblePerfRelease --stacktrace
./tools/gradle_with_jdk21.sh :app:verifyDeliveredCompiledArtifacts -PartifactVariants=debug,release,perfRelease
./tools/gradle_with_jdk21.sh :app:lintDebug --stacktrace
```

## Build modes (canonical)
`app/build.gradle` suporta quatro modos explícitos de execução para alinhar local, CI interno e release oficial:

1. `debug-local`
   - alvo: iteração local rápida (`:app:assembleDebug`, Android Studio sync/build);
   - `-PdevFastPath=true` (**default é `false`**) permite degradar **apenas** gates pesados de pré-build local:
     - `validateCriticalNativeAbiLayer`;
     - cadeia `verifyShellLoaderArtifact`/`syncShellLoaderBootstrap`;
   - logs sempre marcam degradação controlada com prefixo `[DEGRADE]`.

2. `debug-internal-arm32-arm64`
   - alvo: validação interna em matriz dual-ARM;
   - usar `-PCI_INTERNAL_VALIDATION=true -PAPP_ABI_POLICY=arm32-arm64`;
   - mantém validações de contrato/matriz para assegurar compatibilidade interna.

3. `release-unsigned-internal`
   - alvo: validação interna de release sem segredos de produção;
   - usar `-Psigning_mode=unsigned` (ou `-PALLOW_UNSIGNED_RELEASE=true` onde aplicável);
   - mantém gates estritos de release, exceto exceções internas explicitamente sinalizadas.

4. `release-signed-official`
   - alvo: trilha oficial de distribuição;
   - usar assinatura de produção (`-Psigning_mode=signed` e/ou `-PciRelease=true` em CI oficial);
   - **`devFastPath` é ignorado**: gates pesados e validações estritas permanecem obrigatórios.


### Contrato do `termux-bootstrap` (modo assets-loader x embedded-zip)
- O target nativo `termux-bootstrap` suporta dois modos:
  - `TERMUX_BOOTSTRAP_MODE=assets-loader` (default): não exige payload JNI embutido.
  - `TERMUX_BOOTSTRAP_MODE=embedded-zip`: exige payload JNI embutido para `nativeGetZip()`.
- Fontes aceitas por padrão:
  - `app/src/main/cpp/generated/termux_bootstrap_payload.c`;
  - `app/src/main/cpp/generated/termux_bootstrap_payload.S`;
  - ou caminho explícito via `-DTERMUX_BOOTSTRAP_PAYLOAD_SOURCE=<arquivo>`.
- Para endurecer qualquer modo manualmente, use `-DTERMUX_BOOTSTRAP_REQUIRE_EMBEDDED_PAYLOAD=ON`.
- Para forçar trilha ZIP embutida, use `-DTERMUX_BOOTSTRAP_MODE=embedded-zip` (falha sem payload).

### Assinatura em CI (contrato canônico)
- Workflow Android CI usa `tools/ci/prepare_release_signing.sh` como fonte de verdade para assinatura.
- `signing_mode=auto`:
  - com segredos `ANDROID_KEYSTORE_*` válidos: executa release assinado;
  - sem segredos: executa release interno unsigned (sem degradar trilha oficial `signing_mode=signed`).
- `signing_mode=signed` sem segredos falha explicitamente (não há fallback silencioso).


### Lane -> abi_profile -> uso permitido

| lane | abi_profile resolvido | uso permitido |
|---|---|---|
| `debug-local` | `official_arm32_arm64` | Compatibilidade local/debug com dual-ARM (não-oficial de loja). |
| `debug-internal-arm32-arm64` | `internal_arm32_arm64` | Validação interna dual-ARM em CI. |
| `release-unsigned-internal` | `internal_arm32_arm64` | Release interno sem assinatura de produção. |
| `release-signed-official` | `official_arm64` | **Único perfil permitido para trilha oficial assinada (store oficial).** |

`official_arm32_arm64` permanece disponível apenas como perfil explícito de compatibilidade fora da trilha oficial de loja.

## Fluxo oficial para gerar bootstrap/loader.apk (Termux)
O artefato `loader.apk` é produzido pelo módulo `shell-loader` e copiado para assets intermediários do app
em `app/build/generated/bootstrapAssets/bootstrap/loader.apk` (sem versionar binário em `app/src/main/assets`).

```bash
# Variant estável padrão (release). Pode sobrescrever com -PloaderVariant=debug.
./tools/gradle_with_jdk21.sh :shell-loader:buildStableLoader

# Copia o loader gerado para os assets de build do app.
./tools/gradle_with_jdk21.sh :app:syncShellLoaderBootstrap

# Valida bootstraps versionados + loader quando Termux está habilitado.
python3 tools/verify_bootstrap_assets.py
```

`verifyDeliveredCompiledArtifacts` validates APK/AAB delivery per variant and writes
`app/build/reports/artifacts/compiled-artifacts-report.json`.

> Use `./tools/gradle_with_jdk21.sh` como comando canônico: o wrapper aplica a política de JVM suportada (17/21) e faz autoajuste de `sdk.dir` quando possível.

### Fallback offline do Gradle wrapper
- Se `./gradlew` falhar por indisponibilidade de rede/proxy ao baixar a distribuição, `./tools/gradle_with_jdk21.sh` faz fallback automático para `gradle` do host **somente** em comandos de metadata (`--version`, `help`, `tasks`, `properties`, `projects`, `dependencies`, `dependencyInsight`), preservando paridade da trilha oficial para build/test/release.
- Para forçar explicitamente esse caminho (sem tentar `./gradlew`), use:
  ```bash
  GRADLE_WITH_JDK21_FORCE_SYSTEM_GRADLE=true ./tools/gradle_with_jdk21.sh :app:assembleDebug
  ```



## Matriz local beta de artefatos (debugger + release, arm32+arm64, signed + unsigned)
Use o helper canônico para gerar a entrega beta local completa em `armeabi-v7a` + `arm64-v8a`:

```bash
./tools/ci/build_artifact_matrix_local.sh
```

O helper executa, em ordem:
1. `clean`;
2. build `debugger-beta` (`:app:assembleDebug`) com APK debug assinado pela chave debug padrão;
3. build `release-beta` unsigned (`:app:assembleRelease :app:bundleRelease -Psigning_mode=unsigned`);
4. build `release-beta` signed-internal com keystore local de validação (`-Psigning_mode=signed`);
5. pré-teste de ABI em cada APK com `tools/ci/verify_apk_abi_set.sh`;
6. relatório Gradle `:app:verifyDeliveredCompiledArtifacts -PartifactVariants=debug,release`;
7. manifesto com tamanho individual, SHA-256, estado de assinatura e caminho de upload local.

Saída/upload local:
- `artifacts/local-matrix/app-debug-beta.apk`
- `artifacts/local-matrix/app-release-unsigned.apk`
- `artifacts/local-matrix/app-release-unsigned.aab`
- `artifacts/local-matrix/app-release-signed-internal.apk`
- `artifacts/local-matrix/app-release-signed-internal.aab`
- `artifacts/local-matrix/manifest.json`
- `artifacts/local-matrix/pretest-report.txt`

Observações de assinatura:
- APK debug sem assinatura não é uma entrega Android instalável normal; por isso a trilha `debugger-beta` é `debug-signed` pela configuração padrão do Gradle/AGP.
- `release signed-internal` usa keystore beta local e não substitui a chave oficial de loja.
- `release unsigned` é somente validação interna/CI e deve continuar fora da trilha oficial de publicação.

## RAFCoder Android/NDK

Seção dedicada para execução local, JNI e release do core RAFCoder no pipeline Android oficial da raiz.

### 1) Build local Android (Gradle raiz)
```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Alternativa canônica com proteção de JVM/SDK (recomendada no repositório):
```bash
./tools/gradle_with_jdk21.sh :app:assembleDebug
./tools/gradle_with_jdk21.sh :app:assembleRelease
```

### 2) Build nativo do core (CMake/Ninja ou externalNativeBuild)
Build direto do core JNI (fora do Gradle):
```bash
export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/27.2.12479018"
cmake --preset android-armv7
cmake --build --preset build-android-armv7 -j$(nproc)

cmake --preset android-arm64-v8
cmake --build --preset build-android-arm64-v8 -j$(nproc)
```

Build JNI via Gradle (externalNativeBuild):
```bash
./tools/gradle_with_jdk21.sh :app:externalNativeBuildDebug
./tools/gradle_with_jdk21.sh :app:externalNativeBuildRelease
```

### 3) ABIs suportadas e backend ativo por ABI
ABIs Android ativos para validação dual-ARM: `armeabi-v7a` e `arm64-v8a`.

Backend RAFCoder selecionado no CMake JNI:
- `armeabi-v7a` -> `armv7-c-fallback`
- `arm64-v8a` -> `arm64-asm` (quando NDK >= 26 e `core/arch/primitives.S` presente), com fallback automático `arm64-c-fallback`

A seleção é registrada no configure CMake por ABI com log:
`[RAFCoder] ABI=<abi> selected primitives backend: <backend>`.

### 4) APK release unsigned e signed (segredos `ANDROID_*`)
Unsigned (validação interna):
```bash
./tools/gradle_with_jdk21.sh :app:assembleRelease \
  -Psigning_mode=unsigned \
  -PCI_INTERNAL_VALIDATION=true
```

Signed (oficial), usando injeção Gradle e segredos de CI:
```bash
./tools/gradle_with_jdk21.sh :app:assembleRelease \
  -Psigning_mode=signed \
  -Pandroid.injected.signing.store.file="$VECTRAS_RELEASE_STORE_FILE" \
  -Pandroid.injected.signing.store.password="$VECTRAS_RELEASE_STORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias="$VECTRAS_RELEASE_KEY_ALIAS" \
  -Pandroid.injected.signing.key.password="$VECTRAS_RELEASE_KEY_PASSWORD"
```

Compatibilidade com segredos `ANDROID_*` (legado/ponte para CI interno):
```bash
export VECTRAS_RELEASE_KEYSTORE_B64="$ANDROID_SIGNING_KEYSTORE"
export VECTRAS_RELEASE_STORE_PASSWORD="$ANDROID_SIGNING_STORE_PASSWORD"
export VECTRAS_RELEASE_KEY_ALIAS="$ANDROID_SIGNING_KEY_ALIAS"
export VECTRAS_RELEASE_KEY_PASSWORD="$ANDROID_SIGNING_KEY_PASSWORD"
./tools/ci/prepare_release_signing.sh --mode signed
```

Para gerar matriz local de artefatos unsigned + signed de validação interna:
```bash
./tools/ci/build_artifact_matrix_local.sh
```

### 5) Status JNI do core RAFCoder
Status atual: JNI **ativo no módulo legado `android/`**, com os métodos Java nativos:
- `nativeMessage`
- `nativeSectorSummary`

Ambos estão exportados e consumidos por `MainActivity`, servindo como smoke path JNI mínimo do core RAFCoder no escopo legado.

### 6) Roadmap DevOps curto (Android/NDK/JNI)
1. CI matrix ABI obrigatório para `armeabi-v7a` + `arm64-v8a` em build JNI (`externalNativeBuild`) e CMake puro (`android-cmake-matrix-*`).
2. Assinatura: convergir segredos para namespace único (`VECTRAS_RELEASE_*`) com ponte de compatibilidade `ANDROID_*` apenas em trilha interna controlada.
3. Hardening de release: reforçar gates de alinhamento ABI/SDK/JVM + verificação de cadeia de signing antes de upload.
4. Testes nativos: promover smoke JNI (`nativeMessage`/`nativeSectorSummary`) para check automatizado em CI (instrumentado) e manter selftests low-level por ABI.

## Build model: JNI-first on Android
- Android native builds are hosted/JNI and rely on bionic libc + pthread.
- Baremetal flags are intentionally not used for Android JNI targets.

Forbidden for JNI targets:
- `-ffreestanding`
- `-fno-builtin`
- `-DRMR_NO_STDLIB=1`

Required JNI baseline flags:
- `-DRMR_JNI_BUILD=1`
- `-O2`
- `-fno-fast-math`
- `-fno-exceptions`
- `-fno-rtti`

## ABI policy
A fonte única de verdade dos perfis ABI é `tools/ci/abi_profiles_contract.json`.

- `tools/ci/resolve_abi_profile.py` é o resolvedor canônico para CI/workflows.
- `tools/ci/check_abi_contract_drift.py` valida drift entre:
  - `gradle.properties` (`APP_ABI_POLICY`, `SUPPORTED_ABIS`)
  - `tools/qemu_launch.yml` (`build_env.abi_filters`)
  - `.github/workflows/*.yml`
- `tools/ci/validate_lowlevel_abi.sh` executa esse gate antes de builds Android em CI.

Para inspecionar um perfil sem hardcode:
```bash
python3 tools/ci/resolve_abi_profile.py --profile official_arm64
python3 tools/ci/resolve_abi_profile.py --profile internal_4abi
```

A matriz ABI textual não deve ser duplicada em documentação/workflows; consulte sempre o contrato JSON.


## CMake presets (host + Android ARM32/ARM64)
Use `CMakePresets.json` para compilar de forma determinística com o mesmo baseline do CI.

```bash
# Host
cmake --preset host-ninja
cmake --build --preset build-host -j$(nproc)

# Android ARM32 v7 (armeabi-v7a)
export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/27.2.12479018"
cmake --preset android-armv7
cmake --build --preset build-android-armv7 -j$(nproc)

# Android ARM64 v8 (arm64-v8a)
cmake --preset android-arm64-v8
cmake --build --preset build-android-arm64-v8 -j$(nproc)
```

## Supported version matrix
All values below are defaults from `gradle.properties` and can be overridden with `-P`.

| Area | Property | Min | Default | Max/Policy |
|---|---|---:|---:|---:|
| Compile SDK | `compile.api` → fallback `COMPILE_API` | 35 | 35 | **35 obrigatório para publicação profissional** |
| Target SDK | `target.api` → fallback `TARGET_API` | 35 (`release.min.target.api`) | 35 | **35 obrigatório para publicação profissional** |
| Build Tools | `tools.version` → fallback `TOOLS_VERSION` | 35.0.0 | 35.0.0 | keep aligned with compile SDK |
| NDK | `ndk.version` → fallback `NDK_VERSION` | 23.x | 27.2.12479018 | latest validated in CI |
| CMake | `cmake.version` → fallback `CMAKE_VERSION` | 3.22.1 | 3.22.1 | keep host+JNI parity |
| Runtime Android (SO suportado) | `min.api` → fallback `MIN_API` | 29 (Android 10) | 29 | Android 10+ |
| Java language level | `java.language.version` → fallback `JAVA_LANGUAGE_VERSION` | 17 | 17 | 21 (when toolchain validated) |
| Gradle runtime JVM | `gradle.java.runtime.version` → fallback `GRADLE_JAVA_RUNTIME_VERSION` | 17 | 17 | `gradle.max.runtime.java.version` (default 21) |


Property precedence rule (to avoid config drift):
- Canonical property names use dotted lowercase keys (for example: `compile.api`, `tools.version`).
- Legacy aliases in uppercase snake case (for example: `COMPILE_API`, `TOOLS_VERSION`) are fallback-only for backward compatibility.
- When a legacy alias is used, the Gradle bootstrap emits a deprecation warning and continues.

## Política de publicação e runtime Android
- Faixa de SO suportada em runtime: **Android 10+** (`min.api`/`MIN_API` = 29).
- Para distribuição profissional/publicação em loja: **compile SDK 35 e target SDK 35 são mandatórios** (`compile.api`, `target.api`, `release.min.target.api` e aliases).
- CI/pipeline (`tools/qemu_launch.yml`) deve manter `build_env.compile_sdk=35` e `build_env.target_sdk=35` para evitar drift com Gradle.

Strictness control by pipeline context:
- A validação de bootstrap (`verifyBootstrapAssets`) e a validação final (`verifyGradleRuntimeJvm` + gates de API/ABI) compartilham a mesma política de `buildStrict` (warning em modo local, bloqueante em CI/release).
- `-PbuildStrict=false` (default): local/debug mode; validations emit warnings where allowed.
- `-PbuildStrict=true`: official CI/release mode; validations are blocking.

## CI blocking checks by pipeline type
- Official release CI (`buildStrict=true`):
  - `verifyGradleRuntimeJvm` (blocking)
  - API/ABI validations (`verifyMinApiAbiCompatibility`, release target checks) (blocking)
  - `verifyBootstrapAssets` + `verifyRepoFileDependencies` (blocking; requires Python)
- Local/dev or debug CI (`buildStrict=false`):
  - Same checks run, but max-JVM/API-ABI non-release gates can warn.
  - Python-dependent checks are skipped with warning if Python is unavailable.

## Release oficial vs validação interna (unsigned/placeholder)
- **Release oficial (assinado)**:
  - exige keystore + credenciais de signing de produção;
  - pode forçar assinatura com `-Psigning_mode=signed` (ou `-PciRelease=true`);
  - exige `app/google-services.json` real (sem placeholder);
  - **não** usar `-PCI_INTERNAL_VALIDATION=true`.
- **Validação interna (unsigned)**:
  - permite `-PALLOW_UNSIGNED_RELEASE=true` ou `-Psigning_mode=unsigned`;
  - permite placeholder Firebase **somente** com sinal explícito `-PCI_INTERNAL_VALIDATION=true` (opcionalmente junto de `-PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true`);
  - usada para CI interno quando segredos de produção não estão disponíveis.


## Selftest matrix expectations
Canonical gate names:
- Make: `make run-selftest`
- CMake: `cmake --build <build-dir> --target run_selftest`

Expected per architecture:

| Environment | Canonical gate | Required architecture-specific selftests | Artifact logs |
|---|---|---|---|
| Host x86_64 Linux | `make run-selftest` and/or `run_selftest` | `rmr_casm_bridge_selftest` (supported ABI), `rmr_neon_simd_selftest` not required | `bench/results/selftest-host-x86_64.log`, `bench/results/rmr_casm_bridge_selftest-x86_64.log` |
| Host arm64 Linux (aarch64/arm64) | `make run-selftest` and/or `run_selftest` | `rmr_neon_simd_selftest` (required), `rmr_casm_bridge_selftest` optional/unsupported | `bench/results/selftest-host-arm64.log`, `bench/results/rmr_neon_simd_selftest-arm64.log`, `bench/results/rmr_casm_bridge_selftest-arm64.log` |
| Android (NDK / app ABI lanes) | Native selftests must be wired through the same gate contract before release | ABI-specific execution required for `arm64-v8a`; other ABIs per policy (`APP_ABI_POLICY`) | Keep per-ABI logs in CI artifacts and fail lane when required selftests are missing or failing |

## CI canonical reference (Android/Host)

- Canonical Android pipeline: `.github/workflows/android-ci.yml`.
- Android wrapper entrypoint: `.github/workflows/android.yml`.
- Auxiliary Android ABI compatibility matrix: `.github/workflows/compile-matrix.yml`.
- Canonical host pipeline: `.github/workflows/host-ci.yml`.
- Orchestration and final gates: `.github/workflows/pipeline-orchestrator.yml` and `.github/workflows/quality-gates.yml`.
- Canonical matrix documentation: `docs/ci/workflow-matrix.md`.

- `external_sources.manifest` mantém `androidx_RmR` e `qemu_rafaelia` em branch-tracked (sem commit pin), com validação remota de integridade no CI.

## Verificação dual ABI + assinatura

Use o comando canônico para gerar e validar APK release arm32+arm64 com e sem assinatura:

```bash
./tools/ci/build_verify_apks.sh
```

O script executa:
- `prepare_android_env.sh` (garante `sdk.dir`);
- build release unsigned (`signing_mode=unsigned`);
- build release signed (`signing_mode=signed` + `ciRelease=true` + keystore de validação);
- valida `apksigner verify`;
- confirma libs `armeabi-v7a` e `arm64-v8a` dentro dos APKs;
- imprime tamanho, SHA-256 e diff em bytes entre assinado/unsigned.

### Gate explícito para release interno unsigned
Use obrigatoriamente `-PCI_INTERNAL_VALIDATION=true -Psigning_mode=unsigned`; para placeholder Firebase em validação interna, acrescente `-PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true`.
