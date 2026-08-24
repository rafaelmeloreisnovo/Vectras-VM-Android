# Bootstrap Extraction Receipt V1

## Objetivo

Fechar uma única fronteira causal observada no dispositivo:

```text
APK asset -> extraction/bootstrap -> <filesDir>
```

Este receipt nasce da observação física em que os assets ARM estavam presentes no APK, mas os executáveis base ainda estavam ausentes no `filesDir`.

## Schema

`vectras.bootstrap-extraction-receipt.v1`

Artefatos append-only:

```text
<filesDir>/evidence/bootstrap-extraction/
  vectras-bootstrap-extraction-<UTC>.json
  vectras-bootstrap-extraction-<UTC>.json.sha256
```

## O que é medido

Antes da tentativa:

- ABIs do dispositivo;
- assets PRoot/Alpine relevantes para essas ABIs;
- tamanho e SHA-256 de cada asset;
- estado de `usr/bin/proot`;
- estado de `distro/bin/busybox`;
- estado de `distro/bin/sh`;
- estado de `distro/usr/bin/env`;
- post-check inicial.

Durante/depois:

- `SetupFeatureCore.startExtractSystemFiles` foi tentado;
- retorno booleano do extractor;
- `SetupFeatureCore.lastErrorLog`;
- exceção inesperada, quando existir;
- presença/tamanho/SHA-256 dos arquivos resultantes;
- bit executável;
- modo observado via `Os.stat`;
- errno do `stat`, quando existir;
- post-check posterior;
- gate separado `base_runtime_materialized`;
- próximo gate causal.

## Contrato de modo

Os executáveis base têm expectativa de modo `0755`. O receipt registra separadamente:

- `expected_executable_mode_octal`: contrato;
- `observed_mode_octal`: observação física.

Não se deve converter expectativa em evidência.

## Fronteira epistemológica

```text
ASSET_PRESENT
  != EXTRACTED
  != EXECUTABLE
  != POST_CHECK_CLEAN
  != VM_BOOT
```

QEMU é deliberadamente excluído desta intervenção. O objetivo é preservar causalidade: primeiro provar ou localizar a falha de materialização PRoot/rootfs; somente depois atacar `QEMU_EXECUTABLE_RUNTIME`.

O receipt mantém `claim_allowed=false` e não promove boot, certificação ou execução física de VM.

## Gate de avanço

O gate base fecha apenas quando:

```text
usr/bin/proot          exists + executable + SHA-256
AND distro/bin/busybox exists + executable + SHA-256
AND distro/bin/sh      exists + executable + SHA-256
AND distro/usr/bin/env exists + executable + SHA-256
AND post-check não contém missing-proot
AND post-check não contém missing-distro-busybox
```

Se isso ocorrer, o próximo gate passa a ser exclusivamente:

`QEMU_EXECUTABLE_RUNTIME`.

Caso contrário, permanece:

`BOOTSTRAP_EXTRACTION`.

## Anti-regressão

A instrumentação de evidência não impede a tentativa de reparo se a inicialização do próprio receipt falhar. Isso mantém duas observações separadas:

1. o extractor funciona ou falha por seu próprio comportamento;
2. o mecanismo de evidência funciona ou falha independentemente.

Misturar essas duas classes faria a instrumentação alterar o fenômeno observado.
