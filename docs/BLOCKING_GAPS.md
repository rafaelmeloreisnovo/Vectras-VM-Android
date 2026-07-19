# BLOCKING_GAPS.md
<!-- Atualizado: 2026-07-19 | segunda onda de fechamento -->

Registro canônico dos gaps que impedem a promoção do Vectras VM.

Este documento substitui `TOKEN_VAZIO` por estados verificáveis:

```text
IMPLEMENTED_UNPROVEN
BLOCKED_BY[motivo]
PROVEN
```

Uma alteração de código não é automaticamente build, artefato, instalação, boot ou release.

---

## Estado atual: BETA_BLOCKED

```text
código → build → artefato móvel → instalação → boot VM → teste → prova assinada
```

A cadeia ainda está aberta. O QEMU já possui prova de host CI, mas o HEAD do Vectras não chega ao runner e ainda não existe prova completa em dispositivo.

---

## BG-00: workflows Vectras não chegam ao runner

**Status:** `BLOCKED_BY[GITHUB_ACTIONS_RUNNER_STARTUP]`

Nos heads do PR #1052, os workflows `android-ci`, `host-ci`, `Shell-Loader Smoke`, `APK Wizard`, `audit-benchmark-contract`, `Validate Formula` e o orquestrador encerraram em falha antes de executar steps de compilação. Em `android-ci`, os jobs de resolução/gate falharam sem lista de steps e o job canônico foi pulado.

Consequências:

- não há log Java/Kotlin/C que atribua falha ao diff;
- não há APK/AAB recente;
- não há hash de artefato Vectras;
- o estado não pode ser promovido nem rebaixado por inferência.

**Desbloqueio:** restaurar a capacidade de início dos runners/Actions e repetir o HEAD do PR.

---

## BG-01: hashes reais no RELEASE_EVIDENCE_LEDGER

**Status:** `BLOCKED_BY[CURRENT_GREEN_BUILD_REQUIRED]`

`docs/RELEASE_EVIDENCE_LEDGER.md` contém evidências negativas reais das execuções falhas e mantém o template separado. SHA-256 de APK/AAB, perfil ABI e assinatura continuam bloqueados até um build atual materializar os arquivos.

---

## BG-02: testes em dispositivo físico ARM32/ARM64

**Status:** `BLOCKED_BY[ADB_RUNNER_REQUIRED]`

Necessário:

1. ADB instalado e autorizado;
2. dispositivo ARM32, por exemplo Moto E7 Power;
3. dispositivo ARM64;
4. Android 10 e Android 14+;
5. captura de logcat, instalação, launch e encerramento.

O workflow `device-runtime-smoke.yml` continua com instalação/launch pendentes.

---

## BG-03: assinatura oficial de release

**Status:** `BLOCKED_BY[KEYSTORE_SECRETS_REQUIRED]`

Segredos necessários:

- `VECTRAS_RELEASE_KEYSTORE_BASE64`;
- `VECTRAS_RELEASE_KEY_ALIAS`;
- `VECTRAS_RELEASE_KEY_PASSWORD`;
- `VECTRAS_RELEASE_STORE_PASSWORD`.

Segredos nunca devem ser commitados. Release unsigned/debug não é release oficial.

---

## BG-04: artifact móvel e smoke de boot de VM

**Status:** `BLOCKED_BY[PROOT_ARM64_ARTIFACT_AND_DEVICE_REQUIRED]`

A prova exige:

```text
QEMU artifact móvel validado
→ importação pinada
→ APK integrado
→ instalação ADB
→ VM criada
→ firmware carregado
→ guest iniciado
→ display/rede observados
→ shutdown limpo
→ logs e hashes registrados
```

Estado do produtor `qemu_rafaelia`:

- três guest targets compilados e empacotados em host Linux x86_64;
- artifact host CI e manifestos publicados com sucesso;
- checker de runtime/ABI/libc/modo verde;
- lane PRoot `linux-aarch64 + musl` criada e em compilação;
- lane Android/Bionic separada por contrato, pois exige launcher nativo sem PRoot.

O artifact `host_ci` prova Q1/Q2, mas não pode ser instalado no aparelho.

---

## BG-05: benchmarks ZIPRAF

**Status:** `BLOCKED_BY[DEVICE_EXECUTION_REQUIRED]`

Claims de mmap por extent, janelas L1/L2 e lanes exigem medição em aparelho:

- page faults cold/warm;
- RSS/GC;
- throughput por lane;
- ARM32 versus ARM64;
- mmap extent versus leitura convencional.

---

## BG-06: bootstrap ZIPs e loader.apk verificáveis

**Status:** `IMPLEMENTED_UNPROVEN + BLOCKED_BY[FUNCTIONAL_CONTRACT]`

No `termux-app-rafacodephi`:

- contrato corrigido para `:app:generateRafcodephiBootstraps`;
- gerador padrão classificado como `BOOTSTRAP_BRIDGE_ONLY`;
- módulo `:loader` produz APK stub sem código;
- builds debug ARM32/ARM64/universal passam;
- relatório ARM32 prova package, SDKs, ELF e presença `armeabi-v7a`;
- a assinatura/gate final continua em correção operacional.

Ainda faltam payload pinado, hashes de bootstrap, comportamento funcional, consentimento, rollback e testes instrumentados.

---

## BG-07: SBOM com hashes reais

**Status:** `BLOCKED_BY[CURRENT_GREEN_BUILD_REQUIRED]`

A estrutura SPDX 2.3 existe. Hashes de APK, blobs e bibliotecas só podem ser preenchidos após materialização do build canônico.

---

## BG-08: proveniência de libXlorie.so

**Status:** `BLOCKED_BY[AUDIT_REQUIRED]`

Se incluída no APK, requer origem/upstream, licença SPDX, recipe, commit e SHA-256. Sem isso, permanece em quarentena para distribuição pública.

---

## BG-09: comando QEMU e artifact verificado

**Status:** `IMPLEMENTED_UNPROVEN[CANONICAL_PROOT_PATH]`

O PR #1052 implementa:

- argv direto para `qemu-system-*` standalone;
- PRoot sem concatenação de argumentos;
- preparação fixa de PulseAudio sem texto externo;
- testes para espaços e metacaracteres;
- validação de `qemu-exec.json` no consumidor;
- exigência de `runtime.os=linux`, `execution_mode=proot`, libc permitida e ABI do aparelho;
- resolução de path relativa ao artifact root;
- bloqueio de path traversal/absoluto;
- verificação SHA-256 antes da execução.

Limite preservado:

- wrappers opcionais `xterm -e bash -c`/`bash -c` seguem o caminho de compatibilidade;
- compatibilidade da libc com a rootfs precisa de preflight adicional;
- build unitário, assembleDebug e boot ainda não foram provados no HEAD.

---

## BG-10: escopo NAOCOMERCIAL versus GPLv2

**Status:** `BLOCKED_BY[FILE_LEVEL_LICENSE_AUDIT]`

`legal/LEGAL_SCOPE_MAP.yaml` formaliza a quarentena, mas não resolve eventual incompatibilidade. É necessário separar por arquivo código derivado GPLv2, código autoral independente, documentação comercial e artefatos que não podem ser combinados.

---

## Matriz de fechamento

| Gap | Código | Prova atual | Estado |
|---|---:|---:|---|
| G3 SBOM | sim | estrutura somente | `IMPLEMENTED_UNPROVEN` |
| G4 JNI Termux | sim | sem build Vectras atual | `IMPLEMENTED_UNPROVEN` |
| G5 ZIPRAF | sim | testes adicionados; sem dispositivo | `IMPLEMENTED_UNPROVEN` |
| G7 PROJECT_STATE | sim | documental | `PROVEN_DOCUMENTAL` |
| G8 mover fórmulas | sim | path corrigido | `PROVEN_DOCUMENTAL` |
| G9 argv + artifact gate | sim, caminho PRoot standalone | sem CI Vectras/ADB | `IMPLEMENTED_UNPROVEN` |
| G10 licenças | mapa/quarentena | auditoria incompleta | `PARTIAL` |
| Q1 binários QEMU | sim | três targets verdes no host | `PROVEN_CI_HOST` |
| Q2 packaging/contrato | sim | hashes, manifests e checker verdes | `PROVEN_CI_HOST` |
| Q3 PRoot ARM64 | lane criada | compilação ARM64/musl em curso | `IN_PROGRESS` |
| Q3 Android NDK | contrato definido | dependências/launcher ausentes | `BLOCKED` |
| T1 bootstrap | contrato corrigido | build bridge observado | `PARTIAL` |
| T2 loader | stub compilável | build debug passou; funcionalidade ausente | `STUB_PROVEN_BUILD_ONLY` |

---

## Próxima ordem operacional

1. concluir artifact PRoot `linux-aarch64 + musl`;
2. restaurar runners do Vectras e executar testes/build no PR #1052;
3. fechar assinatura/gates ARM32 do Termux e publicar APKs/relatórios;
4. importar artifact QEMU pinado no Vectras;
5. validar libc da rootfs;
6. executar ADB ARM64 e ARM32;
7. preencher ledger/SBOM com hashes reais;
8. auditar licenças por arquivo antes de distribuição.
