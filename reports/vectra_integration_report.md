# Vectra + Termux RAFCODEΦ integration report

Date: 2026-06-02

## Repository availability

- `Vectras-VM-Android`: available at `/workspace/Vectras-VM-Android` and updated in this change set.
- `termux-app-rafacodephi`: not present under `/workspace` during this run, so its Gradle/JDK, BLAKE3 bootstrap, JNI, ABI-matrix, and APK tasks were not executed here.

## Vectras runtime auditability changes

- First-boot `StartVM.cdrompath` handling is covered by a null-CDROM regression test.
- QEMU `-object` emission is tokenized as `-object` plus `iothread,id=ioth0` without trailing-space argv ambiguity.
- VNC secrets are excluded from CLI argv/cmdline; only `password=on` is passed, and the actual secret is sent through QMP `set_password` after QMP readiness.
- `DownloadWorker` validates URLs with `EndpointValidator` before creating any OkHttp request.
- VM port allocation now uses ephemeral `ServerSocket(0)` candidates and availability checks instead of random port guessing.
- `SYSTEM_ALERT_WINDOW` remains declared because overlay settings helpers exist, and the manifest documents the settings-gated permission flow.
- Execution budget call sites use `CoreExecutionBudgetPolicy` for QEMU/core orchestration, while `com.vectras.vm.qemu.ExecutionBudgetPolicy` remains as a compatibility facade for legacy tests/callers.

## Termux RAFCODEΦ pending verification

Required when the sibling repository is available:

```bash
./gradlew assembleDebug
./gradlew verifyReleaseContract
./build_apk_matrix.sh
```

The run must export the BLAKE3 bootstrap variables expected by that repo and must fail (not mask) any unsupported JDK/Gradle, ABI, or JNI direct-buffer regression.
