# ZIPRAF Payload Digest U1 V1

Status: `REFERENCE_IMPLEMENTATION / REMOTE_GATE_PENDING`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`

## 1. Objetivo

U0 localizou bytes reais dentro do ZIP. U1 atribui identidade criptográfica explícita ao span, sem confundir CRC32, layout, execução ou clock.

```text
ZIP entry
→ bounded payload span
→ digest scope
→ digest algorithm
→ digest bytes
→ PageBlock identity
```

## 2. Escopos

```text
STORED_BYTES
  bytes exatos presentes no arquivo ZIP, comprimidos ou não.

LOGICAL_BYTES
  bytes consumidos pelo módulo depois da materialização.
```

Nesta versão, `LOGICAL_BYTES` é computado diretamente somente para `STORE`, pois:

```text
STORE: stored bytes = logical bytes
DEFLATE: logical bytes require bounded decompression
```

Assim, pedir digest lógico de DEFLATE retorna `MATERIALIZATION_REQUIRED`; nunca reutiliza silenciosamente o digest do fluxo comprimido.

## 3. Algoritmos e produtores

### SHA-256

Implementação C portátil, sem heap, com KATs de `empty` e `abc`.

### BLAKE3

O algoritmo não é copiado para o Vectras. O workflow usa o produtor externo pinado:

```text
repository: rafaelmeloreisnovo/BLAKE3
commit: ff6991d8b13f5b4b16dc311b5acc9c63ae835152
C API version: 1.8.2
path: c/blake3.h + portable C backend
```

O checkout por commit imutável impede que `master` altere o resultado sem mudança explícita no contrato.

## 4. Invariantes

```text
CRC32 != content identity
stored digest != logical digest quando há compressão
digest match != executable authorization
digest match != DMA authorization
digest != clock measurement
provider branch != immutable provider identity
```

A identidade mínima continua:

```text
algorithm + scope + digest + logical_size + provider_profile
```

## 5. KAT e falsificadores

O gate testa:

- SHA-256 empty e abc;
- BLAKE3 empty e abc;
- versão C BLAKE3 1.8.2;
- commit externo pinado;
- digest SHA-256 do payload `abc` dentro de um span;
- digest BLAKE3 lógico de uma entrada STORE;
- recusa do digest lógico de DEFLATE sem materializador;
- recusa de range fora do arquivo;
- recusa de algoritmo desconhecido;
- alteração de um bit muda o SHA-256;
- comparação de digest em tempo constante no nível do laço C.

## 6. Limites

```yaml
sha256_payload_stored: IMPLEMENTED
sha256_payload_store_logical: IMPLEMENTED
blake3_payload_stored: EXTERNAL_PINNED_PROVIDER
blake3_payload_store_logical: EXTERNAL_PINNED_PROVIDER
deflate_logical_digest: MATERIALIZATION_REQUIRED
android_runtime: TOKEN_VAZIO
hardware_acceleration: TOKEN_VAZIO
signature_or_authorship: NOT_PROVIDED_BY_DIGEST
claim_allowed: false
```

## R3

```text
F_ok:
  escopos e algoritmos foram separados; SHA-256 é local e BLAKE3 é pinado
  a um produtor externo imutável, com KATs sobre os mesmos payloads.

F_gap:
  resultado remoto, receipts por corpus, DEFLATE materializado,
  Android/Termux e assinatura de manifestos.

F_next:
  executar o gate remoto; depois gerar manifestos por entrada com
  algorithm, scope, digest, size, offset, provider commit e mapping epoch.
```
