# RAFAELIA Harmonic Clock Matrix — contrato canônico v1

## Estado

```text
contract_state = SPECIFICATION
implementation_state = PARTIAL
runtime_proof = TOKEN_VAZIO
claim_allowed = false
```

Este documento fixa a semântica do relógio harmônico, da janela de oito ciclos, da caminhada bidirecional e da visibilidade BitGhost. Ele não declara que o runtime completo já esteja implementado nem que resultados de desempenho tenham sido medidos no dispositivo.

## 1. Autoridade e escopo

Este contrato é a autoridade para a futura integração entre:

- `app/src/main/java/com/vectras/vm/rafaelia/HdCacheMvp.java`;
- `app/src/main/java/com/vectras/vm/rafaelia/FrequencyResonanceGrid.java`;
- `Rafaelia/rafaelia_bitwalk.h`;
- `engine/rmr/include/rafaelia_formulas_core.h`;
- runtime RMR/BitRaf/BitGhost;
- medição física por contador monotônico/ciclos.

A classe `FrequencyResonanceGrid` existente permanece um perfil legado separado. Seus valores `333/963/999`, 17 ciclos e 22 harmônicas não devem ser confundidos com este contrato.

## 2. Relógios encaixados

A máquina possui níveis diferentes de tempo.

### 2.1 Relógio supervisor

```text
frequency = 10 Hz
period = 100 ms
```

A equivalência é:

\[
T_b = 1/f_b = 1/10 = 0,1\,s = 100\,ms.
\]

Cada janela supervisora pode atualizar fase, direção, layer, máscara Ghost, paridade, parâmetros e recibos. Ela não é o clock físico da CPU.

### 2.2 Clock lógico e clock físico

```text
clock_fisico_CPU != clock_logico_RAFAELIA
```

O runtime controla ticks, fases e portadoras lógicas. A frequência física do processador apenas limita quantos passos podem ser executados por segundo e pode variar por DVFS, temperatura, bateria e política do Android.

### 2.3 Portadoras candidatas

Os perfis de alta taxa citados incluem `144 kHz` e `288 kHz`. Eles permanecem candidatos até existir:

- oscilador lógico implementado;
- mapeamento por core;
- contador de ciclos real;
- medição de jitter;
- prova de que a carga cabe na janela de 100 ms;
- recibo por dispositivo.

Não se altera o clock físico do processador por este contrato.

## 3. Âncoras harmônicas corrigidas

A família corrigida nesta sessão é:

```text
555
633
777
939
```

Relações derivadas:

\[
777/555 = 7/5 = 1,4
\]

\[
633-555=78
\]

\[
777-633=144
\]

\[
939-777=162
\]

\[
939-555=384.
\]

### Regra de unidade

Cada perfil deve declarar explicitamente sua unidade:

```text
HZ
KHZ
TICK_INDEX
DIMENSIONLESS_ANCHOR
```

Nenhum código pode interpretar silenciosamente `939`, `633`, `777` ou `555` como Hz ou kHz sem `unit_id` no cabeçalho/configuração.

### Valores legados

- `936` é uma grafia histórica supersedida por `939` neste contrato.
- `963` pertence ao perfil legado `FrequencyResonanceGrid`/`fOmega` e não substitui `939`.
- `999` pode continuar em perfis legados ou experimentais, mas não é o ápice canônico deste contrato v1.

## 4. Passo adaptativo

O varredor possui pelo menos dois passos:

```text
fine_step = 0.1 Hz
coarse_step = 10 Hz
```

A seleção depende da qualidade observada, não de um número mágico isolado.

\[
\Delta f =
\begin{cases}
0,1\,Hz, & q \ge \theta_{fine}\\
10\,Hz, & q \le \theta_{coarse}
\end{cases}
\]

Deve existir histerese:

\[
\theta_{fine} > \theta_{coarse}
\]

para impedir alternância instável entre os dois passos.

O campo `q` deve declarar sua origem, por exemplo:

- SNR;
- coerência;
- erro de reconstrução;
- jitter;
- divergência direta × inversa;
- pressão térmica/orçamento de ciclos.

## 5. Janela de confiança de oito ciclos

A máquina usa um anel lógico de oito posições:

```text
C0 -> C1 -> C2 -> C3 -> C4 -> C5 -> C6 -> C7
^                                       |
|--------------- fechamento -----------|
```

### Invariante de liberação

O primeiro estado candidato não é liberado no instante em que é produzido.

\[
release(C_0) \iff close(C_7) \land invariants\_ok.
\]

O fechamento deve verificar, no mínimo:

- sequência completa `0..7`;
- ausência de salto não registrado;
- raiz/hash da janela;
- paridades e flags;
- direção de subida/descida;
- resíduos preservados;
- orçamento de perda;
- identidade do perfil e unidade.

### Pipeline deslizante

O contrato permite aquecimento inicial de oito janelas e, depois, liberação deslizante:

```text
0..700 ms = enchimento
800 ms    = C0 elegível
900 ms    = C1 elegível
...
```

Isso só é válido se cada janela mantiver sua genealogia e o fechamento da janela correspondente.

## 6. Subida, ápice e descida

Estados mínimos:

```text
ASCEND
APEX_LOCK
DESCEND
VERIFY_8
COMMIT
ROLLBACK
TOKEN_VAZIO
```

### Subida

A subida prepara e transforma estados. Ela não promove resultado final.

\[
X_{up}=M_7M_6\cdots M_1M_0X_0.
\]

### Ápice

O ápice congela um recibo mínimo:

- raiz da entrada;
- raiz candidata;
- ordem das transformações;
- fases;
- máscaras Ghost;
- resíduos;
- parâmetros do perfil.

### Descida

A descida observa o caminho inverso:

\[
X_{down}=M_0^{-1}M_1^{-1}\cdots M_7^{-1}X_{up}.
\]

Quando uma operação não for inversível, o runtime deve preservar resíduo ou marcar a comparação como `TOKEN_VAZIO`; não pode declarar recuperação exata por aproximação.

### Promoção

```text
ASCEND cria candidato
DESCEND verifica candidato
VERIFY_8 autoriza ou bloqueia
COMMIT somente após prova
```

## 7. Matriz temporal e multidimensional

Uma view pode ser descrita por:

\[
V[x,y,z,c,t,o,h]
\]

onde:

- `x,y`: posição na lâmina;
- `z`: layer/slice;
- `c`: cor, canal ou modalidade;
- `t`: ciclo/fase;
- `o`: orientação/viewpoint;
- `h`: hiperforma/transformação.

Uma cópia girada ou dobrada deve, preferencialmente, ser descrita por `base_ref + transform`, não duplicando o corpo inteiro.

## 8. BitWalk e BitGhost

### BitWalk

Define posição, sentido e salto.

### BitGhost

Define observabilidade:

```text
visible = participa desta projeção
Ghost   = continua no container, mas não participa desta projeção
extracted = 0
```

O mesmo item pode ser Ghost na subida e visível na descida:

\[
G_{up}(x)=0,\qquad G_{down}(x)=1.
\]

`Ghost` não significa apagado, corrompido, cold ou descartado.

## 9. Temperatura e visibilidade são eixos distintos

```text
temperature in {HOT, WARM, COLD}
visibility  in {VISIBLE, GHOST}
```

Combinações válidas:

```text
HOT + VISIBLE
HOT + GHOST
WARM + VISIBLE
WARM + GHOST
COLD + VISIBLE
COLD + GHOST
```

O enum de cache e a máscara BitGhost não devem ser fundidos num único estado.

## 10. Estados de resultado

```text
VISIBLE        dado original observado nesta view
GHOST          dado original residente, invisível nesta view
MISSING        posição ausente conhecida
CORRUPTED      bytes presentes, verificação falhou
DERIVED        estimativa produzida por vizinhança/modelo
RECOVERED      original reconstruído e prova conferida
TOKEN_VAZIO    informação insuficiente
FAILED         invariante quebrada
```

`DERIVED` não pode ser promovido para `RECOVERED` sem hash/prova compatível.

## 11. Paralelismo por core

Cada core pode operar uma transformação/fase diferente, mas o resultado precisa de barreira explícita:

```text
core_id
phase_id
matrix_id
transform_id
cycle_index 0..7
monotonic_start/end
input_root
output_root
status
```

A multiplicação de espaço relacional não deve ser confundida com operações físicas executadas.

## 12. Medição obrigatória

Para qualquer claim de desempenho:

- ciclos reais;
- tempo monotônico;
- `p50/p95/p99`;
- jitter;
- operações úteis por passo;
- largura SIMD e lanes realmente usados;
- número de cores ativos;
- temperatura e throttling;
- perdas, overruns e rollbacks;
- hash do binário;
- dispositivo/ABI;
- parâmetros completos do perfil.

```text
estados representáveis != operações executadas por segundo
```

Claims como “trilhões de cálculos por segundo” permanecem `TOKEN_VAZIO` até benchmark físico reproduzível.

## 13. Compatibilidade

Este contrato não muda silenciosamente formatos ou classes existentes.

Uma implementação futura deve usar versão explícita, por exemplo:

```text
profile = RAF_HARMONIC_CLOCK_MATRIX_V1
abi_version = 1
```

Perfis legados continuam legíveis por suas regras originais.

## 14. Critério de saída da especificação

O estado pode passar de `SPECIFICATION` para `IMPLEMENTED` somente quando houver:

1. configuração versionada com unidades;
2. máquina de estados de subida/descida;
3. barreira real de oito ciclos;
4. gate BitGhost integrado;
5. passo adaptativo com histerese;
6. testes determinísticos;
7. teste de inversão e resíduos;
8. benchmark em dispositivo;
9. recibo machine-readable;
10. documentação atualizada sem conflito de autoridade.

---

```text
F_ok   = semântica consolidada
F_gap  = runtime integrado e prova física ausentes
F_next = implementar primeiro em modo sombra, sem efeitos irreversíveis
```
