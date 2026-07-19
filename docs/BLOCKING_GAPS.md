# BLOCKING_GAPS.md
<!-- Atualizado: 2026-07-19 -->

Documento de registro explícito dos gaps que impedem o fechamento completo
da cadeia de prova sem recursos externos (hardware, segredos CI, dispositivos físicos).

Substitui o estado `TOKEN_VAZIO` por `BLOCKED_BY[motivo]` — mais informativo
e rastreável como tarefa de infraestrutura, não como falha de código.

---

## Estado atual: BETA_BLOCKED

O projeto possui código substancial, arquitetura documentada e pipeline CI sofisticado.
O bloqueio remanescente é a **cadeia de prova** — não o código em si:

```
código → build → artefato → instalação → boot VM → teste → prova assinada
```

---

## Gaps Bloqueados por Hardware/Infraestrutura

### BG-01: SHA-256 reais no RELEASE_EVIDENCE_LEDGER

**Status:** `BLOCKED_BY[CI_BUILD_REQUIRED]`

O arquivo `docs/RELEASE_EVIDENCE_LEDGER.md` está estruturado corretamente mas
contém apenas linhas de exemplo. SHA-256 do APK/AAB, perfil ABI e data de build
só podem ser preenchidos após um CI run completo que produza artefatos reais.

**Desbloqueio:** Executar `android-ci.yml` com lane `full_debug` ou `release_gate`
em um runner com segredos configurados. O CI job de `build` já produz e publica
artefatos; basta executar e copiar os valores para o ledger.

---

### BG-02: Testes em dispositivo físico ARM32/ARM64

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

Referência de workflow: `.github/workflows/moto-e7-arm32-beta.yml` já define
a estrutura esperada para ARM32.

---

### BG-03: Assinatura oficial de release

**Status:** `BLOCKED_BY[KEYSTORE_SECRETS_REQUIRED]`

O pipeline `.github/workflows/release-dual-track.yml` e `sign-release.yml`
estão implementados e funcionais, mas requerem:
- `VECTRAS_RELEASE_KEYSTORE_BASE64`
- `VECTRAS_RELEASE_KEY_ALIAS`
- `VECTRAS_RELEASE_KEY_PASSWORD`
- `VECTRAS_RELEASE_STORE_PASSWORD`

Esses segredos devem ser configurados em Settings → Secrets → Actions
no repositório ou na organização.

**Nota de segurança:** Segredos de keystore NUNCA devem ser commitados
no repositório. O pipeline já usa `${{ secrets.* }}` corretamente.

---

### BG-04: Smoke test de boot de VM em dispositivo

**Status:** `BLOCKED_BY[QEMU_BINARY_AND_DEVICE_REQUIRED]`

Para verificar que o QEMU realmente inicializa uma VM guest em um dispositivo
Android físico, é necessário:
1. APK compilado com QEMU binary embarcado (via qemu_rafaelia artifact)
2. Dispositivo com ADB
3. Imagem de VM mínima (Alpine ou similar)
4. Runner que execute logcat e parse a saída de boot

Sequência de verificação documentada:
```
APK instalado → app iniciado → VM criada → QEMU executado → firmware carregado
→ guest iniciado → display funcional → shutdown limpo → logs + hashes registrados
```

---

### BG-05: Benchmarks de performance ZIPRAF

**Status:** `BLOCKED_BY[DEVICE_EXECUTION_REQUIRED]`

Os claims de performance do `ZiprafDirectRuntime.kt` (mmap direto, cache windows
L1/L2, lane routing por core) requerem medição em hardware real para:
- Comparação mmap extent vs FileChannel.read convencional
- Page faults cold/warm
- RSS e GC durante operações de janela
- Throughput por lane em ARM32 vs ARM64

Workflow de referência: `.github/workflows/audit-benchmark-contract.yml`

---

### BG-06: Bootstrap ZIPs e loader.apk verificáveis

**Status:** `BLOCKED_BY[BUILD_ARTIFACTS_REQUIRED]` (ver termux-app-rafacodephi)

Os arquivos:
- `bootstrap-aarch64.zip`
- `bootstrap-arm.zip`
- `bootstrap-i686.zip`
- `bootstrap-x86_64.zip`
- `loader.apk`

São build artifacts que devem ser gerados ou baixados de uma fonte pinada
com SHA-256 verificado. Não estão versionados no Git por design (correto),
mas a fonte de geração e os hashes esperados devem estar documentados.

Ver: `termux-app-rafacodephi/docs/BOOTSTRAP_SOURCE_CONTRACT.md`

---

### BG-07: SBOM com hashes reais de binários

**Status:** `BLOCKED_BY[CI_BUILD_REQUIRED]`

O arquivo `sbom/SBOM.spdx.json` foi criado com campos `"checksumValue": "NOASSERTION"`
para os binários (APK, OVMF blobs, libXlorie.so se presente).

Os hashes reais só podem ser calculados após:
1. Build CI completo que produza os artefatos
2. `sha256sum` dos artefatos gerados
3. Atualização dos campos `checksums` no SBOM

---

### BG-08: Proveniência de libXlorie.so

**Status:** `BLOCKED_BY[AUDIT_REQUIRED]`

Se `libXlorie.so` for incluída no APK, requer:
- Origem (repositório upstream ou build própria)
- Licença SPDX
- Recipe de build (como compilar a partir de fontes)
- SHA-256 do binário

Sem isso, a biblioteca está em quarentena para fins de distribuição pública.

---

## Itens Resolvidos por Este PR (2026-07-19)

| Gap | Resolução |
|-----|-----------|
| G5: ZiprafDirectRuntime mapeava arquivo inteiro | FIXED: mmap agora usa `extent.payloadOffset, extent.payloadSize` |
| G5: Sem parser ZIP estrutural | FIXED: `parseStoredExtent()` lê EOCD + CD + local header |
| G5: Testes insuficientes | FIXED: 12 novos casos de teste adicionados |
| G4: termux.c em _incoming/ | FIXED: promovido a `app/src/main/cpp/termux_jni.c` + CMakeLists |
| G3: Sem SBOM | PARTIAL: `sbom/SBOM.spdx.json` criado com estrutura SPDX 2.3 (hashes requerem BG-07) |
| G10: NAOCOMERCIAL sem decisão | PARTIAL: quarentena formalizada em `legal/LEGAL_SCOPE_MAP.yaml` |
| G7: PROJECT_STATE desatualizado | FIXED: sincronizado para 2026-07-19 |
| Q1/Q2: qemu_rafaelia CI sem binários | FIXED: scripts de packaging + job adicionados |
| T1: Bootstrap ZIPs sem contrato | FIXED: `BOOTSTRAP_SOURCE_CONTRACT.md` criado |

---

## Próxima Ação Prioritária

1. Provisionar runner com ADB → desbloqueia BG-02 e BG-04
2. Configurar keystore secrets → desbloqueia BG-03
3. Executar `android-ci.yml` no HEAD atual → desbloqueia BG-01 e BG-07
4. Auditar NAOCOMERCIAL/ arquivo por arquivo → desbloqueia BG-08 parcialmente
