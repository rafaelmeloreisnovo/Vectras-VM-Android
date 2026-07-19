<!-- DOC_ORG_SCAN: 2026-07-19 | source-scan: current-audit -->

# CANONICAL_BUILD_STATUS

> Fonte de verdade para status oficial de build/release. Em caso de divergência, este arquivo prevalece.

## Estado canônico atual

- **Estado:** `BETA_BLOCKED`
- **Auditoria corrente:** 2026-07-19
- **Baseline master auditado:** `54c70615c77772a3a7074fd297743f25936cb168`
- **Branch de fechamento:** `claude/vectra-vm-gaps-audit-pvtiki`
- **Prova no HEAD corrente:** `BLOCKED_BY_CI[RECENT_WORKFLOWS_FAILED]`
- **Regra:** mudança de código não equivale a build, artefato, instalação ou release comprovados.

## Última validação oficial bem-sucedida (UTC)

- **Data/hora:** 2026-04-03T22:29:21Z
- **Commit SHA validado:** `0acd029fff6cb05d928249bace5d9d9a9d0c558f`
- **Drift documental encerrado:** o registro antigo continua preservado como última prova positiva, mas não representa o HEAD de 2026-07-19.

## Evidência recente sem promoção

Os heads dos PRs de fechamento #1051 (`9f9e9cb44f4bf5df7359d9d5c1860470b1667f16`) e #1050 (`2e4f225586b86f5a805c9d64cf6754bf8fa53b9a`) acionaram workflows, porém as execuções observadas terminaram em falha. Portanto:

- nenhum APK/AAB recente foi promovido a evidência canônica;
- nenhum SHA-256 de release foi inferido;
- o ledger deve registrar `BLOCKED:workflow-failed`;
- as correções de ZIPRAF, SBOM, JNI e argv QEMU permanecem como código pendente de prova CI/dispositivo.

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
