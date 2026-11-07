# 001_PONTEIRO — Vectra Ω Hypervisor Current (S2 successor)

READ -> NOVOexport/VECTRA_A32_S2.md -> NOVOexport/VECTRA_HYPERVISOR_ATLAS_V1.md (V1 history) -> hypervisor/autoral/README.md -> rafhv.h/rafhv.c -> rafhv_a32.h/rafhv_a32.c -> tests -> Makefile.

Original base master@cf5483279d425080d0434e32e709550a1fc8fb06. V1 new VM control and H1 host test introduced with additive paths; S2 adds bounded A32 interpreter on same draft feature branch, not APK integrated. No legacy code or license removed.

Evidence local: GCC and Clang each pass 23 control plus 22 encoded A32 assertions; nm -u linked relocatable core empty, ARMv7 freestanding ELF32 EABI5 observed. GitHub exact-head CI/installed Moto/guest boot NOT_RUN until verified. H1 PASS_SCOPED_LOCAL; partial H2 instruction subset PASS_SCOPED_LOCAL; full H2-H6 TOKEN_VAZIO/BLOCKED.

Authority: Vectra code owns local new modules; Termux/Android/QEMU remain provider boundaries; Mapa specific route TOKEN_VAZIO. Rollback: draft PR can be closed, master untouched. claim_allowed=false.

R3=<bounded encoded A32 execution and control tests, no full CPU/guest/hardware claims, instruction differential/exception/MMU/backend and Android gates>.
