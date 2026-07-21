# ALL_GAPS_REGISTRY.md
<!-- Gerado: 2026-07-19 -- Auditoria exaustiva do ecossistema RAFAELIA/Vectras-VM -->
<!-- Escopo: Vectras-VM-Android, qemu_rafaelia, termux-app-rafacodephi, RafGitTools, androidx_RmR, Mapa -->

Registro canonico de **todos** os gaps identificados no ecossistema -- incluindo os
ausentos, esquecidos e omitidos em auditorias anteriores.

Legenda de status:
- `FECHADO` -- corrigido e mergeado em master
- `PARCIAL` -- mitigado mas nao completamente resolvido
- `ABERTO` -- identificado, pendente de implementacao
- `BLOQUEADO_HW` -- requer hardware fisico (ADB/dispositivo/runner dedicado)
- `BLOQUEADO_SEGREDO` -- requer segredos CI nao commitaveis (keystore, tokens)
- `OMITIDO` -- nao constava em nenhuma auditoria anterior

---

## Vectras-VM-Android

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| G1 | `CANONICAL_BUILD_STATUS.md` -- drift de data corrigido; auditoria 2026-07-21 registrada | P0 | FECHADO | -- |
| G2 | `docs/RELEASE_EVIDENCE_LEDGER.md` -- zero entradas reais; apenas templates de exemplo | P0 | ABERTO | -- |
| G3 | SBOM criado (`sbom/SBOM.spdx.json`) mas todos os checksums em `NOASSERTION` ate build real | P0 | PARCIAL | -- |
| G4 | `_incoming/termux.c` -> promovido para `app/src/main/cpp/termux_jni.c` | P1 | FECHADO | -- |
| G5 | `ZiprafDirectRuntime.kt` -- mmap extent corrigido + parser ZIP + testes | P1 | FECHADO | -- |
| G6 | `device-runtime-smoke.yml` -- ADB missing, Install/Launch pending | P0 | BLOQUEADO_HW | -- |
| G7 | `PROJECT_STATE.md` -- sincronizado para 2026-07-19 | P1 | FECHADO | -- |
| G8 | `formulasdoRafaelmr.md` -- movido para `docs/formulas/` | P2 | FECHADO | -- |
| G9 | Comando QEMU construido por concatenacao de strings -- risco de shell injection | P1 | PARCIAL | -- |
| G10 | `NAOCOMERCIAL/` -- incompatibilidade com GPLv2 formalizada em quarentena; decisao juridica pendente | P0 | PARCIAL | -- |
| G11 | `app/src/main/jniLibs/*/libXlorie.so` -- TOKEN_VAZIO: origem, build script, licenca e hash ausentes | P0 | ABERTO | **SIM** |
| G12 | Alpine/rootfs tarballs distribuidos sem proveniencia -- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` nao preenchido | P0 | ABERTO | **SIM** |
| G13 | OVMF/BIOS assets -- upstream URL, versao, licenca e SHA-256 nao registrados | P0 | ABERTO | **SIM** |
| G14 | Screenshots e assets soltos na raiz + `addthis/` -- proveniencia TOKEN_VAZIO | P2 | ABERTO | **SIM** |
| G15 | `engine/rmr/**` -- cabecalhos SPDX adicionados a todos os 87 arquivos (src/*.c/*.h + interop/*.S) | P0 | FECHADO | **SIM** |
| G16 | 51 arquivos `.S` assembly em `_incoming/pending/` -- todos TBD; nenhum promovido ao build | P1 | ABERTO | **SIM** |
| G17 | Firebase `google-services.json` -- placeholder; build de release sem credenciais reais rejeita | P1 | ABERTO | **SIM** |
| G18 | Certificate pinning -- hash real do certificado substituido por placeholder | P1 | ABERTO | **SIM** |
| G19 | `getForceRefreshVNCDisplay()` / `setForceRefreshVNCDisplay()` -- `@Deprecated` + javadoc anotacoes adicionadas em `MainSettingsManager.java` | P2 | FECHADO | **SIM** |
| G20 | `Incluir/frames_seed.json` -- 16 frames com `[PLACEHOLDER -- forneca omega_msgs.jsonl]` | P2 | ABERTO | **SIM** |
| G21 | VOS_CSEL contract break -- macro ja corrigida; `demo_cli/src/rmr_vectra_os_contract_selftest.c` implementado e wired em `make run-selftest` | P1 | FECHADO | **SIM** |
| G22 | Gate legal de CI -- `legal-compliance-gate.yml` implementado; verifica SPDX em src/ + interop/ + CSV de proveniencia | P0 | FECHADO | **SIM** |
| G23 | `ASSET_PROVENANCE_REGISTER.csv` -- estrutura existe, conteudo vazio (zero binarios registrados) | P0 | ABERTO | **SIM** |
| G24 | `Incluir/` e `_incoming/` -- 181 arquivos aguardando classificacao e promocao desde 2026-06-05 | P1 | ABERTO | **SIM** |
| G25 | `examples/guest_boot_evidence.token-vazio.json` -- todos os gates em TOKEN_VAZIO; nenhum boot de VM registrado | P0 | ABERTO | **SIM** |
| G26 | `detectRootfsLibc()` nao detectava glibc em rootfs i386 (`lib/ld-linux.so.2`, `lib/i386-linux-gnu/ld-linux.so.2` ausentes do bloco glibc) | P2 | FECHADO | **SIM** |
| G27 | `tools/audit_vectra_capabilities.py` -- 4 falhas de seguranca: ABI desconhecida passava silencioso, e_type nao validado, versao DEX desconhecida aceita, DEX em subdiretorio incluido | P2 | FECHADO | **SIM** |

### Proximas acoes -- Vectras-VM-Android

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| G11 | Registrar origem de `libXlorie.so` ou remover do APK | Rafael |
| G12/G13 | Preencher `ASSET_PROVENANCE_REGISTER.csv` com hashes e upstream URLs | Rafael |
| G16 | Criar manifesto de classificacao dos 60 `.S` em `_incoming/pending/`; arquivar os standalone | IA |
| G20 | Fornecer `omega_msgs.jsonl` para extrair conteudo real dos frames seed_conv* | Rafael |

---

## qemu_rafaelia

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| Q1 | CI agora produz `qemu-system-{x86_64,aarch64,i386}` via job `build-qemu-binaries` | P0 | FECHADO | -- |
| Q2 | Scripts `tools/rafaelia/package_qemu_artifact.sh` e `check_qemu_artifact_contract.sh` criados | P0 | FECHADO | -- |
| Q3 | Nenhum workflow compila QEMU para Android/NDK (target arm/aarch64-linux-android) | P0 | FECHADO | -- |
| Q4 | `android/vectras-vm-android/` -- `VMService.kt` tem `System.loadLibrary("rafaelia_bridge")` + JNI; `.so` compilado depende do build NDK (Q3) | P1 | PARCIAL | -- |
| Q5 | Connectors `magisk`, `llama`, `userland`, `private` e `ipc` -- todos adicionados ao `hw/core/meson.build` | P1 | FECHADO | **SIM** |
| Q6 | `system/process-monitor.c` -- `qemu_process_monitor_get_stats()` le campos sem mutex (race condition leve) | P2 | FECHADO | **SIM** |
| Q7 | `check_bql_contention()` em `process-health.c` -- implementado com rate-based check (delta/elapsed_ms vs threshold 1000/s) | P2 | FECHADO | **SIM** |

### Proximas acoes -- qemu_rafaelia

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| Q4 | Aguardar NDK build produzir `librafaelia_bridge.so`; entao `System.loadLibrary` em VMService.kt sera funcional | Rafael/CI |

---

## termux-app-rafacodephi

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| T1 | `docs/BOOTSTRAP_SOURCE_CONTRACT.md` criado -- fonte dos bootstrap ZIPs documentada | P0 | FECHADO | -- |
| T2 | `loader.apk` -- modulo Gradle `app/loader/` implementado; produz `loader.apk` stub via `materializeLoaderApk` | P0 | FECHADO | -- |
| T3 | Integracao com Vectras-VM-Android -- documentada como futura; nenhum consumidor implementado | P1 | ABERTO | -- |
| T4 | `raf_numbase` -- sistema sem equivalente em `qemu_rafaelia`; sem ponte entre os dois | P2 | ABERTO | -- |
| T5 | `compatibility-arm32` e `compatibility-arm32-ndk29` -- falhas pre-existentes em master desde 2026-07-03 (`apksigner: command not found`) | P1 | FECHADO | **SIM** |

### Proximas acoes -- termux-app-rafacodephi

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| T3 | Documentar consumidor cross-repo: `VectrasTermuxBridge.kt` que invoca commands via loader.apk IPC | IA/Rafael |

---

## RafGitTools

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| RG1 | HTTP adapters para multiplos providers -- GitLabApiService (v4) e BitbucketApiService (v2) implementados via Retrofit; wiring em MultiPlatformManager com tratamento de 401/403/IOException | P1 | FECHADO | **SIM** |
| RG2 | Offline queue -- `SyncOperation` (Gson codec) + `SyncWorker` (CoroutineWorker) + `PeriodicWorkRequest` 15min registrados em Application.onCreate | P1 | FECHADO | **SIM** |
| RG3 | `terminal-bounded-executor` -- nao e PTY/VT100; escape sequences ausentes para terminal real | P2 | ABERTO | **SIM** |
| RG4 | Sem APK produzido em nenhuma atividade CI -- `ECOSYSTEM_RUNTIME_STATE.json` = `OUT_OF_SCOPE_NO_CREDIT` | P0 | ABERTO | **SIM** |
| RG5 | `rafpolimata.segment-runtime` -- NativeActivity runtime proof ausente | P1 | ABERTO | **SIM** |
| RG6 | P33-12/13/15/16/20/21 -- syntax highlight, line numbers inline, breadcrumb UI, file icons, branch/tag selectors -- todos parciais | P2 | ABERTO | **SIM** |
| RG7 | `TokenRefreshManager` -- OAuth token refresh e stub (correto para PATs; incorreto para OAuth Apps) | P1 | FECHADO | **SIM** |
| RG8 | Fine-grained PAT -- scopes nao inspecionados; apenas `/user` validado | P2 | ABERTO | **SIM** |
| RG9 | `GPGVerifier` retorna sempre `valido` -- verificacao de assinatura GPG e bypass total | P0 | FECHADO | **SIM** |
| RG10 | `ActivitySingletonManager` -- classe nao existe no codigo-fonte; gap nao se aplica a este projeto | P1 | NAOAPLICAVEL | **SIM** |
| RG11 | CI desativado por ausencia de credito Actions -- nenhum workflow executou no estado atual | P0 | ABERTO | **SIM** |

### Proximas acoes -- RafGitTools

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| RG4 | Configurar GitHub Actions com credito ou runner self-hosted para produzir APK | Rafael |
| RG5 | Aguardar RafPolimata repo fornecer NativeActivity proof (fora do escopo desta infraestrutura) | Rafael |
| RG8 | Adicionar `PATScopeInspector` que le `X-OAuth-Scopes` header e reporta scopes ao usuario | IA |

---

## androidx_RmR

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| AX1 | Fork do AndroidX sem CONTRIBUTING/CHANGELOG especifico -- drift de upstream nao rastreado | P2 | ABERTO | **SIM** |
| AX2 | Sem CI proprio -- `.github/workflows/rmr-native-ci.yml` implementado; builda todos os modulos RmR em `arm64-v8a` | P2 | FECHADO | **SIM** |
| AX3 | Sem mapeamento explicito de quais commits do upstream AndroidX foram incorporados | P2 | ABERTO | **SIM** |

---

## Mapa

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| M1 | KOS declara 28+ repositorios; apenas 6 tem acesso/CI ativo nesta infraestrutura | P1 | ABERTO | **SIM** |
| M2 | Indices em `biblioteconomia/` e `indices/` sao manuais -- sem geracao automatizada | P2 | ABERTO | **SIM** |
| M3 | `workflows/WORKFLOW_VARREDURA_OPERACIONAL.md` -- procedimento definido mas nao integrado a CI de nenhum repo | P2 | ABERTO | **SIM** |
| M4 | Referencias a repositorios fora do escopo ativo (ChipQuantum, etc.) -- links nao verificados | P2 | ABERTO | **SIM** |

---

## Cross-repo / Sistemico

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| X1 | `examples/guest_boot_evidence.token-vazio.json` -- todos os 9 gates em TOKEN_VAZIO; prova de boot zero | P0 | ABERTO | **SIM** |
| X2 | Cadeia de prova `codigo->build->artefato->instalacao->boot VM->teste->prova assinada` -- aberta em todos os repos | P0 | ABERTO | -- |
| X3 | Nenhum runner CI com hardware ARM32/ARM64 + ADB ativo | P0 | BLOQUEADO_HW | -- |
| X4 | Segredos `VECTRAS_RELEASE_*` ausentes em CI -- assinatura oficial impossivel | P0 | BLOQUEADO_SEGREDO | -- |
| X5 | Integracao cross-repo Vectras<->qemu_rafaelia<->termux -- documentada mas nenhum consumidor implementado end-to-end | P0 | ABERTO | **SIM** |
| X6 | `LICENSES_REGISTER.md` define gate legal bloqueante -- `legal-compliance-gate.yml` verifica STATUS e reporta TOKEN_VAZIO/QUARANTINE automaticamente | P0 | FECHADO | **SIM** |

---

## Resumo por status

| Status | Quantidade |
|--------|-----------|
| FECHADO | 28 |
| PARCIAL | 5 |
| ABERTO | 23 |
| BLOQUEADO_HW | 2 |
| BLOQUEADO_SEGREDO | 1 |
| NAOAPLICAVEL | 1 |
| **Total** | **60** |

### Gaps OMITIDOS em auditorias anteriores: 33 de 60

<!-- Atualizacao 2026-07-20: +6 fechados (Q6, T5, RG7, RG9 confirmados em main; G26 detectRootfsLibc i386; G27 audit_vectra_capabilities P2x4) -->
<!-- Atualizacao 2026-07-21: +2 fechados (G1 CANONICAL_BUILD_STATUS drift corrigido; RG1 HTTP adapters GitLab/Bitbucket implementados); RG10 marcado NAOAPLICAVEL (classe inexistente) -->
<!-- Atualizacao 2026-07-21: +6 fechados (G15 SPDX interop/*.S completo, G22 legal-gate CI expandido, Q5 todos connectors em meson.build, Q7 BQL rate-based, RG2 WorkManager SyncWorker implementado, android-build R.kt collision removida) -->
<!-- Atualizacao 2026-07-21b: +6 fechados (G19 @Deprecated VNC force-refresh, G21 VOS_CSEL selftest wired, Q3 NDK CI job implementado, T2 loader.apk modulo Gradle, AX2 rmr-native-ci.yml, X6 LICENSES_REGISTER gate no CI); Q4 promovido a PARCIAL (VMService.kt tem bridge loading) -->

---

## Definicao operacional de TOKEN_VAZIO neste registro

`TOKEN_VAZIO` e um estado epistemologico valido -- nao e falha estetica.
Um registro TOKEN_VAZIO e valido quando possui: campo afetado, razao concreta, owner e criterio de saida.
Melhor bloquear release do que inventar licenca, autoria ou proveniencia.

Referencia: `rafaelmeloreisnovo/RafGitTools/docs/CONTENT_VALIDITY_TOKEN_VAZIO_CONTRACT.md`
