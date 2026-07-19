# Release Evidence Ledger

> Ledger operacional para registrar e auditar evidências de build/release Android sem misturar intenção, validação interna e distribuição oficial.

## Regra canônica

Uma execução só entra como artefato comprovado quando existem, em conjunto:

1. commit executado;
2. workflow/lane concluído;
3. APK/AAB materializado;
4. SHA-256 calculado sobre o arquivo final;
5. relatório ABI;
6. modo de assinatura identificado;
7. destino de upload verificável.

Ausência de qualquer elo obrigatório é registrada como `BLOCKED:<motivo>`, nunca como sucesso implícito.

## Vocabulário de assinatura

| Signing mode | Nome operacional | Uso permitido | Proibição |
|---|---|---|---|
| `unsigned` | validação interna | gates de build/ABI sem segredo | não chamar de release oficial |
| `debug-signed` | validação interna | instalação e debug | não promover para distribuição |
| `signed-internal` | validação interna | beta controlado | não confundir com chave oficial |
| `signed` | distribuição oficial | publicação via gate oficial | sem fallback para unsigned |

## Evidências observadas — 2026-07-19

Estas linhas registram execuções reais, inclusive quando falharam. Elas não afirmam que um artefato foi produzido.

| data UTC | commit | workflow/lane | ABI profile | signing mode | APK/AAB gerado | SHA-256 | relatório ABI | status de upload | observações/bloqueios |
|---|---|---|---|---|---|---|---|---|---|
| 2026-07-19 (data da execução) | `9f9e9cb44f4bf5df7359d9d5c1860470b1667f16` | PR #1051 — `android-ci`, `host-ci`, orchestrator e gates auxiliares | `BLOCKED:not-resolved` | `BLOCKED:not-reached` | `BLOCKED:workflow-failed` | `BLOCKED:no-artifact` | `BLOCKED:no-green-report` | `not-uploaded:workflow-failed` | fechamento G3/G4/G5/G7/G8/G10 presente em código; prova CI não concluída |
| 2026-07-19 (data da execução) | `2e4f225586b86f5a805c9d64cf6754bf8fa53b9a` | PR #1050 — `android-ci`, `host-ci`, orchestrator e gates auxiliares | `BLOCKED:not-resolved` | `BLOCKED:not-reached` | `BLOCKED:workflow-failed` | `BLOCKED:no-artifact` | `BLOCKED:no-green-report` | `not-uploaded:workflow-failed` | ZIPRAF KAT/política/sessão incorporados; sem promoção de release |
| 2026-07-19 (auditoria) | `54c70615c77772a3a7074fd297743f25936cb168` | master após merge #1050 | `BLOCKED:no-current-green-run` | `BLOCKED:not-evaluated` | `BLOCKED:no-canonical-artifact` | `BLOCKED:no-canonical-artifact` | `BLOCKED:no-current-green-run` | `not-uploaded:no-current-proof` | estado preservado como `BETA_BLOCKED` |

## Última prova positiva preservada

| data UTC | commit | tarefa | classificação | observação |
|---|---|---|---|---|
| 2026-04-03T22:29:21Z | `0acd029fff6cb05d928249bace5d9d9a9d0c558f` | `:app:assembleDebug` | validação histórica | continua sendo a última prova positiva registrada; não representa o HEAD atual |

## Template para nova evidência

Copie esta linha somente depois da execução; não deixe valores como se fossem evidência real.

| data UTC | commit | workflow/lane | ABI profile | signing mode | APK/AAB gerado | SHA-256 | relatório ABI | status de upload | observações/bloqueios |
|---|---|---|---|---|---|---|---|---|---|
| `<timestamp-real>` | `<sha-executado>` | `<workflow/job>` | `<perfil-resolvido>` | `<modo-comprovado>` | `<caminho-real-ou-BLOCKED>` | `<hash-real-ou-BLOCKED>` | `<relatório-real-ou-BLOCKED>` | `<destino-real-ou-BLOCKED>` | `<decisão e limites>` |

## Fontes preferenciais

- timestamp: `generated_at_utc` do manifest ou horário do job;
- commit: `GITHUB_SHA` ou manifest de artefato;
- workflow/lane: job summary e inputs resolvidos;
- ABI: `compiled-artifacts-report.json` e verificador do APK;
- assinatura: relatório de signing, sem inferência pelo nome;
- SHA-256: hash do arquivo entregue, após materialização;
- upload: artifact/release realmente publicado.

## Critérios de promoção

- `release-unsigned-internal` permanece validação interna, mesmo verde;
- distribuição oficial exige `official_arm64`, assinatura `signed`, SHA-256 e asset publicado;
- relatório ABI ausente implica `BLOCKED:abi-report-missing`;
- upload falho preserva o hash local, mas não constitui distribuição;
- nenhuma linha de falha pode ser reinterpretada como artefato produzido.
