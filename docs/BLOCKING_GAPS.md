# BLOCKING_GAPS.md
<!-- Atualizado: 2026-07-19 -- revisao 2: gaps BG-09 a BG-13 adicionados (omitidos anteriormente) -->

Documento de registro explicito dos gaps que impedem o fechamento completo
da cadeia de prova sem recursos externos (hardware, segredos CI, dispositivos fisicos).

Substitui o estado `TOKEN_VAZIO` por `BLOCKED_BY[motivo]` -- mais informativo
e rastreavel como tarefa de infraestrutura, nao como falha de codigo.

---

## Estado atual: BETA_BLOCKED

O projeto possui codigo substancial, arquitetura documentada e pipeline CI sofisticado.
O bloqueio remanescente e a **cadeia de prova** -- nao o codigo em si:

```
codigo -> build -> artefato -> instalacao -> boot VM -> teste -> prova assinada
```

---

## Gaps Bloqueados por Hardware/Infraestrutura

### BG-01: SHA-256 reais no RELEASE_EVIDENCE_LEDGER

**Status:** `BLOCKED_BY[CI_BUILD_REQUIRED]`

O arquivo `docs/RELEASE_EVIDENCE_LEDGER.md` esta estruturado corretamente mas
contem apenas linhas de exemplo. SHA-256 do APK/AAB, perfil ABI e data de build
so podem ser preenchidos apos um CI run completo que produza artefatos reais.

**Desbloqueio:** Executar `android-ci.yml` com lane `full_debug` ou `release_gate`
em um runner com segredos configurados. O CI job de `build` ja produz e publica
artefatos; basta executar e copiar os valores para o ledger.

---

### BG-02: Testes em dispositivo fisico ARM32/ARM64

**Status:** `BLOCKED_BY[ADB_RUNNER_REQUIRED]`

O arquivo `.github/workflows/device-runtime-smoke.yml` existe mas retorna:
- ADB: missing
- Install: pending
- Launch: pending
- Estado: `DEVICE_PENDING`

**Desbloqueio:** Provisionar um runner auto-hospedado com:
1. ADB instalado e autorizado
2. Dispositivo ARM32 (ex.: Moto E7 Power) conectado via USB
3. Dispositivo ARM64 (ex.: Realme ou equivalente) conectado via USB
4. Android 10 e Android 14+

Referencia de workflow: `.github/workflows/moto-e7-arm32-beta.yml` ja define
a estrutura esperada para ARM32.

---

### BG-03: Assinatura oficial de release

**Status:** `BLOCKED_BY[KEYSTORE_SECRETS_REQUIRED]`

O pipeline `.github/workflows/release-dual-track.yml` e `sign-release.yml`
estao implementados e funcionais, mas requerem:
- `VECTRAS_RELEASE_KEYSTORE_BASE64`
- `VECTRAS_RELEASE_KEY_ALIAS`
- `VECTRAS_RELEASE_KEY_PASSWORD`
- `VECTRAS_RELEASE_STORE_PASSWORD`

Esses segredos devem ser configurados em Settings -> Secrets -> Actions
no repositorio ou na organizacao.

**Nota de seguranca:** Segredos de keystore NUNCA devem ser commitados
no repositorio. O pipeline ja usa `${{ secrets.* }}` corretamente.

---

### BG-04: Smoke test de boot de VM em dispositivo

**Status:** `BLOCKED_BY[QEMU_BINARY_AND_DEVICE_REQUIRED]`

Para verificar que o QEMU realmente inicializa uma VM guest em um dispositivo
Android fisico, e necessario:
1. APK compilado com QEMU binary embarcado (via qemu_rafaelia artifact)
2. Dispositivo com ADB
3. Imagem de VM minima (Alpine ou similar)
4. Runner que execute logcat e parse a saida de boot

Sequencia de verificacao documentada:
```
APK instalado -> app iniciado -> VM criada -> QEMU executado -> firmware carregado
-> guest iniciado -> display funcional -> shutdown limpo -> logs + hashes registrados
```

---

### BG-05: Benchmarks de performance ZIPRAF

**Status:** `BLOCKED_BY[DEVICE_EXECUTION_REQUIRED]`

Os claims de performance do `ZiprafDirectRuntime.kt` (mmap direto, cache windows
L1/L2, lane routing por core) requerem medicao em hardware real para:
- Comparacao mmap extent vs FileChannel.read convencional
- Page faults cold/warm
- RSS e GC durante operacoes de janela
- Throughput por lane em ARM32 vs ARM64

Workflow de referencia: `.github/workflows/audit-benchmark-contract.yml`

---

### BG-06: Bootstrap ZIPs e loader.apk verificaveis

**Status:** `BLOCKED_BY[BUILD_ARTIFACTS_REQUIRED]` (ver termux-app-rafacodephi)

Os arquivos:
- `bootstrap-aarch64.zip`
- `bootstrap-arm.zip`
- `bootstrap-i686.zip`
- `bootstrap-x86_64.zip`
- `loader.apk`

Sao build artifacts que devem ser gerados ou baixados de uma fonte pinada
com SHA-256 verificado. Nao estao versionados no Git por design (correto),
mas a fonte de geracao e os hashes esperados devem estar documentados.

Ver: `termux-app-rafacodephi/docs/BOOTSTRAP_SOURCE_CONTRACT.md`

---

### BG-07: SBOM com hashes reais de binarios

**Status:** `BLOCKED_BY[CI_BUILD_REQUIRED]`

O arquivo `sbom/SBOM.spdx.json` foi criado com campos `"checksumValue": "NOASSERTION"`
para os binarios (APK, OVMF blobs, libXlorie.so se presente).

Os hashes reais so podem ser calculados apos:
1. Build CI completo que produza os artefatos
2. `sha256sum` dos artefatos gerados
3. Atualizacao dos campos `checksums` no SBOM

---

### BG-08: Proveniencia de libXlorie.so

**Status:** `BLOCKED_BY[AUDIT_REQUIRED]`

Se `libXlorie.so` for incluida no APK, requer:
- Origem (repositorio upstream ou build propria)
- Licenca SPDX
- Recipe de build (como compilar a partir de fontes)
- SHA-256 do binario

Sem isso, a biblioteca esta em quarentena para fins de distribuicao publica.

---

### BG-09: libXlorie.so e Alpine/rootfs -- proveniencia total ausente

**Status:** `BLOCKED_BY[AUDIT_REQUIRED]`

Identificado em `LICENSES_REGISTER.md`. Dois grupos de binarios distribuidos sem registo:

1. **`app/src/main/jniLibs/*/libXlorie.so`** (arm64-v8a + armeabi-v7a) -- origem exata, build script, licenca SPDX e SHA-256 TOKEN_VAZIO. Biblioteca nao pode entrar em release sem esses dados.
2. **Alpine/rootfs tarballs** -- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` existe mas esta completamente vazio (zero linhas de conteudo). Cada tarball incluido no APK ou empacotado junto precisa de upstream URL + licenca + hash.

**Desbloqueio:** Identificar origem de `libXlorie.so` (fork? build propria? upstream?) e preencher `ASSET_PROVENANCE_REGISTER.csv` com uma linha por binario distribuido.

---

### BG-10: `engine/rmr/**` sem cabecalhos SPDX -- bloqueia qualquer distribuicao

**Status:** `BLOCKED_BY[AUTHOR_DECISION_REQUIRED]`

O diretorio `engine/rmr/` contem codigo autoral de Rafael mas os arquivos nao possuem cabecalhos SPDX. `LICENSES_REGISTER.md` registra explicitamente:

> `engine/rmr/**` -- TOKEN_VAZIO juridico ate cabecalhos SPDX finais -- nao distribuivel como licenca fechada

**Desbloqueio:** Rafael precisa decidir a licenca (GPL-2.0-only e consistente com o restante do projeto) e adicionar cabecalhos `SPDX-License-Identifier: GPL-2.0-only` em cada arquivo do diretorio.

---

### BG-11: GPGVerifier em RafGitTools retorna sempre valido

**Status:** `BLOCKED_BY[IMPLEMENTATION_REQUIRED]`

Em `rafaelmeloreisnovo/RafGitTools`, o componente `GPGVerifier` retorna sempre `valido` independente da assinatura -- bypass total da verificacao de integridade de commits. Identificado em analise de audit (`bug/RAFGITTOOLS_BUG_HUNTER_v6.md`).

**Desbloqueio:** Implementar verificacao GPG real usando BouncyCastle/KeyStore ou, alternativamente, marcar commits como `UNVERIFIED` quando a chave publica nao esta disponivel (comportamento honesto).

---

### BG-12: Gate legal de CI ausente -- LICENSES_REGISTER nao executado por nenhum workflow

**Status:** `BLOCKED_BY[CI_IMPLEMENTATION_REQUIRED]`

`LICENSES_REGISTER.md` define criterios obrigatorios:
- Falhar se componente de release sem licenca
- Falhar se licenca incompativel
- Falhar se arquivo em quarentena no pacote

Nenhum dos 23+ workflows atualmente implementa esses checks. O gate existe como documento mas nao como verificacao automatica.

**Desbloqueio:** Adicionar step de compliance check (ex.: usando `spdx-tools`, `licensee` ou script proprio) ao workflow `android-ci.yml` antes do step de packaging.

---

### BG-13: 51 arquivos `.S` assembly em `_incoming/pending/` -- TBD desde 2026-06

**Status:** `BLOCKED_BY[AUTHOR_REVIEW_REQUIRED]`

`docs/TODO_INCOMING_PENDING.md` lista 51 arquivos de assembly (`rafaelia_*.S`) aguardando revisao de contrato de registradores. Nenhum foi promovido ao build desde junho de 2026. Muitos implementam rotinas criticas (sincronizacao, hashing, geometria toroidal).

**Desbloqueio:** Rafael precisa revisar contrato de registradores de cada arquivo e promover (com testes) ou arquivar definitivamente.

---

## Itens Resolvidos por Este PR (2026-07-19)

| Gap | Resolucao |
|-----|-----------|
| G5: ZiprafDirectRuntime mapeava arquivo inteiro | FIXED: mmap agora usa `extent.payloadOffset, extent.payloadSize` |
| G5: Sem parser ZIP estrutural | FIXED: `parseStoredExtent()` le EOCD + CD + local header |
| G5: Testes insuficientes | FIXED: 12 novos casos de teste adicionados |
| G4: termux.c em _incoming/ | FIXED: promovido a `app/src/main/cpp/termux_jni.c` + CMakeLists |
| G3: Sem SBOM | PARTIAL: `sbom/SBOM.spdx.json` criado com estrutura SPDX 2.3 (hashes requerem BG-07) |
| G10: NAOCOMERCIAL sem decisao | PARTIAL: quarentena formalizada em `legal/LEGAL_SCOPE_MAP.yaml` |
| G7: PROJECT_STATE desatualizado | FIXED: sincronizado para 2026-07-19 |
| Q1/Q2: qemu_rafaelia CI sem binarios | FIXED: scripts de packaging + job adicionados |
| T1: Bootstrap ZIPs sem contrato | FIXED: `BOOTSTRAP_SOURCE_CONTRACT.md` criado |

---

## Proxima Acao Prioritaria

1. Provisionar runner com ADB -> desbloqueia BG-02 e BG-04
2. Configurar keystore secrets -> desbloqueia BG-03
3. Executar `android-ci.yml` no HEAD atual -> desbloqueia BG-01 e BG-07
4. Auditar NAOCOMERCIAL/ arquivo por arquivo -> desbloqueia BG-08 parcialmente
5. Identificar origem de `libXlorie.so` -> desbloqueia BG-09
6. Rafael define licenca de `engine/rmr/**` e adiciona SPDX headers -> desbloqueia BG-10
7. Implementar GPGVerifier real em RafGitTools -> desbloqueia BG-11
8. Adicionar step de license gate ao CI -> desbloqueia BG-12
9. Revisar 51 arquivos `.S` e promover ou arquivar -> desbloqueia BG-13

## Referencia completa de todos os gaps

Ver `docs/ALL_GAPS_REGISTRY.md` para o registro exaustivo de 58 gaps
(incluindo 31 omitidos em auditorias anteriores) com status, prioridade e proximas acoes.
