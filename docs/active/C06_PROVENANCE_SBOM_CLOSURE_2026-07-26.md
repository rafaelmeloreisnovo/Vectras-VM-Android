# C06 — Provenance, SBOM and Quarantine Closure — 2026-07-26

**Base congelada:** `a29392e65948463ab9cb6dbfefe64eb060e23a07`  
**Estado inicial:** `EXECUTION_PENDING`  
**Claim:** `claim_allowed=false`

## Finalidade

Gerar um receipt único e determinístico que cruze:

- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv`;
- `sbom/SBOM.spdx.json`;
- `legal/LEGAL_SCOPE_MAP.yaml`;
- `tools/qemu_rafaelia_assets.lock.yml`;
- arquivos binários realmente presentes em `jniLibs` e `_incoming/pending`.

O gate não tenta inventar autoria ou licença. Lacunas conhecidas podem permanecer como `TOKEN_VAZIO` somente quando estiverem registradas e bloqueadas para distribuição.

## Estados

- `PASS`: inventário sem lacunas materiais, hashes coerentes, SBOM resolvido e pins coerentes;
- `PASS_WITH_QUARANTINE`: não há erro duro, mas existem itens críticos registrados/bloqueados, SBOM incompleto ou pin externo divergente;
- `FAIL`: binário não registrado, hash divergente, item aprovado com identidade vazia, crítico não bloqueado, SBOM inválido ou contrato essencial ausente.

## Detector de arquivos

Arquivos candidatos são detectados por:

- extensões `.so`, `.bin`, `.elf`, `.apk`, `.aab`, `.img`, `.fd`;
- magic bytes ELF ou ZIP para binários sem extensão.

A presença em `_incoming/pending` não é, sozinha, tratada como binário. Isso evita classificar documentação e fonte textual como blob executável.

## Coerência de hashes

Quando `permission_proof` contém `sha256=<64 hex>`, o arquivo local deve produzir o mesmo digest. Ausência de hash pode permanecer vazia apenas quando o item não é promovido e está controlado por quarentena.

## SBOM

O relatório conta:

- pacotes SPDX;
- campos `NOASSERTION`, `TOKEN_VAZIO` ou `TODO`;
- checksums ausentes ou inválidos;
- pacotes ainda não totalmente resolvidos.

O gate não reescreve o SBOM automaticamente, porque uma inferência de licença ou origem não deve virar fato por automação.

## Pin QEMU

O pin canônico esperado neste ciclo é:

```text
d4b3ef09956fa1abaeacba61dca3965f591c3a6a
```

O lock existente registra:

```text
46d994ba6a3b3a9b8a0590238666f19d800c1abc
```

Essa divergência não é corrigida silenciosamente. Ela produz `PASS_WITH_QUARANTINE` enquanto nenhum artifact externo for promovido e impede liberação pública até reconciliação explícita.

## Itens críticos conhecidos

- `libXlorie.so` em quatro ABIs: SHA-256 conhecido; autor, origem e licença ainda vazios;
- `_incoming/pending/rafaelia_ttl`: SHA-256 conhecido; fonte, build e licença ainda vazios;
- rootfs glob: sem path/version/hash/licença exatos;
- firmware OVMF/UEFI: origem geral conhecida, release e hashes ainda vazios;
- `bios-vectras.bin`: origem e licença vazias;
- materiais `NAOCOMERCIAL`: autoria observada, mas sem grant SPDX compatível com release do APK.

Esses itens permanecem bloqueados ou excluídos; o receipt deve provar o controle, não declarar resolução inexistente.

## Execução

```sh
python3 tools/ci/build_provenance_closure_report.py \
  --repo . \
  --expected-qemu-commit d4b3ef09956fa1abaeacba61dca3965f591c3a6a \
  --source-commit "$SOURCE_COMMIT" \
  --output artifacts/c06-provenance/provenance_closure_report.json
```

## Testes

`tests/test_build_provenance_closure_report.py` cobre:

- inventário totalmente resolvido → `PASS`;
- crítico com identidade vazia, mas bloqueado → `PASS_WITH_QUARANTINE`;
- pin QEMU divergente → `PASS_WITH_QUARANTINE`;
- binário não registrado → `FAIL`.

## Fronteira epistemológica

Uma execução positiva pode promover:

```yaml
registered_asset_inventory: VERIFIED_BY_EXECUTION
known_critical_assets_controlled: VERIFIED_BY_EXECUTION
```

Ela não promove:

```yaml
all_provenance_resolved: false_when_quarantine_exists
public_release_allowed: false
binary_distribution_allowed: false
claim_allowed: false
```

## Fechamento

O C06 pode ser fechado como `PASS_WITH_QUARANTINE` quando o relatório provar que todas as lacunas críticas conhecidas estão registradas, hashadas quando possível e bloqueadas. A quarentena só é removida por evidência direta de autoria, origem, licença e hash do artifact exato.