# PBIP-L1 — Vectras consumer binding

- `FEDERATION_ID`: `PBIP-L1-FED-V1`
- `FORMULA_ID`: `PBIP-L1`
- `role`: `EXECUTABLE_CONSUMER`
- `state`: `IMPLEMENTED / CI_PENDING / DEVICE_NOT_PROVEN`
- `claim_allowed`: `false`
- `date`: `2026-09-06`

## Upstream authority

Formal mathematics stays in `rafaelmeloreisnovo/Matem-tica-` (PR #23). Cross-repository relation authority stays in `rafaelmeloreisnovo/Mapa`. Academic synthesis stays in `rafaelmeloreisnovo/papers` (PR #73).

The implemented relation is:

```math
q^2=r^2-d_\perp^2,
\qquad
\Delta_B=4(r^2-d_\perp^2)=4q^2.
```

## Executable anchors

- `app/src/main/java/com/vectras/vm/core/PbipL1.java`
- `app/src/test/java/com/vectras/vm/core/PbipL1Test.java`
- existing general math anchor: `app/src/main/java/com/vectras/vm/core/MathUtils.java`

`PbipL1` is deterministic and side-effect-free. It exposes:

```text
halfChordSquared(r, d_perp)
discriminant(r, d_perp)
classifyDiscriminant(delta, tolerance)
classify(r, d_perp[, tolerance])
```

Classification contract:

```text
Delta_B > tolerance   -> SECANT
|Delta_B| <= tolerance -> TANGENT
Delta_B < -tolerance  -> NO_REAL_INTERSECTION
```

Default floating-point tolerance is `1e-12`. The three canonical integer-valued vectors are asserted exactly (`EPS=0`).

## Canonical vectors

```text
r=5, d_perp=3 -> q^2=16,  Delta_B=64  -> SECANT
r=5, d_perp=5 -> q^2=0,   Delta_B=0   -> TANGENT
r=5, d_perp=6 -> q^2=-11, Delta_B=-44 -> NO_REAL_INTERSECTION
```

## Geometry namespace guard

```text
H_RADIAL_30 = (sqrt(3)/2) r
H_MAX_EQUILATERAL_MERIDIAN = (3/2) r
TANGENTS_SYMMETRIC_±30 != PARALLEL_PULSE_30
Poincare return map != Poincare conjecture
```

The implementation is Euclidean line-circle geometry only. It does not establish a physical vortex law or any Poincare-conjecture implication.

## Evidence state

```text
SOURCE_OBSERVED=true
WIRED_DOCUMENTALLY=true
IMPLEMENTED_PBIP_CONSUMER=true
BUILD_PROVEN=false
UNIT_TEST_EXECUTION_PROVEN=false
RUNTIME_PROVEN=false
DEVICE_PROVEN=false
REPRODUCED=false
TOKEN_VAZIO_CI_BINDING_PBIP_L1
```

`BUILD_PROVEN` and `UNIT_TEST_EXECUTION_PROVEN` may change only after observing a successful provider CI run for the implementation commit.

## R3

- `F_ok`: deterministic implementation and canonical tests exist on the PR branch.
- `F_gap`: provider CI receipt is still pending; device/runtime evidence is absent.
- `F_next`: bind a successful CI run into RafPolimata as the first executed PBIP-L1 receipt.
