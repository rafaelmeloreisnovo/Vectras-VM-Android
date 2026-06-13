# RAFAELIA BITWALK — direção, inversão e camadas de visualização

## Estado

`FATO_IMPLEMENTADO`: header `Rafaelia/rafaelia_bitwalk.h`.

Este documento corrige a nomenclatura anterior: a família nova **não deve ser chamada de CRC**.

CRC permanece com seu papel clássico no Core:

```text
integridade
verificação
commit
rollback
cadeia causal
```

A nova família é **BITWALK**:

```text
caminhada na cadeia de bits
inversão de direção
pulo +1 / -1
pulo +2 / -2
visualização por layer/cor/ponto de vista
```

---

## Ideia central

A cadeia de bits não precisa ser retirada, expandida ou copiada para ser observada.

O observador muda:

```text
posição
sentido
pulo
camada
cor
ponto de vista
```

Assim o mesmo dado pode revelar camadas diferentes dependendo do operador de caminhada aplicado.

---

## Operadores básicos

| Operador | Sentido operacional |
|---|---|
| `CONTINUE` | continua no sentido atual |
| `BACK` | volta / inverte direção |
| `FWD1` | pula um para frente |
| `BACK1` | pula um para trás |
| `FWD2` | pula dois para frente |
| `BACK2` | pula dois para trás |
| `LAYER` | salto calculado pela camada/ponto de vista |
| `COLOR` | salto calculado por cor/camada cromática |

---

## Relação com p0/p1

A paridade dupla continua sendo útil, mas agora não é chamada de CRC.

```text
p0 = paridade dos bits pares
p1 = paridade dos bits ímpares
```

Regra simples:

```text
p0 ^ p1 = 0 → continua
p0 ^ p1 = 1 → volta
```

Isso casa com o motor existente em que paridade decide se o estado avança ou retorna.

---

## Brincadeira operacional

A brincadeira vira método:

```text
0 → continua
1 → volta
2 → pula +1
3 → pula -1
4 → pula +2
5 → pula -2
6 → layer jump
7 → color jump
```

Isto permite usar pequenos códigos para percorrer uma cadeia sem fazer salto fixo de 32 bits.

---

## Por que abandonar o nome CRC aqui

Porque CRC é verificador.

BITWALK é operador.

| Nome | Papel |
|---|---|
| `CRC32C` | integridade forte padrão |
| `CRC16` | checksum leve de ponto |
| `p0/p1` | paridade direcional |
| `BITWALK` | caminhada/inversão/visualização |

A mistura entre eles pode existir, mas o nome precisa preservar o papel.

---

## Camadas por cor acima de 760

Antes, a visualização podia parar em torno de `760` camadas por cor como limite operacional/convenção.

Com BITWALK, `760` deixa de ser teto rígido e vira apenas uma referência de stride/camada:

```text
color_layers = 760          → referência antiga
color_layers > 760          → permitido
layer + color + viewpoint   → define salto lógico
```

A cadeia de bits permanece no lugar. O que muda é o caminho de leitura.

---

## Exemplo conceitual

```text
mesmo payload
mesmo buffer
mesmo endereço-base

view A: continua
view B: volta
view C: pula +1
view D: pula -2
view E: salta pela camada 812 da cor 3
```

Não há necessidade de extrair a cadeia inteira. A leitura muda por operador.

---

## API mínima

```c
#include "rafaelia_bitwalk.h"

raf_bitwalk_view v = {
    .bit_count = 4096,
    .pos = 42,
    .layer = 812,
    .color = 3,
    .viewpoint = 7,
    .color_layers = 1024
};

raf_bitwalk_step_result r = raf_bw_step(v, RAF_BW_LAYER);
```

Para usar código pequeno:

```c
raf_bitwalk_step_result r = raf_bw_step_from_code(v, code);
```

---

## Relação com BITRAF

BITRAF já trabalha com:

```text
pontos append-only
p0/p1
slot10
sym20
noise
crc16 leve
Top-42
estado D/I/P/R
```

BITWALK adiciona a camada de leitura:

```text
ponto
→ paridade
→ operador
→ próxima posição lógica
→ layer/cor/ponto de vista
```

---

## Ledger

| Estado | Objeto | Valor |
|---|---|---|
| `FATO` | CRC32C | permanece integridade |
| `FATO` | CRC16 | checksum leve de ponto |
| `FATO` | p0/p1 | paridade direcional |
| `FATO_IMPLEMENTADO` | BITWALK | operador de direção/camada |
| `FATO` | 760 | referência, não teto rígido |
| `F_NEXT` | integrar BITWALK no BITRAF | pendente |

---

## Frase canônica

```text
CRC verifica.
BITWALK caminha.
Paridade decide direção.
Layer muda o ponto de vista.
A cadeia de bits pode permanecer no lugar.
```
