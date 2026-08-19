# AGENTS.md — RAFAELIA / Vectras-VM-Android

## Federation entry

This repository is the RAFAELIA **VM/QEMU consumer**. Bind the exact ref/commit before using any historical status. `Mapa` is the federated routing/state authority; RafGitTools provides the executor kernel when available.

Federated routing/state authority: `rafaelmeloreisnovo/Mapa`.  
Control-plane executor contract: `rafaelmeloreisnovo/RafGitTools:configs/agent-entry-kernel.v1.json`.

### Mandatory service preflight — Q01..Q12

Before mutating or promoting state, answer with exact pointers or typed `TOKEN_VAZIO`:

1. **Quem sou?** — VM/QEMU consumer agent + local repository role.
2. **Qual repo/ref/path/hash estou lendo?** — repo/ref/exact commit/path/blob or artifact identity.
3. **Qual minha autoridade?** — Vectra owns VM request/consumer implementation; Termux owns provider implementation; `Mapa` owns federated route/state.
4. **Qual minha fronteira?** — dispatch, process execution, guest boot and performance are separate claims.
5. **Quais índices locais devo abrir?** — minimum build/integration/gap contracts only.
6. **Qual rota do Mapa corresponde ao objetivo?** — explicit route/anchors or typed `TOKEN_VAZIO`.
7. **Que lacunas já existem?** — gaps, TOKEN_VAZIO, uncertainties and upstream dependencies.
8. **Qual evidência é atual?** — exact source/APK/QEMU/protocol/device/receipt scope and staleness.
9. **Qual gate posso executar?** — static contract, emulator, physical transaction or guest fixture with falsifier/exit/rollback.
10. **Quando devo parar?** — stop on authority/dependency/privacy/security block, exit observed, or no marginal reconstruction gain.
11. **Onde registro o delta?** — local receipt; federated transition in Mapa when material; Drive reconstruction only for durable navigation/provenance changes.
12. **Quais regras de governança, dados, privacidade e segurança governam esta unidade?** — classify all four before mutation.

### Local governance/data/privacy/security defaults

- **Governance:** local VM consumer changes remain local authority; cross-repo Termux claims require provider + consumer evidence. High/critical image/protocol mutation requires rollback.
- **Data:** VM images, snapshots, console/log output and request/result payload may contain user or guest data; never assume PUBLIC from file location alone.
- **Privacy:** minimize guest/user data in receipts. Prefer transaction IDs, digests, bounded status and redacted metadata over raw VM files, usernames, paths or console contents.
- **Security:** QEMU arguments, protected options, disk/image mutation, IPC permissions/actions, result integrity, safe-state and executable identity are security surfaces. Unknown security/privacy classification blocks mutating work.

Execution order:

`bind → authority/boundary → indices → Mapa route → gaps → service classification → select → baseline/rollback → execute → verify-local → verify-edges → receipt → append → recompute F_gap/F_next`

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

If this set reconstructs the target and more history cannot change the gate/evidence/provenance/privacy/security classification, stop crawling and run the bounded gate.

## Cross-repository protocol invariants

- Discovery/capability negotiation v2 and bounded execution v3 are distinct layers; do not conflate version numbers.
- `DISPATCHED` is not evidence of QEMU execution, exit status or guest boot.
- Every request must remain bounded, deterministic and hashable under the execution contract.
- Protected QEMU options must not be bypassed through arbitrary arguments.
- Safe-state must be preserved on permission, service, validation or result failure.
- Cross-repository success requires matching Termux package/permission/action/result semantics on both producer and consumer sides.
- Physical Android E2E remains `TOKEN_VAZIO` until a transaction links request digest → Termux result → observed process/exit; guest boot needs its own receipt.
- Guest/image payload is not federated evidence by default; only minimum necessary identity/status metadata should cross the boundary.

## Local VM/assembly specialization

Rules below apply only to modules that actually use the VECTRA/RafaelOS low-level kernel; they are not federation-wide rules.

- AArch64 may be primary in those modules, but ARM32 fallback/support must not be silently removed where declared.
- Preserve fixed-point/NEON/register conventions only where the owning source contract defines them.
- Do not introduce heap/libc/branching abstractions into freestanding hot paths without proving the local contract still holds.
- Any theorem/invariant claim requires an explicit falsification condition.
- Historical attractor/BitOmega assertions must be revalidated against the current commit before being used as present evidence.

If editing `.S` or low-level RafaelOS/VECTRA kernel files, read the owning architecture document (for example `VECTRA_OS.md` when present) before mutation.

## Urgency and gaps

- P0 safety, image-mutation, permission, result-integrity, privacy/security and cross-repository execution blockers first.
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
- A local Vectra receipt cannot by itself promote the Termux provider/runtime claim.

## Required transition receipt

Record at minimum:

```text
event/parent
repo/ref/commit/path
authority + write scope
gap/goal IDs
urgency + risk
governance/data/privacy/security classification
action + falsifier + exit criterion + stop reason
evidence refs
F_ok / F_gap / F_next
uncertainty delta
rollback ref
claim_allowed
```

## Historical determinism

`TOKEN_VAZIO != 0`; `READY_TO_TEST != RESOLVED`; urgency is not confidence. Append successor receipts with parent-event/source-commit linkage and retain rollback references for mutating changes. Never copy raw guest/user data into a public receipt when a digest/redacted reference is sufficient.
