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

**Status:** `PARCIAL` (2026-07-21) — libXlorie.so SHA-256 reais adicionados; APK/AAB ainda `BLOCKED_BY[CI_BUILD_REQUIRED]`

O arquivo `sbom/SBOM.spdx.json` agora contem entradas reais para os 4 artefatos libXlorie.so
(arm64-v8a, armeabi-v7a, x86, x86_64) com SHA-256 computados 2026-07-21.

Os hashes de APK/AAB e OVMF blobs ainda requerem:
1. Build CI completo que produza os artefatos
2. `sha256sum` dos artefatos gerados
3. Atualizacao dos campos `checksums` no SBOM

---

### BG-08: Proveniencia de libXlorie.so

**Status:** `PARCIAL` (2026-07-21) — SHA-256 e identidade computados; source-url e licenca `BLOCKED_BY[AUDIT_REQUIRED]`

Progresso 2026-07-21: `libXlorie.so` presente em todos os 4 ABIs identificada como biblioteca
X11/EGL-pixman Android display server. SHA-256 computados e registrados em
`ASSET_PROVENANCE_REGISTER.csv` e `sbom/SBOM.spdx.json`.

Remanescente (requer Rafael):
- Origem exata (repositorio upstream ou build propria — possivel relacao com termux-x11)
- Licenca SPDX
- Recipe de build (como compilar a partir de fontes)

BuildID sha1: `fa8b07c20a74960492bbf454821fa0a0e7f08c5e` (arm64-v8a).
Biblioteca permanece em quarentena para distribuicao publica ate confirmacao de licenca.

---

### BG-09: libXlorie.so e Alpine/rootfs -- proveniencia total ausente

**Status:** `PARCIAL` (2026-07-21) — SHA-256 computados; source-url e licenca `BLOCKED_BY[AUDIT_REQUIRED]`

Progresso 2026-07-21:
1. **`app/src/main/jniLibs/*/libXlorie.so`** -- SHA-256 computados para todos os 4 ABIs (arm64, armv7, x86, x86_64); binario identificado como X11/EGL-pixman Android display library. Entradas `blocked-sha256-known` em `ASSET_PROVENANCE_REGISTER.csv`. Licenca e source-url pendentes de Rafael.
2. **Alpine/rootfs tarballs** -- `ASSET_PROVENANCE_REGISTER.csv` tem entradas de guarda glob. Tarballs nao commitados no repositorio (gitignored); entradas serao preenchidas quando Rafael adicionar os assets.

**Desbloqueio remanescente:** Rafael confirmar origem de `libXlorie.so` (possivel relacao com termux-x11 ou X.Org no Android) e fornecer source-url + licenca SPDX.

---

### BG-10: `engine/rmr/**` sem cabecalhos SPDX -- bloqueia qualquer distribuicao

**Status:** `RESOLVED` (2026-07-21 — gap G15 FECHADO)

Cabecalhos SPDX adicionados a todos os 87 arquivos (`src/*.c`, `src/*.h`, `interop/*.S`).
CI step `legal-compliance-gate.yml` verifica cobertura em cada push.

---

### BG-11: GPGVerifier em RafGitTools retorna sempre valido

**Status:** `RESOLVED` (2026-07-21 — gap RG9 FECHADO)

`GPGVerifier` refatorado: verificacao real via BouncyCastle; commits sem chave publica
disponivel sao marcados `UNVERIFIED` (comportamento honesto, sem bypass).

---

### BG-12: Gate legal de CI ausente -- LICENSES_REGISTER nao executado por nenhum workflow

**Status:** `RESOLVED` (2026-07-21 — gaps G22/X6 FECHADOS)

`legal-compliance-gate.yml` implementado: verifica SPDX em `src/` + `interop/`,
valida `ASSET_PROVENANCE_REGISTER.csv`, reporta `TOKEN_VAZIO`/`QUARANTINE` em cada push.

---

### BG-14: CI runners esgotados -- workflows existem mas nao executam

**Status:** `BLOCKED_BY[CI_CREDITS_REQUIRED]`

Tres repos do ecossistema mostram `runner_id=0` com jobs concluidos em <3 segundos:
- `Vectras-VM-Android` (PR #1060): todos os checks (build-apk-wizard, host-engine, ban-binaries, etc.)
- `RafGitTools` (PR #289): todos os checks (build devDebug, CodeQL, Secret Scanning, etc.)
- `Mapa` (PR #40): Validate Repository Structure

**Diagnostico:** Credito GitHub Actions esgotado para a conta `rafaelmeloreisnovo`.
Os workflows estao corretos -- o codigo passa localmente em todos os steps testados.

**Desbloqueio:** Uma das opcoes abaixo:
1. Recarregar credito GitHub Actions (plano pago ou minutos adicionais)
2. Configurar runner auto-hospedado via `Settings -> Actions -> Runners`
3. Aguardar reset mensal de minutos gratuitos (primeiro dia do mes)

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

Ver `docs/ALL_GAPS_REGISTRY.md` para o registro exaustivo de 67 gaps
(incluindo 40+ omitidos em auditorias anteriores) com status, prioridade e proximas acoes.
