# AGENTS.md — RAFAELIA / Vectras-VM-Android

## Federation entry

This repository is the RAFAELIA **VM/QEMU consumer**. On entry, bind the exact ref/commit, load the smallest relevant local contract, classify open work on orthogonal state axes, and preserve `TOKEN_VAZIO` instead of filling unknowns by assumption.

Execution order:

`bind → route → gaps → select → baseline → execute → verify-local → verify-edges → receipt → append`

Use `F_ok`, `F_gap`, `F_next` after every meaningful action. Federation kernel authority: `rafaelmeloreisnovo/RafGitTools:configs/agent-entry-kernel.v1.json` when cross-repository access is available.

## Local role and entry routes

Role: construct bounded VM/QEMU requests, consume the Termux provider boundary, preserve safe-state behavior, and produce execution/guest evidence without overstating dispatch acceptance.

Read only what the task needs, starting with:

- `BUILDING.md`
- `app/src/main/java/com/vectras/vm/integration/CrossRepoIntegrationManager.kt`
- `app/src/main/java/com/vectras/vm/integration/VectrasTermuxBridge.kt`
- `app/src/main/java/com/vectras/vm/integration/VectrasTermuxIpcContract.kt`
- `docs/contracts/VECTRAS_TERMUX_IPC_V2.md`
- the relevant `tools/verify_vectras_termux_ipc_*.py` and workflow gate
- `docs/ALL_GAPS_REGISTRY.md` for unresolved work

## Cross-repository protocol invariants

- Discovery/capability negotiation v2 and bounded execution v3 are distinct layers; do not conflate version numbers.
- `DISPATCHED` is not evidence of QEMU execution, exit status or guest boot.
- Every request must remain bounded, deterministic and hashable under the execution contract.
- Protected QEMU options must not be bypassed through arbitrary arguments.
- Safe-state must be preserved on permission, service, validation or result failure.
- Cross-repository success requires matching Termux package/permission/action/result semantics on both producer and consumer sides.
- Physical Android E2E remains `TOKEN_VAZIO` until a transaction links request digest → Termux result → observed process/exit; guest boot needs its own receipt.

## Local VM/assembly specialization

Rules below apply only to modules that actually use the VECTRA/RafaelOS low-level kernel; they are not federation-wide rules.

- AArch64 may be primary in those modules, but ARM32 fallback/support must not be silently removed where declared.
- Preserve fixed-point/NEON/register conventions only where the owning source contract defines them.
- Do not introduce heap/libc/branching abstractions into freestanding hot paths without proving the local contract still holds.
- Any theorem/invariant claim requires an explicit falsification condition.
- Historical attractor/BitOmega assertions must be revalidated against the current commit before being used as present evidence.

If editing `.S` or low-level RafaelOS/VECTRA kernel files, read the owning architecture document (for example `VECTRA_OS.md` when present) before mutation.

## Urgency and gaps

- P0 safety, image-mutation, permission, result-integrity and cross-repository execution blockers first.
- Within equal urgency, unblock the Termux/provider dependency before polishing downstream VM UX.
- Prefer `READY_TO_TEST` with an observable result receipt over speculative refactors.
- Do not delete deferred/ignored findings; record an attention state and reason.

## Evidence boundaries

- Static contract PASS proves static contract scope only.
- CI/emulator PASS does not prove target physical-device execution.
- APK hash does not prove VM correctness.
- QEMU process start does not prove guest boot.
- Guest boot does not prove performance improvement.
- Evidence belongs to the exact source commit, APK/binary hash, protocol and device/environment that produced it.

## Historical determinism

`TOKEN_VAZIO != 0`; `READY_TO_TEST != RESOLVED`; urgency is not confidence. Append successor receipts with parent-event/source-commit linkage and retain rollback references for mutating changes.
