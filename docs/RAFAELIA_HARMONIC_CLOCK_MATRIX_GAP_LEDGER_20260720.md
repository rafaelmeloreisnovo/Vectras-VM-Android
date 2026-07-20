# RAFAELIA Harmonic Clock Matrix — gap ledger

**Data:** 2026-07-20  
**Escopo:** Vectras/RMR/BitRaf/BitGhost/HdCache  
**Estado:** análise conservadora; nenhum claim de performance autorizado

## Resumo

```text
P0 = 8
P1 = 8
P2 = 5
closed = 0
claim_allowed = false
```

Cada gap contém achado, risco, próxima ação e critério de saída.

---

## P0 — bloqueadores de coerência e implementação

### HC-P0-01 — Autoridade de frequência conflitante

**Achado**

Há pelo menos três conjuntos concorrentes:

```text
FrequencyResonanceGrid: 333 / 963 / 999
formulas_core:          963 .. 999
contrato corrigido:     555 / 633 / 777 / 939
```

O documento histórico ainda possui `936`.

**Risco**

Uma inversão de dígitos ou perfil implícito muda fase, banda, score e testes sem erro explícito.

**Próxima ação**

Criar perfil versionado com `unit_id`, `profile_id` e constantes próprias.

**Critério de saída**

- testes rejeitam unidade ausente;
- `936` não é aceito no perfil v1;
- perfil legado continua separado;
- nenhuma constante global ambígua.

---

### HC-P0-02 — Barreira de oito ciclos ausente

**Achado**

O scheduler alterna a direção a cada oito ticks, mas processa eventos imediatamente. Não retém `C0` até `C7`.

**Risco**

Resultado parcial pode ser marcado `DONE` antes da verificação estrutural da janela.

**Próxima ação**

Implementar anel `C0..C7` em modo sombra, com estado candidato e commit atrasado.

**Critério de saída**

- nenhum `COMMIT` antes do fechamento de `C7`;
- erro em qualquer posição bloqueia a janela;
- teste prova pipeline deslizante após aquecimento.

---

### HC-P0-03 — Supervisor 10 Hz/100 ms ausente

**Achado**

`tick()` é chamado pelo consumidor e não possui deadline de 100 ms, jitter ou relógio monotônico de janela.

**Risco**

“10 Hz” permanece linguagem, não comportamento medido.

**Próxima ação**

Adicionar supervisor observacional com timestamp monotônico e sem busy-wait.

**Critério de saída**

- período alvo 100 ms declarado;
- `p50/p95/p99` e jitter emitidos;
- atraso não é escondido por média;
- teste com clock injetável.

---

### HC-P0-04 — Subida/descida sem prova inversa

**Achado**

A subida/descida atual é apenas ordenação ascendente/descendente das layers. Não existe snapshot de ápice nem comparação inversa.

**Risco**

Direção visual pode ser confundida com reversibilidade matemática.

**Próxima ação**

Introduzir estados `ASCEND/APEX_LOCK/DESCEND/VERIFY_8` e ledger de transformações.

**Critério de saída**

- ordem de transformação preservada;
- inversas testadas;
- operação não inversível exige resíduo;
- comparação produz `RECOVERED`, `DERIVED`, `TOKEN_VAZIO` ou `FAILED` corretamente.

---

### HC-P0-05 — BitGhost não integrado ao scheduler

**Achado**

`raf_bg_decide()` existe em header isolado. O scheduler/cache não usa layer, color, viewpoint ou `include_ghost` para decidir a projeção.

**Risco**

O conceito de Ghost fica documental, enquanto o engine processa tudo que está na fila.

**Próxima ação**

Criar adaptador entre decisão BitGhost e seleção de evento, sem copiar payload.

**Critério de saída**

- item Ghost não entra na view atual;
- permanece recuperável no mesmo container;
- `extracted=0` comprovado;
- mudança de view torna o mesmo item visível.

---

### HC-P0-06 — Passo adaptativo sem definição executável

**Achado**

Não existe controle `0,1 Hz` versus `10 Hz`, nem limiares de ruído/coerência.

**Risco**

Varredura pode oscilar, desperdiçar ciclos ou atravessar picos sem medida.

**Próxima ação**

Definir `quality_source`, dois thresholds e histerese.

**Critério de saída**

- passo fino e grosso testados;
- alternância não ocorre em chattering;
- toda troca registra motivo e métrica;
- unidade declarada.

---

### HC-P0-07 — Portadoras 144/288 kHz sem runtime e prova

**Achado**

Há números em constantes e documentos, mas não há oscilador por core, contador de ciclos e recibo físico.

**Risco**

Valor simbólico pode ser promovido como frequência efetivamente executada.

**Próxima ação**

Implementar benchmark isolado, sem efeitos externos, medindo custo por janela.

**Critério de saída**

- frequência lógica real derivada de timestamps/ciclos;
- jitter e overruns registrados;
- throttling observado;
- dispositivo, ABI e hash do binário presentes.

---

### HC-P0-08 — Claims de completude acima da evidência

**Achado**

O relatório de status declara módulos `100%`, `production-ready` e uso em tempo real, embora a integração harmônica, a prova de dispositivo e vários gates ainda estejam ausentes.

**Risco**

Documentação vira autoridade superior ao código e aos testes.

**Próxima ação**

Reclassificar claims por `IMPLEMENTED`, `TESTED_LOCAL`, `TESTED_DEVICE`, `INTEGRATED`, `PRODUCTION_PROVEN`.

**Critério de saída**

Nenhum `production-ready` sem recibo operacional e critérios de promoção explícitos.

---

## P1 — gaps importantes de arquitetura e qualidade

### HC-P1-01 — `633` possui semânticas concorrentes

`Trinity633` é fórmula `6/3/3`; isso não é automaticamente uma frequência. Criar nomes distintos.

### HC-P1-02 — `999` legado sem fronteira clara

`999` aparece em grid, fórmula e scheduler. Definir se é âncora, ângulo, frequência ou perfil legado em cada uso.

### HC-P1-03 — Ética associada a frequência sem base operacional

O `ethicalGate` atual associa score de ressonância a uma banda chamada ética. Ética não deve depender de um número de frequência. Manter o gate técnico e renomear sua função quando o código for revisado.

### HC-P1-04 — Tiers e visibilidade ainda confundidos em documentos

`HOT/WARM/COLD` são residência; `GHOST` é view. Atualizar mapas e diagramas.

### HC-P1-05 — Falta ledger por core

Não há recibo com `core_id`, fase, matriz, transformação, janela e raízes.

### HC-P1-06 — Falta budget de perda por janela

Não há contrato quantitativo que diga o que pode sumir como Ghost, o que é missing e o que é corrupção.

### HC-P1-07 — Falta benchmark de overhead da prova

Hash, snapshot, barreira e BitGhost têm custo não medido.

### HC-P1-08 — Falta ligação com sensores/Conversation Chunks

A máquina ainda não recebe de modo canônico áudio, toque, acelerômetro, texto e tempo sincronizados.

---

## P2 — evolução e pesquisa

### HC-P2-01 — Volume/slicer completo

Não existe ABI completa de volume `x,y,z,c,t,o,h`, composição alfa e projeções.

### HC-P2-02 — Dobra e cópias virtuais

Falta tabela canônica de transform descriptors para rotação, reflexão e dobra sem duplicar payload.

### HC-P2-03 — Recuperação exata por apagamento

Paridades atuais ajudam direção/detecção, mas não demonstram correção de 40–45% de perda real.

### HC-P2-04 — Seleção automática de perfil

Falta comparar 8/17/22/42 ciclos por evidência e custo, sem escolher por preferência simbólica.

### HC-P2-05 — Benchmark multidispositivo

Faltam resultados comparáveis no Moto E7/ARM32 e Realme/ARM64.

---

## Matriz de promoção

| Estado | Requisito mínimo |
|---|---|
| `SPECIFIED` | contrato versionado |
| `IMPLEMENTED` | código compilável e revisão |
| `TESTED_LOCAL` | testes determinísticos com recibo |
| `TESTED_DEVICE` | execução no aparelho com ambiente registrado |
| `INTEGRATED` | BitGhost + scheduler + cache + RMR conectados |
| `PERFORMANCE_PROVEN` | benchmark repetido, incerteza e artefatos |
| `PRODUCTION_PROVEN` | operação sustentada, rollback, segurança e auditoria independente |

Nenhum estágio compensa a ausência do estágio anterior.

## Próximo incremento técnico recomendado

```text
RafHarmonicClockProfileV1
+ unidade explícita
+ estado de oito ciclos em modo sombra
+ recibo determinístico
+ testes sem Android
```

Somente depois:

```text
supervisor 100 ms
+ BitGhost temporal
+ benchmark físico
+ paralelismo por core
```

---

```text
F_ok   = núcleos reutilizáveis localizados
F_gap  = integração e prova ainda incompletas
F_next = fechar HC-P0-01 e HC-P0-02 antes de otimizar performance
```
