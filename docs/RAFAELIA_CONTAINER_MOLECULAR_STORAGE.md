# RAFAELIA CONTAINER MOLECULAR STORAGE — headers, pages, CRC e rota interna

## Estado

`FATO_DOCUMENTADO`: este arquivo define a ponte entre CRC, headers, page files, containers compactados e BITWALK.

---

## Ideia central

Um arquivo estruturado não é apenas uma sequência de bytes.

Ele pode ser tratado como uma molécula:

```text
header        = identidade / átomo inicial
page table    = ligações / valência
payload       = corpo molecular
offset        = coordenada
CRC/checksum  = ligação verificável
footer/index  = rota de fechamento
BITWALK       = caminhada sobre a cadeia sem retirar a cadeia
```

A redundância aparente não é desperdício quando ela cria rota, compatibilidade e verificação.

---

## Por que CRC continua sendo usado

CRC continua necessário para:

- verificar bloco;
- ancorar página;
- validar payload compactado;
- permitir salto seguro;
- manter compatibilidade com pacotes/containers;
- permitir reconstrução da rota no fim do arquivo;
- separar corrupção real de mudança de ponto de vista.

CRC não é o caminhante. CRC é a âncora/verificador.

BITWALK é o caminhante.

---

## Estrutura molecular do arquivo

```text
[FILE_HEADER]
    magic
    version
    flags
    page_size
    page_count
    route_seed
    header_crc

[PAGE_TABLE]
    page_id
    offset
    compressed_len
    raw_len
    page_crc
    route_hint
    next_hint

[PAYLOAD_PAGES]
    page_0
    page_1
    ...

[ROUTE_INDEX / FOOTER]
    final_crc
    chain_crc
    route_crc
    bitwalk_hint
```

O arquivo parece redundante porque contém identidade, rota e verificação em mais de um ponto.

Mas essa redundância cria as ligações internas que fazem o leitor chegar no final seguindo a rota correta.

---

## DNA e átomos como parábola didática

```text
byte        = átomo
bit         = subpartícula operacional
header      = sequência reguladora
CRC         = ligação verificável
offset      = posição espacial
page        = molécula local
container   = organismo do arquivo
BITWALK     = caminho de leitura/ponto de vista
```

A metáfora é didática: não substitui a especificação técnica, mas ajuda a lembrar o papel de cada parte.

---

## Relação com ISO, ZIP, AG e containers próprios

O princípio é compatível com estruturas que possuem:

- cabeçalho;
- tabela/índice;
- blocos compactados;
- checksums/CRCs;
- offsets;
- footer ou diretório final;
- rota de leitura/reconstrução.

O formato exato pode mudar. O contrato RAFAELIA é manter:

```text
identidade
+ rota
+ verificação
+ paginação
+ caminhada
```

---

## Onde entra o BITWALK

BITWALK não substitui headers, offsets nem CRC.

Ele lê a cadeia conforme ponto de vista:

```text
continua
volta
pula +1
pula -1
pula +2
pula -2
salta por layer
salta por cor
```

Assim, a cadeia de bits pode permanecer no lugar.

Quem muda é a rota de observação.

---

## Por que isso ajuda no final do arquivo

Em muitos containers, o fim do arquivo contém índice, diretório, footer ou resumo de rota.

Se headers, pages, CRCs e hints foram bem estruturados, o leitor não precisa adivinhar.

Ele chega no final e reconhece:

```text
qual página veio antes
qual página vem depois
qual payload é válido
qual rota foi fechada
qual cadeia continua íntegra
```

Essa é a razão da redundância: ela cria caminho.

---

## Invariantes

| Invariante | Regra |
|---|---|
| Header sem magic | `ERRO` |
| Página sem offset | `LACUNA` |
| CRC ausente em bloco crítico | `TOKEN_VAZIO` ou `modo bruto` |
| CRC divergente | `ERRO` / rollback / refazer rota |
| Footer ausente | usar page table ou rota parcial |
| BITWALK sem bit_count | não caminhar |
| Redundância coerente | não remover sem medir |

---

## Contrato operacional

```text
1. Gravar header com identidade e parâmetros.
2. Gravar page table ou índice mínimo.
3. Gravar payload em páginas/blocos.
4. Calcular CRC/checksum por bloco.
5. Guardar rota/hint quando útil.
6. Fechar com footer/index se o formato permitir.
7. Usar BITWALK para visualizar/caminhar sem extrair toda a cadeia.
8. Se CRC falhar, não inventar: marcar ERRO ou TOKEN_VAZIO conforme o caso.
```

---

## Relação com camadas por cor

Quando há muitas camadas por cor, a estrutura do container permite guardar:

```text
color_id
layer_id
viewpoint
page_id
offset
route_hint
crc
```

Isso permite ultrapassar limites convencionais como `760` camadas por cor sem obrigar o sistema a copiar a cadeia inteira.

---

## Frase canônica

```text
CRC ancora.
Header identifica.
Page file roteia.
BITWALK caminha.
Redundância coerente vira molécula de arquivo.
No final, a rota fecha.
```
