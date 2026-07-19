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
| G1 | `CANONICAL_BUILD_STATUS.md` -- drift de data (ultima entrada: 2026-04-03, >107 dias) | P0 | ABERTO | -- |
| G2 | `docs/RELEASE_EVIDENCE_LEDGER.md` -- zero entradas reais; apenas templates de exemplo | P0 | ABERTO | -- |
| G3 | SBOM criado (`sbom/SBOM.spdx.json`) mas todos os checksums em `NOASSERTION` ate build real | P0 | PARCIAL | -- |
| G4 | `_incoming/termux.c` -> promovido para `app/src/main/cpp/termux_jni.c` | P1 | FECHADO | -- |
| G5 | `ZiprafDirectRuntime.kt` -- mmap extent corrigido + parser ZIP + testes | P1 | FECHADO | -- |
| G6 | `device-runtime-smoke.yml` -- ADB missing, Install/Launch pending | P0 | BLOQUEADO_HW | -- |
| G7 | `PROJECT_STATE.md` -- sincronizado para 2026-07-19 | P1 | FECHADO | -- |
| G8 | `formulasdoRafaelmr.md` -- movido para `docs/formulas/` | P2 | FECHADO | -- |
| G9 | Comando QEMU construido por concatenacao de strings -- risco de shell injection | P1 | ABERTO | -- |
| G10 | `NAOCOMERCIAL/` -- incompatibilidade com GPLv2 formalizada em quarentena; decisao juridica pendente | P0 | PARCIAL | -- |
| G11 | `app/src/main/jniLibs/*/libXlorie.so` -- TOKEN_VAZIO: origem, build script, licenca e hash ausentes | P0 | ABERTO | **SIM** |
| G12 | Alpine/rootfs tarballs distribuidos sem proveniencia -- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` nao preenchido | P0 | ABERTO | **SIM** |
| G13 | OVMF/BIOS assets -- upstream URL, versao, licenca e SHA-256 nao registrados | P0 | ABERTO | **SIM** |
| G14 | Screenshots e assets soltos na raiz + `addthis/` -- proveniencia TOKEN_VAZIO | P2 | ABERTO | **SIM** |
| G15 | `engine/rmr/**` -- sem cabecalhos SPDX finais; TOKEN_VAZIO juridico bloqueia qualquer distribuicao | P0 | ABERTO | **SIM** |
| G16 | 51 arquivos `.S` assembly em `_incoming/pending/` -- todos TBD; nenhum promovido ao build | P1 | ABERTO | **SIM** |
| G17 | Firebase `google-services.json` -- placeholder; build de release sem credenciais reais rejeita | P1 | ABERTO | **SIM** |
| G18 | Certificate pinning -- hash real do certificado substituido por placeholder | P1 | ABERTO | **SIM** |
| G19 | `getForceRefreshVNCDisplay()` / `setForceRefreshVNCDisplay()` -- API deprecated ainda em `MainSettingsManager.java` | P2 | ABERTO | **SIM** |
| G20 | `Incluir/frames_seed.json` -- 16 frames com `[PLACEHOLDER -- forneca omega_msgs.jsonl]` | P2 | ABERTO | **SIM** |
| G21 | VOS_CSEL contract break -- identificado em `VECTRA_OS Living System Gap Ledger` (2026-06-09) mas nao resolvido | P1 | ABERTO | **SIM** |
| G22 | Gate legal de CI -- `LICENSES_REGISTER.md` define criterios mas nenhum workflow os executa | P0 | ABERTO | **SIM** |
| G23 | `ASSET_PROVENANCE_REGISTER.csv` -- estrutura existe, conteudo vazio (zero binarios registrados) | P0 | ABERTO | **SIM** |
| G24 | `Incluir/` e `_incoming/` -- 181 arquivos aguardando classificacao e promocao desde 2026-06-05 | P1 | ABERTO | **SIM** |
| G25 | `examples/guest_boot_evidence.token-vazio.json` -- todos os gates em TOKEN_VAZIO; nenhum boot de VM registrado | P0 | ABERTO | **SIM** |

### Proximas acoes -- Vectras-VM-Android

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| G9 | Converter `StartQemuCommand.java` para usar `ProcessBuilder` com lista de argumentos | IA/Rafael |
| G11 | Registrar origem de `libXlorie.so` ou remover do APK | Rafael |
| G12/G13 | Preencher `ASSET_PROVENANCE_REGISTER.csv` com hashes e upstream URLs | Rafael |
| G15 | Adicionar cabecalhos SPDX a cada arquivo em `engine/rmr/` | Rafael |
| G16 | Revisar contrato de registradores de cada `.S` e promover ou arquivar | Rafael |
| G22 | Adicionar step de verificacao de licenca em `android-ci.yml` | IA |

---

## qemu_rafaelia

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| Q1 | CI agora produz `qemu-system-{x86_64,aarch64,i386}` via job `build-qemu-binaries` | P0 | FECHADO | -- |
| Q2 | Scripts `tools/rafaelia/package_qemu_artifact.sh` e `check_qemu_artifact_contract.sh` criados | P0 | FECHADO | -- |
| Q3 | Nenhum workflow compila QEMU para Android/NDK (target arm/aarch64-linux-android) | P0 | ABERTO | -- |
| Q4 | `android/vectras-vm-android/` -- scaffold Gradle sem nenhum codigo que carregue o binario QEMU | P1 | ABERTO | -- |
| Q5 | Connectors `magisk`, `llama`, `userland`, `private` existem como `.c` mas nao estao no `meson.build` | P1 | ABERTO | **SIM** |
| Q6 | `system/process-monitor.c` -- `qemu_process_monitor_get_stats()` le campos sem mutex (race condition leve) | P2 | ABERTO | **SIM** |
| Q7 | `check_bql_contention()` em `process-health.c` -- retorna sempre `true`; implementacao real e TODO | P2 | ABERTO | **SIM** |

### Proximas acoes -- qemu_rafaelia

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| Q3 | Adicionar job NDK cross-compile no CI; usar `android-ndk-r26` e `--cross-file` meson | IA |
| Q4 | Implementar `QemuLoader.kt` no modulo Android que chama `System.loadLibrary` e expoe API de lancamento | IA/Rafael |
| Q5 | Avaliar se os 4 connectors sao necessarios no build principal; adicionar ao `meson.build` se sim | Rafael |
| Q7 | Implementar rate-based BQL contention check em `check_bql_contention()` | IA |

---

## termux-app-rafacodephi

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| T1 | `docs/BOOTSTRAP_SOURCE_CONTRACT.md` criado -- fonte dos bootstrap ZIPs documentada | P0 | FECHADO | -- |
| T2 | `loader.apk` -- nao existe em nenhum repositorio; contrato de bootstrap o requer | P0 | ABERTO | -- |
| T3 | Integracao com Vectras-VM-Android -- documentada como futura; nenhum consumidor implementado | P1 | ABERTO | -- |
| T4 | `raf_numbase` -- sistema sem equivalente em `qemu_rafaelia`; sem ponte entre os dois | P2 | ABERTO | -- |
| T5 | `compatibility-arm32` e `compatibility-arm32-ndk29` -- falhas pre-existentes em master desde 2026-07-03 (`apksigner: command not found`) | P1 | ABERTO | **SIM** |

### Proximas acoes -- termux-app-rafacodephi

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| T2 | Criar modulo Gradle minimo `app/loader/` que produz `loader.apk` stub | IA |
| T5 | Instalar `apksigner` no runner CI ou usar alternativa; verificar se e bloqueio de infra | IA/Rafael |

---

## RafGitTools

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| RG1 | HTTP adapters para multiplos providers (GitHub/GitLab/Bitbucket) -- declarados como STUB | P1 | ABERTO | **SIM** |
| RG2 | Offline queue -- codec de operacao de producao e integracao com WorkManager nao conectados | P1 | ABERTO | **SIM** |
| RG3 | `terminal-bounded-executor` -- nao e PTY/VT100; escape sequences ausentes para terminal real | P2 | ABERTO | **SIM** |
| RG4 | Sem APK produzido em nenhuma atividade CI -- `ECOSYSTEM_RUNTIME_STATE.json` = `OUT_OF_SCOPE_NO_CREDIT` | P0 | ABERTO | **SIM** |
| RG5 | `rafpolimata.segment-runtime` -- NativeActivity runtime proof ausente | P1 | ABERTO | **SIM** |
| RG6 | P33-12/13/15/16/20/21 -- syntax highlight, line numbers inline, breadcrumb UI, file icons, branch/tag selectors -- todos parciais | P2 | ABERTO | **SIM** |
| RG7 | `TokenRefreshManager` -- OAuth token refresh e stub (correto para PATs; incorreto para OAuth Apps) | P1 | ABERTO | **SIM** |
| RG8 | Fine-grained PAT -- scopes nao inspecionados; apenas `/user` validado | P2 | ABERTO | **SIM** |
| RG9 | `GPGVerifier` retorna sempre `valido` -- verificacao de assinatura GPG e bypass total | P0 | ABERTO | **SIM** |
| RG10 | `ActivitySingletonManager` guarda `Activity` sem `WeakReference` -- memory leak em rotation/back | P1 | ABERTO | **SIM** |
| RG11 | CI desativado por ausencia de credito Actions -- nenhum workflow executou no estado atual | P0 | ABERTO | **SIM** |

### Proximas acoes -- RafGitTools

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| RG9 | Implementar verificacao GPG real ou marcar como `UNVERIFIED` em vez de `valid` | IA |
| RG10 | Substituir `ActivitySingletonManager` por `WeakReference<Activity>` ou ViewModel | IA |
| RG4 | Configurar GitHub Actions com credito ou runner self-hosted | Rafael |
| RG7 | Documentar que refresh e PAT-only; adicionar guard para OAuth Apps | IA |

---

## androidx_RmR

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| AX1 | Fork do AndroidX sem CONTRIBUTING/CHANGELOG especifico -- drift de upstream nao rastreado | P2 | ABERTO | **SIM** |
| AX2 | Sem CI proprio -- depende de CI do projeto consumidor; atualizacoes de upstream podem quebrar silenciosamente | P2 | ABERTO | **SIM** |
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
| X6 | `LICENSES_REGISTER.md` define gate legal bloqueante mas nenhum workflow CI o verifica automaticamente | P0 | ABERTO | **SIM** |

---

## Resumo por status

| Status | Quantidade |
|--------|-----------|
| FECHADO | 8 |
| PARCIAL | 3 |
| ABERTO | 44 |
| BLOQUEADO_HW | 2 |
| BLOQUEADO_SEGREDO | 1 |
| **Total** | **58** |

### Gaps OMITIDOS em auditorias anteriores: 31 de 58

---

## Definicao operacional de TOKEN_VAZIO neste registro

`TOKEN_VAZIO` e um estado epistemologico valido -- nao e falha estetica.
Um registro TOKEN_VAZIO e valido quando possui: campo afetado, razao concreta, owner e criterio de saida.
Melhor bloquear release do que inventar licenca, autoria ou proveniencia.

Referencia: `rafaelmeloreisnovo/RafGitTools/docs/CONTENT_VALIDITY_TOKEN_VAZIO_CONTRACT.md`
