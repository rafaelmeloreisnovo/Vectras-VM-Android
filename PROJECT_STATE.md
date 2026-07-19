<!-- DOC_METADATA_SYNC: 2026-07-19 | role: project-state -->

# PROJECT_STATE

## Metadados canônicos

- Versão do documento: 2.2.
- Última revisão: 2026-07-19.
- Escopo: estado operacional validado, bloqueios, riscos, limitações e critérios para não inferir build/release sem evidência atual.
- Status: canônico vigente; estado técnico declarado continua condicionado às validações registradas no próprio documento.
- Commit de referência: `HEAD`.
- Fonte de verdade relacionada: [`reports/CANONICAL_BUILD_STATUS.md`](reports/CANONICAL_BUILD_STATUS.md), [`BUILDING.md`](BUILDING.md), [`DOC_INDEX.md`](DOC_INDEX.md), [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) e [`.github/workflows/host-ci.yml`](.github/workflows/host-ci.yml).
- Gaps de prova: ver [`docs/BLOCKING_GAPS.md`](docs/BLOCKING_GAPS.md).


Estado atual do projeto: `BETA_BLOCKED`.

## Fonte única de referência
Toda a documentação normativa, relatórios vigentes e histórico deve ser consultada exclusivamente em:

### Δ — 57 Pontos Corrigidos
Veja `FIXES_SUMMARY.md` para tabela completa.

## Definições
- **STABLE**: ciclo estável, foco em manutenção e releases.
- **EXPERIMENTAL**: ciclo exploratório, mudanças rápidas e validações.
- **REFACTORING**: ciclo de reestruturação e consolidação técnica.
- **FIXED_REFACTORING**: reestruturação concluída após CI canônico verde no commit corrente.

## Escopo atual (BETA_BLOCKED)
- ✅ Consolidação de contratos CI host/android em andamento.
- ✅ Fontes externas críticas (`qemu_rafaelia`, `androidx_RmR`) definidas por manifesto e script de verificação.
- ✅ ABI de release oficial definida como `arm64-v8a` (PR #1041, 2026-07-09).
- ✅ `ZiprafDirectRuntime.kt` — runtime ZIPRAF direto via mmap ativo (PR #1048, 2026-07-18).
  - mmap por extent (não arquivo inteiro) — corrigido neste PR.
  - `parseStoredExtent()` com validação de EOCD + CD + local header — adicionado neste PR.
- ✅ `termux_jni.c` promovido de `_incoming/` ao path canônico `app/src/main/cpp/`.
- ✅ SBOM inicial criado em `sbom/SBOM.spdx.json` (hashes dependem de CI build).
- ✅ Mapeamento de escopo legal em `legal/LEGAL_SCOPE_MAP.yaml`.
- ✅ Gaps bloqueados por hardware documentados em `docs/BLOCKING_GAPS.md`.
- ⚠️ Status de build **não pode ser inferido como atual** sem execução CI no commit corrente.
- ⚠️ Afirmações de aceleração/otimização (ex.: NEON) devem ser tratadas como capacidade de build declarada até validação executada no commit atual.
- ⚠️ `NAOCOMERCIAL/` em quarentena — auditar por arquivo antes de qualquer release (ver `legal/LEGAL_SCOPE_MAP.yaml`).

## Documentos canônicos
- `reports/CANONICAL_BUILD_STATUS.md` — **última validação conhecida** de build/release; não substitui execução CI do commit atual.
- `reports/DOC_SYNC_2026-05-24.md` — sincronização documental determinística (inventário, consolidação e gaps desta rodada)
- `FIXES_SUMMARY.md` — tabela completa 57 fixes
- `docs/SETUP_SDK_NDK.md` — setup local
- `docs/RELEASE_EVIDENCE_LEDGER.md` — ledger padrão para evidência de release, SHA-256, ABI, assinatura, upload e bloqueios.
- `tools/qemu_launch.yml` — QEMU configuration
- `archive/root-history/IMPLEMENTATION_COMPLETE.md`
- Política de overlays: ZIPs na raiz não são fonte de verdade; somente a árvore Git é oficial, com bloqueio em CI para conteúdo duplicado.

> Atualize este arquivo sempre que o estado do projeto mudar.

## CI canonical reference (Android/Host)

- Canonical Android pipeline: `.github/workflows/android-ci.yml`.
- Android wrapper entrypoint: `.github/workflows/android.yml`.
- Auxiliary Android ABI compatibility matrix: `.github/workflows/compile-matrix.yml`.
- Canonical host pipeline: `.github/workflows/host-ci.yml`.
- Orchestration and final gates: `.github/workflows/pipeline-orchestrator.yml` and `.github/workflows/quality-gates.yml`.
- Canonical matrix documentation: `docs/ci/workflow-matrix.md`.

## Coerência operacional de release
- Branch padrão operacional inclui `master` no orquestrador, mantendo `main`, `develop` e `feature/**`.
- `release-unsigned-internal` é exclusivo para **validação interna** dual ARM (`internal_arm32_arm64`) sem assinatura.
- `release-signed-official` é exclusivo para **distribuição oficial** `official_arm64` com assinatura oficial.
- Evidências de APK/AAB devem seguir `docs/RELEASE_EVIDENCE_LEDGER.md`; release unsigned nunca deve ser descrita como distribuição oficial.
- `VECTRA_CORE_ENABLED` permanece ativo em release com gates de validação determinística.
- Status canônico de build só é atualizado após CI real concluída.

- `external_sources.manifest` mantém `androidx_RmR` e `qemu_rafaelia` com `pinned_commit_sha`, além de validação remota e contenção do SHA no branch no CI.


Última sincronização documental registrada: **2026-07-19** (v2.2 — auditoria de gaps, ZIPRAF fix, SBOM, legal scope map).
