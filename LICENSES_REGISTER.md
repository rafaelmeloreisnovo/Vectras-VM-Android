# LICENSES_REGISTER

## Finalidade
Registro normativo para garantir conformidade estrita de licenças, proveniência e atribuição.

Este registro é **bloqueante para distribuição pública**. Enquanto qualquer item de release abaixo estiver como `TOKEN_VAZIO`, `PRO_OFFICIAL_PENDING`, `QUARANTINED` ou equivalente bloqueante, o projeto pode ser usado como pesquisa/beta interno, mas não deve ser apresentado como pacote distribuível/compliance fechado.

## Regras mandatórias
1. Nenhum arquivo/componente material entra em distribuição sem licença/origem suficientemente identificadas para o uso pretendido.
2. Dependência sem licença explícita = **proibida para release** até resolução/exclusão.
3. Asset, imagem, áudio, fonte, firmware, BIOS, OVMF, `.so`, tarball e binário sem autor + licença + origem = **quarentena**.
4. Código autoral novo deve conter cabeçalho mínimo de copyright/licença quando aplicável à política/licença adotada.
5. Créditos de inspiração não substituem obrigação de licença.
6. Restrição `NAOCOMERCIAL` não pode ser misturada em artefato distribuído como GPLv2 sem decisão jurídica explícita.
7. **Transformação não apaga origem**: upstream modificado continua upstream + modificação local; rastrear em `resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl`.
8. Na dúvida de autoria/origem: `PRO_OFFICIAL_PENDING`, `claim_allowed=false`.

## Tabela de conformidade viva

| Componente | Tipo | Origem / evidência | Licença | Estado | Ação | Responsável |
|---|---|---|---|---|---|---|
| Vectras upstream / base histórica | código Android | upstream canônico observado: `xoureldeen/Vectras-VM-Android`; o repositório atual aparece no GitHub como `fork=false`, então o import/fork-base exato permanece `TOKEN_VAZIO`; `AboutActivity.java` possui correspondência estrutural/material suficiente para classificá-la conservadoramente como `UPSTREAM_MODIFIED` | upstream `LICENSE` observado como GNU GPL Version 2; escopo/header por arquivo ainda precisa auditoria | `PARTIAL_VERIFIED` / release ainda bloqueado | preservar upstream e créditos; reconstruir base histórica; auditar headers/avisos; reconciliar About; não afirmar autoria integral | Rafael + revisão |
| `resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl` | registro append-only | RAFAELIA governance; contém vínculos source→transform→current | N/A metadata | `ACTIVE` | usar como índice material de transformação; nunca apagar ancestry, apenas superseder | Rafael + agentes |
| `engine/rmr/**` | código autoral/experimental | RAFCODE-Φ / Rafael; autoria por unidade ainda deve ter evidência própria | TOKEN_VAZIO jurídico até cabeçalhos/SPDX finais | não distribuível como licença fechada | definir licença/headers e separar unidade independente de derivativos GPL quando necessário | Rafael |
| `app/src/main/jniLibs/**/libXlorie.so` | binário `.so` | origem exata não registrada nesta árvore | TOKEN_VAZIO | bloqueado | registrar origem, build script, licença e hash; ou remover do pacote | Rafael + IA |
| Alpine/rootfs tarballs | binário/rootfs | origem exata por arquivo ainda não registrada | TOKEN_VAZIO | bloqueado | preencher `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` | Rafael |
| OVMF/BIOS assets | firmware/binário | origem exata por arquivo ainda não registrada | TOKEN_VAZIO | bloqueado | registrar upstream, licença, versão e hash | Rafael |
| `RAFAELMELOREIS/MIT/NAOCOMERCIAL/**` | diretório pessoal/licença divergente | autoral segundo registro local; escopo e relicenciamento precisam revisão | MIT + restrição não-comercial declarada em caminho | incompatibilidade potencial | isolar de pacote GPL derivativo ou resolver licença/escopo explicitamente | Rafael jurídico |
| Screenshots/assets soltos na raiz | imagem/asset | múltiplas origens não normalizadas | TOKEN_VAZIO | bloqueado se distribuído | mover para quarentena ou registrar proveniência | Rafael + IA |
| `addthis/**` | material experimental | origem/finalidade não documentada | TOKEN_VAZIO | bloqueado se distribuído | adicionar README/proveniência ou excluir de release | Rafael + IA |

## Evidência upstream inicial — About

Primeiro vínculo registrado:

- atual: `app/src/main/java/com/vectras/vm/AboutActivity.java`, git blob observado `a2aa44474765ebd21956c5bf5b53fc9e89c95cd2`;
- upstream: `xoureldeen/Vectras-VM-Android`, mesmo caminho, commit observado `74230ebfc0fadb8a06485bda2db30fc6bc0bb7bb` (2026-07-13, An Bui / AnBui2004);
- classificação: `UPSTREAM_MODIFIED` em granularidade whole-file conservadora;
- licença upstream observada: GPL Version 2;
- resolução fina de autoria local por span: `TOKEN_VAZIO` até reconstrução de histórico/diff.

Registro canônico: `resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl`.

## Gate legal/proveniência de CI obrigatório
- Falhar se componente de release estiver sem licença/origem suficiente.
- Falhar se licença for incompatível com o modo de distribuição.
- Falhar se houver item em quarentena no pacote de release.
- Falhar se `THIRD_PARTY_NOTICES.md`, este registro, provenance transform register, About manifest e asset register divergirem materialmente.
- Falhar se componente `UPSTREAM_MODIFIED` perder atribuição/NOTICE exigidos ou for apresentado como autoria local integral.
- Falhar se source/build/artifact digest da evidência não corresponder ao release avaliado.

## Saída mínima do estado `BETA_BLOCKED` jurídico/proveniência
- [ ] hashes SHA256 de todos os binários distribuídos;
- [ ] upstream/URL ou autor documentado por binário/material relevante;
- [ ] licença SPDX quando verificável e apropriada;
- [ ] reconstrução suficiente da base/import histórico Vectras para claims de autoria por unidade;
- [ ] decisão escrita sobre `NAOCOMERCIAL × GPLv2`;
- [ ] lista explícita do que entra e do que não entra no APK/release;
- [ ] About/NOTICE gerados ou verificados contra registros canônicos;
- [ ] CI executando verificação de proveniência antes de empacotar release.

## Princípio RAFAELIA aplicado
`TOKEN_VAZIO` aqui não é falha estética: é lacuna protegida. Melhor bloquear release do que inventar licença, autoria ou proveniência. **Origem legítima permanece visível após qualquer transformação.**
