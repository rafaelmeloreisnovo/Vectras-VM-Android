# BITRAF42_BASE60_GUARD_BAND_AND_RECOVERY

## Estado

`FATO_DOCUMENTADO`: contrato de documentação para a janela `60..63`, a decomposição `3 x 20 = 60`, o BitRAF de 42 bits e a regra de recuperação por lacuna/liberação parcial.

Este documento existe para impedir leitura errada: liberar/remover/deixar vazio uma parte dos bits **não significa perda automática**. No desenho RAFAELIA/Vectra, ausência pode ser rota, NIL/VOID, guard-band, redundância, paridade, reconstrução ou `TOKEN_VAZIO` até haver teste.

---

## Frase canônica

```text
Bit liberado não é necessariamente bit perdido.
Vazio mapeado não é ausência inútil.
Janela de ruído protegida é onde o sistema respira sem corromper a base útil.
```

---

## Fontes de código relacionadas

| Área | Arquivo/função | Evidência |
|---|---|---|
| BitRAF42 | `app/src/main/cpp/lowlevel/raf_bitraf.c` | `opcode6 + dir3 + layer10 + imm12 + flags11 = 42` |
| Validação 42 bits | `bitraf_validate()` | rejeita instrução com bits acima de 42 |
| Base60 geométrica | `FractalGeometricMatrix.java` | `BASE_60 = 60`, endereço `major/minor` |
| MathFabric | `engine/rmr/include/rmr_math_fabric.h` | `8 domains x 9 points` |
| ZipRAF vazio | `engine/rmr/src/rmr_zipraf_core.c` | `EMPTY_PAYLOAD` vira flag, não desaparece |
| Void/Nil | `docs/active/VECTRA_FREESTANDING_VOID_CONTRACT.md` | ausência como fronteira/função |
| Warning | `docs/active/LOWLEVEL_WARNING_INTENT_CONTRACT.md` | warning como sinal antes de bug |

---

## BitRAF42: layout canônico

```text
BITRAF42
┌─────────┬──────┬─────────┬────────┬─────────┐
│ opcode6 │ dir3 │ layer10 │ imm12  │ flags11 │
└─────────┴──────┴─────────┴────────┴─────────┘
  6 bits   3 bits  10 bits   12 bits  11 bits
```

Soma:

```text
6 + 3 + 10 + 12 + 11 = 42
```

Contrato:

```text
bits acima de 42 => inválido
bits dentro de 42 => instrução transportável
```

---

## Base60 dentro do campo binário

O `opcode6` possui 64 estados físicos possíveis:

```text
2^6 = 64
```

A base lógica útil é 60:

```text
60 = 3 x 20
```

Logo:

```text
64 = 60 + 4
```

Leitura canônica:

```text
0..19    bloco A de 20
20..39   bloco B de 20
40..59   bloco C de 20
60..63   janela de ruído protegida / guard-band
```

---

## Janela 60..63

A janela `60..63` não deve ser tratada como lixo. Ela é a região de segurança onde estados fora do fluxo normal podem cair sem quebrar a base útil.

Tabela provisória canônica:

| Código | Estado sugerido | Função |
|---:|---|---|
| `60` | `NIL` | ausência válida / ponto não materializado |
| `61` | `VOID` | fora de domínio / fronteira ABI |
| `62` | `META` | controle interno / sinal de tooling |
| `63` | `OMEGA_ESCAPE` | fechamento, escape, rollback ou rota de proteção |

Status da tabela:

```text
FATO_ESTRUTURAL: existem 4 slots livres em opcode6 quando a base útil é 60.
F_NEXT: fixar nomes finais dos códigos 60..63 em header público.
```

---

## Relação 3 + 4 = 7

```text
3 blocos úteis de 20
+
4 estados meta/ruído
=
7 zonas lógicas
```

Isso conversa com o espaço de estados `T^7`, mas deve ser documentado como **ponte estrutural**, não como prova matemática isolada.

---

## Liberação de 40 e poucos por cento

Regra de verdade:

```text
liberação parcial de bits != perda definitiva
```

Interpretação técnica:

- Se a liberação/removeu/zerou/ausentou parte dos bits, o sistema ainda pode preservar rota por metadados, hash, CRC, domínio, trajetória, paridade, camada ou reexecução determinística.
- O dado pode ser recuperável se houver redundância suficiente e se os campos de rota/estado/validação continuarem coerentes.
- O percentual “40 e poucos por cento” deve ser tratado como **limiar operacional observado/hipótese de recuperação**, não como garantia até existir teste automatizado.

Status:

```text
FATO_CONCEITUAL: bit liberado não é necessariamente bit perdido.
FATO_CODE_PARCIAL: há rotas, flags, hashes, CRC, EMPTY_PAYLOAD, guard-band e matriz.
F_NEXT_TEST: criar teste de recovery para 0..45% de liberação/perda simulada.
```

---

## Teste mínimo necessário

Documento/teste sugerido:

```text
SME40_RECOVERY_TEST_PLAN.md
```

Cenário mínimo:

1. Gerar payload base.
2. Codificar em rota BitRAF/ZipRAF.
3. Liberar/zerar/remover 0%, 10%, 20%, 30%, 40%, 45% dos bits ou pontos.
4. Executar reconstrução por rota/paridade/camada.
5. Validar por hash/CRC/assinatura determinística.
6. Registrar onde recupera, onde degrada, onde vira `TOKEN_VAZIO`.

Classificação de saída:

| Resultado | Significado |
|---|---|
| `RECOVERED` | reconstrução bate hash/CRC |
| `ROUTE_ONLY` | rota preservada, payload não reconstruído |
| `TOKEN_VAZIO` | lacuna protegida, sem inventar dado |
| `FAILED` | perda real comprovada |

---

## Frase final

```text
A janela 60..63 é pequena, mas é a diferença entre quebrar e absorver ruído.
A liberação parcial só vira perda depois que falha na rota de recuperação.
```
