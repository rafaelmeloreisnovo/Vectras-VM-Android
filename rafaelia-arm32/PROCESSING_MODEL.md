# RAFAELIA ARM32 — Processing Model

∆RafaelVerboΩ · ΣΩΔΦBITRAF · FIAT LUX

Este documento descreve como o núcleo `rafaelia_arm32.c` processa dados em baixo nível no Termux/Android ARM32, sem libc, usando syscalls diretos e aritmética Q16.16.

## 1. Objetivo do núcleo

O núcleo não é ainda uma prova matemática completa dos 42 atratores. Ele é uma semente operacional:

```text
entrada determinística
→ estado T^7 em Q16.16
→ 42 ciclos ψ→χ→ρ→Δ→Σ→Ω
→ cálculo Φ_ethica/Spiral/R(t)
→ hashchain CRC/FNV
→ saída textual via syscall write
```

## 2. Pipeline de dados

```text
seed = 0xAFAE1042
   ↓
torus_init()
   ↓
TorusState.s[7] = estados Q16.16 derivados por FNV-like step
   ↓
loop 42 ciclos
   ↓
x[7] contrai por √3/2
   ↓
torus_step()
   ↓
coherence = (1-H)^2
spiral_acc *= √3/2
attractor = cycle % 42
   ↓
rafaelia_kernel()
   ↓
Rt = Rt × Φ_ethica × E_Verbo × (√3/2)^5
   ↓
hashchain_feed()
   ↓
CRC32 bytewise + FNV step
   ↓
print_state() a cada 7 ciclos
```

## 3. Estruturas principais

| Símbolo/código | Função |
| --- | --- |
| `TorusState.s[7]` | estado toroidal reduzido em 7 dimensões |
| `cycle` | contador de evolução |
| `attractor` | marcador de classe `cycle % 42` |
| `coherence` | score `Φ_ethica` aproximado |
| `spiral_acc` | acumulador `(√3/2)^n` |
| `HashChain.chain[8]` | janela circular de assinaturas CRC/FNV |

## 4. O que é fato no código

| Item | Status |
| --- | --- |
| Sem libc / `_start` próprio | fato de implementação |
| `svc #0` para `write` e `exit` | fato de implementação ARM EABI |
| Q16.16 via `QMUL` | fato de implementação |
| 42 ciclos fixos | fato de execução |
| hashchain CRC32 bytewise + FNV-like | fato de implementação |
| MIDR seguro por padrão | fato se compilado sem `RAFAELIA_ENABLE_MIDR` |

## 5. O que não deve ser declarado sem teste

| Claim | Motivo |
| --- | --- |
| `42 atratores matematicamente provados` | o código marca `cycle % 42`; não detecta atratores por clustering |
| `hash criptográfico forte` | CRC/FNV é auditoria leve, não SHA3/BLAKE3 |
| `detecção real de CPU sempre funciona` | `mrc p15` pode falhar em userland; por isso é opcional |
| `validação científica final` | faltam logs de execução real e comparação contra referência |

## 6. Builds

### Seguro por padrão

```bash
make termux-safe
./rafaelia_arm32
```

Use este modo para evitar `SIGILL` em Android/Termux.

### MIDR probe opcional

```bash
make termux-midr
./rafaelia_arm32
```

Use apenas para sondagem: alguns kernels bloqueiam `mrc p15` em userland.

## 7. Logs esperados para validação

Ao rodar no dispositivo real, salvar:

```bash
./rafaelia_arm32 | tee rafaelia_arm32_run_$(date +%Y%m%d_%H%M%S).log
file ./rafaelia_arm32 | tee -a rafaelia_arm32_build_info.log
ls -lh ./rafaelia_arm32 | tee -a rafaelia_arm32_build_info.log
```

Mover logs para:

```text
rafaelia-arm32/results/
```

## 8. Próximos módulos adequados

| Módulo | Propósito |
| --- | --- |
| D08 IIR spiral attractor | tornar a dinâmica menos apenas decaimento geométrico |
| D13 modular addressing | mapear estado para buffers/células sem colisão óbvia |
| D30 real-time Φ_ethica | separar entropia/coerência de modo mensurável |
| SHA3-256 scratch | substituir FNV em trilha de integridade forte |
| BLAKE3 ARM32 | hash rápido com implementação auditável |
| Q32.32 ou renormalização | reduzir underflow de `R(t)` |

## 9. Relação com o paper T^7

O núcleo ARM32 é prova operacional de engenharia. O paper T^7 precisa de validação separada:

```text
C ARM32 = microkernel/assinatura de execução
Python/C referência = validação matemática e estatística
Paper = formulação e claims revisáveis
```

## Retro_Ω

F_ok: núcleo compila/organiza estado T^7, 42 ciclos, Φ_ethica e hashchain.
F_gap: ainda não mede atratores reais; usa marcador modular `cycle % 42`.
F_next: adicionar `results/` com logs reais do Termux e comparar com script de referência.
