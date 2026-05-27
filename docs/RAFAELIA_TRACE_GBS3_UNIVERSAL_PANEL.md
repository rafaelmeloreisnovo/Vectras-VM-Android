# RAFAELIA TRACE + GBS3_COLOR — Painel Universal de Ciclos, ΔP e Melhor Resultado

**Status:** especificação operacional para Codex / implementação incremental  
**Fonte de verdade inicial:** arquivos enviados pelo autor (`gbs3_color.c.txt`, `geolm.txt`, `geoia.txt`, `uniao-2.txt`) + runtime Vectras/QEMU já mapeado por código.  
**Objetivo:** transformar `gbs3_color` no painel universal de leitura de resultados dos ciclos RAFAELIA, sem alterar a matemática/invariantes existentes.

---

## 0. Regra de ouro

Este documento **não manda reescrever a matemática**.

Não substituir:

- T7 / toro 7D;
- Q16;
- EMA;
- CRC32C;
- RAF_FIBO;
- gates;
- ΔP;
- clusters semânticos;
- arena estática;
- dual-mode libc/freestanding;
- ARM32/NEON;
- B0-B18;
- KATs cripto;
- QEMU/Vectras runtime traces.

A tarefa é **padronizar emissão, leitura e comparação de traces**, para que `gbs3_color` enxergue resultados de vários motores usando uma métrica comum.

---

## 1. Leitura correta do `gbs3_color`

`gbs3_color.c` não é apenas interface colorida.

Ele é atualmente:

```text
GBS3_COLOR = TRIAD_LAB + RAF_FIBO + ARENA + ΔP + sweep + painel Termux
```

Funções/papéis observados:

1. Menu BBS/Termux com opções:
   - `(1) trace stats`
   - `(2) sweep top`
   - `(3) raf fibo`
   - `(4) arena`
   - paths, color, emoji, exit
2. Helpers CSV:
   - `parse_csv_header_map`
   - `col_index`
   - `split_csv_row`
   - `bar60`
3. Métrica central:
   - `compute_trace_deltaP(trace_path)`
4. Visualização/simulação:
   - arena interativa;
   - agentes IA;
   - player;
   - gate por RAF_FIBO;
   - comportamento influenciado por `deltaP`.

Interpretação correta:

```text
gbs3_color não é núcleo matemático isolado.
gbs3_color é o observatório dos resultados.
```

Ele deve responder:

```text
quais gates/ciclos produzem estabilidade melhor que o fundo?
qual configuração tem melhor ΔP?
qual motor está produzindo melhor coerência operacional?
```

---

## 2. Métrica ΔP — contrato canônico

A fórmula real já usada pelo `gbs3_color` é:

```text
p_peaks = stable_in_peaks / total_peaks
p_non   = stable_outside_peaks / total_non_peaks
ΔP      = p_peaks - p_non
```

Interpretação:

```text
ΔP > 0  → gates/picos têm vantagem real de estabilidade
ΔP ≈ 0  → gates/picos não melhoram o fundo
ΔP < 0  → gates/picos pioram ou estão invertidos
```

Essa métrica deve virar API comum, não ficar presa ao `gbs3_color`.

Criar:

```text
raf_trace_metrics.h
raf_trace_metrics.c
```

API mínima:

```c
typedef struct RafTraceSummary {
    long rows;
    long stable_count;
    long escaped_count;
    long peak_count;
    long peak_stable_count;
    long non_peak_count;
    long non_peak_stable_count;
    long match_count;
    double p_peaks;
    double p_non;
    double deltaP;
    long jhist[16];
    long gate_hist[64];
} RafTraceSummary;

double raf_trace_deltaP(const char *path);
int raf_trace_summary(const char *path, RafTraceSummary *out);
```

Reaproveitar/adaptar do `gbs3_color`:

```text
parse_csv_header_map
col_index
split_csv_row
compute_trace_deltaP
```

---

## 3. Schema universal de trace RAFAELIA

Criar documento:

```text
docs/RAFAELIA_TRACE_SCHEMA.md
```

Header mínimo canônico:

```csv
t,source,phase,gate,J_n,cluster,delta,C,H,stable_any,escaped,gate_in_peaks,fr_matches_gate,crc
```

Campos obrigatórios:

| Campo | Tipo | Sentido |
|---|---:|---|
| `t` | inteiro | ciclo, step, frame, evento ou linha temporal |
| `source` | string | motor emissor: `gbs3`, `geolm`, `geoia`, `uniao`, `vectras`, `qemu` |
| `phase` | inteiro/float | fase do ciclo |
| `gate` | inteiro | gate discreto |
| `J_n` | inteiro | classe/estado discreto usado pela triad/gate |
| `cluster` | inteiro/string | cluster semântico ou operacional |
| `delta` | float/int | distância, loss, latência, variação ou erro |
| `C` | float/int | coerência |
| `H` | float/int | entropia |
| `stable_any` | 0/1 | estabilidade detectada |
| `escaped` | 0/1 | fuga, erro, timeout, instabilidade |
| `gate_in_peaks` | 0/1 | gate pertence aos picos relevantes |
| `fr_matches_gate` | 0/1 | RAF_FIBO/Fibonacci-Rafael bate com gate |
| `crc` | hex/int | CRC32C/FNV/hash/assinatura do evento |

Campos opcionais por motor:

```csv
seed,R,r,alpha,beta,kappa,lam,zcap,arch,mode,vm_id,profile,latency,p50,p95,p99,loss,token_id,repo,commit,abi
```

Regra:

```text
Todo motor pode adicionar campos extras, mas não pode quebrar o header mínimo.
```

---

## 4. Como cada arquivo entra no sistema

### 4.1 `gbs3_color.c`

Papel:

```text
Painel BBS/Termux de leitura de traces, ΔP, sweep, RAF_FIBO e arena.
```

Entradas atuais:

```text
out/triad_trace.csv
out_sweep/results.csv
```

Entradas novas:

```text
out/geolm_trace.csv
out/geoia_trace.csv
out/uniao_trace.csv
out/vectra_qemu_trace.csv
out/triad_trace.csv
```

Menu novo proposto:

```text
(1) trace stats
(2) sweep top
(3) raf fibo
(4) arena
(5) universal trace
(6) compare traces
(7) geolm clusters
(8) vectras/qemu trace
(p) paths
(c) color on/off
(e) emoji on/off
(0) exit
```

Novas funções:

```c
static void show_universal_trace(const char *path);
static void compare_traces(const char **paths, int n);
static void show_geolm_clusters(const char *trace_path);
static void show_vectras_qemu_trace(const char *trace_path);
```

Critério:

```text
O menu antigo deve continuar funcionando.
Nenhum cálculo antigo deve ser removido.
```

---

### 4.2 `geolm.txt`

Papel:

```text
GEOLM visual/geográfico: imagem → features 7D → T7 Q16 → cluster → catálogo → EMA centroid update.
```

Elementos já descritos no arquivo:

- extrai features de imagem;
- histograma RGB;
- geometria;
- pHash;
- T7 toroidal Q16;
- CRC32C;
- clusters;
- KNN k=3;
- EMA de centróides;
- arena estática;
- sem malloc/heap/GC;
- branchless em hot paths;
- clusters `C0..C5`:
  - `TOROIDE`
  - `MATEMATICA`
  - `VVM`
  - `CICLO`
  - `SIMBOLICO`
  - `APLICADO`

Adicionar emissão:

```text
out/geolm_trace.csv
```

Evento por `learn`, `query`, `catalog_insert`, `ema_update_centroid`:

```csv
t,source,phase,gate,J_n,cluster,delta,C,H,stable_any,escaped,gate_in_peaks,fr_matches_gate,crc
```

Mapeamento sugerido:

| Campo | GEOLM |
|---|---|
| `source` | `geolm` |
| `phase` | ciclo interno ou `t % 42/56` |
| `gate` | RAF_FIBO gate ou gate do cluster |
| `J_n` | classe discreta derivada do gate |
| `cluster` | cluster classificado `C0..C5` |
| `delta` | distância T7/L1 toroidal |
| `C` | coerência inversa da distância |
| `H` | entropia/proxy do vetor |
| `stable_any` | distância abaixo do limiar ou cluster consistente |
| `escaped` | erro de parse, imagem inválida, dist acima do limiar |
| `gate_in_peaks` | gate ∈ picos definidos |
| `fr_matches_gate` | RAF_FIBO bate com gate |
| `crc` | CRC32C da entrada/catálogo |

---

### 4.3 `geoia.txt`

Papel:

```text
GeoLM textual/transformer ARM32: arena, vocab, tokenizer, embeddings, atenção vetorial, forward, trainer, ingestão, REPL, save/load.
```

Elementos canônicos:

- ARM32 Termux Android 10;
- arena 64MB;
- zero GC;
- tokenizer/vocab;
- embeddings geométricos;
- atenção vetorial;
- forward/loss/backprop;
- trainer;
- ingestão JSON/texto;
- REPL;
- save/load pesos.

Adicionar emissão:

```text
out/geoia_trace.csv
```

Mapeamento sugerido:

| Campo | GEOIA |
|---|---|
| `source` | `geoia` |
| `phase` | step/ciclo de treino ou geração |
| `gate` | RAF_FIBO gate |
| `J_n` | estado de treino/generation gate |
| `cluster` | classe textual/semântica se houver |
| `delta` | loss, variação de loss ou mudança de logits |
| `C` | coerência de geração, inverso normalizado da loss |
| `H` | entropia dos logits/tokens |
| `stable_any` | queda consistente da loss ou saída válida |
| `escaped` | NaN, overflow, OOM, token inválido |
| `gate_in_peaks` | gate em pico |
| `fr_matches_gate` | RAF_FIBO bate com gate |
| `crc` | CRC/FNV do lote/estado/pesos |

---

### 4.4 `uniao-2.txt`

Papel:

```text
RAFAELIA UNIÃO: núcleo de convergência, validação, segurança, bench, cripto, rollback e infraestrutura.
```

Elementos canônicos:

- B0-B18;
- dual-mode libc/freestanding;
- syscall/write;
- arena align;
- hardware profile;
- GPU sysfs no-crash;
- CRC32C KATs;
- ECC 8x8 Hamming;
- memory bandwidth;
- cache latency;
- NEON dot vs scalar;
- watchdog FSM;
- rollback CRC snapshot;
- dispatch pipeline;
- bench p50/p95/p99;
- SHA-256;
- HMAC-SHA256;
- AES-128;
- ChaCha20;
- SPSC lock-free ring;
- slab mempool O(1).

Adicionar emissão:

```text
out/uniao_trace.csv
```

Mapeamento sugerido:

| Campo | UNIÃO |
|---|---|
| `source` | `uniao` |
| `phase` | índice B0-B18 ou ciclo de bench |
| `gate` | gate derivado de Bn/ciclo |
| `J_n` | classe do teste |
| `cluster` | `crypto`, `memory`, `cache`, `neon`, `watchdog`, `rollback`, `dispatch` |
| `delta` | variação de latência, erro, throughput delta |
| `C` | estabilidade do KAT/bench |
| `H` | dispersão p95/p99 ou falhas |
| `stable_any` | teste PASS |
| `escaped` | FAIL, timeout, mismatch |
| `gate_in_peaks` | gate em pico |
| `fr_matches_gate` | RAF_FIBO bate com gate |
| `crc` | CRC32C/FNV/SHA truncado do teste |

---

### 4.5 Vectras/QEMU

Papel:

```text
Runtime Android/QEMU/PROOT: preflight, resolução de binário, start service, proot, QMP/VNC/X11/headless, boot, stop/error.
```

Criar emissão:

```text
out/vectra_qemu_trace.csv
```

Eventos mínimos:

```text
preflight
qemu_binary_resolved
proot_started
qmp_ready
vnc_ready
x11_ready
boot_ready
stopped
error
```

Header:

```csv
t,source,phase,gate,J_n,cluster,delta,C,H,stable_any,escaped,gate_in_peaks,fr_matches_gate,crc,vm_id,arch,profile,mode,event
```

Mapeamento sugerido:

| Campo | Vectras/QEMU |
|---|---|
| `source` | `vectras` ou `qemu` |
| `phase` | fase do launch / boot / poller |
| `gate` | RAF_FIBO/phase gate |
| `J_n` | estado discreto do launch |
| `cluster` | `preflight`, `qemu`, `proot`, `qmp`, `vnc`, `x11`, `boot`, `error` |
| `delta` | latência desde fase anterior ou erro normalizado |
| `C` | coerência operacional da fase |
| `H` | entropia/risco/ruído de launch |
| `stable_any` | fase concluída com sucesso |
| `escaped` | erro, timeout, QEMU missing, PROOT fail |
| `crc` | hash do command contract ou runtime contract |
| `vm_id` | VM atual |
| `arch` | arquitetura guest/host |
| `profile` | perfil RAFAELIA/QEMU |
| `mode` | VNC/SPICE/X11/headless |

---

## 5. Arquitetura final pretendida

```text
GEOLM       → out/geolm_trace.csv
GEOIA       → out/geoia_trace.csv
UNIÃO       → out/uniao_trace.csv
Vectras/QEMU→ out/vectra_qemu_trace.csv
TRIAD       → out/triad_trace.csv

Todos → raf_trace_metrics.c/h → gbs3_color universal panel
```

Fluxo:

```text
motor executa
→ emite trace universal
→ raf_trace_metrics calcula ΔP/resumo
→ gbs3_color mostra stats/top/arena/comparação
→ Codex/humano escolhe melhor regime
```

---

## 6. Critérios de pronto

### Fase 1 — Métrica comum

- [ ] criar `raf_trace_metrics.h`;
- [ ] criar `raf_trace_metrics.c`;
- [ ] mover/adaptar cálculo ΔP;
- [ ] manter `gbs3_color` compilando;
- [ ] fixtures CSV mínimas;
- [ ] teste de trace vazio;
- [ ] teste de header ausente;
- [ ] teste de ΔP conhecido.

### Fase 2 — Schema

- [ ] criar `docs/RAFAELIA_TRACE_SCHEMA.md`;
- [ ] definir header mínimo;
- [ ] documentar campos opcionais;
- [ ] documentar mapeamento por motor.

### Fase 3 — GBS3 universal

- [ ] menu `(5) universal trace`;
- [ ] menu `(6) compare traces`;
- [ ] menu `(7) geolm clusters`;
- [ ] menu `(8) vectras/qemu trace`;
- [ ] preservar opções antigas.

### Fase 4 — Emissão por motores

- [ ] GEOLM emite `out/geolm_trace.csv`;
- [ ] GEOIA emite `out/geoia_trace.csv`;
- [ ] UNIÃO emite `out/uniao_trace.csv`;
- [ ] Vectras/QEMU emite `out/vectra_qemu_trace.csv`.

### Fase 5 — Comparação de resultado

- [ ] `gbs3_color` ordena traces por `deltaP`;
- [ ] mostra `p_peaks`, `p_non`, `deltaP`, rows, escaped, stable;
- [ ] gera ranking de motores/configurações;
- [ ] exporta resumo em Markdown/CSV.

---

## 7. Comandos esperados

Termux/ARM32:

```bash
clang -x c -O2 -std=c11 gbs3_color.c raf_trace_metrics.c -o gbs3_color
./gbs3_color
```

Teste simples:

```bash
make test-trace
```

Ou sem Makefile:

```bash
clang -x c -O2 -std=c11 tests/test_trace_metrics.c raf_trace_metrics.c -o test_trace_metrics
./test_trace_metrics
```

---

## 8. Prompt operacional para Codex

```text
Você está no repositório Vectras-VM-Android e deve integrar os arquivos/motores:
- gbs3_color.c
- geolm.txt / geolm_core.c
- geoia.txt / GeoLM textual ARM32
- uniao.txt / rafaelia_uniao.c
- runtime Vectras/QEMU

Objetivo:
Transformar gbs3_color no painel universal RAFAELIA de ciclos, gates, ΔP e melhor resultado.

Não reescrever a matemática.
Não substituir T7/Q16/EMA/CRC/RAF_FIBO por estatística genérica.
Não remover invariantes.
Não apagar comentários de licença/invariante.
Não quebrar Termux ARM32.

Implementar por etapas:

1. Criar raf_trace_metrics.h/c.
2. Mover/adaptar compute_trace_deltaP e helpers CSV.
3. Criar docs/RAFAELIA_TRACE_SCHEMA.md.
4. Atualizar gbs3_color com menus 5-8.
5. Criar fixtures em tests/fixtures/.
6. Criar testes para deltaP/header vazio/header ausente.
7. Adicionar exportadores de trace para GEOLM, GEOIA, UNIÃO e Vectras/QEMU.
8. Garantir que gbs3_color antigo continua compilando e funcionando.

Arquivos de saída obrigatórios:
- raf_trace_metrics.h
- raf_trace_metrics.c
- docs/RAFAELIA_TRACE_SCHEMA.md
- docs/RAFAELIA_TRACE_GBS3_UNIVERSAL_PANEL.md
- tests/fixtures/triad_trace.csv
- tests/fixtures/geolm_trace.csv
- tests/fixtures/uniao_trace.csv
- tests/fixtures/vectra_qemu_trace.csv
- tests/test_trace_metrics.c

Critério final:
Rodar no Termux ARM32:
clang -x c -O2 -std=c11 gbs3_color.c raf_trace_metrics.c -o gbs3_color
./gbs3_color

E rodar testes:
make test-trace
```

---

## 9. Fronteira honesta

Este documento especifica a integração de traces e métrica universal.

Ele não afirma que todos os motores já emitem o trace comum.
Ele define o que o Codex deve implementar para que isso aconteça.

Estado esperado após implementação:

```text
GBS3_COLOR deixa de ser apenas painel TRIAD isolado
e vira observatório universal RAFAELIA:
GEOLM + GEOIA + UNIÃO + Vectras/QEMU + TRIAD
```

---

## 10. Síntese

```text
GBS3_COLOR = visão
raf_trace_metrics = métrica
RAFAELIA_TRACE_SCHEMA = contrato
GEOLM/GEOIA/UNIÃO/Vectras = emissores
ΔP = critério de melhor resultado
```

A melhor forma de usar esses dados é padronizar a emissão dos ciclos e comparar `ΔP` entre motores, fases, gates, clusters e perfis.
