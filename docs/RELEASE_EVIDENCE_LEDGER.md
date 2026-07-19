# Release Evidence Ledger

> Ledger operacional para registrar e auditar evidências de build/release Android sem misturar intenção, validação interna, componente externo e distribuição oficial.

## Regra canônica

Uma execução só entra como artefato comprovado quando existem, em conjunto:

1. commit executado;
2. workflow/lane concluído;
3. arquivo materializado;
4. SHA-256 calculado sobre o arquivo final;
5. relatório ABI/runtime;
6. modo de assinatura identificado quando aplicável;
7. destino de upload verificável.

Ausência de qualquer elo obrigatório é registrada como `BLOCKED:<motivo>`, nunca como sucesso implícito.

## Vocabulário de assinatura

| Signing mode | Nome operacional | Uso permitido | Proibição |
|---|---|---|---|
| `n/a-native-binary` | binário nativo não-APK | prova de compilação/empacotamento | não confundir com assinatura Android |
| `unsigned` | validação interna | gates de build/ABI sem segredo | não chamar de release oficial |
| `debug-signed` | validação interna | instalação e debug | não promover para distribuição |
| `signed-internal` | validação interna | beta controlado | não confundir com chave oficial |
| `signed` | distribuição oficial | publicação via gate oficial | sem fallback para unsigned |

## Evidências Vectras observadas — 2026-07-19

Estas linhas registram execuções reais, inclusive quando falharam. Elas não afirmam que um artefato Vectras foi produzido.

| data UTC | commit | workflow/lane | ABI profile | signing mode | arquivo gerado | SHA-256 | relatório ABI | status de upload | observações/bloqueios |
|---|---|---|---|---|---|---|---|---|---|
| 2026-07-19 | `9f9e9cb44f4bf5df7359d9d5c1860470b1667f16` | PR #1051 — `android-ci`, `host-ci`, orchestrator e gates auxiliares | `BLOCKED:not-resolved` | `BLOCKED:not-reached` | `BLOCKED:workflow-failed` | `BLOCKED:no-artifact` | `BLOCKED:no-green-report` | `not-uploaded:workflow-failed` | fechamento G3/G4/G5/G7/G8/G10 presente em código; prova CI não concluída |
| 2026-07-19 | `2e4f225586b86f5a805c9d64cf6754bf8fa53b9a` | PR #1050 — `android-ci`, `host-ci`, orchestrator e gates auxiliares | `BLOCKED:not-resolved` | `BLOCKED:not-reached` | `BLOCKED:workflow-failed` | `BLOCKED:no-artifact` | `BLOCKED:no-green-report` | `not-uploaded:workflow-failed` | ZIPRAF KAT/política/sessão incorporados; sem promoção de release |
| 2026-07-19 | `54c70615c77772a3a7074fd297743f25936cb168` | master após merge #1050 | `BLOCKED:no-current-green-run` | `BLOCKED:not-evaluated` | `BLOCKED:no-canonical-artifact` | `BLOCKED:no-canonical-artifact` | `BLOCKED:no-current-green-run` | `not-uploaded:no-current-proof` | estado preservado como `BETA_BLOCKED` |
| 2026-07-19 | `e19a3ade5f816db73eb1af1bd8a52439c0e1899d` | PR #1052 — nova cadeia argv/artifact | `BLOCKED:runner-not-started` | `BLOCKED:not-reached` | `BLOCKED:no-artifact` | `BLOCKED:no-artifact` | `BLOCKED:no-job-steps` | `not-uploaded` | workflows falharam antes de executar steps; classificado como `BLOCKED_BY[GITHUB_ACTIONS_RUNNER_STARTUP]` |

## Evidências positivas de componentes externos

Estas linhas são provas reais dos componentes, mas **não constituem release Vectras**.

| componente | run / commit | runtime ou ABI | signing | artefato e SHA-256 | prova | classificação no Vectras |
|---|---|---|---|---|---|---|
| `qemu_rafaelia` | run `29695204340`; branch head `eda7837d376c14ea3ab857db8ba64a978f3e9f99`; merge-test compilado `aede3897b18e5bff2f249b00ad644c211b89e159` | `linux-x86_64`, `glibc`, `host_ci`; guests x86_64/aarch64/i386 | `n/a-native-binary` | artifact archive `fbefe36be6fde0311e8e4b820d5934298b3e328964758821171f0b7d55292ffd`; binaries: aarch64 `768ea7da2f3605483c5c528c85bc67168ac403e213dc6a08af88cd24f23da4a5`, i386 `e5ff96794c18e4c6cfd497c827eb3b0c3dbb4311b961d1ca83f0fff1c9157cbe`, x86_64 `da7c138f1689ae1fd478869b209f74535cf63b6e8431b243dd8c258602db8a3e` | build, existência, packaging, JSON runtime-aware, SHA256SUMS e upload verdes | `PROVEN_CI_HOST`; rejeitado para execução no aparelho por `execution_mode=host_ci` |
| `termux-app-rafacodephi` | run `29695525477`; head `3f459e2e2e2ab6dc1fe00709b55f4c5828496167` | APK split `armeabi-v7a`, split `arm64-v8a`, universal; minSdk 21; targetSdk 28 | `debug-signed` | arm32 `94369d6dea2bf36fcfba36965ddcf91a514bcdb2fc28bb1ae8c16df426c41b36`; arm64 `0497a684f78795e983630a48c5dbb050197d3f3c034d23d69e0dc7f51d466f36`; universal `4557903b3f5ab66d9aa8a5e016aded26813c1c742ff6c721d1495213acdfdf4c` | badging, ELF, assinatura, bootstrap ARM32, política e uploads verdes | `PROVEN_CI_COMPONENT`; ainda não integrado ao APK Vectras |
| `termux-app-rafacodephi:loader` | run `29695934190`; head `f538423a9bea67f86dabb1249646b54624cfb87b` | package `com.termux.rafacodephi.loader`; minSdk 21; targetSdk 28; `hasCode=false` | `debug-signed` v1/v2 | `loader.apk` 7.965 bytes; `e5bc3ca105a6b0b04afeaaa0d575ada2dafc4777549e5df92c3dd217c07fe24f` | módulo dedicado, package/SDK/manifest, DEX somente `R`, assinatura e upload verdes | `STUB_PROVEN_CI`; funcionalidade de instalação bloqueada |

## Última prova positiva do app Vectras preservada

| data UTC | commit | tarefa | classificação | observação |
|---|---|---|---|---|
| 2026-04-03T22:29:21Z | `0acd029fff6cb05d928249bace5d9d9a9d0c558f` | `:app:assembleDebug` | validação histórica | continua sendo a última prova positiva do app; não representa o HEAD atual |

## Template para nova evidência

Copie esta linha somente depois da execução; não deixe valores como se fossem evidência real.

| data UTC | commit | workflow/lane | ABI profile | signing mode | arquivo gerado | SHA-256 | relatório ABI | status de upload | observações/bloqueios |
|---|---|---|---|---|---|---|---|---|---|
| `<timestamp-real>` | `<sha-executado>` | `<workflow/job>` | `<perfil-resolvido>` | `<modo-comprovado>` | `<caminho-real-ou-BLOCKED>` | `<hash-real-ou-BLOCKED>` | `<relatório-real-ou-BLOCKED>` | `<destino-real-ou-BLOCKED>` | `<decisão e limites>` |

## Fontes preferenciais

- timestamp: `generated_at_utc` do manifest ou horário do job;
- commit: `GITHUB_SHA`, head SHA e manifest de artefato, distinguindo merge-test de branch head;
- workflow/lane: job summary e inputs resolvidos;
- ABI/runtime: relatório ELF/APK e `qemu-exec.json`;
- assinatura: relatório de signing, sem inferência pelo nome;
- SHA-256: hash do arquivo entregue, após materialização;
- upload: artifact/release realmente publicado.

## Critérios de promoção

- prova de componente externo não equivale a release Vectras;
- artifact QEMU `host_ci` nunca é consumível pelo app;
- `release-unsigned-internal` permanece validação interna, mesmo verde;
- distribuição oficial exige `official_arm64`, assinatura `signed`, SHA-256 e asset publicado;
- relatório ABI ausente implica `BLOCKED:abi-report-missing`;
- upload falho preserva o hash local, mas não constitui distribuição;
- nenhuma linha de falha pode ser reinterpretada como artefato produzido.
