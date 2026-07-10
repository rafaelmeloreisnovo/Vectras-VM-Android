# RAFAELIA Ω v3.2 — Ablation & Propagation Lab

This directory records the validated ARM32 evidence for the RAFAELIA Ω v3.2 ablation and propagation experiment executed on 10 July 2026.

## Scope

The experiment compares six deterministic modes on an `8×8×8` toroidal grid with 512 cells, 96 cycles, seed `(4,4,4)` and edge weight `1/16`:

1. `GLOBAL_ALPHA_ONLY`
2. `GLOBAL_ALPHA_XY`
3. `GLOBAL_ALPHA_XYZ`
4. `SEED_ALPHA_XY`
5. `SEED_ALPHA_XYZ`
6. `GAMMA_XYZ_ONLY`

## Validated assertions

- global modes remain fixed and finish in identical states;
- XY diffusion isolates the seeded Z layer;
- XYZ diffusion reaches all Z layers;
- first activation follows the toroidal shortest-path vector `4,3,2,1,0,1,2,3`;
- XY and XYZ seeded states diverge as expected;
- gamma-only propagation reaches all layers without continuous alpha forcing;
- two executions of the original ELF are byte-identical;
- original and stripped executables produce byte-identical output.

## ELF contract

The validated artifact is:

- ELF32 ARM EABI5;
- `ET_EXEC`;
- statically linked;
- without `PT_INTERP`;
- without `PT_DYNAMIC`;
- with zero `DT_NEEDED` entries;
- with zero undefined external symbols;
- with zero relocations;
- without libc or CRT;
- using direct Linux kernel syscalls in userspace.

This is a loaderless Linux/Android userspace artifact, not physical bare-metal firmware.

## Reference identity

| Artifact | Size | SHA-256 | Exit |
|---|---:|---|---:|
| `rafaelia_omega_v32` | 19016 bytes | `188f92e95c56eca2fa429733531b84afaf7ddd358b4ed2e6deae0e42e2b882a4` | 0 |
| `rafaelia_omega_v32.stripped` | 17676 bytes | `e23aceeb75130849637108b1ed5651d18b152018e63f18ef46796ebc5e6b6ac3` | 0 |

Reference output SHA-256:

```text
aaf86dc47582e5a48ca5ef4b87cc4ff48644c2f9ed1ee00267bda2be5c4eec61
```

## Key observations

### Global fixed point

All three global modes finish with:

```text
VALUE RANGE       1517719 .. 1517719
ACTIVE CELLS      512
PLANAR COHERENCE  98304 / 98304
3D COHERENCE      131072 / 131072
STATE SIGNATURE   15627567
```

### XY isolation

`SEED_ALPHA_XY` finishes with 64 active cells and:

```text
LAYER MASK 0x00000010
```

Since each Z layer contains `8×8 = 64` cells, the result demonstrates confinement to the seeded plane.

### XYZ toroidal propagation

`SEED_ALPHA_XYZ` reaches every layer and reports:

```text
FIRST ACTIVE Z 4,3,2,1,0,1,2,3
LAYER MASK     0x000000ff
```

The vector matches the shortest toroidal distance from seed layer `z=4`.

### Gamma-only control

`GAMMA_XYZ_ONLY` also reaches every layer with the same first-activation vector. The precise claim is that gamma transports a non-zero initial condition without later alpha forcing; it does not claim propagation from an absent seed.

## Scientific boundary

The evidence proves isolation, reachability, shortest-path timing, symmetry, replay determinism and observational parity for the current no-input execution path. It does not yet prove:

- convergence of every seeded mode at cycle 96;
- security properties;
- portability to every ARM32 system;
- deterministic timing;
- universal equivalence for future versions with files, sensors, networking, arguments or clocks;
- reproducible rebuilding from source on an independent machine.

## Provenance rule

`rafaelia_omega_v32.reference.manifest` preserves the values observed in the successful execution. It is a reference evidence record, not a reconstruction of source from hashes. A source bundle is canonical only when its computed hashes match the four source hashes in the manifest.

## Integration invariant

```text
source hashes
→ ARM32 build
→ negative ELF contract
→ deterministic replay
→ stripped parity
→ ablation assertions
→ reference manifest
```

**Status:** validated within the declared experimental scope. FIAT LUX.
