# Final CI Gate Probe — 2026-08-08

Purpose: trigger the current canonical Android/host CI from `master` without changing runtime code, so stale/expired logs from the 2026-08-03 B7 merge are replaced by fresh auditable evidence.

Invariant: no B7/QEMU/RMR behavior is changed by this probe.

Expected classification:
- if wrapper/canonical CI starts and reaches executable steps, classify the 2026-08-03 pre-step failure separately from current code;
- if it fails again, use the fresh runner/job logs as the hotfix root cause;
- keep ARMv7/ARM64/device/Vulkan/OpenCL claims at `TOKEN_VAZIO` until corresponding receipts exist.

claim_allowed=false
