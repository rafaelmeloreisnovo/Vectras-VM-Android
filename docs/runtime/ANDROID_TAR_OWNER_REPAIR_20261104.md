# Vectras Android tar ownership repair — 2026-11-04

## Source / evidence / scope

- Physical screenshot: Moto E7 Power, Android 10, setup repair screen on 2026-11-04.
- Observed: `POST_CHECK_FAIL:missing-proot,missing-distro-busybox,missing-qemu-binary`; `baseReady=false`; `qemuReady=false`.
- Blocking cause: `tar xf <filesDir>/*.tar -C <filesDir>` returned `exit=1` with repeated `tar: chown 0:0 ... Operation not permitted`, and `EXTRACTION_FAIL:PROCESS_NON_ZERO_OR_OUTPUT_VALIDATION_FAIL`.
- The asset exists and was copied to the app-private files directory; presence is not proof of successful extraction or execution.
- This change fixes only the ownership-restoration extraction argument shared by `bootstrap`, `alpine19` and `qemu19`; it does not claim that PRoot or a VM boots.
- Producer: `rafaelmeloreisnovo/Vectras-VM-Android`, base `master`; isolated change branch `fix/android-tar-no-same-owner-20261104`.

## Minimal reversible change

In `SetupFeatureCore.extractSystemFiles`, replace `tar xf ARCHIVE -C TARGET` with the array from `SetupProcessIntegration.buildOwnerSafeTarExtractionCommand`:

```text
tar -o -x -f ARCHIVE -C TARGET
```

For extracting, `-o` disables restoring file owner/group (the UID/GID archived as 0:0) and works with Android Toybox, GNU tar, and BusyBox. The extracted files remain owned by the app's Android UID. Keep `ProcessBuilder(String[])` (no shell), normalized app-private destinations, existing integrity checks, the executable chmod post-check, rollback, and receipts.

## Android permission boundary

- `context.getFilesDir()` is an app-private internal directory; asking for external-storage permission is **not** the remedy for a `chown 0:0` failure there.
- For an ISO or external file explicitly selected by the user, use the Android document picker and persist a URI grant where appropriate; keep this separated from the APK asset bootstrap.
- Select an ABI matching the device. Do not unpack four ABI archives merely because they are packaged in the APK.
- The runtime order remains asset validate → extraction → executable/hash check → PRoot/Alpine check → QEMU runtime check → VM launch. Never bypass gates.

## Validation requirements

1. Unit: `SetupProcessIntegrationTest.buildOwnerSafeTarExtractionCommand_disablesOwnerRestoreBeforeExtract` asserts the exact no-owner-restore argument vector.
2. Host: extract a tar fixture with UID/GID 0 headers under an unprivileged user using `tar -o -x -f` and verify `exit=0`, extracted owner is current UID, executable mode preserved as expected.
3. Device: rebuild & install the exact branch APK; run Repair; record a fresh `bootstrap-extraction` receipt and compare its SHA-256. Verify `usr/bin/proot`, `distro/bin/busybox`, `distro/bin/sh`, `distro/usr/bin/env` with hash and executable bits.
4. Separately test QEMU binary resolution/execution and guest boot with an observed process/console/VNC; do not infer success from the source patch.
5. Inspect archive entries and available free storage; a file-copy and extraction can require more working space than the compressed payload size.

## Evidence gate / rollback

- `SOURCE`: screenshot and producer code.
- `ARTIFACT`: three source/test changes on isolated branch and this note.
- `EXECUTION`: no physical device install in this change.
- `EVIDENCE`: unit/CI and later physical receipts remain separate.
- `CLAIM`: `claim_allowed=false` until exact APK/device evidence is observed.
- Rollback: close/revert the isolated PR; preserve the original `master` and append a superseding receipt for any later correction.

R3 = <F_ok: causal error and corrective option identified, F_gap: exact APK/ARMv7 device E2E and QEMU execution, F_next: scoped CI then Moto E7 receipt and independent promotion gate>.
