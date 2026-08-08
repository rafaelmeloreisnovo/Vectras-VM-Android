# Bootstrap Asset Provenance Recovery V1

Status: `BETA_BLOCKED_MISSING_BOOTSTRAP_ASSETS`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`  
Release global: `release_allowed=false`

## 1. Diagnóstico observado

O pipeline Android constrói e sincroniza corretamente:

```text
:shell-loader:assembleDebug
→ app/build/generated/bootstrapAssets/bootstrap/loader.apk
```

Mas os quatro payloads arquiteturais obrigatórios não existem no repositório:

```text
app/src/main/assets/bootstrap/arm64-v8a.tar
app/src/main/assets/bootstrap/armeabi-v7a.tar
app/src/main/assets/bootstrap/x86.tar
app/src/main/assets/bootstrap/x86_64.tar
```

Portanto:

```text
loader.apk presente
≠ bootstrap arquitetural completo
≠ APK beta instalável
≠ runtime Android verificado
```

O bloqueio do Android CI é legítimo e não deve ser removido por fallback vazio, arquivo placeholder ou redução do verificador.

## 2. Manifesto de produção

`configs/bootstrap_assets.production.v1.json` registra, para cada ABI:

```text
abi
filename
state
source_uri
source_ref
license_or_provenance
sha256
size_bytes
```

Enquanto os arquivos estiverem ausentes:

```yaml
state: MISSING
source_uri: TOKEN_VAZIO_SOURCE_URI
source_ref: TOKEN_VAZIO_SOURCE_REF
license_or_provenance: TOKEN_VAZIO_LICENSE_OR_PROVENANCE
sha256: TOKEN_VAZIO_SHA256
size_bytes: null
```

Isso transforma ausência em evidência auditável, sem inventar origem ou checksum.

## 3. Estados permitidos

### Estado A — bloqueado

```text
BETA_BLOCKED_MISSING_BOOTSTRAP_ASSETS
```

- os quatro TARs continuam ausentes;
- campos de origem continuam `TOKEN_VAZIO`;
- release e runtime permanecem falsos;
- o gate de proveniência passa porque descreve corretamente o bloqueio;
- o Android build permanece bloqueado.

### Estado B — arquivos verificados, dispositivo ainda não testado

```text
BOOTSTRAP_ASSETS_VERIFIED_NOT_DEVICE_TESTED
```

Exige os quatro arquivos, origem concreta, referência imutável, licença/proveniência, tamanho, SHA-256 e TAR seguro.

### Estado C — dispositivo verificado de forma limitada

```text
DEVICE_BOOTSTRAP_VERIFIED_LIMITED
```

Só pode ser usado depois do estado B e de receipt físico separado. O manifesto V1 continua com `claim_allowed=false` até uma decisão de promoção independente.

## 4. Validador

```bash
python3 tools/bootstrap/validate_bootstrap_assets_manifest.py \
  --manifest configs/bootstrap_assets.production.v1.json \
  --expect blocked
```

Para um conjunto fornecido localmente:

```bash
python3 tools/bootstrap/validate_bootstrap_assets_manifest.py \
  --manifest /caminho/manifest-ready.json \
  --assets-dir /caminho/staging \
  --expect verified
```

O validador rejeita:

- ABI ou filename divergente;
- arquivo ausente;
- hash ou tamanho diferente;
- proveniência vazia ou `TOKEN_VAZIO` no estado verificado;
- TAR ilegível ou vazio;
- caminho absoluto ou `..`;
- links que escapam do TAR;
- device nodes;
- tentativa de usar `loader.apk` como substituto dos TARs;
- promoção de release/runtime/claim no contrato V1.

## 5. Materialização offline

O materializador não faz download:

```bash
python3 tools/bootstrap/materialize_bootstrap_assets.py \
  --manifest manifest-ready.json \
  --staging-dir staging \
  --target-dir app/src/main/assets/bootstrap
```

Sem `--apply`, ele apenas valida e produz plano/receipt. Com `--apply`, copia atomicamente somente os quatro arquivos cujo tamanho, SHA-256 e TAR já passaram.

```text
nenhuma rede
nenhum SHA inferido
nenhuma origem inventada
nenhum arquivo parcial publicado
```

## 6. Fontes aceitáveis

Uma fonte futura pode ser:

1. release versionada de um repositório autorizado;
2. artifact de workflow com commit e receipt preservados;
3. arquivo local produzido no Termux, acompanhado de receita de build, licença e SHA-256;
4. espelho no Drive, desde que a fonte original e o digest permaneçam registrados.

O Drive não substitui a proveniência. Ele pode armazenar o objeto, mas o manifesto ainda deve declarar de onde ele veio e como foi produzido.

## 7. Promoção mínima

```text
ALL_FOUR_FILES_PRESENT
AND SOURCE_URI
AND IMMUTABLE_SOURCE_REF
AND LICENSE_OR_PROVENANCE
AND SHA256_MATCH
AND SIZE_MATCH
AND TAR_READABLE
AND NO_PATH_TRAVERSAL
AND ANDROID_BUILD_PASS
AND DEVICE_BOOTSTRAP_RECEIPT
```

Mesmo após isso:

```text
APK build pass ≠ device runtime pass
Device boot pass ≠ full VM/QEMU release pass
```

## 8. Relação com ZIPRAF

Os TARs podem futuramente ser inventariados pelo U2 e identificados pelo U1, mas isso não resolve sua autoridade:

```text
U0 span + U1 digest + U2 manifest
≠ source provenance
≠ license
≠ execution authorization
```

A proveniência do bootstrap é uma camada própria.

## R3

```text
F_ok:
  a ausência dos quatro TARs foi transformada em contrato explícito,
  validador, schema, testes adversariais e materializador offline.

F_gap:
  fonte real, licença, SHA-256, tamanho e arquivos para quatro ABIs;
  Android build final e receipt físico de dispositivo.

F_next:
  localizar ou produzir cada TAR por fonte autorizada, preencher um
  manifesto ready e executar primeiro dry-run, depois build e dispositivo.
```
