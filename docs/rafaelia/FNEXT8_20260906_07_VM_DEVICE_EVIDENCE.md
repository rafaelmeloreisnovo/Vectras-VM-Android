# RAFAELIA FNEXT8 — 07 — Vectras VM / Physical Device Evidence Contract

id: FNEXT8-20260906-07
state: IMPLEMENTED_ON_BRANCH
claim_allowed: false
authority: Vectras-VM-Android / VM execution boundary

## Purpose
Separate app installation, QEMU/proot availability, VM definition, VM launch and guest execution into independently receipted states.

## State chain
`SOURCE_OBSERVED -> APK_BUILT -> APK_INSTALLED -> APP_LAUNCHED -> VM_CONFIG_VALID -> VM_PROCESS_STARTED -> GUEST_BOOT_OBSERVED -> WORKLOAD_EXECUTED -> REPRODUCED`

## Required receipt fields
- `vectras_commit`
- `termux_commit_if_bound`
- `apk_sha256`
- `device_id_or_model`
- `android_version`
- `qemu_binary_and_hash`
- `proot_or_runtime_dependency_state`
- `vm_definition_hash`
- `launch_command_or_intent`
- `process_evidence`
- `guest_boot_evidence`
- `workload_evidence`
- `logs_hashes[]`
- `failure_code`
- `token_vazio[]`

## Fail-closed rules
Missing QEMU/proot/busybox/runtime components are explicit dependency states, not inferred successes. An app launch does not prove a VM launch; a VM process does not prove guest boot; guest boot does not prove workload execution.

## Provenance
Upstream Vectras lineage and RAFAELIA transformations remain distinct attribution layers. This file creates no authorship claim over upstream components.
