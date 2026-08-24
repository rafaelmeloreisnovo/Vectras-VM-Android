# Vectras Evidence Catalog V1

Status: `IMPLEMENTED_PENDING_CI_AND_DEVICE_RECEIPT`

```yaml
claim_allowed: false
certification_claim: false
physical_vm_launch_verified: false
```

## Purpose

The Evidence Catalog is the installation-identification and chain-of-custody surface for Vectras VM. It exists in two coupled forms:

1. **build-side evidence** produced by CI for each APK lane;
2. **device-side evidence** collected by the installed application from the exact APK and runtime that are actually present on the Android device.

The catalog is designed to support technical audit, reproducibility, scientific reference, incident analysis and later normative mapping. It does **not** claim compliance with a specific standard merely because the fields exist.

The invariant is:

```text
BUILD INPUT
  -> BUILD CONTEXT EMBEDDED IN APK
  -> APK SHA-256 + PAYLOAD RECEIPTS
  -> INSTALLED APK SHA-256 + SIGNING CERTIFICATE
  -> DEVICE/RUNTIME OBSERVATION
  -> PHYSICAL EXECUTION RECEIPT
  -> CLAIM (only after the applicable gate)
```

## 1. Build-side artifact

`tools/ci/generate_build_evidence_catalog.py` records for each lane:

- repository and exact Git HEAD;
- Git ref and worktree state;
- lane name, ABI policy and supported ABIs;
- Python/Java/runner observation;
- GitHub Actions run/ref/workflow metadata when present;
- SHA-256 and size of critical source/build-contract files;
- APK size and SHA-256 after assembly;
- ZIP inventory of DEX, native libraries, runtime seed entries and embedded evidence;
- bootstrap/runtime-seed/payload receipts and their SHA-256 digests;
- explicit claim boundary.

Before each APK lane is assembled, a reduced build record is written to:

```text
app/build/generated/bootstrapAssets/evidence/build-context.json
```

Because `app/build/generated/bootstrapAssets` is an Android assets source directory, the APK carries:

```text
assets/evidence/build-context.json
```

This creates an internal provenance bridge between the external CI artifact and the APK later installed on a physical device.

Output artifacts:

```text
artifacts/apk-wizard/<lane>.build-evidence.json
artifacts/apk-wizard/<lane>.runtime-payload.json
artifacts/apk-wizard/bootstrap-materialization.json
artifacts/apk-wizard/alpine19-materialization.json
```

## 2. Device-side Evidence Catalog screen

The installed application exposes:

```text
Settings -> Catálogo de Evidências
```

The blocked setup gate also exposes the same screen so evidence can be collected **before** PRoot/rootfs/QEMU are repaired.

The screen performs collection on a worker thread because SHA-256 calculation may cover the installed APK, native libraries, embedded TARs and runtime binaries.

Schema:

```text
vectras.device-evidence-catalog.v1
```

Primary sections:

```text
build_identity
installed_application
embedded_build_context
embedded_runtime_assets
device_context
runtime_filesystem
native_libraries
permissions
privacy_exclusions
token_vazio
artifact_integrity
```

### Installed application identity

The catalog records:

- runtime package name;
- application ID;
- source namespace distinction;
- build type/debug state;
- version name/code;
- first install and last update timestamps;
- installer package when Android exposes it;
- installed `base.apk` size + SHA-256;
- signing certificate SHA-256 fingerprints.

This allows a physical receipt to answer the question:

> Which exact APK, signed by which certificate, was installed when this observation was made?

## 3. Embedded runtime evidence

The collector inventories and hashes, when present:

```text
bootstrap/arm64-v8a.tar
bootstrap/armeabi-v7a.tar
bootstrap/x86.tar
bootstrap/x86_64.tar
alpine19/arm64-v8a.tar
alpine19/armeabi-v7a.tar
alpine19/x86.tar
alpine19/x86_64.tar
bootstrap/loader.apk
evidence/build-context.json
```

Absence is recorded as an observation, not silently converted into success.

## 4. Runtime filesystem evidence

The catalog checks the installed app data for:

```text
<filesDir>/usr/bin/proot
<filesDir>/distro/bin/busybox
<filesDir>/distro/bin/sh
<filesDir>/distro/usr/bin/env
```

For each present file it records:

```text
exists
executable
size_bytes
sha256
normalized_path
```

It also records the current `SetupFeatureCore.runSetupPostCheck()` result.

QEMU is recorded separately:

```text
selected guest architecture
resolver result
candidate paths
resolved binary hash
actual host ELF class/e_machine inspection
```

The filename `qemu-system-x86_64` is never treated as proof of host x86_64 ABI.

## 5. Device context

For reproducibility the catalog records non-personal execution context such as:

- manufacturer/brand/model/device/board/hardware;
- Android release and SDK level;
- OS build ID/display/fingerprint;
- supported ABIs;
- kernel version and OS architecture;
- processor count and JVM max memory;
- app-files volume total/usable bytes;
- locale and timezone.

The following persistent/personal identifiers are deliberately excluded:

```text
ANDROID_ID
hardware serial
IMEI/MEID
SIM identifiers
accounts
MAC addresses
adopted-storage UUID
user documents
```

That boundary keeps the artifact useful for reproducibility without turning it into an unnecessary identity dossier.

## 6. Artifact export and integrity

The screen can emit an append-only timestamped JSON artifact under the app-private evidence directory:

```text
<filesDir>/evidence/catalog/vectras-evidence-catalog-<UTC>.json
```

A companion digest is written:

```text
vectras-evidence-catalog-<UTC>.json.sha256
```

Digest algorithm:

```text
SHA-256
```

The pair can be shared through the application `FileProvider`; no broad storage permission is required for the internal evidence files.

## 7. Scientific / normative evidence model

The catalog intentionally captures the generic fields commonly needed to substantiate a later scientific or normative reference:

```text
identity
version
observation timestamp
instrument/execution context
source provenance
input integrity
output integrity
cryptographic digest
signing identity
method of observation
runtime state
uncertainty/gap ledger
chain of custody
```

However:

```text
FIELD_PRESENT != STANDARD_COMPLIANCE
HASH_PRESENT != CORRECT_EXECUTION
APK_BUILT != APK_INSTALLED
APK_INSTALLED != RUNTIME_READY
RUNTIME_READY != VM_BOOTED
VM_BOOTED != SCIENTIFIC_VALIDATION
```

A future standards mapping may reference a named standard only after its exact clauses and evidence obligations are mapped and reviewed.

## 8. TOKEN_VAZIO semantics

`TOKEN_VAZIO` is used only where the collector cannot directly substantiate a required statement. Examples include:

```text
EMBEDDED_BUILD_CONTEXT
INSTALLED_APK_SHA256
APK_SIGNING_CERTIFICATE_SHA256
POST_CHECK_CLEAN_RECEIPT
QEMU_EXECUTABLE_RUNTIME
QEMU_HOST_ELF_ABI_RECEIPT
PHYSICAL_VM_LAUNCH_RECEIPT
END_TO_END_VM_BOOT_EVIDENCE
```

A missing optional ABI asset is recorded as `present=false`; it is not automatically a gap because the build lane may intentionally exclude that ABI.

## 9. Promotion gate

```text
build evidence PASS
 -> APK artifact digest
 -> installed APK digest matches intended artifact
 -> signing certificate observed
 -> embedded build context observed
 -> PRoot/rootfs extraction receipt
 -> QEMU distribution receipt
 -> QEMU host ELF receipt
 -> clean post-check
 -> real VM launch receipt
```

Only the evidence actually observed may move forward. Prior receipts remain append-only even if a later gate reopens.

## R3

```text
F_ok:
  build and device evidence now share one catalog topology;
  installed APK/signing identity becomes directly observable;
  runtime gaps remain explicit;
  exported JSON is integrity-bound by a SHA-256 companion.

F_gap:
  current branch CI;
  physical Android collection receipt;
  exact installed-vs-built APK digest comparison;
  QEMU/VM physical execution.

F_next:
  compile the APK Wizard lane, inspect *.build-evidence.json,
  install that APK, open Catálogo de Evidências, export the JSON+SHA256 pair,
  then compare build APK SHA-256 with installed APK SHA-256 before promotion.
```
