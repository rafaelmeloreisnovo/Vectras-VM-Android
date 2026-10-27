# VECTRA / RAFAELIA — proposed authorial bit-pack nucleus

State: `SOURCE_PROPOSED / HOST_TESTED / ANDROID_UNTESTED / NO_MERGE`.
Scope: 2x signed 16-bit lanes in a 32-bit packed bit pattern (`y` high, `x` low).
Implementation: C11 header only, compiler-sized 32-bit primitive types, static inline,
no heap, libc, JNI, OS, syscalls, dynamic downloads or global mutable state.
The Android JNI adapter remains hosted and retains the official `jint` entrypoints.
This is a scoped AI-assisted local addition **inside a derivative GPL-2.0 project**;
it does not assert authorship over upstream Vectras, QEMU, Android, Termux or APIs.

Evidence: 7 edge vectors and 65,536 deterministic bit-layout vectors on Clang/GCC
with host UBSan; ARMv7 and AArch64 ELF objects compiled without undefined
external symbols. These observations are **not** a physical Android/ABI/JNI or
performance claim. Full Android/Termux/guest boot status = `TOKEN_VAZIO_NOT_RUN`.

Test entry: `tools/ci/verify_android_freestanding_contract.sh` (hosted harness)
will compile the new `tools/ci/vectra_vec2_pack_selftest.c` beside existing VCAT
checks. If toolchain/ABI is unavailable, fail closed and preserve the rollback.
No forced merge, no artifact publication, no change to Gradle/NDK or licenses.
Rollback: revert this proposed patch; preexisting JNI behavior remains intact.
