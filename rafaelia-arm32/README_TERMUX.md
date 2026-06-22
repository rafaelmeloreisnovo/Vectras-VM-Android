# RAFAELIA ARM32 — Termux Quick Start
# ∆RafaelVerboΩ | ΣΩΔΦBITRAF

## 1. Instalar dependências no Termux

```bash
pkg update && pkg upgrade -y
pkg install clang make git
```

## 2. Compilar

```bash
# Clonar ou entrar na pasta do repositório
cd Vectras-VM-Android/rafaelia-arm32
make termux-safe
```

### Build alternativo: MIDR probe

```bash
make termux-midr
```

> Use somente para sondagem de hardware: alguns Android/kernels podem bloquear `mrc p15` em userland e gerar `SIGILL`.

## 3. Executar

```bash
./rafaelia_arm32
```

## 4. Saída esperada

```text
╔══════════════════════════════════════════╗
║  RAFAELIA ARM32 — FIAT LUX               ║
║  ∆RafaelVerboΩ · ΣΩΔΦBITRAF              ║
╚══════════════════════════════════════════╝

[CPU] MIDR=0x...
[CPU] Implementer=0x41 (ARM Ltd)
[CPU] PartNum=0xC07 Cortex-A7
[CPU] Arch=F (ARMv7+)

[RAFAELIA] Executando 42 ciclos ψ→χ→ρ→Δ→Σ→Ω ...

--- Ciclo 7 | Atrator 7/42 ---
  Φ_ethica : 0.xx
  Spiral   : 0.xx
  Estado T7: [0.xx, 0.xx, ...]
  R(t)     : 0.xx
[HashChain] xxxxxxxx·xxxxxxxx·...

...

╔══════════════════════════════════════════╗
║  ESTADO FINAL — 42 ciclos completos     ║
╚══════════════════════════════════════════╝
Ω = Amor | ΣΩΔΦBITRAF | RAFCODE-Φ-∆RafaelVerboΩ
```

No build seguro, a linha `MIDR` pode aparecer como fallback (`0x00000000`) para evitar crash por instrução privilegiada/bloqueada em userland.

## Arquitetura interna

| Componente | Descrição |
|------------|-----------|
| `TorusState` | Estado T^7, 7 dims Q16.16 |
| `torus_step()` | Ciclo ψ→χ→ρ→Δ→Σ→Ω completo |
| `rafaelia_kernel()` | R(t+1) = R(t)×Φ×E×(√3/2)^5 |
| `cpu_read_midr()` | MIDR opcional; fallback seguro por padrão |
| `hashchain_feed()` | CRC32+FNV encadeado |
| `sys_write/exit` | syscall EABI svc #0 |

## Constantes Q16.16

| Símbolo | Q16.16 hex | Decimal |
|---------|------------|---------|
| √3/2 | 0xDDB4 | 0.86602 |
| φ | 0x19E04 | 1.61803 |
| α | 0x4000 | 0.25 |

## Validação local antes do PR

```text
make termux-safe
ELF 32-bit LSB executable, ARM, EABI5, statically linked
Tamanho aproximado: 6.4 KiB
```

Execução real em Android/Termux deve ser registrada depois como log de hardware.

## Próximos módulos

- [ ] D08: IIR spiral attractor
- [ ] D13: Modular addressing
- [ ] D30: Real-time Φ_ethica computation
- [ ] SHA3-256 scratch para substituir FNV
- [ ] BLAKE3 v4 thread-safe port ARM32
- [ ] Q32.32 para `R(t)` ou renormalização contra underflow
- [ ] CI opcional para compilar o núcleo com clang ARMv7

---
RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ | Ω=Amor
