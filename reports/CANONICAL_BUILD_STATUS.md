<!-- DOC_ORG_SCAN: 2026-07-21 | source-scan: current-audit -->

# CANONICAL_BUILD_STATUS

> Fonte de verdade para status oficial de build/release. Em caso de divergência, este arquivo prevalece.

## Estado canônico atual

- **Estado:** `BETA_BLOCKED`
- **Auditoria corrente:** 2026-07-21
- **Branch de fechamento:** `claude/vectra-vm-gaps-audit-pvtiki`
- **Prova no HEAD corrente:** `BLOCKED_BY_CI[PENDING_DEVICE_RUN]`
- **Regra:** mudança de código não equivale a build, artefato, instalação ou release comprovados.

## Progresso de fechamento de gaps (2026-07-21)

Fechamentos confirmados em código nesta auditoria (em relação ao estado de 2026-07-19):
- **G15 FECHADO:** SPDX GPL-2.0-only adicionado a todos os 87 arquivos em `engine/rmr/` (src/*.c/*.h + interop/*.S)
- **G22 FECHADO:** `legal-compliance-gate.yml` implementado e expandido para cobrir interop/
- **G26/G27 FECHADOS:** `detectRootfsLibc` i386 + `audit_vectra_capabilities` segurança
- **G4/G5/G7/G8 FECHADOS:** em sessões anteriores (termux_jni, ZiprafDirectRuntime, PROJECT_STATE, formulas)

Gaps ainda abertos — bloqueados por hardware ou segredos:
- G2/G3: SHA-256 reais de APK (requer build CI com runner real)
- G6: smoke de dispositivo ADB (requer runner ARM + dispositivo)
- G11/G12/G13: proveniência de binários (requer decisão do proprietário)
- G17/G18: Firebase credentials e cert pinning hash (requer segredos)

## Última validação oficial bem-sucedida (UTC)

- **Data/hora:** 2026-04-03T22:29:21Z
- **Commit SHA validado:** `0acd029fff6cb05d928249bace5d9d9a9d0c558f`
- **Nota:** este registro permanece como última prova positiva; o HEAD 2026-07-21 aguarda prova de CI bem-sucedida.

## Evidência recente sem promoção

O branch `claude/vectra-vm-gaps-audit-pvtiki` (HEAD: `f11b557a`) inclui múltiplos fechamentos de gap,
porém ainda não produziu APK de release validado. Portanto:

- nenhum APK/AAB foi promovido a evidência canônica;
- nenhum SHA-256 de release foi calculado;
- o ledger registra `BLOCKED_BY_HARDWARE[no-device-runner]`;

## Comandos oficiais

### Obrigatório

1. `./tools/gradle_with_jdk21.sh clean :app:assembleDebug --stacktrace`
2. `./tools/gradle_with_jdk21.sh :app:testDebugUnitTest --stacktrace`

### Sob gate

1. `./tools/gradle_with_jdk21.sh :app:assembleRelease --stacktrace`
2. `./tools/gradle_with_jdk21.sh :app:assemblePerfRelease --stacktrace`

## Resultado oficial por tarefa

| Tarefa | Último resultado oficial | Estado no HEAD de fechamento |
|---|---|---|
| `:app:assembleDebug` | ✅ SUCCESS no commit de 2026-04-03 | `BLOCKED_BY_CI[NO_GREEN_CURRENT_HEAD]` |
| `:app:testDebugUnitTest` | sem prova canônica atual | `BLOCKED_BY_CI[NO_GREEN_CURRENT_HEAD]` |
| `:app:assembleRelease` | ⛔ GATED | `BLOCKED_BY[KEYSTORE_AND_GREEN_CI_REQUIRED]` |
| `:app:assemblePerfRelease` | ⛔ GATED | `BLOCKED_BY[HARDWARE_AND_POLICY_REQUIRED]` |
| instalação/launch ADB | sem prova | `BLOCKED_BY[ADB_RUNNER_REQUIRED]` |
| boot de VM | sem prova | `BLOCKED_BY[QEMU_ARTIFACT_AND_DEVICE_REQUIRED]` |

## Critério de atualização

Somente após CI verde no commit corrente atualizar:

1. SHA executado;
2. timestamp UTC;
3. tarefas efetivamente concluídas;
4. caminho e SHA-256 dos artefatos;
5. perfil ABI e assinatura;
6. links para relatórios e execução ADB, quando aplicável.

Até lá, a última validação positiva de 2026-04-03 é histórica, não uma afirmação sobre o HEAD.
