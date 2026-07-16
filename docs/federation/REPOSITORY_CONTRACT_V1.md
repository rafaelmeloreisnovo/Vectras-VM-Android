# Vectras VM Android — Federated Repository Contract v1

**Federated role:** Android VM lifecycle and QEMU/proot execution boundary.  
**Local authority remains:** `PROJECT_STATE.md`, `BUILDING.md`, `DOC_INDEX.md`, release workflows and evidence ledgers.

## Concrete interface

```text
input: VM configuration + image/rootfs + runtime capabilities
output: stopped | booted | blocked, with logs and artifact identity
```

The federation may consume status and evidence from this repository. It may not infer a successful boot from UI availability, a compiled APK, a host-side QEMU test or an old workflow run.

## Non-negotiable invariants

1. No disk/image mutation before storage, image and command validation.
2. Shell, proot, QEMU, rootfs, ABI and display channel are separate gates.
3. Official release state is defined only by the repository's signed release lane.
4. Experimental and historical directories cannot silently become build/release truth.
5. A missing device boot transcript is `TOKEN_VAZIO`, not `PASS`.

## Health gates

Minimum ordered gate set:

```text
android_app_build
shell_ok
storage_ok
proot_ok
qemu_binary_ok
image_or_rootfs_ok
command_render_ok
process_start_ok
display_or_console_ok
guest_boot_evidence
```

Each gate records: commit, device/runner, command, exit code, log path and artifact hash.

## Fail-safe

On any preflight failure:

- stop before launching the VM;
- do not rewrite the image;
- preserve the last command and environment as evidence;
- return `BLOCKED:<gate>`;
- keep the UI in a recoverable stopped state.

## Failover

`qemu_rafaelia` may act as an engine-level comparison/failover target only when the same architecture, machine type, image and command contract can be reproduced. A host-side QEMU success cannot prove Android integration.

## Rollback

Rollback anchor:

```text
base commit + APK/AAB SHA-256 + image hash + release evidence ledger entry
```

Recovery requires rerunning preflight and a bounded smoke boot. Rollback is incomplete until the guest produces the expected console/display evidence.

## Blind tests

- inject a missing QEMU binary after configuration parsing;
- select one compatible fixture image by recorded seed;
- permute non-semantic CLI option order and compare the rendered command contract;
- run one expected-invalid image without revealing its filename to the validator;
- compare UI-started and command-harness-started lifecycle results without assuming equivalence.

## Temporal refusal

Terms such as “current”, “ready” or “working” require a commit and a dated validation result. Prior green runs remain historical evidence only.

## Federated output

```text
F_ok: gates with direct evidence
F_gap: TOKEN_VAZIO / CONTRADICTION / BLOCKED gates
F_next: one smallest executable gate
rollback_anchor: commit + artifact hashes
```
