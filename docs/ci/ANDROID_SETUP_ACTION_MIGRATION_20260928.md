# Android setup action migration — 2026-09-28

**State:** `IMPLEMENTED_UNTESTED`  
**Scope:** active GitHub Actions workflows only.

## Trigger

Observed pull-request jobs failed before repository build logic entered the Android toolchain installation step:

```text
Setup Android SDK
Warning: Failed to find package 'tools'
sdkmanager ... exit code 1
```

The upstream `android-actions/setup-android` v4 line removes the deprecated `tools` package from
its default package request. The repository already has explicit/pinned SDK/NDK/CMake installation
steps after setup, so this migration asks the action to install **no additional SDK packages**.

## Supply-chain binding

```text
action: android-actions/setup-android
release: v4.0.4
commit: be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd
packages: ''
```

Using the commit SHA avoids a moving action tag. The version comment remains for human navigation.

## Active workflows updated

```text
.github/workflows/android-ci.yml
.github/workflows/android-native-ci.yml
.github/workflows/apk-wizard.yml
.github/workflows/beta-debug-builds.yml
.github/workflows/compile-matrix.yml
.github/workflows/gaiaphi-android-build.yml
.github/workflows/moto-e7-arm32-beta.yml
.github/workflows/runtime-evidence-contracts.yml
.github/workflows/shell-loader-smoke.yml
```

Archived workflows are intentionally unchanged: historical source is not rewritten merely to make
current CI green.

## Evidence boundary

This change removes one pre-run dependency failure. A subsequent green build must still pass the
repository-owned SDK/NDK installation, Gradle, native, ABI, artifact and runtime gates.

```text
setup action repaired != Android build PASS
Android build PASS != device/runtime PASS
```

## Rollback

Revert this transaction and restore the prior action references. No application ABI or persisted
format changes.

## R3

F_ok = deprecated package dependency removed from active setup layer and action pinned by SHA.  
F_gap = exact-head workflows must execute after this change.  
F_next = inspect the next first failing step; do not skip downstream gates.
