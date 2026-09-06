# PBIP-L1 — Vectras consumer binding

- `FEDERATION_ID`: `PBIP-L1-FED-V1`
- `FORMULA_ID`: `PBIP-L1`
- `role`: `EXECUTABLE_CONSUMER_CANDIDATE`
- `state`: `WIRED_DOCUMENTALLY / RUNTIME_NOT_PROVEN`
- `claim_allowed`: `false`
- `date`: `2026-09-06`

## Upstream authority

Formal mathematics stays in `rafaelmeloreisnovo/Matem-tica-` (PR #23). Cross-repository relation authority stays in `rafaelmeloreisnovo/Mapa`. Academic synthesis stays in `rafaelmeloreisnovo/papers` (PR #73).

The relation to consume is:

```math
q^2=r^2-d_\perp^2,
\qquad
\Delta_B=4(r^2-d_\perp^2)=4q^2.
```

## Local anchors

Existing Vectras mathematical anchors include:

- `app/src/main/java/com/vectras/vm/rafaelia/MathUtils.java`
- `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaFormulas.java`

This document does not claim that PBIP-L1 is already implemented by those classes. It binds the future implementation to a stable ID and prevents ad-hoc duplicate formulas.

## Required executable contract

A future implementation SHOULD expose deterministic, side-effect-free operations equivalent to:

```text
half_chord_sq(r, d_perp) = r*r - d_perp*d_perp
pbip_discriminant(r, d_perp) = 4 * half_chord_sq(r, d_perp)
classify(delta): delta>0 SECANT; delta==0 TANGENT; delta<0 NO_REAL_INTERSECTION
```

Numerical tests must include positive, zero and negative discriminant cases and state tolerance explicitly for floating-point paths.

## Geometry namespace guard

Keep distinct:

```text
H_RADIAL_30 = (sqrt(3)/2) r
H_MAX_EQUILATERAL_MERIDIAN = (3/2) r
TANGENTS_SYMMETRIC_±30 != PARALLEL_PULSE_30
Poincare return map != Poincare conjecture
```

## Evidence state

```text
SOURCE_OBSERVED=true
WIRED_DOCUMENTALLY=true
BUILD_PROVEN=false
RUNTIME_PROVEN=false
DEVICE_PROVEN=false
REPRODUCED=false
TOKEN_VAZIO_CI_BINDING_PBIP_L1
```

## R3

- `F_ok`: stable consumer ID and local anchors are defined.
- `F_gap`: executable method/test/CI receipt is absent.
- `F_next`: add deterministic implementation and receipt without changing claim state prematurely.
