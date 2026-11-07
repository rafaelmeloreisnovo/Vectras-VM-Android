# RAFAELIA — Freestanding ELF ABI compatibility preflight V1

## Goal / authority
Owner: Vectras-VM-Android VM consumer. This additive preflight is **not**
a replacement for the Termux provider, QEMU emulation engine, PRoot,
BusyBox, Android platform, or the existing SetupFeatureCore installer.
It does not modify any VM, binary, firmware, or bootloader.

User-supplied Motorola E7 Power installation catalog:
`vectras-evidence-catalog-20261107T025406.116Z.json`,
SHA-256 `cf9793a90490a84fede57615e4c54f373cb82031a0a1bb1584f61db9e7f16c55`.
The observed package 3.6.6(70) lacks PRoot/BusyBox/QEMU executables and
ARMv7 bootstrap/rootfs archives. These are **observations**, not proof
that a new beta APK is or is not installable.

## Architecture
```text
Android App Settings (read-only)
  -> RuntimeAbiSnapshot.java (hosted assets, file metadata, 64-byte reads)
  -> raf_runtime_elf_jni.c (hosted JNI bridge, 64-byte bounded stack buffer)
  -> raf_runtime_elf.c (pure C11; no libc, heap, syscalls, clock, filesystem)
       -> ELF magic, endian/class, type, machine, EABI5, ARM hard-float
  -> HEADER_MATCH_ONLY | TOKEN_VAZIO | explicit incompatibility
```

The native core is linked into existing `abi_core_freestanding`; JNI
is linked into existing `vectra_core_accel`. No external runtime
libraries, downloads, or new manifest permissions are introduced by this
change. Compiler-provided `stdint.h` is not a hosted runtime dependency.

## Evidence boundary
`HEADER_MATCH_ONLY` means that inspected ELF header fields are
compatible with the **selected host process ABI**. It does **not** validate:
- actual program header contents, full ELF size, ELF signature or digest;
- ELF interpreter, shared library DT_NEEDED list or their availability;
- native library loading, filesystem mounts/extraction, provider permission;
- QEMU guest architecture/command line, process execution or exit;
- successful guest boot or any performance improvement.

`QEMU=TOKEN_VAZIO_PATH` is deliberate: the canonical QEMU path must be
resolved by the existing runtime owner, never guessed by this preflight.
The runtime remains BLOCKED when executables are missing. PRoot/BusyBox
are only observed under the already-cataloged app-owned paths.

## Falsifiers
1. Corrupt magic/endian/ELF header => non-positive status.
2. ARM64 or x86 executable on an ARM32 host => ABI mismatch.
3. ARM32 EABI other than 5 or hard-float calling convention => rejected.
4. Missing/unreadable/non-executable runtime file => explicit non-PASS state.
5. GCC and Clang compile standalone C object with no undefined symbols.
6. Android Gradle/JNI physical device checks remain separate gates.

Host scope: `CC=gcc bash tools/ci/test_raf_runtime_elf.sh` and
`CC=clang bash tools/ci/test_raf_runtime_elf.sh` each run 20 synthetic
ELF header vectors. Cross-target example:
`clang --target=armv7a-none-eabi -std=c11 -ffreestanding -fno-builtin -c app/src/main/cpp/core/raf_runtime_elf.c`
followed by ELF32 ARM EABI inspection. No test here establishes a bootable APK.

## Dependencies retained by design
- Android/Java/JNI and an Android-built APK: hosted app/platform boundary.
- PRoot: Linux user-space path translation; separate producer and binary.
- BusyBox, bootstrap, Alpine rootfs: trusted ARMv7 payloads, separate origin.
- QEMU: full ISA emulation/VM and guest boot, retained external provider.
- Gradle/NDK/CMake/GitHub Actions: build-time and CI tools, not the core.

No third-party implementation was copied, adapted or relabeled as original.
The new source follows this repository's SPDX GPL-2.0-only license.
To remove one of these retained dependencies, first demonstrate equivalent
behavior, licensing, performance, resource budgets and physical ARMv7 E2E.

## Rollback and progression
This is isolated in a draft PR; do not auto-merge. Revert the additive
CMake source entries, Java read-only summary and new files to roll back.
Existing SetupFeatureCore/BootstrapAbiMapper paths remain unchanged.
After review, rerun exact-head CI and a signed/unmodified APK receipt on
Motorola E7 Power, followed by extraction, process and guest boot receipts.
`claim_allowed=false` until these independent gates pass.
