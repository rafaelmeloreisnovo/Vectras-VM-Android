---
name: vm-pattern-leaf
description: Apply reusable architecture patterns to the Vectras VM/QEMU consumer role while preserving bounded requests, safe-state behavior, protected arguments, data minimization, and dispatch/execution/guest-boot evidence separation. Use when a prior architecture suggests a useful trigger, state, validation, logging, queue, storage-layout, memory-map, bus-resource, rollback, or error mechanism.
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

## Event and commit geometry

Treat request creation, update, cancellation/delete, dispatch, process exit and guest-observed state as distinct transitions. A VM configuration update should preserve prior evidence when the local ledger is append/supersede oriented.

```text
request identity
-> validated config
-> dispatch
-> host process observation
-> exit/status
-> guest observation
-> receipt/current view
```

## Memory and bus geometry

Legacy IRQ/DMA/ISA/bridge/address-map reasoning transfers as a resource-topology model:

```text
resource
-> address/port/channel
-> owner
-> width/alignment
-> event/transfer route
-> contention/failure state
```

QEMU/device-model configuration and current guest/host architecture contracts own exact addresses, IRQ numbers and MMIO layout. Historical constants are never copied blindly.

## Storage locality geometry

Disk geometry and fragmentation ideas map to VM images as:

```text
guest logical block
-> image format allocation
-> host file extent/cache
-> storage backend
-> measured latency/throughput
```

Keep logical guest layout separate from host physical placement. A sparse image, aligned extent or sequential read strategy is an optimization only if integrity and rollback remain testable.

HDD seek times, SSD startup latency and flash-cell type are benchmark inputs, not universal constants.

## Binary/boot geometry

Historical boot records, option ROM/EEPROM and executable-header ideas transfer as:

```text
boot source
-> validated metadata/header
-> mapped region/device
-> firmware/loader transition
-> guest-visible state
```

Exact boot vectors, magic values and offsets require the target firmware/image specification.

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

Record `source_pattern`, `vm_leaf`, request/state boundary, memory/bus/storage geometry if relevant, falsifier, safe-state/rollback, `F_ok`, `F_gap`, `F_next` and `claim_allowed=false` unless an exact-scope gate promotes it.
