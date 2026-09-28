# RAFAELIA Formula Constants Audit V1

**Scope:** `engine/rmr` fixed-point formula encodings  
**claim_allowed:** `false`

## Problem observed

The source expression for formula 0.5 is:

```text
(√3/2)^(π·φ)
```

At the pre-change repository state, the Java surface computed that expression directly with
`Math.pow`, while both C surfaces encoded `23163 / 65536 ≈ 0.35343933`.

Re-evaluating the declared expression gives approximately:

```text
(√3/2)^(π·φ) ≈ 0.4813439043705785
round(value × 65536) = 31545
```

Formula 29 also had a smaller cross-runtime drift:

```text
|π·sin(999°)| ≈ 3.102914434849979
round(value × 65536) = 203353
observed stale C literal = 203360
```

## Correction

This patch changes only the fixed-point encodings derived from those expressions:

```text
SPIRAL_PI_PHI: 23163 → 31545
THETA_999_SIN_PI_MAG: 203360 → 203353
```

It does not redefine `√3/2`, `π`, `φ`, Trinity633 or the symbolic interpretation of the
formula family.

## Reproducible gate

```bash
python3 tools/audit/verify_rafaelia_formula_constants.py
```

The script recomputes the expressions using Python's standard `math` library, converts by an
explicit nearest-half-up rule for non-negative Q16.16 magnitudes, parses both C surfaces and fails
if a literal diverges.

## Evidence boundary

`PASS` proves only that the declared fixed-point constants match the declared mathematical
expressions under the recorded rounding rule. It does **not** prove device execution, performance,
a physical interpretation, a scientific claim, or equivalence of unrelated spiral angular laws.

## Navigation for humans and agents

```text
formula semantics
  → app/.../MathUtils.java / RafaeliaKernelV22.java
  → engine/rmr/include/rafaelia_formulas_core.h
  → engine/rmr/src/rafaelia_formulas_core.c
  → engine/rmr/src/rmr_math_fabric.c
  → tools/audit/verify_rafaelia_formula_constants.py
```

When adding a derived fixed-point constant, add it to this gate rather than copying an unexplained
literal to another surface.

## R3

F_ok = explicit formula-to-Q16 parity rule + corrected derived constants + reproducible gate.  
F_gap = compiler/runtime/device parity remains separate and must be executed.  
F_next = run the gate on the exact PR head, then include it in the smallest relevant CI lane.
