<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: evolving-by-domain -->

# THIRD_PARTY_NOTICES

Este arquivo registra upstreams e componentes de terceiros relevantes ao repositório. **Origem não é apagada por modificação.** As condições de licença devem ser verificadas na fonte canônica e no escopo realmente distribuído.

## Upstream / base histórica — Vectras VM

Evidência atual:

- Fonte canônica observada: `https://github.com/xoureldeen/Vectras-VM-Android`
- Licença do upstream observada: GNU GPL Version 2 (`GPL-2.0-only` como classificação de trabalho; confirmar avisos/headers por unidade).
- O repositório atual `rafaelmeloreisnovo/Vectras-VM-Android` aparece no GitHub como `fork=false`; portanto o **import/fork-base exato ainda é TOKEN_VAZIO** e precisa ser reconstruído por histórico/conteúdo.
- `app/src/main/java/com/vectras/vm/AboutActivity.java` já possui evidência material de ancestralidade e está classificada conservadoramente como `UPSTREAM_MODIFIED` no registro de proveniência.
- Modificações locais são atribuíveis localmente apenas no escopo que a evidência delimitar; elas não transformam o código herdado em autoria local.

Este projeto modificado **não deve ser apresentado como versão oficial/endossada pelo upstream** sem autorização explícita.

Registro: `resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl`.

## Bibliotecas e projetos

Os itens abaixo aparecem no ecossistema/repositório; inclusão efetiva em cada release e licença aplicável devem ser verificadas pelo gate de empacotamento/proveniência:

- 3DFX QEMU PATCH — https://github.com/kjliew/qemu-3dfx
- Alpine Linux — https://www.alpinelinux.org/
- Glide — https://github.com/bumptech/glide
- Gson — https://github.com/google/gson
- OkHttp — https://github.com/square/okhttp
- PROOT — https://proot-me.github.io/
- QEMU — https://github.com/qemu/qemu
- Termux — https://github.com/termux
- ZoomImageView — https://github.com/k1slay/ZoomImageView

## Aviso sobre ISOs, firmware, rootfs e binários

Arquivos ISO, firmware, BIOS/OVMF, rootfs, bibliotecas `.so`, tarballs e outros binários só podem integrar um release quando origem, versão, licença/direitos, hash e obrigações de redistribuição estiverem documentados.

`arquivo presente no repositório != direito de redistribuir`

## Política de autoria, upstream licenciado e clean-room

- Upstream/terceiro **com licença compatível** pode permanecer, desde que as condições, copyrights e notices sejam cumpridos e a origem continue transparente.
- Componentes sem prova suficiente de origem/licença entram em `PRO_OFFICIAL_PENDING`, `TOKEN_VAZIO` ou `QUARANTINED`; não recebem autoria local por default.
- Clean-room é usado quando necessário e somente quando a fronteira de independência puder ser demonstrada; não é um mecanismo para apagar ancestry existente.
- Créditos de inspiração não substituem obrigações de licença, e licença permissiva não elimina autoria/origem.
- Para componentes modificados, preserve simultaneamente `upstream origin` + `local modifications`.

Consulte também:

- `AUTHORSHIP_CLEANROOM_PLAN.md`
- `LICENSES_REGISTER.md`
- `docs/provenance/AGENTS.md`
- `docs/provenance/ABOUT_PROVENANCE_CONTRACT.md`
- `resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl`
- `CREDITS_INSPIRATION.md`

## Claim boundary

Este NOTICE é um índice de atribuição/proveniência; ele **não declara que todos os itens estejam integralmente auditados nem que o projeto esteja certificado/conforme com qualquer norma**. Lacunas permanecem tipadas nos registros canônicos até evidência suficiente.