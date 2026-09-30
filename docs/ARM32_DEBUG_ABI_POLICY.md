# ARM32 Debug ABI Policy

`APP_ABI_POLICY=arm32-debug` is an internal beta/debug-only contract for Motorola E7 Power / Android 10 / no root.

Required Gradle properties:

```bash
-PAPP_ABI_POLICY=arm32-debug \
-PSUPPORTED_ABIS=armeabi-v7a \
-PCI_INTERNAL_VALIDATION=true \
-Psigning_mode=unsigned
```

Rules:

- `SUPPORTED_ABIS` must be exactly `armeabi-v7a`.
- `CI_INTERNAL_VALIDATION` must be `true`.
- The APK must be debug signed by Gradle; no release keystore is required.
- The policy is rejected for release/bundle tasks, `ciRelease=true`, or `signing_mode=signed`.
- The APK verifier must report PASS before the artifact is considered deliverable.
- Required native libraries are not optional: missing `libXlorie.so` or `libvectra_core_accel.so` is a hard FAIL.


## Hotfix 2026-09-30 — terminal-emulator parity

CI run 36702476327 proved that the APK wizard's ARM32 lane reached Gradle with `APP_ABI_POLICY=arm32-debug`, while `terminal-emulator/build.gradle` still rejected that already-registered profile. The hotfix adds the same debug-only policy to that module with `CI_INTERNAL_VALIDATION=true` required and exact `armeabi-v7a` ABI matching.

This change does **not** promote device execution. It only restores policy coherence across the root/app profile registry, APK wizard, and terminal-emulator module. Physical Moto E7 execution remains a separate evidence gate and `claim_allowed=false` until a device receipt exists.

Rollback: revert the hotfix commit that adds the `arm32-debug` case to `terminal-emulator/build.gradle`; rerun APK Wizard and ABI policy gates. Do not alter the broader `arm32-arm64` or release profiles as part of that rollback.
