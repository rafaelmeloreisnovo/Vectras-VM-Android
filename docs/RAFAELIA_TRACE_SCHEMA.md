# RAFAELIA Trace Schema

## Header mínimo obrigatório

```csv
t,source,phase,gate,J_n,cluster,delta,C,H,stable_any,escaped,gate_in_peaks,fr_matches_gate,crc
```

Este header mínimo é **obrigatório e imutável na ordem e nos nomes** para compatibilidade entre motores.

## Semântica dos campos obrigatórios

- `t`: índice temporal discreto da amostra/evento (inteiro não negativo).
- `source`: identificador do motor/origem que gerou a linha.
- `phase`: fase lógica atual do ciclo/processamento (string curta padronizada pelo motor).
- `gate`: identificador do gate/regra ativa no passo `t`.
- `J_n`: índice/valor de transição no passo `n` (ex.: passo interno, salto, ou estado-junção conforme o motor).
- `cluster`: rótulo de cluster associado à amostra.
- `delta`: variação principal do passo (estado, energia, erro, ou métrica de diferença definida pelo motor).
- `C`: componente de coerência/capacidade do estado (ex.: `C_{t+1}=(1-\alpha)C_t+\alpha C_{in}`).
- `H`: componente de entropia/harmonia do estado (ex.: `H_{t+1}=(1-\alpha)H_t+\alpha H_{in}`).
- `stable_any`: flag booleana (`0/1` ou `false/true`) indicando estabilidade detectada por qualquer critério habilitado.
- `escaped`: flag booleana indicando fuga de órbita/regime (ou violação de domínio estável).
- `gate_in_peaks`: flag booleana indicando se o `gate` atual coincide com regiões de pico detectadas.
- `fr_matches_gate`: flag booleana indicando se a assinatura/fronteira de frequência (`fr`) está consistente com o `gate`.
- `crc`: checksum/CRC da linha (ou payload associado) para integridade.

## Valores esperados para `source`

Valores canônicos aceitos:

- `gbs3`
- `geolm`
- `geoia`
- `uniao`
- `vectras`
- `qemu`
- `triad`

## Campos opcionais permitidos

Motores podem incluir colunas opcionais adicionais, por exemplo:

- contexto temporal: `timestamp`, `epoch`, `cycle`
- identificação: `trace_id`, `run_id`, `seed`, `token`, `hash`
- métricas geométricas/toroidais: `u`, `v`, `psi`, `chi`, `rho`, `sigma`, `torus_index`
- métricas estatísticas: `mean`, `std`, `entropy`, `hurst`, `zscore`
- integração de ambiente: `device`, `api_level`, `build_id`

> Observação: nomes opcionais devem evitar colisão semântica com os campos mínimos obrigatórios.

## Regra de compatibilidade (explícita)

**Motores podem adicionar colunas, mas nunca quebrar o header mínimo obrigatório**:

1. Os 14 campos mínimos devem existir com os mesmos nomes.
2. A ordem dos 14 campos mínimos deve ser preservada.
3. Colunas extras devem ser anexadas após `crc` (ou mapeadas por parser compatível que preserve o bloco mínimo).
4. Consumidores devem validar primeiro o bloco mínimo antes de processar extensões.

## Exemplos curtos de linha

### TRIAD

```csv
42,triad,lock,g7,11,cA,0.0312,0.8125,0.4375,1,0,1,1,9F32A1BC
```

### GEOLM

```csv
43,geolm,scan,g2,12,cB,-0.0040,0.7980,0.4520,1,0,0,1,10AD77E2
```

### UNIÃO

```csv
44,uniao,merge,g5,13,cU,0.0095,0.8210,0.4410,1,0,1,0,0C44F190
```

### Vectras/QEMU

```csv
45,vectras,vm_step,g9,14,cV,0.0000,0.8300,0.4200,1,0,1,1,7E9910AF
46,qemu,vm_step,g9,14,cQ,0.0000,0.8290,0.4210,1,0,1,1,7E9910B0
```
