# NOVOexport — Vectra Ω — Authorial Hypervisor Architecture V1

State 2026-11-07: SOURCE_IMPLEMENTED / LOCAL_TEST_PASS_SCOPED / PHYSICAL_VM_NOT_RUN / claim_allowed=false. Base: rafaelmeloreisnovo/Vectras-VM-Android master@cf5483279d425080d0434e32e709550a1fc8fb06.

## Goal and boundaries

The authorial nucleus owns bounded VM state, disjoint borrowed-memory region descriptors (8 max), byte permissions, event dispatch with a 4096-step budget, explicit halt/io/fault and fail-closed external backend errors. All code in hypervisor/autoral was newly written for this branch; no existing QEMU/Vectras implementation copied.

Architecture: Android/hosted UI and authorization -> RAFHV ABI v1 C freestanding control -> explicit backend.enter callback -> FUTURE CPU engine/privileged KVM or software interpreter -> FUTURE guest devices/MMU/boot -> independent execution receipt.

Reality: a C freestanding object is not a real bare-metal hypervisor. Without privileged KVM/EL2 access (must be observed per device), a non-root Android application would require its own user-space CPU interpreter/translator. A VM stack also requires ISA/traps, MMU, exceptions, timer/IRQ, boot loader, storage/display, bounded guest inputs, resource control, and Android adapter. None of those exist in this new nucleus yet.

## Upstream and licenses

Root LICENSE identifies original Vectras author xoureldeen and GPL v2; this fork maintains that history and notices. New files have GPL-2.0-only headers for inclusion in this GPL project. New authorship covers only these newly implemented source expressions; does not erase upstream, certify clean-room, or select a separate standalone license. Before separating/distributing as independent product, perform per-component legal/provenance review.

## Migration gates (legacy preserved, no deletes)

H0 provenance register and runtime asset licensing: OPEN.
H1 local control nucleus: GCC + Clang test 23 assertions each, nm -u empty, ARMv7 ELF32 EABI5 object: PASS_SCOPED_LOCAL.
H2 independent CPU backend / real ISA fixtures / MMU fault behavior: TOKEN_VAZIO.
H3 guest boot with process exit receipt and device models: TOKEN_VAZIO.
H4 ARM32 Android APK + Moto E7 physical test: TOKEN_VAZIO.
H5 SBOM/NOTICES/license and binary/runtime safety review: TOKEN_VAZIO.
H6 feature parity then human authorization to disable legacy: BLOCKED.

H1 tests are hosted mock-backend tests only. No APK, device, or guest is claimed. No new Android launch path; no legacy code changed. Any full switch now would be a regression.

## Interfaces, risks, falsifiers

- Backend function pointer is trusted host code, not a security sandbox. Must not run untrusted guest bytes directly.
- Borrowed buffers are validated only by caller assertions; core cannot verify actual underlying allocation length, pinning, memory safety of the host backend or concurrency. Caller owns zeroization.
- Map addresses reject overlap, zero/overflow length, unknown permissions, missing read bit, and native pointer-width excessive range.
- H1 falsifiers: compiler warning treated as error; any nm -u symbol; ARMv7 output not ELF32 EABI5; any 23-check failure including fault/overlap/readonly/budget/rollback.
- H2/H3 falsifiers require real independent execution fixture and explicit machine exit/boot receipts; no synthetic event qualifies.
- SOURCE != ARTIFACT != EXECUTION != EVIDENCE != CLAIM; IMPLEMENTED_UNTESTED != PASS; TOKEN_VAZIO != 0.

Rollback: close draft PR / revert only newly added paths; preserve master, existing VM consumers, permissions and third-party notices. Mapa federated route for this exact new feature = TOKEN_VAZIO until indexed. Owner is this Vectras producer repo, not Termux or Mapa.
