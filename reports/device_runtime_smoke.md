# Vectra Android device runtime smoke

Date: 2026-06-02
Scope: Vectras-VM-Android runtime smoke contract for a real Android device.

## Status

- **CI/unit smoke:** defined and covered by JVM tests for process supervision, QMP failover, port allocation, endpoint validation, and argv password redaction.
- **Real device smoke:** **not measured in this container** because no Android device, Termux runtime, or QEMU binary is attached.

## Required real-device procedure

1. Install the generated debug APK on an Android API 28+ device.
2. Start a dummy/minimal VM profile with VNC enabled and QMP local Unix socket enabled.
3. Verify the QEMU process is alive without exposing any VNC secret in `/proc/<pid>/cmdline`.
4. Wait for QMP socket readiness.
5. Apply VNC password with QMP `set_password` after boot.
6. Stop the VM via QMP `system_powerdown`.
7. If QMP fails, verify FAILOVER escalates to TERM/KILL and writes `audit-ledger.jsonl`.
8. Preserve the ledger and `/proc/<pid>/cmdline` evidence as real-device artifacts.

## Acceptance evidence separation

- Benchmark defined is not benchmark measured.
- CI smoke is not real-device smoke.
- Real-device evidence must include device model, Android version, ABI, APK hash, QEMU binary path, QMP stop result, and ledger excerpt.
