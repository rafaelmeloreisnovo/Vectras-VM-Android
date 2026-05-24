# START_HERE — Vectras VM Android

> Guia de entrada **profissional, formal, contemporâneo, moderno e elegante** para iniciar, validar e evoluir o projeto com coerência técnica entre Android, Gradle, CMake, NDK, JNI, CI e release.

## 1) Visão executiva
O **Vectras VM Android** é um sistema híbrido (Android + nativo) com foco em determinismo operacional, rastreabilidade e governança documental. O objetivo deste guia é reduzir atrito inicial e apontar, com precisão, as fontes oficiais para build, CI, validação e troubleshooting.

## 2) Princípios de operação
- Fonte de verdade: **árvore Git versionada** (sem overlays ZIP como origem).
- Caminho oficial Android: **Gradle da raiz** (`./gradlew` e wrappers em `tools/`).
- Release oficial: assinatura e trilha de CI sem atalhos inseguros.
- Documentação orientada à auditoria: estado, histórico, índices e mapas por diretório.

## 3) Recursos disponíveis (quick access)
### Governança e estado
- Estado do projeto: `PROJECT_STATE.md`
- Sincronização documental mais recente: `reports/DOC_SYNC_2026-05-24.md`
- Histórico: `CHANGELOG.md`
- Release notes: `RELEASE_NOTES.md`
- Índice documental: `DOC_INDEX.md`
- Build oficial: `BUILDING.md`
- Troubleshooting: `TROUBLESHOOTING.md`

### Pipelines e CI/CD
- Orquestrador canônico: `.github/workflows/pipeline-orchestrator.yml`
- Pipeline host: `.github/workflows/host-ci.yml`
- Pipeline Android: `.github/workflows/android-ci.yml`

### Arquitetura operacional (fonte primária)
- Fluxo fechado UI → `StartVM` → builders/resolvers → JNI (`NativeFastPath`/bridges) → `rmr_*` → args finais QEMU: `docs/architecture/VM_EXECUTION_FLOW.md`

### Domínios técnicos principais
- App Android: `app/`
- Engine e núcleo: `engine/`
- Emulador/terminal: `terminal-emulator/`, `terminal-view/`, `shell-loader/`
- Ferramentas e automação: `tools/`
- Documentação ativa: `docs/`

## 4) Trilha rápida de bootstrap
```bash
# 0) Ler sincronização documental atual
cat reports/DOC_SYNC_2026-05-24.md

# 1) Configurar SDK local
cp local.properties.example local.properties

# 2) Verificar Java/Gradle runtime
./tools/gradle_with_jdk21.sh --version
./tools/gradle_with_jdk21.sh verifyGradleRuntimeJvm

# 3) Validar cadeia principal (setup + build + checks)
./tools/gradle_with_jdk21.sh checkNativeAllMatrix
```

## 5) Contratos técnicos críticos
- Priorizar propriedades canônicas (`lowercase.with.dots`) no Gradle.
- Manter alinhamento de ABIs entre `gradle.properties` e `tools/qemu_launch.yml`.
- Evitar `ndk.dir` em `local.properties` (usar contrato atual com `ndk.version`).
- Não usar caminho legado `android/` como trilha oficial de build/release (apenas compatibilidade local).

## 6) Mapa de leitura recomendado (30 minutos)
1. `README.md`
2. `BUILDING.md`
3. `docs/README.md`
4. `tools/README.md`
5. `app/README.md` e `engine/README.md`

## 7) Metadados e tags
**Perfil:** Formal · Profissional · Contemporâneo · Moderno · Elegante · Ultra Moderno  
**Tags:** `#android` `#gradle` `#cmake` `#ndk` `#jni` `#sdk` `#jdk` `#github-actions` `#ci` `#release` `#governanca` `#determinismo`

---

Se você está chegando agora, comece por este arquivo e depois siga para `README.md` e `BUILDING.md` para execução orientada por trilha oficial.
