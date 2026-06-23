# Motorola E7 Power ARM32 Beta Install

This lane is a manual beta/debug lane for Motorola E7 Power on Android 10 without root. It uses `APP_ABI_POLICY=arm32-debug`, `SUPPORTED_ABIS=armeabi-v7a`, Gradle debug signing, and is not a store/release lane.

## Download from GitHub Actions

1. Open the **Actions** tab in GitHub.
2. Run **Moto E7 Power ARM32 Beta APK** with `workflow_dispatch`.
3. After it completes, download the artifact named `vectras-moto-e7-power-arm32-debug-beta`.
4. Extract the artifact ZIP.
5. Confirm it contains:
   - `app-debug.apk` (or the generated debug APK name)
   - `APK_ARM32_BETA_CONTRACT.md`
   - `apk_arm32_beta_contract.json`

## Manual install on the phone

1. Copy the APK to the Motorola E7 Power.
2. Open the APK on the device.
3. Allow **install unknown apps** for the file manager/browser used to open it.
4. Install the APK.
5. Run the app with no root access.

## ADB install alternative

```bash
adb install -r caminho/do/apk.apk
```

## Collect logcat

```bash
adb logcat -c
adb logcat | grep -Ei "Vectras|Rafa|Xlorie|qemu|JNI|UnsatisfiedLinkError|armeabi|armv7|fatal|crash"
```

## Validate on device

1. The app opens.
2. No `UnsatisfiedLinkError` appears.
3. The detected ABI is `armeabi-v7a`.
4. `libXlorie.so` loads.
5. `libvectra_core_accel.so` loads.
6. VM/screen/shell starts, or fails with a clear log message.
7. No root is required.

## ARM32 termux/libtermux fallback note

`libtermux.so` may be warn-only for ARM32 in this beta lane. Without it, the internal PTY/terminal JNI path can be unavailable or degraded. VNC/QEMU display flow can still work when `libXlorie.so`, `libvectra_core_accel.so`, and runtime assets load correctly. The ABI verifier records warn-only native libraries separately from required libraries and never reports PASS when required libraries are missing.
