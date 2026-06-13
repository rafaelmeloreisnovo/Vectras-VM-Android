# RAFAELIA CRC-LANES 2→64 — portas, pinos e endereçamento

## Estado

`FATO_IMPLEMENTADO`: header `Rafaelia/rafaelia_crc_lanes.h`.

Este documento define a extensão **CRC-LANES 2→64** para o núcleo RAFAELIA/Vectra.

A ideia não é substituir o CRC32C Castagnoli já usado no Core. O CRC32C continua sendo o verificador padrão de integridade. A extensão cria uma camada adicional de **codificação geométrica por largura variável**, onde a largura do CRC vira parte da instrução de endereço.

---

## Problema operacional

O sistema já possui:

- CRC32C Castagnoli para estado, memória, BitStacks e cadeia temporal;
- buffer fixo e arena sem malloc;
- endereçamento toroidal 7D;
- camadas L1/L2/BUF/RAM;
- paridade XOR e CRC de volume;
- hit/miss como estado operacional.

O que faltava era separar melhor o papel do CRC em duas famílias:

| Família | Função |
|---|---|
| `CRC32C` | verificação padrão de integridade |
| `CRC-LANES 2→64` | codificação de lane, porta, pino, paridade e endereço |

---

## Princípio

```text
endereço lógico
→ pin lógico
→ porta serial/paralela
→ paridade par/ímpar
→ largura CRC 2..64
→ CRC-lane
→ instrução de roteamento
```

Em vez de todo bloco usar apenas um CRC32, o sistema pode gerar uma assinatura menor ou maior conforme a porta lógica de acesso.

---

## Analogia operacional: serial, paralela, DB9 e DB25

### DB9 lógico

```text
pinos 1..9
serial: widths 2..10
```

Uso típico:

```c
raf_crc_lane_instr ins = raf_crc_lane_db9_serial(buf, len, addr);
```

### DB25 lógico

```text
pinos 1..25
paralelo: widths 27..51
```

Uso típico:

```c
raf_crc_lane_instr ins = raf_crc_lane_db25_parallel(buf, len, addr);
```

### Paridade

```text
addr & 1 = 0 → endereço par
addr & 1 = 1 → endereço ímpar
```

A paridade entra no seed do CRC-lane, de modo que o mesmo payload em endereço par e ímpar gere assinatura diferente.

---

## Contrato de largura

```text
CRC2  → lane mínima, sinal binário compacto
CRC8  → byte-lane / pino leve
CRC16 → lane intermediária
CRC32 → compatível com CRC32C Castagnoli
CRC64 → lane larga / assinatura de rota
```

Larguras não clássicas entre 2 e 64 são válidas como **lanes de instrução**, não como substitutas de padrões externos.

---

## Relação com número par e ímpar

A paridade do endereço não é tratada como detalhe. Ela entra como parte da instrução:

```text
par   → rota A / fase 0
ímpar → rota B / fase 1
```

Isso permite usar CRC como uma forma de codificação leve para decidir:

- caminho serial ou paralelo;
- pino lógico;
- largura do verificador;
- lane de cache;
- rota de buffer;
- separação de blocos pares/ímpares.

---

## Relação com BitRAF

O BitRAF já usa:

```text
10×10×10 + 8 = 1008 pontos
42 bits por ponto
stride 7
paridade XOR
CRC de volume
```

A extensão CRC-LANES permite que cada escrita ou leitura tenha uma assinatura de lane:

```text
posição BitRAF
→ pino lógico
→ width 2..64
→ crc_lane
→ commit/verify/route
```

---

## Relação com hot path

Regra do hot path:

```text
sem malloc
sem heap
sem tabela dinâmica obrigatória
sem dependência externa
```

O header é `header-only`, usa apenas `stdint.h` e `stddef.h`, e mantém tudo como `static inline`.

---

## API mínima

```c
#include "rafaelia_crc_lanes.h"

raf_crc_lane_instr a = raf_crc_lane_db9_serial(buf, len, addr);
raf_crc_lane_instr b = raf_crc_lane_db25_parallel(buf, len, addr);
```

Campos relevantes:

```c
pin     // pino lógico
pins    // total da porta lógica
mode    // serial/paralelo
parity  // par/ímpar
width   // largura CRC selecionada
addr    // endereço lógico
crc     // valor codificado
```

---

## Não confundir

`CRC-LANES 2→64` não é criptografia.

É uma camada de:

```text
integridade leve
+ codificação de endereço
+ roteamento por pino
+ instrução serial/paralela
+ paridade par/ímpar
```

Para prova externa, hash criptográfico continua sendo outro papel.

---

## Próximo encaixe recomendado

1. Usar `raf_crc_lane_encode_addr()` em escritas do BitRAF.
2. Guardar `crc_lane.width`, `crc_lane.pin` e `crc_lane.crc` junto da escrita.
3. Corrigir `par_xor` incremental para não virar duplicata do CRC.
4. Incluir `cycle/core/layer/phase/offset` na cadeia CRC causal.

---

## Ledger

| Estado | Objeto | Valor |
|---|---|---|
| `FATO` | CRC32C Castagnoli | já existe no Core |
| `FATO` | CRC-LANES 2→64 | implementado em header isolado |
| `FATO` | DB9/DB25 lógico | mapeado para pinos/larguras |
| `FATO` | par/ímpar | entra no seed de endereço |
| `LACUNA` | integração no BitRAF hot path | pendente |
| `LACUNA` | teste dedicado | pendente |
| `F_NEXT` | plugar em `bf_write()` | próximo passo |
