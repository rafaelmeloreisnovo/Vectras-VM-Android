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
| 2026-07-19 | `9f9e9cb44f4bf5df7359d9d5c1860470b1667f16` | PR #1051 | `BLOCKED:not-resolved` | `BLOCKED:not-reached` | `BLOCKED:workflow-failed` | `BLOCKED:no-artifact` | `BLOCKED:no-green-report` | `not-uploaded` | correções presentes em código; prova CI não concluída |
| 2026-07-19 | `2e4f225586b86f5a805c9d64cf6754bf8fa53b9a` | PR #1050 | `BLOCKED:not-resolved` | `BLOCKED:not-reached` | `BLOCKED:workflow-failed` | `BLOCKED:no-artifact` | `BLOCKED:no-green-report` | `not-uploaded` | ZIPRAF incorporado; sem promoção |
| 2026-07-19 | `54c70615c77772a3a7074fd297743f25936cb168` | master após merge #1050 | `BLOCKED:no-current-green-run` | `BLOCKED:not-evaluated` | `BLOCKED:no-canonical-artifact` | `BLOCKED:no-canonical-artifact` | `BLOCKED:no-current-green-run` | `not-uploaded` | `BETA_BLOCKED` |
| 2026-07-19 | `e19a3ade5f816db73eb1af1bd8a52439c0e1899d` | PR #1052 — argv/artifact | `BLOCKED:runner-not-started` | `BLOCKED:not-reached` | `BLOCKED:no-artifact` | `BLOCKED:no-artifact` | `BLOCKED:no-job-steps` | `not-uploaded` | `BLOCKED_BY[GITHUB_ACTIONS_RUNNER_STARTUP]` |

## Evidências positivas de componentes externos

Estas linhas são provas reais dos componentes, mas **não constituem release Vectras**.

| componente | run / commit | runtime ou ABI | signing | artefato e SHA-256 | prova | classificação no Vectras |
|---|---|---|---|---|---|---|
| `qemu_rafaelia:host` | run `29695204340`; branch head `eda7837d376c14ea3ab857db8ba64a978f3e9f99`; merge-test compilado `aede3897b18e5bff2f249b00ad644c211b89e159` | `linux-x86_64`, `glibc`, `host_ci`; guests x86_64/aarch64/i386 | `n/a-native-binary` | archive `fbefe36be6fde0311e8e4b820d5934298b3e328964758821171f0b7d55292ffd`; aarch64 `768ea7da2f3605483c5c528c85bc67168ac403e213dc6a08af88cd24f23da4a5`; i386 `e5ff96794c18e4c6cfd497c827eb3b0c3dbb4311b961d1ca83f0fff1c9157cbe`; x86_64 `da7c138f1689ae1fd478869b209f74535cf63b6e8431b243dd8c258602db8a3e` | build, packaging, JSON, SHA256SUMS e upload verdes | `PROVEN_CI_HOST`; rejeitado no aparelho por `host_ci` |
| `qemu_rafaelia:proot-arm64` | run `29696118677`; branch head `0c1096963666f2737e46b7e8ca7e88b80e0146bd`; merge-test empacotado `7624db0da24d8e21c10cbd270546cf40448c2954` | `linux-aarch64`, `musl`, `proot`; guests x86_64/aarch64/i386 | `n/a-native-binary` | artifact ZIP `e66998db55f77befcf61d1734b06391fcc26e5dd04a8d7bc14764de6a2d840b6`; manifest ZIP `fd9ce4db6ad6c050fd72aedf47a670c58149acbd718062d6ea83a1bd56a10b4a`; inner tar.gz `e7af6e44304d6ad26463f3d040dc8c5f6a063d8ba247a7af7d8c47415a290fe3`; aarch64 `237e9674562320a9be4a345d6366bd4b12d37f55448ea66abb1ea4e75662786c`; i386 `70a11470fe5a1791261725db47a887ce54159700eb56f2e39e8f89bac550c859`; x86_64 `f5632cc9b1264b4ce42ad9675ed83546c2fc41642293145b617162f247c9c654` | runner ARM64 nativo, Alpine musl, três ELF AArch64, contract checker, SHA256SUMS e uploads verdes | `PROVEN_CI_ARTIFACT`; coerente com asset rootfs `alpine19`, ainda sem integração/ADB |
| `termux-app-rafacodephi` | run `29695525477`; head `3f459e2e2e2ab6dc1fe00709b55f4c5828496167` | APK split `armeabi-v7a`, split `arm64-v8a`, universal; minSdk 21; targetSdk 28 | `debug-signed` | arm32 `94369d6dea2bf36fcfba36965ddcf91a514bcdb2fc28bb1ae8c16df426c41b36`; arm64 `0497a684f78795e983630a48c5dbb050197d3f3c034d23d69e0dc7f51d466f36`; universal `4557903b3f5ab66d9aa8a5e016aded26813c1c742ff6c721d1495213acdfdf4c` | badging, ELF, assinatura, bootstrap ARM32, política e uploads verdes | `PROVEN_CI_COMPONENT`; ainda não integrado ao APK Vectras |
| `termux-app-rafacodephi:loader` | run `29695934190`; head `f538423a9bea67f86dabb1249646b54624cfb87b` | package `com.termux.rafacodephi.loader`; minSdk 21; targetSdk 28; `hasCode=false` | `debug-signed` v1/v2 | `loader.apk` 7.965 bytes; `e5bc3ca105a6b0b04afeaaa0d575ada2dafc4777549e5df92c3dd217c07fe24f` | módulo dedicado, package/SDK/manifest, DEX somente `R`, assinatura e upload verdes | `STUB_PROVEN_CI`; funcionalidade de instalação bloqueada |

## Compatibilidade da rootfs

O caminho interno de setup do Vectras extrai o asset `alpine19` para `files/distro`. O artifact QEMU PRoot requer `/lib/ld-musl-aarch64.so.1`; portanto a combinação declarada é coerente no nível de libc e modo de execução.

O wizard externo ainda referencia tarballs históricos sem SHA pinado. Esses downloads não substituem a prova do asset interno e devem receber manifesto/hashes antes de promoção.

## Última prova positiva do app Vectras preservada

| data UTC | commit | tarefa | classificação | observação |
|---|---|---|---|---|
| 2026-04-03T22:29:21Z | `0acd029fff6cb05d928249bace5d9d9a9d0c558f` | `:app:assembleDebug` | validação histórica | última prova positiva do app; não representa o HEAD atual |

## Template para nova evidência

| data UTC | commit | workflow/lane | ABI profile | signing mode | arquivo gerado | SHA-256 | relatório ABI | status de upload | observações/bloqueios |
|---|---|---|---|---|---|---|---|---|---|
| `<timestamp-real>` | `<sha-executado>` | `<workflow/job>` | `<perfil-resolvido>` | `<modo-comprovado>` | `<caminho-real-ou-BLOCKED>` | `<hash-real-ou-BLOCKED>` | `<relatório-real-ou-BLOCKED>` | `<destino-real-ou-BLOCKED>` | `<decisão e limites>` |

## Critérios de promoção

- prova de componente externo não equivale a release Vectras;
- artifact QEMU `host_ci` nunca é consumível pelo app;
- artifact QEMU `proot` ainda exige importação, build consumidor e ADB;
- `release-unsigned-internal` permanece validação interna;
- distribuição oficial exige assinatura oficial, SHA-256 e asset publicado;
- upload falho não constitui distribuição;
- nenhuma falha pode ser reinterpretada como artefato produzido.
