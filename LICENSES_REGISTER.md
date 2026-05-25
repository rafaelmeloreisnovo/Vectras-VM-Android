# LICENSES_REGISTER

## Finalidade
Registro normativo para garantir conformidade estrita de licenças, proveniência e atribuição.

## Regras mandatórias
1. Nenhum arquivo entra em distribuição sem licença identificada.
2. Dependência sem licença explícita = **proibida**.
3. Asset (imagem/áudio/fonte/binário) sem autor + licença + origem = **quarentena**.
4. Código autoral novo deve conter cabeçalho mínimo de copyright e licença.
5. Créditos de inspiração não substituem obrigação de licença.

## Tabela de conformidade (preencher e manter em CI)
| Componente | Tipo | Origem | Licença | Compatível? | Ação | Responsável |
|---|---|---|---|---|---|---|
| (preencher) | código/asset/binário | URL ou autor | SPDX | sim/não | manter/substituir/remover | nome |

## SPDX recomendado
- `MIT`
- `Apache-2.0`
- `BSD-2-Clause` / `BSD-3-Clause`
- `GPL-2.0-only` (avaliar impacto de copyleft)

## Gate legal de CI (obrigatório)
- Falhar se componente sem licença.
- Falhar se licença incompatível com política do projeto.
- Falhar se houver arquivo em quarentena no pacote de release.
- Falhar se `THIRD_PARTY_NOTICES.md` e esta matriz divergirem.
