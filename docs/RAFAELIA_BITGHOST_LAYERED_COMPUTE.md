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
