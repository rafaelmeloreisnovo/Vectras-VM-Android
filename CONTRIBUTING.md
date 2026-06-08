# Contributing to Vectras VM Android

Thank you for helping improve Vectras VM Android.

This project combines Android UI, QEMU execution, Termux-compatible runtime components, native C/C++/ASM acceleration, CI gates and documentation governance. Contributions must preserve build determinism, ABI contracts and user safety.

## Ground Rules

- Keep changes small, reviewable and tied to one purpose.
- Prefer deterministic behavior over clever behavior.
- Do not introduce silent fallbacks in release or security-sensitive paths.
- Do not add binary artifacts without documented provenance, license and validation path.
- Do not commit secrets, keystores, tokens or local environment files.
- Preserve attribution to original upstream projects and forks.

## Development Setup

Recommended local checks:

```bash
./tools/gradle_with_jdk21.sh verifyGradleRuntimeJvm
./tools/gradle_with_jdk21.sh verifyRepoFileDependencies verifyBootstrapAssets
./tools/gradle_with_jdk21.sh clean :app:assembleDebug --stacktrace
```

For Android SDK/NDK alignment:

```bash
./tools/ci/prepare_android_env.sh
./tools/ci/verify_android_local_properties_contract.sh
```

Use JDK 17 by default unless a specific task documents a compatible higher runtime. The repository validates the Gradle JVM range and may fail when the runtime exceeds the configured maximum.

## ABI Policy

The project uses explicit ABI policies.

| Policy | Purpose |
|---|---|
| `arm64-only` | Official distribution baseline |
| `arm32-arm64` | Internal dual-ARM validation: `arm64-v8a` + `armeabi-v7a` |
| `internal-4abi` | Internal app/bootstrap validation |
| `internal-5abi` | Expanded technical diagnostics; not official distribution |

When changing native code, check both ARM64 and ARM32 behavior unless the change is explicitly scoped to one ABI.

## Native Code Rules

Native and JNI code must:

- validate null pointers and array ranges;
- avoid undefined behavior;
- keep Java/C constants aligned;
- preserve deterministic fallback behavior;
- keep freestanding low-level gates active for release-safe paths;
- document any new ABI-visible symbol or contract.

Relevant areas:

- `app/src/main/cpp/`
- `engine/rmr/`
- `app/src/main/java/com/vectras/vm/core/NativeFastPath.java`
- `tools/ci/validate_lowlevel_abi.sh`

## Android Runtime Rules

Android lifecycle code must avoid leaking `Activity` references, blocking the UI thread, or bypassing lifecycle boundaries.

When changing VM or QEMU runtime paths, review:

- process supervision;
- foreground service behavior;
- QMP/VNC/X11 reconnection behavior;
- file access and URI sharing;
- Android permission behavior;
- user-visible failure states.

## Security-sensitive Changes

Open security-sensitive changes with extra context when they touch:

- command construction or execution;
- QEMU parameters;
- file providers and shared storage;
- native JNI memory access;
- release signing;
- CI secrets;
- accessibility services;
- bootstraps, firmware, ISO images or binary blobs.

Update `SECURITY.md`, `docs/THREAT_MODEL.md` or `docs/PERMISSIONS_RATIONALE.md` when applicable.

## Documentation Rules

Documentation is part of the architecture. Update docs in the same pull request when behavior changes.

Preferred docs for common changes:

| Change type | Documentation to update |
|---|---|
| Build, SDK, NDK, CMake, Java | `BUILDING.md`, `docs/AI_BUILD_RELEASE_INDEX.md` |
| ABI or native behavior | `docs/THREAT_MODEL.md`, native docs, CI docs |
| Android permissions | `docs/PERMISSIONS_RATIONALE.md` |
| Release/signing | `SECURITY.md`, build/release docs |
| Third-party binaries | `THIRD_PARTY_NOTICES.md` |
| Package/app identity | `docs/IDENTITY_AND_PACKAGE_POLICY.md` |

## Commit Style

Use clear prefixes:

- `docs:` documentation only
- `build:` Gradle, CI, SDK, NDK or CMake
- `fix:` bug fix
- `refactor:` internal restructuring without behavior change
- `perf:` measured performance improvement
- `test:` tests or verification gates
- `security:` vulnerability fix or hardening

Examples:

```text
docs: add Android permission rationale
build: enforce ABI profile alignment in CI
fix: validate QEMU command argv before execution
security: block shell control operators in VM launch path
```


### Signing namespace review rule

New or modified workflows, Gradle release paths and CI scripts must not introduce a new signing namespace. The central contract is `VECTRAS_RELEASE_STORE_FILE`, `VECTRAS_RELEASE_STORE_PASSWORD`, `VECTRAS_RELEASE_KEY_ALIAS`, `VECTRAS_RELEASE_KEY_PASSWORD` and optional `VECTRAS_RELEASE_KEYSTORE_B64`. Any intentional change to signing names must update `BUILDING.md`, `docs/OPERATIONS.md`, `SECURITY.md` and `tools/ci/prepare_release_signing.sh` in the same change. `ANDROID_*` may appear only inside the documented internal compatibility bridge in `tools/ci/prepare_release_signing.sh`.

## Pull Request Checklist

Before submitting:

- [ ] Build or relevant check was run locally or in CI.
- [ ] ABI impact is documented.
- [ ] Security impact is considered.
- [ ] Android permission impact is documented.
- [ ] No secrets or local files are committed.
- [ ] Third-party license/provenance impact is documented.
- [ ] Docs are updated with the code change.

## Release-sensitive Checklist

For release or perf-release paths:

- [ ] Release signing uses CI secrets or secure secret storage.
- [ ] Signing variables use the central `VECTRAS_RELEASE_*` contract; no new namespace was introduced without updating the contract docs and `prepare_release_signing.sh`.
- [ ] Unsigned release is explicitly marked internal only.
- [ ] Firebase or similar production config is real and not placeholder.
- [ ] Native ABI contract gate passes.
- [ ] Delivered artifacts are verified.
- [ ] Third-party notices are current.
