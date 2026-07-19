# BLOCKING_GAPS.md
<!-- Atualizado: 2026-07-19 | segunda onda de fechamento -->

Registro canônico dos gaps que impedem a promoção do Vectras VM.

```text
IMPLEMENTED_UNPROVEN
BLOCKED_BY[motivo]
PROVEN
```

Uma alteração de código não é automaticamente build, artefato, instalação, boot ou release.

---

## Estado atual: BETA_BLOCKED

```text
código → build → artefato móvel → integração → instalação → boot VM → teste → prova assinada
```

A cadeia avançou substancialmente:

- QEMU host CI: comprovado;
- QEMU PRoot ARM64/musl: artifact comprovado;
- Termux ARM32/ARM64/universal: comprovado;
- loader stub: comprovado;
- Vectras HEAD: ainda sem runner/build atual;
- dispositivo/ADB: ainda não comprovado.

---

## BG-00: workflows Vectras não chegam ao runner

**Status:** `BLOCKED_BY[GITHUB_ACTIONS_RUNNER_STARTUP]`

Nos heads do PR #1052, os workflows encerraram antes de executar steps de compilação. Em `android-ci`, os jobs de resolução/gate falharam sem lista de steps e o job canônico foi pulado.

Consequências:

- não há log Java/Kotlin/C que atribua falha ao diff;
- não há APK/AAB Vectras recente;
- não há hash de artefato Vectras;
- o estado não pode ser promovido nem rebaixado por inferência.

**Desbloqueio:** restaurar a capacidade de início dos runners/Actions e repetir o HEAD do PR.

---

## BG-01: hashes reais no RELEASE_EVIDENCE_LEDGER

**Status:** `BLOCKED_BY[CURRENT_VECTRAS_GREEN_BUILD_REQUIRED]`

O ledger já contém hashes reais dos componentes QEMU e Termux. Permanecem bloqueados apenas os hashes de APK/AAB do HEAD Vectras, assinatura e relatório ABI do app integrado.

---

## BG-02: testes em dispositivo físico ARM32/ARM64

**Status:** `BLOCKED_BY[ADB_RUNNER_REQUIRED]`

Necessário:

1. ADB instalado e autorizado;
2. dispositivo ARM32, por exemplo Moto E7 Power;
3. dispositivo ARM64;
4. Android 10 e Android 14+;
5. instalação, launch, boot da VM, I/O, logcat e encerramento.

---

## BG-03: assinatura oficial de release

**Status:** `BLOCKED_BY[KEYSTORE_SECRETS_REQUIRED]`

Segredos necessários:

- `VECTRAS_RELEASE_KEYSTORE_BASE64`;
- `VECTRAS_RELEASE_KEY_ALIAS`;
- `VECTRAS_RELEASE_KEY_PASSWORD`;
- `VECTRAS_RELEASE_STORE_PASSWORD`.

Release unsigned/debug não é release oficial.

---

## BG-04: integração e smoke do artifact QEMU móvel

**Status:** `BLOCKED_BY[ARTIFACT_IMPORT_AND_DEVICE_REQUIRED]`

O produtor `qemu_rafaelia` comprovou no run `29696118677`:

```text
runtime.os = linux
runtime.arch = aarch64
runtime.abi = linux-aarch64
runtime.libc = musl
runtime.execution_mode = proot
```

Provas:

- runner ARM64 nativo;
- Alpine 3.22 ARM64/musl;
- três guest targets compilados;
- ELF AArch64 com `/lib/ld-musl-aarch64.so.1`;
- SHA256SUMS e checker verdes;
- artifact e manifestos publicados.

Hashes:

```text
artifact ZIP = e66998db55f77befcf61d1734b06391fcc26e5dd04a8d7bc14764de6a2d840b6
inner tar.gz = e7af6e44304d6ad26463f3d040dc8c5f6a063d8ba247a7af7d8c47415a290fe3
qemu-system-aarch64 = 237e9674562320a9be4a345d6366bd4b12d37f55448ea66abb1ea4e75662786c
qemu-system-i386 = 70a11470fe5a1791261725db47a887ce54159700eb56f2e39e8f89bac550c859
qemu-system-x86_64 = f5632cc9b1264b4ce42ad9675ed83546c2fc41642293145b617162f247c9c654
```

A rootfs canônica instalada pelo caminho interno do app usa o asset `alpine19` em `files/distro`. Portanto, `musl + proot` é a classe coerente com esse fluxo. O wizard externo ainda aponta para tarballs históricos sem SHA pinado e permanece fonte alternativa não canônica.

Faltam:

```text
importação atômica
→ qemu-exec.json no path controlado
→ validação consumer SHA/runtime/libc
→ build Vectras
→ ADB
→ boot/shutdown
```

---

## BG-05: benchmarks ZIPRAF

**Status:** `BLOCKED_BY[DEVICE_EXECUTION_REQUIRED]`

Exigem aparelho real: page faults, RSS, throughput por lane, ARM32/ARM64 e mmap por extent.

---

## BG-06: bootstrap ZIPs e loader.apk

**Status:** `STUB_PROVEN_CI + BLOCKED_BY[FUNCTIONAL_CONTRACT]`

No `termux-app-rafacodephi`:

- ARM32 padrão e NDK 29 verdes;
- APKs ARM32, ARM64 e universal publicados;
- loader stub de 7.965 bytes comprovado;
- package, SDK, `hasCode=false`, assinatura e DEX limitado à classe gerada `R` comprovados;
- SHA-256 do loader: `e5bc3ca105a6b0b04afeaaa0d575ada2dafc4777549e5df92c3dd217c07fe24f`.

Ainda faltam payload funcional, hashes de bootstrap real, consentimento, rollback, atualização e testes instrumentados.

---

## BG-07: SBOM com hashes reais

**Status:** `PARTIAL + BLOCKED_BY[CURRENT_VECTRAS_BUILD]`

A estrutura SPDX 2.3 existe. Os hashes dos componentes externos já são conhecidos; os hashes do APK e blobs efetivamente integrados ao Vectras dependem do build atual.

---

## BG-08: proveniência de libXlorie.so

**Status:** `BLOCKED_BY[AUDIT_REQUIRED]`

Se incluída, requer origem, licença SPDX, recipe, commit e SHA-256.

---

## BG-09: comando QEMU e artifact verificado

**Status:** `IMPLEMENTED_UNPROVEN[CANONICAL_PROOT_PATH]`

O PR #1052 implementa:

- argv direto para QEMU standalone;
- PRoot sem concatenação de argumentos;
- `qemu-exec.json` runtime-aware;
- ABI do aparelho;
- detecção da libc da rootfs;
- path confinado ao artifact root;
- SHA-256 antes da execução;
- rejeição fail-closed quando o manifesto existe e falha.

O produtor e a classe de rootfs agora são coerentes; falta build do consumidor e prova ADB.

---

## BG-10: escopo NAOCOMERCIAL versus GPLv2

**Status:** `BLOCKED_BY[FILE_LEVEL_LICENSE_AUDIT]`

O mapa de quarentena existe, mas a compatibilidade de distribuição deve ser resolvida por arquivo e artifact.

---

## Matriz de fechamento

| Gap | Código | Prova atual | Estado |
|---|---:|---:|---|
| G3 SBOM | sim | estrutura + hashes externos | `PARTIAL` |
| G4 JNI Termux | sim | sem build Vectras atual | `IMPLEMENTED_UNPROVEN` |
| G5 ZIPRAF | sim | testes adicionados; sem dispositivo | `IMPLEMENTED_UNPROVEN` |
| G7 PROJECT_STATE | sim | documental | `PROVEN_DOCUMENTAL` |
| G8 mover fórmulas | sim | path corrigido | `PROVEN_DOCUMENTAL` |
| G9 argv + artifact gate | sim | produtor móvel verde; consumidor sem CI/ADB | `IMPLEMENTED_UNPROVEN` |
| G10 licenças | mapa/quarentena | auditoria incompleta | `PARTIAL` |
| Q1 binários QEMU | sim | três targets verdes no host | `PROVEN_CI_HOST` |
| Q2 packaging/contrato | sim | hashes, manifests e checker verdes | `PROVEN_CI_HOST` |
| Q3 PRoot ARM64 | sim | artifact AArch64/musl/proot verde | `PROVEN_CI_ARTIFACT` |
| Q3 Android NDK | contrato definido | dependências/launcher ausentes | `BLOCKED_OPTIONAL_PATH` |
| T1 bootstrap | contrato corrigido | bridge e APK pipeline comprovados | `PROVEN_DOCUMENTAL_PARTIAL_RUNTIME` |
| T2 loader | stub dedicado | workflow, artifact, hash e contrato verdes | `STUB_PROVEN_CI` |

---

## Próxima ordem operacional

1. importar atomicamente o artifact QEMU PRoot ARM64 no Vectras;
2. restaurar runners do Vectras e executar testes/build no PR #1052;
3. executar ADB ARM64 com boot/shutdown de VM;
4. executar ADB ARM32 para o app/Termux e definir artifact QEMU ARM32 se necessário;
5. preencher ledger/SBOM do APK integrado;
6. implementar contrato funcional do loader;
7. auditar licenças por arquivo antes de distribuição.
