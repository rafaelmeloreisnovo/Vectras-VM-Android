# LICENSES_REGISTER

## Finalidade
Registro normativo para garantir conformidade estrita de licenças, proveniência e atribuição.

Este registro é **bloqueante para distribuição pública**. Enquanto qualquer item abaixo estiver como `TOKEN_VAZIO`, `QUARANTINE` ou `NEEDS_OWNER_PROVENANCE`, o projeto pode ser usado como pesquisa/beta interno, mas não deve ser apresentado como pacote distribuível/compliance fechado.

## Regras mandatórias
1. Nenhum arquivo entra em distribuição sem licença identificada.
2. Dependência sem licença explícita = **proibida para release**.
3. Asset, imagem, áudio, fonte, firmware, BIOS, OVMF, `.so`, tarball e binário sem autor + licença + origem = **quarentena**.
4. Código autoral novo deve conter cabeçalho mínimo de copyright e licença quando aplicável.
5. Créditos de inspiração não substituem obrigação de licença.
6. Restrição `NAOCOMERCIAL` não pode ser misturada em artefato distribuído como GPLv2 sem decisão jurídica explícita.

## Tabela de conformidade viva

| Componente | Tipo | Origem | Licença | Compatível? | Ação | Responsável |
|---|---|---|---|---|---|---|
| Vectras upstream / base fork | código Android | fork histórico do projeto Vectras | GPL-2.0-only / confirmar headers por arquivo | provável, pendente varredura completa | manter com atribuição e notices | Rafael + revisão |
| `engine/rmr/**` | código autoral/experimental | RAFCODE-Φ / Rafael | TOKEN_VAZIO jurídico até cabeçalhos SPDX finais | não distribuível como licença fechada | definir SPDX por diretório antes de release | Rafael |
| `app/src/main/jniLibs/**/libXlorie.so` | binário `.so` | origem exata não registrada nesta árvore | TOKEN_VAZIO | não | registrar origem, build script, licença e hash; ou remover do pacote | Rafael + IA |
| Alpine/rootfs tarballs | binário/rootfs | origem exata por arquivo ainda não registrada | TOKEN_VAZIO | não | preencher `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` | Rafael |
| OVMF/BIOS assets | firmware/binário | origem exata por arquivo ainda não registrada | TOKEN_VAZIO | não | registrar upstream, licença, versão e hash | Rafael |
| `RAFAELMELOREIS/MIT/NAOCOMERCIAL/**` | diretório pessoal/licença divergente | autoral | MIT + restrição não-comercial declarada em caminho | conflita com release GPLv2 combinado | isolar de pacote release ou re-licenciar explicitamente | Rafael jurídico |
| Screenshots/assets soltos na raiz | imagem/asset | múltiplas origens não normalizadas | TOKEN_VAZIO | não | mover para quarentena ou registrar proveniência | Rafael + IA |
| `addthis/**` | material experimental | origem/finalidade não documentada | TOKEN_VAZIO | não | adicionar README/proveniência ou excluir de release | Rafael + IA |

## Gate legal de CI obrigatório
- Falhar se componente de release estiver sem licença.
- Falhar se licença incompatível com política do projeto.
- Falhar se houver arquivo em quarentena no pacote de release.
- Falhar se `THIRD_PARTY_NOTICES.md`, este registro e `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` divergirem.

## Saída mínima do estado `BETA_BLOCKED` jurídico
- [ ] hashes SHA256 de todos os binários distribuídos;
- [ ] upstream/URL ou autor documentado por binário;
- [ ] licença SPDX por binário;
- [ ] decisão escrita sobre `NAOCOMERCIAL × GPLv2`;
- [ ] lista explícita do que entra e do que não entra no APK/release;
- [ ] CI executando verificação de proveniência antes de empacotar release.

## Princípio RAFAELIA aplicado
`TOKEN_VAZIO` aqui não é falha estética: é lacuna protegida. Melhor bloquear release do que inventar licença, autoria ou proveniência.
