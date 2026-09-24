# ZIPRAF Bit Layer Raster Bridge V1

State: BRIDGE_SPEC / IMPLEMENTATION_UNTESTED  
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
