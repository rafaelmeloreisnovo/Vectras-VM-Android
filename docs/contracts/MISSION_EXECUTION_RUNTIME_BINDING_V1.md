# Vectras — Mission Execution Runtime Binding V1

**Binding ID:** `VECTRAS-MISSION-RUNTIME-BINDING-V1`  
**Mission contract:** Termux PR #425 / `a13b04693428d21bf9bf411b353f6a79c835afe7`  
**Atlas route:** Mapa PR #541 / `ATLAS:X-MISSION-EXECUTION-BOUNDARY-20260906`  
**Vectras baseline:** `c98e5a79f70f6fd1316cad26faf328a725b8a792`  
**claim_allowed:** `false`

Vectras is an optional execution/runtime backend. It does not own corpus semantics, mission goals, model-training authority, or scientific promotion.

## Execution boundary

```text
Governed ExecutionPlan
 -> Vectras dispatch/bootstrap
 -> QEMU/process evidence
 -> guest boot evidence
 -> physical-device evidence (when required)
 -> scoped ExecutionResult / receipt
```

The evidence states remain distinct:

```text
dispatch != bootstrap
bootstrap != QEMU process
QEMU process != guest boot
guest boot != physical-device reproduction
```

## Required invariants

- `DATASET_INFORMS != MISSION_AUTHORITY`
- `VECTRAS_RUNTIME != SOURCE_SEMANTICS`
- `DISPATCH != RUNTIME_PROOF`
- `GUEST_BOOT != PHYSICAL_DEVICE_PROOF`
- `TOKEN_VAZIO != 0`
- `EXTERNAL_AUTHORITY_REQUIRED != RUNTIME_PERMISSION`
- no missing binary/package/QEMU/device receipt is promoted to PASS.

Blocked runtime evidence does not terminate other independent safe mission lanes and does not authorize bypass.

This binding changes no Android, QEMU, package, bootstrap or UI source code.
