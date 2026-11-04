# QEMU direct process ownership — 2026-11-04 (append-only successor)

**Parent:** `docs/runtime/PROCESS_SUPERVISION_HOTFIX_AUDIT_20261104.md`, PR #1182.

## Source-level bug

Before this change, `QemuDirectLauncher.runLaunch` reserves a `ProcessBudgetRegistry.SlotToken` and then invokes `VMManager.registerVmProcess`, which reserves a second slot. In `finally`, QEMU is unregistered, file descriptors are closed, and `Terminal.qemuProcess` is cleared without verifying that QEMU has exited. Those operations can create an alive-but-disconnected process.

## Focused correction

- `VMManager` is the single owner of the slot and its lifecycle. The extra direct slot is removed.
- `isVmProcessRegistered(vmId, process)` checks **identity + alive** after registration; a silently rejected registration cannot be presented as successful.
- In an exceptional launch path, attempt bounded teardown before cleanup. When the process is still alive, preserve supervision and Terminal reference rather than clearing them.
- Only clear file descriptors and QEMU process reference after confirmed terminal state.
- Exact-process regression added in `VMManagerStopVmProcessTest`.

## Risks not claimed resolved

`PRoot_PARENT_CHILD_REAP=TOKEN_VAZIO`; `ANDROID_APK_E2E=TOKEN_VAZIO`; `QMP_REAL_GUEST_SHUTDOWN=TOKEN_VAZIO`; `CROSS_ABI_BUILD=TOKEN_VAZIO`; `CONCURRENT_VM_CAPACITY_REGRESSION=TOKEN_VAZIO`; `CI_EXACT_HEAD=TOKEN_VAZIO`.

Source is not execution. This branch remains draft/nonpromoting until exact CI and hardware proof. No reset/merge/release.

R3 = <F_ok: central ownership and fail-closed liveness check, F_gap: CI/real QEMU child-tree and guest proof, F_next: exact-head CI, device stop/failover receipt, independent review>.
