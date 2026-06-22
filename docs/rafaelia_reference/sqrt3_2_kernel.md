# RAFAELIA_H_KERNEL — sqrt(3)/2

## Leitura central

`h = sqrt(3)/2 ≈ 0.866025403784` é tratado neste repositório como operador geométrico mensurável para simetria 30°/60°/120°, malha triangular/hexagonal, projeção vetorial e decaimento recursivo.

A regra de honestidade técnica é: `sqrt(3)/2` organiza ciclos e projeções quando existe estrutura angular ou reticulada; ele não substitui probabilidade normal, z-score ou densidade total de empacotamento sem fórmula intermediária.

## Constantes freestanding

| Nome | Valor | Uso |
| --- | ---: | --- |
| `SQRT3_OVER_2` | `0.866025403784` | referência matemática |
| `Q16_SQRT3_OVER_2` | `0xDDB4 = 56756` | filtro fixo Q16.16 |
| `REVERSE_GAIN` | `2/sqrt(3) ≈ 1.154700538379` | reconstrução/expansão |
| `HEX_PACKING_DENSITY_2D` | `pi/(2*sqrt(3)) ≈ 0.906899682117` | densidade correta, distinta de `h` |

## Módulos entregáveis

### 1. `geometry_hex_grid`

- Entrada: `side`, `row`, `col`.
- Fórmula: `x = col + 0.5*(row mod 2)`, `y = row*h`.
- Saída: coordenadas hex/triangulares sem sobreposição.
- Teste: validar `Δy = Q16_SQRT3_OVER_2` em Q16.
- Limite: não afirmar densidade de empacotamento; para círculos usar `pi/(2*sqrt(3))`.

### 2. `recursive_decay_filter`

- Entrada: `R_n`, `Entrada_n`.
- Fórmula: `R_{n+1} = Entrada_n + h*R_n`.
- Saída: memória amortecida com convergência controlada.
- Teste: meia-vida `ln(0.5)/ln(h) ≈ 4.8188417` ciclos.
- Limite: somatório unitário converge para `1/(1-h) ≈ 7.4641016`.

### 3. `signal_6fold_projection`

- Entrada: vetor `(x,y)` e eixo de 30°/60°.
- Fórmula: `cos30 = sin60 = h`, `sin30 = cos60 = 0.5`.
- Saída: projeções direcionais 6-fold para sinais/FFT/amostragem angular.
- Teste: `cos²30 + sin²30 = 1` em tolerância fixa.
- Limite: só usar como simetria angular, não como prova universal.

### 4. `regression_angle_map`

- Entrada: `R²` ou ângulo entre vetor observado e projetado.
- Fórmula: `R² = cos²(theta)`, `sqrt(1-R²)=sin(theta)`.
- Saída: leitura angular de erro/projeção.
- Teste: quando `theta=60°`, `sin(theta)=h`.
- Limite: não mapear `h` diretamente para `1σ`, p-valor ou z-score.

### 5. `risk_buffer_smoother`

- Entrada: `Demanda_n`, `Buffer_n`, ciclos estáveis.
- Fórmula: `Buffer_{n+1}=Demanda_n+h*Buffer_n` e `Risco_filtrado=Risco_bruto*h^n`.
- Saída: suavizador operacional para fila, cache, latência e watchdog.
- Teste: queda para 10% em `ln(0.1)/ln(h) ≈ 16.0078456` ciclos.
- Limite: heurística operacional; estoque de segurança clássico continua `SS=z*σ*sqrt(LT)`.

## Falsificação obrigatória

O kernel deve falhar ou emitir aviso quando:

1. `sqrt(3)/2` for usado como `1σ`, z-score ou probabilidade normal direta.
2. `sqrt(3)/2` for apresentado como densidade ótima de empacotamento de círculos 2D.
3. uma rotina tentar corrigir silenciosamente o VOID do atrator #22.
4. uma implementação nativa usar float em hot path onde o contrato exige Q16.16.

## Contrato Q16.16

```c
#define Q16_ONE 65536
#define Q16_SQRT3_OVER_2 56756
#define RAFAELIA_H_STEP(x_q16) (((x_q16) * Q16_SQRT3_OVER_2) >> 16)
```

Este contrato mantém o caminho de baixo nível branchless, sem heap, sem GC e compatível com ARM32/ARM64 quando transposto para C/ASM.
