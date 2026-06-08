# Release Evidence Ledger

> Ledger operacional para humanos e agentes de IA registrarem, compararem e auditarem evidências de release Android sem misturar validação interna com distribuição oficial.

## Finalidade

Este arquivo é o formato esperado para registrar cada APK/AAB gerado por lanes de CI, builds locais controlados ou publicação oficial. Ele complementa os manifests materializados por `tools/ci/materialize_android_ci_artifacts.sh` e o relatório Gradle `app/build/reports/artifacts/compiled-artifacts-report.json`.

Use este ledger para responder, sem inferência silenciosa:

- qual commit gerou o artefato;
- qual workflow/lane executou a cadeia;
- qual perfil ABI foi aplicado;
- se a assinatura era de validação interna ou distribuição oficial;
- qual APK/AAB foi gerado;
- qual SHA-256 permite conferir integridade;
- onde está o relatório ABI;
- se houve upload e para qual destino;
- quais bloqueios impedem promoção, publicação ou confiança operacional.

## Vocabulário obrigatório de assinatura

| Signing mode | Nome operacional obrigatório | Uso permitido | Proibição explícita |
|---|---|---|---|
| `unsigned` | **validação interna** | Testar gates de release, ABI, empacotamento e materialização sem segredo de produção. | Não chamar de release oficial, distribuição oficial, store-ready ou artefato publicável ao usuário final. |
| `debug-signed` | **validação interna** | Iteração/debug instalável com chave debug padrão. | Não promover como release oficial. |
| `signed-internal` | **validação interna** | Ensaios com keystore local/beta controlado, sem substituir a chave oficial. | Não confundir com assinatura oficial de loja. |
| `signed` | **distribuição oficial** | Publicação oficial apenas via `.github/workflows/release-dual-track.yml` + `.github/workflows/android-ci.yml`, com segredos `VECTRAS_RELEASE_*`. | Não fazer fallback para unsigned; falha de segredo deve bloquear. |

Regra curta: **unsigned/debug/internal = validação interna**; **signed oficial = distribuição oficial**. Se a evidência não provar assinatura oficial, registre como validação interna.

## Tabela padrão

Preencha uma linha por artefato ou por pacote lógico de release quando o mesmo relatório cobrir APK e AAB. Use `n/a` somente quando o campo não se aplica; use `BLOCKED:<motivo>` quando a evidência deveria existir e não existe.

| data UTC | commit | workflow/lane | ABI profile | signing mode | APK/AAB gerado | SHA-256 | relatório ABI | status de upload | observações/bloqueios |
|---|---|---|---|---|---|---|---|---|---|
| 2026-06-08T00:00:00Z | `<sha>` | `android-ci.yml / release-unsigned-internal` | `internal_arm32_arm64` | `unsigned` — validação interna | `ci-artifacts/android-artifacts/...apk` | `<sha256>` | `app/build/reports/artifacts/compiled-artifacts-report.json` ou `ci-artifacts/android-cmake-matrix/...` | `uploaded:actions-artifact` ou `not-uploaded:<motivo>` | `validação interna; não publicar como distribuição oficial` |
| 2026-06-08T00:00:00Z | `<sha>` | `release-dual-track.yml / release-signed-official` | `official_arm64` | `signed` — distribuição oficial | `ci-artifacts/android-artifacts/...aab` | `<sha256>` | `app/build/reports/artifacts/compiled-artifacts-report.json` ou release asset ABI report | `uploaded:github-release` | `distribuição oficial somente após gate signed verde` |

## Campos e fonte de evidência

| Campo | Como preencher | Fonte preferencial |
|---|---|---|
| data UTC | Timestamp ISO-8601 UTC da geração/materialização. | `generated_at_utc` em `artifact-manifest.json` ou horário do job. |
| commit | SHA do commit executado. | `GITHUB_SHA`, `git rev-parse HEAD` ou manifest de artefato. |
| workflow/lane | Workflow e lane real, não intenção humana. | `GITHUB_WORKFLOW`, `ABI_PROFILE`, `ARTIFACT_LANE`, job summary. |
| ABI profile | Perfil resolvido (`official_arm64`, `internal_arm32_arm64`, etc.). | Inputs do workflow, Gradle properties e manifest. |
| signing mode | `unsigned`, `debug-signed`, `signed-internal` ou `signed`. | `signing_mode`, `ciRelease`, relatório de build/assinatura. |
| APK/AAB gerado | Caminho relativo do binário entregue ou asset publicado. | `ci-artifacts/android-artifacts`, Gradle outputs ou release asset. |
| SHA-256 | Hash do arquivo final entregue. | `sha256sum`, campo `files[].sha256` do manifest. |
| relatório ABI | Caminho para relatório que prova ABI entregue. | `compiled-artifacts-report.json`, `android-cmake-matrix`, `verify_apk_abi_set` report. |
| status de upload | Destino e resultado (`uploaded:actions-artifact`, `uploaded:github-release`, `not-uploaded`, `blocked`). | Logs do Actions, release page, materialização de staging. |
| observações/bloqueios | Riscos, exceções, divergências, links internos e decisão operacional. | Logs, PR, release notes, issues ou auditoria manual. |

## Boas práticas de preenchimento

1. Registre evidência no commit que realmente executou a cadeia; não copie status de commits anteriores.
2. Não promova `release-unsigned-internal` para usuário final. Ela é validação interna mesmo quando passa em todos os gates.
3. Não chame `signed-internal` de distribuição oficial; keystore beta/local não equivale aos segredos `VECTRAS_RELEASE_*`.
4. Para distribuição oficial, exija `release-signed-official`, ABI `official_arm64`, signing mode `signed`, upload de GitHub Release e SHA-256 do asset publicado.
5. Para validação dual ARM, exija `internal_arm32_arm64` e deixe claro se o APK/AAB é apenas evidência técnica.
6. Se um relatório ABI estiver ausente, marque `BLOCKED:abi-report-missing` em vez de inferir pelo nome do arquivo.
7. Se o upload falhar, mantenha o SHA-256 e marque `not-uploaded:<motivo>`; artefato local sem upload não é distribuição oficial.
8. Sempre preserve links internos para manifests, relatórios e workflows quando disponíveis.

## Relação com CI e documentação

- `tools/ci/materialize_android_ci_artifacts.sh` deve materializar manifests com referência a este arquivo como ledger esperado.
- `BUILDING.md` descreve como gerar APK/AAB e como classificar assinatura sem fallback inseguro.
- `README.md` aponta a cadeia oficial e a separação vocabular entre validação interna e distribuição oficial.
- `PROJECT_STATE.md` mantém o estado real e lembra que status de build só é atual após CI do commit corrente.
- `docs/OPERATIONS.md` usa este ledger como evidência operacional para auditoria, troubleshooting e upload.
