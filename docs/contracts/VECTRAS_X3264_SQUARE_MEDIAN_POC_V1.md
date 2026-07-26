# Vectras x32/x64 Square-Median PoC Contract V1

## Purpose

Route the bounded square-median geometry proof of concept from the scientific
paper and QEMU producer into the Vectras consumer without confusing QEMU
linux-user execution with a full-system virtual machine.

## Authorities

| Plane | Authority | Artifact |
|---|---|---|
| Mathematics and claims | `rafaelmeloreisnovo/papers` PR #28 | `papers/square_median_rotation_x3264_v1/` |
| x86 execution producer | `rafaelmeloreisnovo/qemu_rafaelia` PR #65 | TCG multiarch test and source-built linux-user workflow |
| Android consumer | `Vectras-VM-Android` | Termux IPC bridge and receipt store |
| Federated pointers | `Mapa` | evidence and authority registry |

## Critical profile distinction

The existing IPC v3 profile is a **full-system QEMU** profile. It prepends:

```text
-accel tcg
-display none
-monitor none
-serial stdio
-no-reboot
-name vectras-termux-ipc-v3
```

Those arguments are appropriate for `qemu-system-i386` and
`qemu-system-x86_64`. They are not the argument contract of the linux-user
executors `qemu-i386` and `qemu-x86_64` used by the bounded PoC.

Therefore the safe state is:

```yaml
current_system_profile: VERIFIED_STATIC
linux_user_profile: TOKEN_VAZIO_NOT_IMPLEMENTED
dispatch_allowed: false
claim_allowed: false
```

## Forbidden shortcut

Adding `qemu-i386` or `qemu-x86_64` to the current binary allowlist without
changing the argument profile is forbidden. That would make the allowlist look
complete while dispatching the wrong command contract.

## Required v4 separation

A future IPC v4 should type execution before dispatch:

```text
SYSTEM_VM
  binary: qemu-system-*
  fixed arguments: accel/display/monitor/serial/no-reboot/name

LINUX_USER
  binary: qemu-i386 | qemu-x86_64
  fixed arguments: none by default
  payload: canonical immutable ELF path
  optional loader/cpu arguments: separately allowlisted
```

The `LINUX_USER` profile must bind:

- payload path;
- payload SHA-256;
- source repository and commit;
- target ABI;
- expected exit code;
- stdout/stderr lengths and truncation state;
- QEMU binary SHA-256 and version;
- transaction ID and request SHA-256.

## Current proof boundary

The QEMU repository already contains the executable kernel and remote workflow.
Vectras presently records only that the consumer profile is not yet compatible.
This contract is a useful negative result: it blocks an incorrect integration
and defines the next implementable interface.

```yaml
paper_exact_math: VERIFIED_LOCAL
qemu_elf32_build: VERIFIED_LOCAL
qemu_elf64_native_execution: VERIFIED_LOCAL
qemu_source_built_linux_user_execution: TOKEN_VAZIO_PENDING_WORKFLOW
vectras_linux_user_dispatch: TOKEN_VAZIO_PROFILE_MISMATCH
full_system_guest_boot: NOT_REQUIRED_FOR_THIS_POC
claim_allowed: false
```
