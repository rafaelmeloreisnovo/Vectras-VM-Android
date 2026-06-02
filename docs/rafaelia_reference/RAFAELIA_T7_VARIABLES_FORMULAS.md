# RAFAELIA/T7 — variáveis, fórmulas e dicionário de dados

Este documento consolida os dados conceituais fornecidos na ordem de serviço em formato auditável. Ele não afirma validade física/financeira por si só; serve como vocabulário para alinhar documentos, protótipos e implementações reais.

## Invariantes mínimos que devem permanecer explícitos

| Invariante | Forma | Observação de engenharia |
|---|---|---|
| Toro de estados | `T^7 = (R/Z)^7`, `s = (u,v,ψ,χ,ρ,δ,σ)` | Representação conceitual; implementação deve declarar escala fixa se virar código. |
| Lyapunov/coerência | `φ = (1 - H) · C` | Não confundir com razão áurea também chamada `φ`; usar nomes distintos em código. |
| Atractores | `|A| = 42` | Bug conhecido: `attractor_table` incompleta não deve ser fechado sem correção. |
| Período | `x_{n+42} = x_n`, `period(BitOmega)=42` | Validar por teste antes de atualizar relatório. |
| Travessia toroidal | `gcd(Δr, R)=1`, `gcd(Δc, C)=1` | Necessário para terminação/cobertura de loops. |
| VOID | `Πmax = max{H | estado != VOID}` | Attractor #22 deve continuar marcado como paradoxo estrutural. |

## Fórmulas normalizadas

| Grupo | Fórmula/variável | Uso documental |
|---|---|---|
| Estado | `s = ToroidalMap(dados, entropia, hash, estado)` | Mapeamento de entrada para estado T7. |
| Suavização | `C_{t+1}=(1-α)C_t+αC_in`, `H_{t+1}=(1-α)H_t+αH_in`, `α=0.25` | Atualização determinística; se virar código, preferir Q16.16. |
| Entropia | `entropy_milli=(unique*6000)/256 + (transitions*2000)/(len-1)` | Evitar `float`; documentar comportamento para `len <= 1`. |
| Hash/FNV | `h=(h xor byte)*0x100000001B3` | Separar de hash conceitual `h=(h xor x)*φ`. |
| CRC/Merkle | `crc=~Σ byte_i*poly(x)`, `R=Merkle(H1,H2,...)` | Definir polinômio e endianness antes de implementação. |
| Geometria | `bits_geom=log2(M*N)`, `C_geom=M*N`, `Spiral(n)=(sqrt(3)/2)^n` | Log/sqrt exigem aproximação fixa se executados no core. |
| Campo/onda | `E=sin(Δθ)cos(Δφ)`, `E_link=α sin(Δθ)cos(Δφ)` | Funções trigonométricas precisam tabela/fixed-point no hot path. |
| Espectro | `R_L = ∫S_L(ω)H_cardio(ω)dω/(||S_L||||H_cardio||)` | Especificação conceitual; requer discretização antes de código. |
| Hamiltoniano | `Ĥ = Σ ε_i |a_i><a_i| + Σ J_ij(|a_i><a_j|+|a_j><a_i|)` | Documento teórico, não contrato de runtime atual. |

## Dicionário de variáveis por domínio

| Domínio | Campos |
|---|---|
| Matriz | `matrix_id`, `row`, `col`, `cell_id`, `value`, `layer`, `state`, `tag14`, `rafbit10`, `epoch`, `cycle`, `timestamp` |
| Combinatória | `pair_id`, `source_a`, `source_b`, `ordered`, `block_2x2_id`, `permutation_id`, `stride`, `modulo`, `orbit_id` |
| Geometria | `x`, `y`, `z`, `radius`, `theta`, `phi`, `distance`, `angle`, `torsion`, `curvature`, `topology_class`, `torus_index` |
| Estatística | `mean`, `median`, `variance`, `std`, `covariance`, `pearson`, `spearman`, `kendall`, `mutual_information`, `entropy`, `fractal_entropy`, `hurst`, `zscore` |
| Temporal | `time`, `lag`, `lead`, `window`, `rolling_mean`, `rolling_std`, `autocorrelation`, `crosscorrelation`, `granger_score`, `regime` |
| Mercado | `ticker`, `asset_type`, `open`, `high`, `low`, `close`, `last`, `volume`, `liquidity`, `spread`, `orderbook_bid`, `orderbook_ask`, `pnl`, `roi`, `tax`, `fee`, `slippage` |
| Social/eventos | `news_id`, `source`, `actor`, `politician`, `company`, `cnpj`, `ceo`, `statement`, `sentiment`, `emotion_score`, `event_type`, `impact_score` |
| Supply chain | `supplier`, `buyer`, `inventory`, `shipment_delay`, `production_index`, `demand_change`, `price_input`, `bottleneck_score`, `bullwhip_effect` |
| Molecular/DNA | `atom_id`, `element`, `nucleotide`, `base_pair`, `x_atom`, `y_atom`, `z_atom`, `charge`, `dipole`, `magnetic_moment`, `field_B`, `bond_length`, `bond_angle`, `torsion_angle`, `electron_density` |
| RAFAELIA | `tag14`, `entropy14`, `sigma_seal`, `plect_state`, `fibR`, `voynich_token`, `70x7_step`, `halfcycle_35`, `base7_value`, `delta_state`, `omega_state` |

## Critérios de promoção para código

1. Declarar unidade, escala e overflow antes de implementar.
2. Usar fixed-point Q16.16 ou inteiro onde o contrato exigir sem `float`.
3. Definir condição de falsificação para cada teorema novo.
4. Provar terminação de loops por limite explícito ou `gcd` quando houver travessia toroidal.
5. Adicionar teste de equivalência/rollback/failover antes de marcar como fonte canônica.
