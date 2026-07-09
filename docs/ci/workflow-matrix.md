<!-- DOC_METADATA_SYNC: 2026-06-08 | role: ci-workflow-matrix -->

# CI workflow matrix (canonical + classification)

## Metadados canônicos

- Versão do documento: 2.1.
- Última revisão: 2026-06-08.
- Escopo: matriz canônica de workflows, entradas de CI, gates, release, signing, ABIs e publicação de artefatos.
- Status: canônico técnico vigente.
- Commit de referência: `HEAD`.
- Fonte de verdade relacionada: [`../../BUILDING.md`](../../BUILDING.md), [`../AI_BUILD_RELEASE_INDEX.md`](../AI_BUILD_RELEASE_INDEX.md), [`.github/workflows/android-ci.yml`](../../.github/workflows/android-ci.yml) e [`.github/workflows/release-dual-track.yml`](../../.github/workflows/release-dual-track.yml).

## Classificação oficial

### 1) Canônico obrigatório

| Workflow | Quando roda | Papel |
|---|---|---|
| `.github/workflows/pipeline-orchestrator.yml` | `push`, `pull_request`, `workflow_dispatch` | Orquestra perfil (`host_only`/`android_only`/`full`) e chama trilhas canônicas. |
| `.github/workflows/host-ci.yml` | direto por evento e/ou `workflow_call` | Pipeline host canônica (build, contratos e evidências host). |
| `.github/workflows/android-ci.yml` | `workflow_call` | Pipeline Android canônica parametrizada (Gradle/NDK/CMake/JNI/testes/artefatos); é a fonte de verdade executável usada pelo release oficial. |
| `.github/workflows/release-dual-track.yml` | `push` tag `v*.*.*`, `workflow_dispatch` com `release_tag` quando publicar | **Único workflow autorizado a publicar artefato oficial**; delega as lanes unsigned interna e signed oficial para `android-ci.yml` e só cria GitHub Release após gate signed verde. |
| `.github/workflows/quality-gates.yml` | `workflow_call` | Gate final consolidando resultado host + android por perfil. |

### 2) Wrapper permitido

| Workflow | Quando roda | Papel |
|---|---|---|
| `.github/workflows/android.yml` | `push`, `pull_request`, `workflow_dispatch` | Wrapper de entrada Android; delega para `android-ci.yml` (sem redefinir política oficial). |
| `.github/workflows/ci.yml` | `push`/`pull_request` em `main`, `master`, `dev`, `release/**`; `workflow_dispatch`; `workflow_call` | Wrapper legado/compatível ativo para encaminhar trilha host canônica com a mesma política de branches do wrapper Android. |

### 3) Auxiliar técnico

| Workflow | Quando roda | Papel |
|---|---|---|
| `.github/workflows/android-native-ci.yml` | evento direto e/ou `workflow_call` | Matriz nativa Android (debug/release por perfil ABI) para cobertura técnica complementar. |
| `.github/workflows/compile-matrix.yml` | `workflow_call` | Matriz auxiliar de compatibilidade ABI/variant para regressão. |
| `.github/workflows/sign-release.yml` | `workflow_dispatch` manual | Legado/compatibilidade; bloqueado para publicação oficial e sem trigger de tag. |

### 4) Legado/arquivado

- Não há workflow removido automaticamente nesta atualização.
- Wrappers legados permanecem classificados e não devem substituir trilhas canônicas.

---

## Como os workflows são usados na prática

1. **Entrada principal de validação contínua:** `pipeline-orchestrator.yml` para branches/PRs.
2. **Entrada oficial de publicação:** `release-dual-track.yml`; ele chama `android-ci.yml` duas vezes, uma lane `release-unsigned-internal` e uma lane `release-signed-official`, valida ambas e publica somente a saída assinada oficial arm64-v8a.
3. `android-ci.yml` aplica `prepare_android_env.sh`, `prepare_release_signing.sh`, `:app:verifyDeliveredCompiledArtifacts`, política `APP_ABI_POLICY`/`SUPPORTED_ABIS` resolvida por `abi_profiles_contract.json` e `materialize_android_ci_artifacts.sh`.
4. Workflows wrapper (`android.yml`, `ci.yml`) são permitidos para compatibilidade, sem virar fonte de verdade de política.
5. `sign-release.yml` não é caminho oficial: é manual, legado, exige confirmação `allow_compat_artifact=true` e não publica GitHub Release.

## Política ABI resumida

- **Oficial de publicação neste fluxo dual-track:** `official_arm64` para pacote assinado arm64-v8a. `official_arm32_arm64` permanece perfil de compatibilidade controlada fora da publicação oficial de loja.
- **Validação interna/compatibilidade controlada:** `official_arm32_arm64`, `internal_arm32_arm64` e matrizes expandidas conforme lane/profile; ARM32 não é distribuição oficial sem decisão documentada em contrário.
- **NEON:** existe sinalização de build e inclusão condicional de fontes por ABI ARM; classificação de implementação deve sempre ser comprovada por execução/teste, não só por flag.
