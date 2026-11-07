# 001_PONTEIRO — VECTRA Hypervisor Current

READ -> NOVOexport/VECTRA_HYPERVISOR_ATLAS_V1.md -> hypervisor/autoral/README.md -> rafhv.h -> rafhv.c -> tests/rafhv_selftest.c -> Makefile.

2026-11-07 base master cf5483279d425080d0434e32e709550a1fc8fb06; new standalone control core introduced in feature branch, no legacy replacement yet. Local GCC/Clang PASS_SCOPED: 23 assertions each, no undefined symbols, ARMv7 ELF32 EABI5 object. Exact pushed commit CI/APK/physical Moto guest execution = TOKEN_VAZIO until readback and gate.

Authority: Vectra VM consumer implementation, Mapa federated route independent, Termux/QEMU provider external. Risks: no true CPU backend, MMU, KVM/EL2 evidence, IRQ/devices/guest or Android adapter. Legacy stays operational.

R3 = <H1 local checks pass, H2-H6 missing, implement independently verified ARM32 software CPU backend and then staged Android adapter>.
