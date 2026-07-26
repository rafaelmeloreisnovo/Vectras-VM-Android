# C05 — Freestanding Final-Link Closure Receipt — 2026-07-26

**Base congelada:** `a29392e65948463ab9cb6dbfefe64eb060e23a07`  
**Estado inicial:** `EXECUTION_PENDING`  
**Claim:** `claim_allowed=false`

## Finalidade

Consolidar as provas de link final freestanding já produzidas pela Vectras em um único recibo canônico do Ciclo 5, sem duplicar o probe existente e sem promover link NDK a runtime físico.

## Provas de entrada

O workflow `.github/workflows/cmake-language-link-contract.yml` produz três manifests independentes:

- host `host-x86_64`;
- Android `armeabi-v7a`;
- Android `arm64-v8a`.

Cada manifest deve seguir `vectra.freestanding-link-probe.v1` e registrar:

- commit exato;
- ABI exata;
- comandos efetivos de compilação, archive e link;
- SHA-256 e BLAKE3 do ELF;
- SHA-256 e BLAKE3 do map;
- duas builds comparadas;
- símbolo de entrada controlado;
- witness do archive freestanding;
- símbolos indefinidos;
- bibliotecas `NEEDED`;
- símbolos proibidos;
- identidade de compiler, linker, readelf, nm, objdump e b3sum.

## Agregador

```sh
python3 tools/ci/aggregate_freestanding_ndk_receipts.py \
  --host artifacts/downloaded/host/manifest.json \
  --arm32 artifacts/downloaded/armeabi-v7a/manifest.json \
  --arm64 artifacts/downloaded/arm64-v8a/manifest.json \
  --output artifacts/c05-closure/receipt.json \
  --commit "$SOURCE_COMMIT"
```

Schema de saída:

```text
vectra.c05-freestanding-closure.v1
```

## Condições obrigatórias

O recibo somente retorna `PASS` quando os três manifests:

1. existem;
2. possuem schema esperado;
3. apontam para o mesmo commit solicitado;
4. usam ABI esperada;
5. reportam `PASS`;
6. têm SHA-256 e BLAKE3 válidos;
7. comprovam reprodutibilidade binária;
8. confirmam consumo do archive;
9. confirmam entry point controlado;
10. não possuem símbolos proibidos;
11. não possuem bibliotecas `NEEDED`;
12. não possuem símbolos indefinidos inesperados.

Ausência de qualquer manifest gera `INCOMPLETE`. Contradição ou check inválido gera `FAIL`.

## Testes do agregador

`tests/test_aggregate_freestanding_ndk_receipts.py` cobre:

- três manifests coerentes → `PASS`;
- ARM32 ausente → `INCOMPLETE`;
- commit ARM64 divergente → `FAIL`;
- BLAKE3 vazio no ARM32 → `FAIL`.

## Workflow consolidado

O job `c05-closure-receipt`:

1. depende dos probes host e Android;
2. baixa os três artifacts;
3. agrega os manifests;
4. publica o recibo mesmo quando incompleto ou falho;
5. falha o job quando o agregador não retorna `PASS`.

## Fronteira epistemológica

Uma execução positiva pode promover somente:

```yaml
host_final_link: VERIFIED_BY_EXECUTION
arm32_ndk_final_link: VERIFIED_BY_EXECUTION
arm64_ndk_final_link: VERIFIED_BY_EXECUTION
```

Permanece obrigatório:

```yaml
device_runtime: TOKEN_VAZIO
apk_install: TOKEN_VAZIO
termux_dispatch: TOKEN_VAZIO
guest_boot: TOKEN_VAZIO
performance_claim: FORBIDDEN_OUT_OF_SCOPE
claim_allowed: false
```

## Falsificadores

- manifest ausente;
- schema inesperado;
- commit divergente;
- ABI divergente;
- resultado do probe diferente de `PASS`;
- SHA-256 ou BLAKE3 inválido;
- binário não reproduzível;
- símbolo indefinido inesperado;
- biblioteca dinâmica necessária;
- símbolo proibido presente;
- entry point divergente;
- archive witness ausente.

## Gate de fechamento

O C05 permanece `EXECUTION_PENDING` enquanto não houver receipt observado e artifact publicado. A existência do agregador, dos testes e do workflow prova implementação da cadeia de custódia, não prova que os três links foram executados.