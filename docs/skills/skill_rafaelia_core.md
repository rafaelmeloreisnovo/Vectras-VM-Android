---
name: rafaelia_core
domain: toroid_spiral
formula: spiral_q16
arch: X86_64
generated: 20260625T090419Z
ck: 2303
---

# Skill: rafaelia_core

**Domínio:** toroid_spiral | **Formula:** `spiral_q16`

## Invariantes Q16.16
| Constante | Q16.16 | Float |
|-----------|--------|-------|
| √3/2 | 56755 | 0.86602540378 |
| φ | 105965 | 1.61803398875 |
| G_PERIOD | 42 | — |

## Build
```sh
# ARM32 Termux (Moto E7 Power)
gcc -ffreestanding -nostdlib -nostartfiles -O2 \
    -march=armv7-a -mfpu=neon -mfloat-abi=hard \
    -o rafaelia_core skill_rafaelia_core.c -lgcc && ./rafaelia_core

# x86_64
gcc -ffreestanding -nostdlib -nostartfiles -O2 \
    -o rafaelia_core skill_rafaelia_core.c && ./rafaelia_core
```

## Garantias
- Zero malloc · zero heap · zero GC · zero libc
- Branchless: watchdog/failsafe/reset via máscara `-(i32)cond`
- Estado 64B alinhado = 1 linha L1 (32KB)
- CRC32c Castagnoli: integridade por ciclo
- TTL watchdog: auto-reset após 42 ciclos
- Syscall direto: ARM32 swi / ARM64 svc / x86_64 syscall

## Retroalimentação
```
F_ok:   C freestanding + skill card gerados sem overhead
F_gap:  CRC cobre apenas s[7*4=28B]; incluir ttl/ck no futuro
F_next: adicionar CRC32CX HW via __builtin_arm_crc32cw em ARM
```
*RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ | Ω=Amor*
