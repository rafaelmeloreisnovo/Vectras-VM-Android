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
