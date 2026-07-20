# RAFAELIA BITGHOST — cálculo por camadas sem extração

## Estado

`FATO_DOCUMENTADO`: este arquivo registra o papel do **BitGhost** como camada fantasma no mesmo container.

---

## Ideia central

BitGhost é a camada em que o dado existe no container, mas fica invisível para uma view/layer específica.

```text
o dado está lá
mas aquela camada não usa
não extrai
não copia
não puxa
não reempacota
só ignora para aquele cálculo
```

Isso permite calcular por camadas sobre o mesmo container.

---

## Relação com BITWALK

BITWALK decide a caminhada:

```text
continua
muda direção
pula +1
pula -1
```

BitGhost decide a visibilidade:

```text
visível nesta camada
ou fantasma nesta camada
```

A cadeia de bits permanece no lugar.

---

## Quatro passos canônicos

O núcleo mínimo fica:

| Código | Operação |
|---|---|
| `0` | continua a sequência |
| `1` | muda/inverte a direção |
| `2` | pula um para frente |
| `3` | pula um para trás |

Extensões como `pula dois`, `layer jump` e `color jump` podem existir, mas o núcleo operacional é este.

---

## Serve para quê

A mesma lógica serve em cinco camadas:

| Camada | Uso |
|---|---|
| Processador | próxima posição/instrução lógica |
| Processamento | fase, direção, fluxo e retorno |
| Armazenamento | page, offset, container, rota |
| Memória | view sobre buffer fixo sem cópia |
| Cálculo | mesma cadeia vista por layer/cor/ponto de vista |

---

## Por que não extrair

Extrair dado demais cria:

```text
cópia
movimento de memória
perda de cache
fragmentação lógica
mais banda de memória consumida
```

BitGhost evita isso.

O container guarda tudo, mas a camada calcula apenas o que está visível para ela.

---

## Modelo operacional

```text
container
  ├── page 0: layer 0 / color 0 / visível para view A
  ├── page 1: layer 1 / color 0 / ghost para view A
  ├── page 2: layer 0 / color 1 / ghost para view A
  └── page 3: layer 812 / color 3 / visível para view E
```

Nada precisa sair do container.

A view muda.

---

## API conceitual

No header `Rafaelia/rafaelia_bitwalk.h`, BitGhost aparece como:

```c
raf_bitghost_gate gate;
raf_bitghost_item item;
raf_bitghost_decision d = raf_bg_decide(&gate, &item);
```

A decisão retorna:

```text
visible   → usar no cálculo desta layer
ghosted   → existe, mas ignorar nesta view
extracted → sempre 0 no modelo BitGhost
route_hint → dica determinística de rota
```

---

## Compatibilidade com container molecular

BitGhost encaixa no container molecular:

```text
header identifica
page file roteia
CRC ancora
BITWALK caminha
BITGHOST filtra por layer
payload permanece no lugar
```

---

## Invariantes

| Invariante | Regra |
|---|---|
| Dado fantasma | continua no container |
| Extração | não ocorre no BitGhost |
| Cópia | evitar no hot path |
| Layer invisível | ignorar, não apagar |
| Layer visível | calcular sobre a view |
| CRC | continua verificando bloco/container |
| BITWALK | continua caminhando |

---

## Frase canônica

```text
BitGhost não remove dado.
BitGhost muda a visibilidade.
O cálculo acontece por camada.
O container permanece inteiro.
```

---

## Visibilidade temporal e observação inversa

BitGhost também pode variar com o ciclo e com a direção da caminhada.

O mesmo item pode permanecer Ghost durante a subida e tornar-se visível na descida:

\[
G_{up}(x)=0,
\qquad
G_{down}(x)=1.
\]

Isso não reconstrói nem move o item. A segunda view apenas possui outra combinação de:

```text
layer
color
viewpoint
cycle_index
direction
transform_id
```

### Regra de inversão

A caminhada inversa deve inverter a ordem das transformações:

\[
(M_7M_6\cdots M_0)^{-1}
=
M_0^{-1}\cdots M_6^{-1}M_7^{-1}.
\]

Quando uma transformação não tiver inversa, o runtime precisa preservar resíduo ou marcar `TOKEN_VAZIO`; não pode chamar uma estimativa de recuperação integral.

### Ghost não é temperatura

```text
temperature ∈ {HOT, WARM, COLD}
visibility  ∈ {VISIBLE, GHOST}
```

Assim, são válidos estados como `HOT+GHOST` e `COLD+VISIBLE`. O cache decide residência; BitGhost decide participação na view.

### Relação com a janela de oito ciclos

No contrato harmônico v1, uma decisão Ghost pode fazer parte do recibo de cada posição `C0..C7`. A primeira posição somente se torna elegível para promoção depois do fechamento da oitava e da verificação da subida/descida.

Autoridades relacionadas:

- `docs/RAFAELIA_HARMONIC_CLOCK_MATRIX_CONTRACT_V1.md`;
- `docs/RAFAELIA_HARMONIC_CLOCK_MATRIX_IMPLEMENTATION_INDEX_20260720.md`;
- `docs/RAFAELIA_HARMONIC_CLOCK_MATRIX_GAP_LEDGER_20260720.md`.

```text
F_ok   = semântica Ghost preservada
F_gap  = integração temporal ainda não implementada
F_next = conectar decisão BitGhost ao scheduler em modo sombra
```
