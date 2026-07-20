# RAFAELIA Harmonic Clock Matrix — índice de implementação

**Data:** 2026-07-20  
**Escopo:** leitura conservadora do código existente  
**Contrato alvo:** `docs/RAFAELIA_HARMONIC_CLOCK_MATRIX_CONTRACT_V1.md`

## Estado resumido

```text
architecture_fragments_present = true
integrated_runtime = false
eight_cycle_direction_toggle = true
eight_cycle_release_barrier = false
bitghost_gate_present = true
bitghost_scheduler_integration = false
10_hz_supervisor = false
adaptive_0_1_or_10_hz_step = false
physical_benchmark = TOKEN_VAZIO
claim_allowed = false
```

## 1. Autoridades localizadas

| Artefato | Função real observada | Estado perante o contrato v1 |
|---|---|---|
| `FrequencyResonanceGrid.java` | grade Java `333/963/999`, 17 ciclos, 22 harmônicas | perfil legado, incompatível com as novas âncoras |
| `FrequencyResonanceGridTest.java` | fixa por teste os valores legados | teste válido do perfil antigo |
| `HdCacheMvp.java` | cache L1/L2/L3/L4, store append-only, scheduler harmônico | parcialmente aderente |
| `HdCacheMvpTest.java` | testes de store, tiers, scheduler e engine | não testa fechamento em oito ciclos |
| `rafaelia_bitwalk.h` | caminhada, inversão, layer/color jump e BitGhost | núcleo funcional isolado |
| `RAFAELIA_BITGHOST_LAYERED_COMPUTE.md` | semântica canônica de Ghost sem extração | aderente; precisa extensão temporal |
| `rafaelia_formulas_core.h/.c` | kernel Q16.16 e banda `963..999` | perfil legado/conflitante |
| `sessao_completa_possibilidades_e_matematica.md` | inventário histórico de ideias e fórmulas | fonte histórica, não autoridade canônica |
| `STATUS_IMPLEMENTACAO_COMPLETO.md` | relatório amplo de maturidade | contém claims fortes e constantes antigas |
| `test_raf_capacity.c` | capacidade de áudio/FFT genérica | útil, mas não mede a máquina harmônica |

## 2. `FrequencyResonanceGrid` — o que existe

A classe atual define:

```text
FREQ_INNER  = 333 Hz
FREQ_CENTER = 963 Hz
FREQ_OUTER  = 999 Hz
CYCLES_INNER = 17
HARMONICS_OUTER = 22
```

Ela oferece:

- harmônicas `963/n` e `963*n`;
- coerência por distância à harmônica mais próxima;
- score agregado das três frequências;
- retroalimentação limitada a `[0,1]`;
- gate chamado `ethicalGate`;
- 17 iterações internas;
- 22 passagens externas.

### Limites

- não usa 10 Hz/100 ms como supervisor;
- não usa anel de oito ciclos;
- não possui `ASCEND/APEX/DESCEND/VERIFY_8`;
- não contém `555/633/777/939`;
- não possui passo `0,1/10 Hz`;
- não usa BitGhost;
- não mede clock físico ou ciclos;
- associa números de frequência a linguagem de cura/ética sem evidência operacional.

Conclusão:

```text
FrequencyResonanceGrid = LEGACY_PROFILE
```

Não deve ser renomeada silenciosamente como implementação do contrato v1.

## 3. `HdCacheMvp.HarmonicScheduler` — o núcleo mais próximo

### Constantes existentes

```text
{12, 144, 288, 144000, 777, 555, 963, 999}
```

### Pesos existentes

```text
freq >= 100000 -> 64
freq >= 1000   -> 16
freq >= 500    -> 8
freq >= 100    -> 4
otherwise      -> 2
```

### Comportamento de oito ticks

O scheduler contém:

```java
boolean up = ((tick / 8) % 2) == 0;
```

Portanto, já existe uma alternância:

```text
8 ticks em uma direção
8 ticks na direção oposta
```

A direção é materializada por ordenação de frequências:

- `up=true`: frequências maiores primeiro;
- `up=false`: frequências menores primeiro.

### O que ainda não existe

- retenção do resultado de `C0` até o fechamento de `C7`;
- hash/raiz da janela de oito ciclos;
- snapshot no ápice;
- comparação da subida com a descida;
- rollback por janela;
- passo de frequência real;
- duração de 100 ms por tick;
- unidade obrigatória por valor;
- âncoras `633` e `939`;
- integração com `raf_bg_decide()`;
- perfil por core;
- medição de jitter.

O scheduler atual é:

```text
weighted ordering + rotation
```

Ele ainda não é:

```text
eight-cycle delayed-commit verifier
```

## 4. Cache e temperaturas

`HdCacheMvp` possui quatro tiers:

```text
L1 = hot
L2 = warm
L3 = historical/slow RAM
L4 = cold mapped tier
```

O enum de evento contém:

```text
NEW, HOT, COLD, EXPIRED, DROPPED, DONE, RETRYING
```

Não existe `GHOST` nesse enum, e isso é correto se os eixos forem separados.

### Regra recomendada

```text
cache tier  = residência
BitGhost    = visibilidade
```

Não adicionar `GHOST` ao enum de temperatura apenas para parecer integrado. A integração deve usar uma máscara/view paralela.

## 5. BitWalk e BitGhost

`Rafaelia/rafaelia_bitwalk.h` já implementa:

- `CONTINUE`;
- `BACK`;
- `FWD1/BACK1`;
- `FWD2/BACK2`;
- `LAYER`;
- `COLOR`;
- wrap circular;
- stride por layer/cor/viewpoint;
- direção derivada de `p0 xor p1`;
- item e gate BitGhost;
- decisão `visible/ghosted/extracted/route_hint`.

Invariante presente:

```text
extracted = 0
```

Limite atual:

- o header fornece primitivas;
- não foi encontrada ligação com `HdCacheMvp.HarmonicScheduler`;
- não foi encontrada barreira de oito ciclos usando as decisões Ghost;
- não foi encontrada projeção volumétrica completa;
- não foi encontrada tabela de transformações reversíveis/resíduos.

## 6. Kernel de fórmulas

O kernel C define:

```text
RAF_FOMEGA_LOW  = 963
RAF_FOMEGA_HIGH = 999
RAF_CYCLE_LEN   = 6
```

Também implementa:

- `Trinity633` como nome de fórmula/expoentes `6/3/3`;
- `theta_999` em uma recorrência;
- banda `963..999`;
- Q16.16;
- operações sem `malloc` e sem `libm` no hot path.

### Distinção importante

`Trinity633` não prova que `633` esteja implementado como frequência operacional. Atualmente ele é nome de uma fórmula:

\[
Amor^6\cdot Luz^3\cdot Consciência^3.
\]

O contrato v1 precisa de constante e unidade próprias para a âncora 633.

## 7. Documento histórico

`Incluir/sessao_completa_possibilidades_e_matematica.md` registra:

```text
144, 999, 936, 777, 555, 155, 1008, 288000
999/936
777/555
hot/warm/cold/ghost
tempo por ciclos
```

Estado correto:

- documento de proveniência histórica;
- `936` supersedido por `939` no contrato v1;
- `777/555` preservado;
- `288000` preservado como candidato;
- não deve ser usado como configuração executável sem perfil versionado.

## 8. Testes existentes

### Cobertura real

- constantes e score do perfil `333/963/999`;
- peso do scheduler;
- geração de schedule;
- processamento de filas;
- hash de payload;
- retries e drops;
- tiers de cache;
- capacidade genérica de amostragem/FFT.

### Testes ausentes

- exatamente oito ciclos antes da primeira liberação;
- pipeline deslizante após aquecimento;
- alternância `ASCEND/DESCEND` observável;
- fechamento com hash/paridades;
- erro em qualquer ciclo bloqueando commit;
- unidade inválida rejeitada;
- `936` rejeitado no perfil v1;
- presença de `555/633/777/939`;
- passo fino/grosso e histerese;
- Ghost na subida e visible na descida;
- reversão de transformações;
- resíduo obrigatório para operação não inversível;
- benchmark de 10 Hz/100 ms;
- benchmark 144/288 kHz;
- comportamento sob thermal throttling.

## 9. O que está pronto para reaproveitar

```text
FATO_IMPLEMENTADO
```

- store append-only com SHA-256;
- tiers de cache e promoção/demissão;
- scheduler ponderado;
- alternância de sentido em blocos de oito ticks;
- primitivas BitWalk;
- decisão BitGhost sem extração;
- Q16.16 e funções low-level;
- testes unitários de partes isoladas;
- contador lógico do scheduler.

## 10. O que está parcialmente pronto

```text
PARCIAL
```

- harmônicas espalhadas em classes diferentes;
- subida/descida como ordenação, não como verificação;
- armazenamento por layers sem view temporal integrada;
- paridades usadas para direção, não para recuperação completa;
- documentação ampla com conflitos de constantes;
- perfis de performance sem recibo de dispositivo.

## 11. O que falta construir

```text
TOKEN_VAZIO
```

1. autoridade única de configuração;
2. `RafHarmonicClockProfileV1`;
3. scheduler monotônico de 100 ms;
4. barreira de oito ciclos;
5. snapshot de ápice;
6. verificador de descida;
7. integração BitGhost;
8. adaptive step com histerese;
9. per-core phase ledger;
10. recibo de benchmark;
11. testes no Android real;
12. export machine-readable dos resultados.

## 12. Ordem de implementação recomendada

```text
1. profile/config + unidades
2. máquina de estados sem efeitos externos
3. oito ciclos em modo sombra
4. recibos e testes
5. BitGhost temporal
6. passo adaptativo
7. paralelismo por core
8. benchmark físico
9. promoção controlada
```

A primeira versão não deve controlar CPU, publicar, apagar ou executar ações irreversíveis. Ela deve apenas observar, simular, medir e emitir recibos.
