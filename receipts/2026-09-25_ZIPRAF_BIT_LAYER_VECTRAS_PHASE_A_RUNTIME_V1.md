# Receipt — ZIPRAF Bit Layer Vectras Phase A Runtime V1

Date: 2026-09-25  
Producer authority: `RafPolimata@d52afbc38acf6d9580b32cbf9f7f259fa4afdf4b`  
Vector Git blob: `4c3ba2202afb6f62bb465d435273031ebb16c3b3`  
claim_allowed=false

## Delta

Independent Vectras-side Phase-A implementation with:
- no includes/libc/libm/heap/syscall in the core header;
- canonical witness selftest plus exhaustive byte/q sweep;
- ARMv7 and AArch64 raw `_start` exit veneers;
- static freestanding link/audit;
- QEMU user-mode execution wired into the existing Omega LayersBit Safety workflow.

The adapter is intentionally separate from `omega_layersbit.h`; existing
LayersBit semantics are not reclassified as canonical ZIPRAF raster semantics.

## State at write

```text
SOURCE = IMPLEMENTED
HOST_SELFTEST = NOT_RUN
ARMV7_STATIC_ELF = NOT_RUN
ARMV7_QEMU = NOT_RUN
AARCH64_STATIC_ELF = NOT_RUN
AARCH64_QEMU = NOT_RUN
M = TOKEN_VAZIO
G(M) = TOKEN_VAZIO
T-BL-010 = TOKEN_VAZIO
PHYSICAL_ANDROID = TOKEN_VAZIO
```
