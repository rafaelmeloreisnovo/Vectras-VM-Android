# RAFAELIA CRC-LANES 2→64 — registro histórico de analogia

## Estado

`SUPERADO_POR`: `docs/RAFAELIA_BITWALK_DIRECTION_LAYERS.md`  
`HEADER_HISTORICO`: `Rafaelia/rafaelia_crc_lanes.h`

Este documento fica preservado como **registro histórico da analogia**.

A leitura corrigida é:

```text
CRC verifica.
BITWALK caminha.
Paridade decide direção.
Layer muda o ponto de vista.
A cadeia de bits pode permanecer no lugar.
```

---

## Correção conceitual

A analogia com portas serial/paralela, DB9/DB25 e pinos foi útil como parábola didática, mas **não deve ser tratada como especificação física literal**.

O que se queria modelar era:

```text
continua
volta
pula +1
pula -1
pula +2
pula -2
salta por layer
salta por cor/ponto de vista
```

Portanto, a família operacional nova passa a se chamar **BITWALK**, não CRC.

---

## O que permanece válido

Permanece válido:

- CRC32C Castagnoli como verificador padrão de integridade;
- CRC16 como checksum leve de ponto;
- p0/p1 como paridade direcional;
- a ideia de variar a observação por endereço, camada, cor e ponto de vista.

Não permanece como nome canônico:

```text
CRC-LANES para caminhar em bits
```

O nome canônico agora é:

```text
BITWALK
```

---

## Mapeamento antigo → novo

| Nome anterior | Nome corrigido |
|---|---|
| `CRC-LANES 2→64` | `BITWALK` |
| `porta` | modo de visualização |
| `pino` | posição/lane lógica |
| `width CRC` | operador de caminhada |
| `par/ímpar` | direção/inversão |
| `DB9/DB25` | analogia didática, não contrato físico |

---

## Documento canônico atual

O documento canônico atual é:

```text
docs/RAFAELIA_BITWALK_DIRECTION_LAYERS.md
```

O header canônico atual é:

```text
Rafaelia/rafaelia_bitwalk.h
```

---

## Ledger

| Estado | Objeto | Valor |
|---|---|---|
| `FATO` | CRC32C | integridade |
| `FATO` | CRC16 | checksum leve |
| `FATO` | p0/p1 | paridade direcional |
| `SUPERADO` | CRC-LANES como nome canônico | substituído por BITWALK |
| `FATO_IMPLEMENTADO` | BITWALK | caminhada/inversão de bits |
