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
- `BLOQUEADO_INFRA` -- workflows corretos mas CI sem runners (credito Actions esgotado)
- `NAOAPLICAVEL` -- gap identificado nao se aplica ao codigo real deste projeto
- `OMITIDO` -- nao constava em nenhuma auditoria anterior

---

## Vectras-VM-Android

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| G1 | `CANONICAL_BUILD_STATUS.md` -- drift de data corrigido; auditoria 2026-07-21 registrada | P0 | FECHADO | -- |
| G2 | `docs/RELEASE_EVIDENCE_LEDGER.md` -- estrutura correta com BLOCKED entries; SHA-256 reais bloqueados por ausencia de build CI verde | P0 | BLOQUEADO_INFRA | -- |
| G3 | SBOM: 4 entradas `libXlorie.so` adicionadas com SHA-256 reais (arm64/armv7/x86/x86_64); restante NOASSERTION ate build CI produzir APK/AAB | P0 | PARCIAL | -- |
| G4 | `_incoming/termux.c` -> promovido para `app/src/main/cpp/termux_jni.c` | P1 | FECHADO | -- |
| G5 | `ZiprafDirectRuntime.kt` -- mmap extent corrigido + parser ZIP + testes | P1 | FECHADO | -- |
| G6 | `device-runtime-smoke.yml` -- ADB missing, Install/Launch pending | P0 | BLOQUEADO_HW | -- |
| G7 | `PROJECT_STATE.md` -- sincronizado para 2026-07-19 | P1 | FECHADO | -- |
| G8 | `formulasdoRafaelmr.md` -- movido para `docs/formulas/` | P2 | FECHADO | -- |
| G9 | Comando QEMU -- `QemuArgvContract` tokeniza e `QemuDirectLauncher` usa `ProcessBuilder.command(argv[])`; shell nunca avalia a string QEMU | P1 | FECHADO | -- |
| G10 | `NAOCOMERCIAL/` -- audit 2026-07-22: identificado como papeis academicos proprios de Rafael (pre-prints de geometria toroidal v0.1/v0.2/v0.3 + imagem ChatGPT); NAO e codigo de terceiros com licenca nao-comercial; 4 entradas especificas em ASSET_PROVENANCE_REGISTER.csv com status `excluded-from-release`; Rafael precisa declarar licenca open-source explicita para eventual distribuicao | P0 | PARCIAL | -- |
| G11 | `app/src/main/jniLibs/*/libXlorie.so` -- SHA-256 calculados (audit 2026-07-21; arm64=70aed270, armv7=91fd5f2e, x86=bc2bf69d, x86_64=c2a051f3); identificado como X11/EGL-pixman Android display lib (ELF stripped; BuildID sha1=fa8b07c2); source-url e licenca TOKEN_VAZIO pendentes de Rafael | P0 | PARCIAL | **SIM** |
| G12 | Alpine/rootfs tarballs -- glob guard em `ASSET_PROVENANCE_REGISTER.csv` existe; tarballs nao commitados (gitignored); entradas TOKEN_VAZIO aguardam Rafael fornecer URL+versao+hash quando assets forem adicionados | P0 | PARCIAL | **SIM** |
| G13 | OVMF/BIOS assets -- audit 2026-07-22: 4 entradas especificas de firmware em `ASSET_PROVENANCE_REGISTER.csv` com source_url e licenca preenchidos (TianoCore BSD-2-Clause-Patent; Linaro BSD-2-Clause-Patent); runtime paths corrigidos para `${filesDir}/firmware/` (NAO APK-bundled); sha256 TOKEN_VAZIO aguardando build CI produzir artefatos; SBOM tem 4 packages especificos; `qemu_rafaelia_assets.lock.yml` preenchido | P0 | PARCIAL | **SIM** |
| G14 | `addthis/` inventariado: 37 arquivos, proveniencia 22/22 documentada. Imagens UUID (3) confirmadas GPT-4o via forense C2PA (chunk caBX -- JUMBF manifest c2pa.actions.v2, softwareAgent=GPT-4o; manifest IDs registrados). Distribuicao das imagens ChatGPT aguarda decisao de Rafael (ToS OpenAI + GPL-2.0 compat). | P2 | PARCIAL | **SIM** |
| G15 | `engine/rmr/**` -- cabecalhos SPDX adicionados a todos os 87 arquivos (src/*.c/*.h + interop/*.S) | P0 | FECHADO | **SIM** |
| G16 | Arquivos `.S`/`.c`/`.h` em `_incoming/pending/` e `Incluir/` -- audit 2026-07-22: cabecalhos SPDX `GPL-2.0-only` adicionados a TODOS os 60 `.S` + 25 arquivos C/H; `CLASSIFICATION_MANIFEST.md` criado com categorias; movimento/promocao a `engine/rmr/` ou `app/` aguarda decisao de Rafael | P1 | PARCIAL | **SIM** |
| G17 | Firebase `google-services.json` -- debug: opcional (build.gradle valida e pula); release: requer arquivo real injetado via CI secret; estrutura de guarda ja implementada | P1 | BLOQUEADO_SEGREDO | **SIM** |
| G18 | Certificate pinning -- mecanismo correto: `SIGNATURE_DIGESTS_SHA256` calculado do keystore real em build time; debug usa debug.keystore automaticamente; release depende de X4 (keystore de release) | P1 | PARCIAL | **SIM** |
| G19 | `getForceRefreshVNCDisplay()` / `setForceRefreshVNCDisplay()` -- migracao completa: `VncDisplayConfig.java` criado em `com.vectras.vm.settings`; 3 call sites migrados (`VNCSettingsActivity.java:52,58`, `MainActivity.java:376`); API `@Deprecated` desativada | P2 | FECHADO | **SIM** |
| G20 | `docs/research/data/frames_seed.json` -- 5 frames com conteudo real; 20 `seed_conv*` com `[PLACEHOLDER]`; `tools/populate_seed_frames.py` implementado e pronto para executar quando Rafael fornecer `omega_msgs.jsonl` | P2 | PARCIAL | **SIM** |
| G21 | VOS_CSEL contract break -- macro ja corrigida; `demo_cli/src/rmr_vectra_os_contract_selftest.c` implementado e wired em `make run-selftest` | P1 | FECHADO | **SIM** |
| G22 | Gate legal de CI -- `legal-compliance-gate.yml` implementado; verifica SPDX em src/ + interop/ + CSV de proveniencia | P0 | FECHADO | **SIM** |
| G23 | `ASSET_PROVENANCE_REGISTER.csv` -- audit 2026-07-22: 18 entradas registradas (libXlorie x4, rootfs glob, 4 firmware runtime com source_url+licenca preenchidos, 4 NAOCOMERCIAL/ especificos, addthis/, rafaelia_ttl novo binario CRITICAL); libXlorie e rafaelia_ttl bloqueados por source-url/licenca; sha256 firmware aguarda CI | P0 | PARCIAL | **SIM** |
| G24 | `Incluir/` -- `CLASSIFICATION_MANIFEST.md` criado (10 categorias, 114 arquivos); 37 arquivos promovidos; cabecalhos SPDX GPL-2.0-only adicionados a todos os 15 arquivos C/H (audit 2026-07-22); C/ASM, ZIPs e patches aguardam confirmacao de Rafael | P1 | PARCIAL | **SIM** |
| G28 | `_incoming/pending/rafaelia_ttl` -- binario ELF32 ARM (410520 bytes, sem extensao; audit 2026-07-22) provavelmente compilado de `rafaelia_ttl.S`; sha256=d2878624...; source, build script e licenca TOKEN_VAZIO; registrado em `ASSET_PROVENANCE_REGISTER.csv` e SBOM; NAO promover ao APK ate proveniencia confirmada por Rafael | P0 | PARCIAL | **SIM** |
| G25 | `examples/guest_boot_evidence.token-vazio.json` -- todos os gates em TOKEN_VAZIO; nenhum boot de VM registrado -- requer QEMU executando em dispositivo real | P0 | BLOQUEADO_HW | **SIM** |
| G26 | `detectRootfsLibc()` nao detectava glibc em rootfs i386 (`lib/ld-linux.so.2`, `lib/i386-linux-gnu/ld-linux.so.2` ausentes do bloco glibc) | P2 | FECHADO | **SIM** |
| G27 | `tools/audit_vectra_capabilities.py` -- 4 falhas de seguranca: ABI desconhecida passava silencioso, e_type nao validado, versao DEX desconhecida aceita, DEX em subdiretorio incluido | P2 | FECHADO | **SIM** |

### Proximas acoes -- Vectras-VM-Android

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| G11 | SHA-256 registrado (4 archs); identificado como X11/EGL-pixman lib; confirmar source-url e licenca | Rafael |
| G12 | Preencher `ASSET_PROVENANCE_REGISTER.csv` com URL, versao e hash das rootfs/alpine tarballs quando forem adicionados | Rafael |
| G13 | source_url e licenca preenchidos; aguardar build CI para preencher sha256 de cada firmware | CI/Rafael |
| G16/G24 | SPDX headers adicionados; decidir quais arquivos promover a engine/rmr/ ou app/ | Rafael |
| G20 | Fornecer `omega_msgs.jsonl` para executar `tools/populate_seed_frames.py --jsonl omega_msgs.jsonl` | Rafael |
| G28 | Confirmar que `rafaelia_ttl` foi compilado de `rafaelia_ttl.S`; fornecer build script e licenca | Rafael |

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
| T3 | Integracao com Vectras-VM-Android -- `VectrasIntegrationReceiver` implementado em termux; `CrossRepoIntegrationManager` consume no Vectras | P1 | FECHADO | -- |
| T4 | `raf_numbase` -- bridge `hw/core/connectors/rafaelia-connector-numbase.c` criado em qemu_rafaelia: base 2-36, Fibonacci/Tribonacci/Primonacci, Pisano, radix economy, prime fluid graph, zero curve dual; IPC async + API sincrona de conveniencia | P2 | FECHADO | -- |
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
| RG3 | `terminal-bounded-executor` -- `AnsiOutputProcessor` adicionado: strip() remove ESC bytes, parse() retorna spans coloridos por cor ANSI (git diff vermelho/verde, log amarelo) | P2 | FECHADO | **SIM** |
| RG4 | Sem APK produzido em nenhuma atividade CI -- `debug-apk.yml` e `android-ci.yml` existem; bloqueado por credito Actions esgotado (runner_id=0 em todos os jobs do PR#289; `ECOSYSTEM_RUNTIME_STATE.json` = `OUT_OF_SCOPE_NO_CREDIT`) | P0 | BLOQUEADO_INFRA | **SIM** |
| RG5 | `rafpolimata.segment-runtime` -- NativeActivity runtime proof pertence ao repo `rafaelmeloreisnovo/RafPolimata` (fora do escopo dos 6 repos auditados); nao ha implementacao no RafGitTools a fazer | P1 | NAOAPLICAVEL | **SIM** |
| RG6 | P33-12/13/15/16/20/21 -- `SyntaxHighlighter` wired no `FileViewer` (Kotlin/Java/Python/JS/TS/XML/JSON/YAML/Shell); branch/tag `AssistChip` + `DropdownMenu` no TopAppBar; browsing por ref sem checkout via `listFiles(ref)` | P2 | FECHADO | **SIM** |
| RG7 | `TokenRefreshManager` -- OAuth token refresh e stub (correto para PATs; incorreto para OAuth Apps) | P1 | FECHADO | **SIM** |
| RG8 | Fine-grained PAT -- scopes inspecionados via `PATScopeInspector` (X-OAuth-Scopes + endpoint probing para fine-grained) | P2 | FECHADO | **SIM** |
| RG9 | `GPGVerifier` retorna sempre `valido` -- verificacao de assinatura GPG e bypass total | P0 | FECHADO | **SIM** |
| RG10 | `ActivitySingletonManager` -- classe nao existe no codigo-fonte; gap nao se aplica a este projeto | P1 | NAOAPLICAVEL | **SIM** |
| RG11 | CI desativado por ausencia de credito Actions -- todos os checks de PR#289 com runner_id=0 em <3s; workflows de CI existem (ci.yml, android-ci.yml, debug-apk.yml, etc.) e estao corretos | P0 | BLOQUEADO_INFRA | **SIM** |

### Proximas acoes -- RafGitTools

| Gap | Acao imediata | Owner |
|-----|--------------|-------|
| RG4 | Configurar GitHub Actions com credito ou runner self-hosted para produzir APK | Rafael |
| RG5 | Aguardar RafPolimata repo fornecer NativeActivity proof (fora do escopo desta infraestrutura) | Rafael |
| RG11 | Configurar GitHub Actions com credito ou runner self-hosted para produzir APK | Rafael |

---

## androidx_RmR

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| AX1 | Fork do AndroidX sem CONTRIBUTING especifico -- `docs/RMR_FORK_CONTRIBUTING.md` criado; documenta todos os 7 modulos rmr/ | P2 | FECHADO | **SIM** |
| AX2 | Sem CI proprio -- `.github/workflows/rmr-native-ci.yml` implementado; builda todos os modulos RmR em `arm64-v8a` | P2 | FECHADO | **SIM** |
| AX3 | Sem mapeamento de commits upstream -- `docs/UPSTREAM_DRIFT_LOG.md` criado; registra sync 91795d49 e politica de drift | P2 | FECHADO | **SIM** |

---

## Mapa

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| M1 | KOS cataloga 28 repos; `docs/ACTIVE_SCOPE.md` criado na Mapa: lista 6 ativos vs 22 referenciados fora do escopo CI; Rafael confirma se lista de 22 esta completa | P1 | PARCIAL | **SIM** |
| M2 | `scripts/generate_asset_index.py` adicionado -- gera `indices/ASSET_INDEX_AUTO.yaml` com 160 arquivos; CI step M2 valida contagem >= 1 | P2 | FECHADO | **SIM** |
| M3 | Varredura operacional -- step CI adicionado a `Mapa/.github/workflows/ci.yml`; verifica campos obrigatorios do workflow | P2 | FECHADO | **SIM** |
| M4 | Referencias out-of-scope -- step informacional CI adicionado; reporta repos fora dos 6 ativos (nao-bloqueante) | P2 | FECHADO | **SIM** |

---

## Cross-repo / Sistemico

| ID | Gap | Prioridade | Status | Omitido anteriormente |
|----|-----|-----------|--------|-----------------------|
| X1 | `examples/guest_boot_evidence.token-vazio.json` -- todos os 9 gates em TOKEN_VAZIO; prova de boot zero -- requer QEMU executando em dispositivo real | P0 | BLOQUEADO_HW | **SIM** |
| X2 | Cadeia de prova `codigo->build->artefato->instalacao->boot VM->teste->prova assinada` -- bloqueada em instalacao/boot/teste: requer CI verde (BG-14) + dispositivo ARM com ADB (X3) + keystore (X4) | P0 | BLOQUEADO_HW | -- |
| X3 | Nenhum runner CI com hardware ARM32/ARM64 + ADB ativo | P0 | BLOQUEADO_HW | -- |
| X4 | Segredos `VECTRAS_RELEASE_*` ausentes em CI -- assinatura oficial impossivel | P0 | BLOQUEADO_SEGREDO | -- |
| X5 | Integracao cross-repo Vectras<->termux -- `CrossRepoIntegrationManager` + `VectrasIntegrationReceiver` implementam IPC broadcast bidirecional; qemu_rafaelia paths descobertos em runtime | P0 | FECHADO | **SIM** |
| X6 | `LICENSES_REGISTER.md` define gate legal bloqueante -- `legal-compliance-gate.yml` verifica STATUS e reporta TOKEN_VAZIO/QUARANTINE automaticamente | P0 | FECHADO | **SIM** |

---

## Resumo por status

| Status | Quantidade |
|--------|-----------|
| FECHADO | 38 |
| PARCIAL | 14 |
| ABERTO | 0 |
| BLOQUEADO_HW | 5 |
| BLOQUEADO_SEGREDO | 2 |
| BLOQUEADO_INFRA | 3 |
| NAOAPLICAVEL | 2 |
| **Total** | **64** |

### Gaps OMITIDOS em auditorias anteriores: 42 de 64

<!-- Atualizacao 2026-07-20: +6 fechados (Q6, T5, RG7, RG9 confirmados em main; G26 detectRootfsLibc i386; G27 audit_vectra_capabilities P2x4) -->
<!-- Atualizacao 2026-07-21: +2 fechados (G1 CANONICAL_BUILD_STATUS drift corrigido; RG1 HTTP adapters GitLab/Bitbucket implementados); RG10 marcado NAOAPLICAVEL (classe inexistente) -->
<!-- Atualizacao 2026-07-21: +6 fechados (G15 SPDX interop/*.S completo, G22 legal-gate CI expandido, Q5 todos connectors em meson.build, Q7 BQL rate-based, RG2 WorkManager SyncWorker implementado, android-build R.kt collision removida) -->
<!-- Atualizacao 2026-07-21b: +6 fechados (G19 @Deprecated VNC force-refresh, G21 VOS_CSEL selftest wired, Q3 NDK CI job implementado, T2 loader.apk modulo Gradle, AX2 rmr-native-ci.yml, X6 LICENSES_REGISTER gate no CI); Q4 promovido a PARCIAL (VMService.kt tem bridge loading) -->
<!-- Atualizacao 2026-07-21c: +5 fechados (RG8 PATScopeInspector implementado PR#283; AX1 RMR_FORK_CONTRIBUTING.md PR#65; AX3 UPSTREAM_DRIFT_LOG.md PR#65; M3 CI varredura step PR#38; M4 CI repo-link step PR#38) -->
<!-- Atualizacao 2026-07-21d: +2 fechados (X5 CrossRepoIntegrationManager+VectrasIntegrationReceiver IPC broadcast implementado; T3 termux VectrasIntegrationReceiver responde a queries do Vectras) -->
<!-- Atualizacao 2026-07-21e: +2 fechados (RG3 AnsiOutputProcessor ANSI strip+parse 13 testes; M2 generate_asset_index.py 160 arquivos + CI step); markdownlint Mapa corrigido; androidx namespace = fix todos 8 modulos -->
<!-- Atualizacao 2026-07-21f: +1 fechado (RG6 SyntaxHighlighter wired + branch/tag AssistChip refpicker sem checkout -- todos os 6 sub-items P33-12/13/15/16/20/21); G16 ABERTO->PARCIAL (CLASSIFICATION_MANIFEST.md criado com 4 categorias; movimento aguarda owner) -->
<!-- Atualizacao 2026-07-21g: +1 fechado (T4 raf_numbase bridge connector criado em qemu_rafaelia: base conversion, sequences, Pisano, prime fluid graph, zero curve dual; IPC async + sync convenience API) -->
<!-- Atualizacao 2026-07-21h: G24 ABERTO->PARCIAL (CLASSIFICATION_MANIFEST.md com 10 categorias para 114 arquivos; 37 arquivos promovidos -- Python/tools, docs/reports, docs/prompts, docs/skills, docs/prototypes/ui; C/ASM e ZIPs aguardam Rafael) -->
<!-- Atualizacao 2026-07-21i: G14 ABERTO->PARCIAL (addthis/ inventariado: 37 arquivos classificados; docs/ADDTHIS_ASSET_PROVENANCE.md criado; 3 imagens UUID aguardam Rafael); G23 ABERTO->PARCIAL (ASSET_PROVENANCE_REGISTER.csv actualizado com addthis/ provenance doc); RG4+RG11 ABERTO->BLOQUEADO_INFRA (CI workflows existem mas credito Actions esgotado) -->
<!-- Atualizacao 2026-07-21j: M1 ABERTO->PARCIAL (Mapa/docs/ACTIVE_SCOPE.md criado: lista 6 ativos vs 22 fora do escopo CI; aguarda confirmacao Rafael); G20 corrigido: 20/25 placeholders (nao 16) -->
<!-- Atualizacao 2026-07-21k: BLOCKING_GAPS.md atualizado -- BG-10/11/12 marcados RESOLVED (G15+RG9+G22 FECHADOS); BG-14 adicionado (CI runners esgotados -- BLOQUEADO_INFRA); G16->PARCIAL; G25/X1->BLOQUEADO_HW; contagem corrigida Total=63 (38+8+9+4+1+2+1), OMITIDOS=41 -->
<!-- Atualizacao 2026-07-21l: G2->BLOQUEADO_INFRA; G11->PARCIAL (SHA-256 calculados 4 archs; identificado X11/EGL-pixman); G12/G13->PARCIAL (glob guards existem; binarios nao commitados); G17->BLOQUEADO_SEGREDO (guarda Firebase debug/release ja implementada); G18->PARCIAL (debug.keystore automatico; release bloqueado por X4); X2->BLOQUEADO_HW; G20->PARCIAL (tools/populate_seed_frames.py implementado; aguarda omega_msgs.jsonl de Rafael); RG5->NAOAPLICAVEL (prova pertence ao repo RafPolimata, fora do escopo); ABERTO=0 (zero gaps abertos sem owner/razao); NAOAPLICAVEL=2 -->
<!-- Atualizacao 2026-07-22: G10 atualizado (NAOCOMERCIAL/ = papeis academicos proprios de Rafael, NAO codigo terceiros; 4 entradas especificas CSV); G13 atualizado (source_url+licenca firmware preenchidos em CSV+SBOM+lock; sha256 aguarda CI); G16/G24 atualizados (SPDX GPL-2.0-only adicionados a TODOS 60 .S + 25 C/H em _incoming/pending/ e Incluir/); G23 atualizado (18 entradas CSV); G28 NOVO PARCIAL (rafaelia_ttl ELF32 ARM 410KB; sha256 registrado; source/licenca TOKEN_VAZIO); SBOM corrigido: paths firmware runtime:${filesDir}/firmware/; relacionamentos SPDXRef-ovmf-bios->4 entries especificas; Total=64 PARCIAL=14 OMITIDOS=42 -->
<!-- Atualizacao 2026-08-03: G19 descricao atualizada -- migracao completa (VncDisplayConfig.java criado; 3 call sites migrados; API deprecated desativada); RG9 confirmado via GpgKeyManager.verifySignature (gpg --verify sobre tmpfiles); todos E1-E10 series confirmados FECHADOS (static globals, -fvisibility=hidden, __builtin_trap, CRC/clamp headers, baremetal fixes, pipe-drain pattern, AtomicBoolean, dead params removidos) -->

---

## Definicao operacional de TOKEN_VAZIO neste registro

`TOKEN_VAZIO` e um estado epistemologico valido -- nao e falha estetica.
Um registro TOKEN_VAZIO e valido quando possui: campo afetado, razao concreta, owner e criterio de saida.
Melhor bloquear release do que inventar licenca, autoria ou proveniencia.

Referencia: `rafaelmeloreisnovo/RafGitTools/docs/CONTENT_VALIDITY_TOKEN_VAZIO_CONTRACT.md`
