# VECTRA_RAFCODE_PHI_BRIDGE

## Estado

`FATO_DOCUMENTADO`: ponte conceitual e operacional entre `tools/baremetal/rafcode_phi/`, o engine RMR, BITWALK/BITGHOST, VecBit e emissão C→ASM→hex.

---

## Frase canônica

```text
RAFCODEphi é a micro-base onde C é casca, ASM é núcleo, hex é palavra emitida e VecBit mede a vizinhança da cadeia.
```

---

## Estrutura localizada

```text
tools/baremetal/rafcode_phi/
├── include/rafcode_phi_abi.h
├── c/rafcode_phi_front_shell.c
├── c/rafcode_phi_vecbit.c
└── asm/rafcode_phi_emit_word.S
```

---

## Papéis

| Arquivo | Papel |
|---|---|
| `include/rafcode_phi_abi.h` | ABI autoral C↔ASM, tipos fixos, opcodes, header binário |
| `c/rafcode_phi_front_shell.c` | casca C determinística, token→hex, CRC32C local |
| `c/rafcode_phi_vecbit.c` | distância de Hamming entre palavras e hash-chain FNV-1a |
| `asm/rafcode_phi_emit_word.S` | núcleo ASM que grava palavra 32-bit no buffer |

---

## Contrato C→ASM→hex

```text
mnemônico
→ parser C
→ opcode_hex
→ emit_word_abi
→ store ASM no buffer
→ stats/CRC
→ vecbit/hamming/hash chain
```

O C não é camada ornamental; ele valida e prepara.

O ASM não é detalhe; ele materializa a palavra.

O hex não é string; é forma determinística da instrução/palavra.

---

## Relação com BITWALK

BITWALK caminha sobre bits/posições.

RAFCODEphi trabalha em palavras emitidas.

A ponte natural é:

```text
palavra emitida
→ vizinhança VecBit
→ distância de Hamming
→ rota/caminhada
→ possível BITWALK por palavra/bit/layer
```

Antes de promover BITWALK para `engine/rmr`, comparar com `rafphi_vecbit_verify()` para não duplicar uma semântica já existente.

---

## Relação com BITGHOST

BITGHOST decide visibilidade por layer sem extração.

RAFCODEphi pode servir como caso mínimo:

```text
palavra existe no buffer
mas uma layer pode ignorar
sem copiar
sem reemitir
sem reempacotar
```

---

## Relação com container molecular

`rafphi_bin_header_t` já aponta para a lógica:

```text
magic
version
arch
word_count
crc32c
flags
```

Isso conversa com:

```text
header identifica
CRC ancora
word_count delimita
flags roteiam
payload permanece no buffer
```

---

## Relação com engine/rmr

RAFCODEphi ainda fica em `tools/baremetal/`.

Ele não é automaticamente core, mas pode fornecer:

- ABI mínima;
- emissão ASM;
- VecBit;
- validação por CRC;
- modelo para código sem libc/stdint;
- ponte para `engine/rmr/include` quando estabilizado.

---

## Regras de auditoria

```text
não tratar rafcode_phi como script auxiliar trivial
não trocar ASM por C por estética
não trocar C por ASM sem preservar parser/CRC/stats
não promover para engine/rmr sem contrato de ABI
não ignorar VecBit: ele mede a cadeia
```

---

## Próxima integração sugerida

| Passo | Ação |
|---|---|
| P0 | documentar relação RAFCODEphi ↔ BITWALK ↔ VecBit |
| P1 | criar teste comparativo: hamming/step/route |
| P2 | decidir se `rmr_bitwalk.h` nasce header-only |
| P3 | só depois avaliar `.c` no manifesto RMR |

---

## Ledger

| Estado | Objeto | Observação |
|---|---|---|
| `FATO` | RAFCODEphi existe | localizado em `tools/baremetal/rafcode_phi/` |
| `FATO` | ABI própria | sem dependência forte de libc/stdint |
| `FATO` | VecBit mede vizinhança | Hamming + FNV chain |
| `FATO` | ASM grava palavra | buffer 32-bit direto |
| `F_NEXT` | ponte com BITWALK | comparar semântica antes de promover |

---

## Frase final

```text
RAFCODEphi é pequeno, mas não é simples; ele é a forma mínima C→ASM→hex da cadeia.
```
