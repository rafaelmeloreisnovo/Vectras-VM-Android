# RAFAELIA CRC-LANES 2→64 — camada de compatibilidade de container

## Estado

`REPOSICIONADO`: não é nome canônico para caminhada de bits.  
`CANÔNICO_PARA_CAMINHADA`: `docs/RAFAELIA_BITWALK_DIRECTION_LAYERS.md`  
`CANÔNICO_PARA_CONTAINER`: `docs/RAFAELIA_CONTAINER_MOLECULAR_STORAGE.md`  
`HEADER_EXPERIMENTAL`: `Rafaelia/rafaelia_crc_lanes.h`

Este documento fica preservado como camada de **compatibilidade estrutural**.

A leitura corrigida é:

```text
CRC verifica e ancora container.
BITWALK caminha sobre a cadeia.
Headers e page files formam a anatomia do arquivo.
Redundância aparente cria rota final correta.
```

---

## Correção conceitual

A analogia com portas serial/paralela, DB9/DB25 e pinos foi útil como parábola didática, mas **não deve ser tratada como especificação física literal**.

Ela representa uma ideia mais geral:

```text
headers
page files
offsets
checksums
CRCs
blocos compactados
rotas internas
saltos estruturais
```

Ou seja: não é apenas caminhar em bits; é também manter compatibilidade com estruturas reais de arquivo, pacotes compactados, imagens/ISOs, ZIPs e containers próprios.

---

## Separação correta dos papéis

| Camada | Papel |
|---|---|
| CRC32C / CRC16 | integridade, ancoragem, verificação de bloco |
| Header | identidade e contrato do bloco |
| Page file / page table | rota e paginação interna |
| Offset | endereço navegável |
| BITWALK | caminhada, inversão, ponto de vista |
| Redundância estrutural | ligações internas tipo molécula/DNA |

---

## O que permanece válido

Permanece válido:

- CRC32C Castagnoli como verificador padrão de integridade;
- CRC16 como checksum leve de ponto;
- p0/p1 como paridade direcional;
- CRC/headers/pages como camada de compatibilidade para containers;
- a ideia de variar a observação por endereço, camada, cor e ponto de vista.

Não permanece como nome canônico:

```text
CRC-LANES para caminhar em bits
```

O nome canônico para caminhar é:

```text
BITWALK
```

O nome canônico para estrutura de arquivo é:

```text
CONTAINER_MOLECULAR_STORAGE
```

---

## Mapeamento corrigido

| Nome/ideia | Papel corrigido |
|---|---|
| `CRC-LANES 2→64` | experimento de compatibilidade/ancoragem |
| `porta` | rota/lane lógica de container |
| `pino` | ponto de ligação estrutural |
| `width CRC` | assinatura/âncora de bloco, não caminhada |
| `par/ímpar` | direção/inversão quando usado pelo BITWALK |
| `DB9/DB25` | analogia didática, não contrato físico |
| `header/page/CRC` | anatomia molecular do arquivo |

---

## Ledger

| Estado | Objeto | Valor |
|---|---|---|
| `FATO` | CRC32C | integridade e ancoragem |
| `FATO` | CRC16 | checksum leve |
| `FATO` | p0/p1 | paridade direcional |
| `REPOSICIONADO` | CRC-LANES | compatibilidade de container |
| `FATO_IMPLEMENTADO` | BITWALK | caminhada/inversão de bits |
| `F_NEXT` | Container molecular | documentado em arquivo canônico separado |
