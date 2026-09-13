# Vectras seven-guard work-unit gate

This directory implements a bounded, fail-closed control gate for Vectras work units.

Authority boundaries:

- **Vectras-VM-Android** owns local VM/QEMU consumer implementation and local receipts.
- **Mapa** owns federated route/state.
- **NOVOexport** is source/index/custody input; it is not execution evidence.
- Provider/runtime claims remain outside this gate unless the matching evidence is supplied.

The seven operational guards are:

1. provenance
2. context
3. evidence
4. contradiction
5. uncertainty
6. reproduction
7. rollback

Reconstructibility is kept as a transversal closure gate so the local contract remains compatible with the Mapa Knowledge Work House contract.

## Boundary invariants

```text
SOURCE != ARTIFACT
ARTIFACT != EXECUTION
EXECUTION != EVIDENCE
EVIDENCE != CLAIM
TOKEN_VAZIO != 0
DISPATCHED != QEMU_EXECUTION
QEMU_EXECUTION != GUEST_BOOT
GUEST_BOOT != PERFORMANCE
ROLLBACK_READY != ROLLBACK_EXECUTED
```

## Run

```bash
python3 tools/governance/validate_vectras_work_unit.py \
  tools/governance/examples/vectras-seven-guards.local.json

python3 -m unittest tools.tests.test_validate_vectras_work_unit
```

The validator uses only the Python standard library. It does not require `jsonschema`; the JSON Schema is the interchange contract, while the Python validator is the executable fail-closed gate.

## Promotion rule

`claim_allowed=true` is accepted only when all guards pass, reconstructibility passes, and evidence is specific to the requested claim target.

Target-specific minimum evidence:

- `STATIC_CONTRACT` -> `STATIC_TEST` or `REVIEW`
- `QEMU_EXECUTION` -> `QEMU_PROCESS`
- `GUEST_BOOT` -> `GUEST_BOOT`
- `PERFORMANCE` -> `PERFORMANCE`
- `PHYSICAL_ANDROID_E2E` -> `DEVICE_TRANSACTION`

A stronger claim cannot be inferred from a weaker evidence type.
