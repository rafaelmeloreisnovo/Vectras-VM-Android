---
name: vm-pattern-leaf
description: Apply reusable architecture patterns to the Vectras VM/QEMU consumer role while preserving bounded requests, safe-state behavior, protected arguments, data minimization, and dispatch/execution/guest-boot evidence separation. Use when a prior architecture suggests a useful trigger, state, validation, logging, queue, rollback, or error mechanism.
---

# VM / QEMU Pattern Leaf

Read `AGENTS.md` first. This skill cannot expand Vectras beyond VM/QEMU consumer authority.

## Local projection

```text
extracted mechanism
-> VM consumer problem
-> bounded request/state model
-> validation + protected-option guard
-> deterministic dispatch/consume action
-> safe-state failure path
-> process/guest falsifier
-> receipt
```

Reference transfers:

- Pascal-like discipline -> explicit numeric widths/ranges, initialized request state, deterministic enums/results, strict argument typing and error boundaries.
- InterBase-like trigger discipline -> event-conditioned VM transitions, guarded request activation, transaction-like result handling, durable bounded logs and rollback/safe-state transitions.

Transfer behavior, not source syntax.

## Preserve

- discovery, dispatch, process execution, exit and guest boot as distinct layers;
- protected QEMU options and bounded/hashable requests;
- producer/consumer contract symmetry with Termux;
- guest/user data minimization in receipts;
- ARM32 support wherever locally declared;
- freestanding/no-heap rules only in modules whose owning contract requires them;
- physical Android E2E as `TOKEN_VAZIO` until a linked transaction receipt exists.

## Forbidden transfer

```text
DISPATCHED != EXECUTED
a process start != guest boot
guest boot != performance proof
local Vectra receipt != Termux provider proof
pattern similarity != evidence
skill != authority
```

Only sanitized mechanisms may arrive from a private seed. Do not publish raw private derivation, corpus, identifiers or guest payload.

## Completion

Record `source_pattern`, `vm_leaf`, request/state boundary, falsifier, safe-state/rollback, `F_ok`, `F_gap`, `F_next` and `claim_allowed=false` unless an exact-scope gate promotes it.
