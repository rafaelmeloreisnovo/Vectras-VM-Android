# ZIPRAF Bit Layer Raster Bridge V1

State: BRIDGE_SPEC / PHASE_A_RUNTIME_IMPLEMENTED_UNTESTED  
claim_allowed=false

## Existing producer evidence

- `Incluir/omega_layersbit.h`: 16 layers x 256 bits, fold, Q16 state.
- `app/src/main/java/android/androidVNC/VncCanvas.java`: pixel / bytesPerPixel framebuffer path.
- Existing ZIPRAF runtime work remains the physical I/O authority.

## Binding

Vectra consumes the canonical state:

```text
I(W,q,M) = R(sum_k 2^k[M dot L_k], G(M), W)
```

Vectra is responsible only for raster/framebuffer projection and parity tests. It must not redefine ZIPRAF binary ABI.

## Required adapter tests

1. W={30,60,120}.
2. q={1,2,4,8}.
3. complete, missing, corrupt and out-of-order M.
4. parity against canonical vectors.
5. fail closed on ambiguous G(M).

F_next: add adapter only after canonical vectors land in RafPolimata.


## Phase A runtime successor — 2026-09-25

Producer reference authority:
- `RafPolimata@d52afbc38acf6d9580b32cbf9f7f259fa4afdf4b`;
- vector Git blob `4c3ba2202afb6f62bb465d435273031ebb16c3b3`.

Vectras now carries an independent geometry-free adapter in
`Incluir/zipraf_bit_layer_phase_a_v1.h`. It is deliberately separate from
`omega_layersbit.h`: the existing 16×256 LayersBit engine is not relabeled as
the canonical ZIPRAF Phase-A raster.

The selftest covers the producer witness bytes, exhaustive 0..255 values,
q=1..8, complete out-of-order convergence, and conflict/incomplete fail-closed
behavior. The existing Omega LayersBit Safety workflow cross-builds static
ARMv7/AArch64 ELFs and executes them under QEMU user-mode.

This does not define M or G(M), and QEMU is not physical Android.

F_next: exact-head dual-ABI gate; then compare bounded Vectras and Termux
Phase-A runtime receipts without promoting full T-BL-010 geometry semantics.
