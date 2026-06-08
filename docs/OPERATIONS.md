<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

# Operações e Troubleshooting

## Indicadores-chave
- `dropped_logs` (counter)
- bytes processados no stream
- estado do supervisor (`RUN/DEGRADED/FAILOVER/STOP`)
- timeout e cancelamentos em ShellExecutor

## Audit Ledger
- Arquivo: `files/audit-ledger.jsonl`
- Rotação simples: `audit-ledger.prev.jsonl`
- Campos: `ts_mono`, `ts_wall`, `vm_id`, `state_from`, `state_to`, `cause_code`, `dropped_logs`, `bytes`, `stall_ms`, `action_taken`

## Matriz de gates de CI (auditoria de processo)
| Gate | Objetivo | Etapa no workflow | Evidência/auditoria |
|---|---|---|---|
| Build Android | Garantir compilação de artefatos Debug/Release com toolchain suportada | `Build Debug APK` e `Build Release APK` | Logs do job `build` + APKs gerados |
| Dependências de arquivos do repositório | Validar integridade de dependências declaradas em arquivos do repo antes da build | `Verify repository file dependencies` (`./tools/gradle_with_jdk21.sh verifyRepoFileDependencies`) | Saída do Gradle no job CI; falha bloqueia build |
| Documentação crítica | Verificar links markdown locais em docs essenciais para evitar referências quebradas | `Validate local Markdown links (optional)` | Log da etapa (não bloqueante por `continue-on-error`) |
| Artefatos | Publicar APKs para rastreabilidade e distribuição interna | `Upload Debug APK as artifact` e `Upload Release APK as artifact` | Artefatos versionados no GitHub Actions |

## Cenário de teste manual: flood
1. Iniciar VM.
2. Rodar comando de alto volume no terminal da VM.
3. Confirmar que UI permanece responsiva.
4. Confirmar crescimento de `dropped_logs` e evento `DEGRADED` no ledger.

## Troubleshooting rápido
| Sintoma | Ação |
|---|---|
| VM não encerra | Validar failover TERM→KILL e `waitFor` |
| Logs cortados | Esperado em flood (backpressure) |
| Erro de pasta externa Android 11+ | Selecionar pasta via SAF |

## Matriz de gates (auditoria de processo)
| Gate | Objetivo | Execução/critério | Evidência gerada |
|---|---|---|---|
| Build Android (debug + release) | Garantir integridade de compilação dos módulos Android antes de distribuição | Workflow canônico Android `android-ci.yml` via `workflow_call`; para publicação oficial, a entrada obrigatória é `release-dual-track.yml`, que executa lanes unsigned interna e signed oficial antes do gate final. | Artefatos `android-artifacts-*`, manifests de staging e APK/AAB publicados pelo Actions. |
| Dependências de arquivos de repositório | Bloquear divergências entre documentação, mapeamentos e cadeia de arquivos essenciais | Etapa explícita `./tools/gradle_with_jdk21.sh verifyRepoFileDependencies` executada antes das etapas de build. | Log da etapa `Verify repository file dependencies` no job de CI. |
| Documentação crítica (links locais markdown) | Detectar referências quebradas em `README.md` e `docs/**/*.md` | Etapa opcional `Validate local markdown links` em Python (com `continue-on-error`) verifica caminhos locais não-HTTP. | Relatório no log da etapa, com lista de links inválidos quando detectados. |
| Artefatos de distribuição | Assegurar rastreabilidade de binários gerados na pipeline | `android-ci.yml` materializa artefatos com `tools/ci/materialize_android_ci_artifacts.sh`; somente `release-dual-track.yml` pode baixar a lane assinada oficial e criar GitHub Release. `sign-release.yml` é legado e bloqueado para publicação oficial. | Artefatos versionados por run no GitHub Actions, manifests `artifact-manifest.*` e release assets oficiais somente após gate signed verde. |



## Contrato formal — Shell Loader Bootstrap (`loader.apk`)

### Fonte de verdade do artefato
- Variant selecionada por `-PloaderVariant` (padrão: `release`).
- Caminho obrigatório: `shell-loader/build/outputs/apk/<loaderVariant>/loader.apk`.

### Gate de validação
- Task: `:app:verifyShellLoaderArtifact`.
- Ordem: depende de `:shell-loader:assemble<LoaderVariant>` e deve executar antes de `:app:syncShellLoaderBootstrap`.

### Critérios de aprovação
1. Arquivo existe no caminho exato esperado.
2. Caminho aponta para arquivo regular.
3. Tamanho do arquivo é maior que zero bytes.
4. **Opcional**: com `-PverifyShellLoaderManifest=true`, o APK deve conter `AndroidManifest.xml`.

### Padrão de erro (único e objetivo)
Quando qualquer critério falha, a exceção segue o formato único:

`Shell loader artifact inválido em '<caminho absoluto esperado>': <motivo>.`

Esse padrão elimina ambiguidade operacional e facilita troubleshooting em CI/local.

## Ciclo Toroidal Recursivo

O container determinístico passa a publicar metadados de ciclo para fechar o loop entre
estado, roteamento, política e troubleshooting operacional:

- `S_n`: estado recursivo da sequência de manifesto (`manifestSequence`).
- `R_n`: rota compactada por camadas/chunks (`totalLayers` + `totalChunks`).
- `P_n`: política aplicada por configuração + IOPS (`laneCount`, `chunkSize`, `writeIops/readIops`).
- `C(S_n,R_n,P_n)`: operação de fechamento por manifesto (`cycleClosureC`) para validar
  reconciliação determinística entre ciclos consecutivos.

### Fases observáveis do ciclo

| Fase | Critério observável | Sinal de troubleshooting |
|---|---|---|
| `CRESCE` | `writeIops + readIops` abaixo do limiar de saturação e delta de IOPS ainda elevado | `crescimento_estavel` |
| `SATURA` | `writeIops + readIops >= 4096` | `saturacao_iops>=4096` |
| `RECOLAPSA` | fora de saturação com `abs(writeIops - readIops) <= 192` | `reconsolidacao_delta<=192` |

### Campos no manifesto para operação

- `cycleStateSn`, `cycleRoutesRn`, `cyclePoliciesPn`, `cycleClosureC`
- `cyclePhase` (`CRESCE`, `SATURA`, `RECOLAPSA`)
- `saturationSignal`, `reconsolidationSignal`, `troubleshootingSignals`

Esses campos fecham o vínculo entre o diagrama toroidal e a telemetria real (`writeIops/readIops`)
para inspeção de gargalo e reconsolidação no troubleshooting.


## Política de acionamento de workflows
- Branches e pull requests continuam sob orquestração/validação normal; publicação oficial não deve ocorrer nessas entradas.
- O fluxo oficial de release é `.github/workflows/release-dual-track.yml`, acionado por tag `v*.*.*` ou `workflow_dispatch` controlado com `release_tag` quando publicar, sempre delegando a build para `.github/workflows/android-ci.yml`.
- Workflows filhos devem operar em modo reutilizável (`on: workflow_call`) e podem manter `workflow_dispatch` somente para debug manual controlado.
- Chamadas entre workflows devem usar `uses: ./.github/workflows/<arquivo>.yml` para preservar rastreabilidade e governança do pipeline.
- `.github/workflows/sign-release.yml` é legado/compatibilidade: pode gerar artefato manual com `allow_compat_artifact=true`, mas não possui permissão nem etapa de publicação oficial.

## Referência canônica de CI Android/Host

- Publicador oficial de release: `.github/workflows/release-dual-track.yml` delegando para `.github/workflows/android-ci.yml`.
- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração/release-dual-track).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade legada sem publicação oficial: `.github/workflows/sign-release.yml`.
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
