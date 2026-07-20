# RAFAELIA — errata e autoridade das frequências

**Data:** 2026-07-20  
**Motivo:** impedir que números visualmente próximos sejam tratados como a mesma constante.

## Regra principal

```text
936 != 939 != 963
```

Cada número possui estado e autoridade próprios.

## Tabela de autoridade

| Valor | Estado | Uso atual/correção |
|---:|---|---|
| `333` | legado | `FrequencyResonanceGrid.FREQ_INNER` |
| `555` | contrato v1 | âncora inferior e termo da razão `777/555` |
| `633` | contrato v1 | âncora intermediária; não confundir com a fórmula `Trinity633` |
| `777` | contrato v1 e legado de scheduler | âncora superior intermediária |
| `936` | supersedido | grafia histórica em `Incluir/sessao_completa_possibilidades_e_matematica.md` |
| `939` | contrato v1 | ápice corrigido |
| `963` | legado | centro da classe Java e limite inferior `fOmega` em C |
| `999` | legado/experimental | exterior da classe Java, fórmula `theta_999` e scheduler antigo |
| `144000` | legado de scheduler | constante inteira existente no cache |
| `288000` | candidato histórico | citado na sessão; sem runtime comprovado |

## Relações canônicas v1

\[
777/555=7/5=1,4
\]

\[
633-555=78,
\qquad
777-633=144,
\qquad
939-777=162.
\]

## Regras de interpretação

1. `633` como frequência/âncora deve possuir nome próprio. `Trinity633` continua sendo uma fórmula de expoentes `6/3/3`.
2. `939` não substitui silenciosamente `963` dentro do perfil legado.
3. `999` não é ápice canônico do contrato v1.
4. `936` deve permanecer somente como proveniência histórica, acompanhado desta errata.
5. Toda configuração nova precisa declarar `unit_id`.
6. Nenhum valor simbólico é automaticamente frequência física medida.

## Perfis

### Perfil legado Java

```text
profile_id = LEGACY_FREQUENCY_RESONANCE_GRID
anchors = 333, 963, 999
cycles = 17
harmonics = 22
```

### Perfil legado C fOmega

```text
profile_id = LEGACY_FOMEGA_BAND
low = 963
high = 999
cycle_len = 6
```

### Contrato harmônico v1

```text
profile_id = RAF_HARMONIC_CLOCK_MATRIX_V1
anchors = 555, 633, 777, 939
supervisor = 10 Hz / 100 ms
confidence_window = 8 cycles
fine_step = 0.1 Hz
coarse_step = 10 Hz
```

## Política de migração

Nenhum perfil existente será reescrito por busca/substituição.

A migração correta é:

```text
ler profile_id
→ aplicar regras da versão
→ produzir recibo
→ migrar explicitamente quando autorizado
```

## Estado

```text
errata_recorded = true
legacy_code_changed = false
new_runtime_implemented = false
claim_allowed = false
```
