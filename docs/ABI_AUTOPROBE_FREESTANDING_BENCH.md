# Vectras ARM32 / ABI auto-diagnostics + freestanding benchmark (draft)

## Source, state and boundary

Source observation: installed device catalog (2026-11-07T01:53:24Z), SHA-256
`d5fb8c6bd4d63680cc017cadcc4a5ab6ac1332d39a422f565c9a6071db6c80c0`.
This is a **catalog integrity** check, not a runtime success claim.

- Moto E7 Power / Android 10: host `armeabi-v7a`, package `com.rafacodephi.app` build 70.
- Recorded APK digest: `71ae6da8446b688e361de8a473d6adae86e1a9bf63c93c7d567f16507346566d`.
- Installed native libraries exist but `bootstrap/armeabi-v7a.tar`, `alpine19/armeabi-v7a.tar`,
  `usr/bin/proot`, rootfs busybox and selected QEMU executable were missing.
- Physical VM boot, E2E run, build certification: `TOKEN_VAZIO`; `claim_allowed=false`.

## Scope / no regression

1. **Existing** `SetupFeatureCore` + `BootstrapAbiMapper` remains the only bootstrap consumer;
   `RuntimeAbiSnapshot` reports process-compatible ABI and packaged assets, without installing.
2. **New** `raf_bench_probe.c` has deterministic bounded arithmetic only:
   no libc, malloc, clock, file access, dynamic download, system calls or Android.
3. **Separate** `raf_bench_jni.c` is hosted and connects the static freestanding object to Android.
4. **Existing** `BenchmarkActivity` remains the full suite; App Settings provides an entry
   and an independently labeled manual ALU microbenchmark.
5. Results use Android monotonic clock. Median is **ns per JNI invocation with 1024 ALU
   iterations**, not guest boot speed, MIPS, QEMU acceleration, memory throughput or cross-device ranking.
6. Missing assets and JNI produce explicit BLOCKED states; no silent fallback or third-party download.
7. Distribution/release remains blocked by the separate upstream asset provenance, provider
   permission, QEMU, native ELF, APK and physical device gates. Never auto-promote this branch.

## Dependency/ownership matrix

| Owner | Responsibility | Boundary |
|---|---|---|
| Vectras-VM-Android | UI, read-only ABI probe, C microkernel, JNI, VM request/receipt | VM consumer |
| termux-app-rafacodephi | Termux process/bootstrap provider | independent producer |
| termux-packages | pinned ARMv7/aarch64 payload provenance and builds | independent producer |
| termux-api | capabilities/permissions where explicitly negotiated | optional |
| QEMU | emulator binary + guest targets, licensing and argv safety | runtime external |
| AndroidX / Gradle / CMake / Ninja | Android UI and build orchestration | hosted/build-time |
| GitHub Actions | build gates, binary asset inventory and ABI verification | CI |
| adb/fastboot | device transport/flash/debug only; not app runtime dependency | optional, risky |

No source/vendor code of these repositories was copied by this change.

## Tests and required receipts

Run `bash tools/ci/test_raf_bench_probe.sh` (host GCC/Clang; pure core no unresolved symbol).
For Android: ABI-aligned Gradle debug build, APK ZIP asset inventory, JNI load test,
manual App Settings microbenchmark, exact APK SHA-256, QEMU host ELF header,
proot/rootfs installation, VM process exit and guest boot receipt as distinct gates.

**Falsifiers:** wrong vector, C undefined symbol, absent target ABI/asset, unusable JNI,
missing executable, illegal QEMU argv, missing provider permission, VM not booting.
**Rollback:** close unmerged draft PR; retain previous default branch and append successor receipt.
**State:** experimental `IMPLEMENTED_UNTESTED` until source/build/physical receipts are independently read back.
