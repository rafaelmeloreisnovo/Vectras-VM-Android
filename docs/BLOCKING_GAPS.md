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
código → build → artefato → instalação → boot VM → teste → prova assinada
```

A cadeia ainda está aberta. Há componentes substanciais e correções implementadas, mas não existe prova completa do HEAD em dispositivo.

---

## BG-00: workflows Vectras não chegam ao runner

**Status:** `BLOCKED_BY[GITHUB_ACTIONS_RUNNER_STARTUP]`

Nos heads do PR #1052, os workflows `android-ci`, `host-ci`, `Shell-Loader Smoke`, `APK Wizard`, `audit-benchmark-contract`, `Validate Formula` e o orquestrador encerraram em falha antes de executar steps de compilação. Em `android-ci`, os jobs de resolução/gate falharam sem lista de steps e o job canônico foi pulado.

Consequências:

- não há log Java/Kotlin/C que atribua falha ao diff;
- não há APK/AAB recente;
- não há hash de artefato;
- o estado não pode ser promovido nem rebaixado por inferência.

**Desbloqueio:** restaurar a capacidade de início dos runners/Actions e repetir o HEAD do PR.

---

## BG-01: hashes reais no RELEASE_EVIDENCE_LEDGER

**Status:** `BLOCKED_BY[CURRENT_GREEN_BUILD_REQUIRED]`

`docs/RELEASE_EVIDENCE_LEDGER.md` agora contém evidências negativas reais das execuções falhas e mantém o template separado. SHA-256 de APK/AAB, perfil ABI e assinatura continuam bloqueados até um build atual materializar os arquivos.

**Desbloqueio:** executar `android-ci.yml` no commit corrente, publicar o artefato e registrar os hashes do arquivo final.

---

## BG-02: testes em dispositivo físico ARM32/ARM64

**Status:** `BLOCKED_BY[ADB_RUNNER_REQUIRED]`

Necessário:

1. ADB instalado e autorizado;
2. dispositivo ARM32, por exemplo Moto E7 Power;
3. dispositivo ARM64;
4. Android 10 e Android 14+;
5. captura de logcat, instalação, launch e encerramento.

O workflow `device-runtime-smoke.yml` continua com ADB ausente e instalação/launch pendentes.

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

## BG-04: smoke de boot de VM

**Status:** `BLOCKED_BY[QEMU_ARTIFACT_AND_DEVICE_REQUIRED]`

A prova exige:

```text
QEMU artifact validado
→ APK integrado
→ instalação ADB
→ VM criada
→ firmware carregado
→ guest iniciado
→ display/rede observados
→ shutdown limpo
→ logs e hashes registrados
```

O `qemu_rafaelia` já possui job multi-target e diagnóstico persistente. A produção dos três binários ainda precisa terminar verde antes da integração Android.

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

- o contrato de origem foi corrigido para a task real `:app:generateRafcodephiBootstraps`;
- o gerador padrão é classificado como `BOOTSTRAP_BRIDGE_ONLY`;
- existe módulo `:loader` que produz um APK stub sem código;
- o build de debug do head do PR #282 passou antes do gate ARM32.

Ainda faltam:

- payload pinado;
- SHA-256/BLAKE3;
- comportamento funcional de instalação;
- consentimento, rollback e atualização;
- assinatura e testes instrumentados.

---

## BG-07: SBOM com hashes reais

**Status:** `BLOCKED_BY[CURRENT_GREEN_BUILD_REQUIRED]`

A estrutura SPDX 2.3 existe. Hashes de APK, blobs e bibliotecas só podem ser preenchidos após materialização do build canônico.

---

## BG-08: proveniência de libXlorie.so

**Status:** `BLOCKED_BY[AUDIT_REQUIRED]`

Se incluída no APK, requer:

- origem/upstream;
- licença SPDX;
- recipe de build;
- commit de origem;
- SHA-256 do binário.

Sem isso, permanece em quarentena para distribuição pública.

---

## BG-09: migração integral do comando QEMU para argv

**Status:** `IMPLEMENTED_UNPROVEN[CANONICAL_STANDALONE_PATH]`

O PR #1052 implementa:

- `QemuArgvContract.toProcessArgv()`;
- `ProotCommandBuilder.buildCommand(List<String>)`;
- `QemuDirectLauncher`;
- despacho direto no `MainService` para executável `qemu-system-*` standalone;
- preparação fixa de PulseAudio sem texto externo;
- testes para espaços e metacaracteres.

Limite preservado:

- wrappers opcionais `xterm -e bash -c`/`bash -c` ainda seguem o caminho de compatibilidade;
- esses wrappers não são falsamente interpretados como executável direto;
- a migração deles requer contrato próprio de argv/terminal.

**Gate:** build unitário + assembleDebug + smoke de boot antes de marcar `PROVEN`.

---

## BG-10: escopo NAOCOMERCIAL versus GPLv2

**Status:** `BLOCKED_BY[FILE_LEVEL_LICENSE_AUDIT]`

`legal/LEGAL_SCOPE_MAP.yaml` formaliza a quarentena, mas não resolve por si só eventual incompatibilidade de distribuição. É necessário separar, arquivo por arquivo:

- código derivado GPLv2;
- código autoral independente;
- documentação/licença comercial separada;
- artefatos que não podem ser combinados/distribuídos no mesmo produto.

---

## Matriz de fechamento

| Gap | Código | Prova atual | Estado |
|---|---:|---:|---|
| G3 SBOM | sim | estrutura somente | `IMPLEMENTED_UNPROVEN` |
| G4 JNI Termux | sim | sem build Vectras atual | `IMPLEMENTED_UNPROVEN` |
| G5 ZIPRAF | sim | testes adicionados; sem dispositivo | `IMPLEMENTED_UNPROVEN` |
| G7 PROJECT_STATE | sim | documental | `PROVEN_DOCUMENTAL` |
| G8 mover fórmulas | sim | path corrigido | `PROVEN_DOCUMENTAL` |
| G9 argv QEMU | caminho standalone | sem CI/ADB | `IMPLEMENTED_UNPROVEN` |
| G10 licenças | mapa/quarentena | auditoria incompleta | `PARTIAL` |
| Q1–Q3 QEMU | job/scripts/fix-em-série | smoke verde; binários em nova rodada | `IMPLEMENTED_UNPROVEN` |
| T1 bootstrap | contrato corrigido | build bridge observado | `PARTIAL` |
| T2 loader | stub compilável | build debug passou; funcionalidade ausente | `STUB_PROVEN_BUILD_ONLY` |

---

## Próxima ordem operacional

1. obter CI verde do `qemu_rafaelia` com três `qemu-system-*` e manifests;
2. restaurar o início dos runners do Vectras e executar testes/build no PR #1052;
3. ler o artifact nomeado dos gates ARM32 do Termux e corrigir a assertiva real;
4. integrar o artifact QEMU ao APK Vectras;
5. executar ADB ARM32/ARM64;
6. preencher ledger e SBOM com hashes reais;
7. auditar licenças por arquivo antes de distribuição.
