# Vectras Runtime Bootstrap Layers — 2026-08-24

Status: `MITIGATION_IMPLEMENTED_UNTESTED / CLAIM_ALLOWED=false`

## Why this contract exists

A physical Android 10 receipt exposed three simultaneous post-check gaps:

```text
missing-proot
missing-distro-busybox
missing-qemu-binary
```

Those gaps belong to different layers. Treating one archive or one successful APK build as proof of all three creates regression and false promotion.

## Layer 0 — APK identity and internal intents

The Android source namespace is `com.vectras.vm`, while the installed package is controlled by:

```gradle
applicationId "com.rafacodephi.app"
```

Explicit preference intents that target internal `com.vectras.vm.*` classes must therefore use the installed application ID as `android:targetPackage`.

Invariant:

```text
SOURCE_NAMESPACE != INSTALLED_PACKAGE_ID
explicit internal Intent.targetPackage == applicationId
```

The anti-regression gate is:

```text
python3 tools/ci/validate_internal_intent_package_contract.py
```

## Layer 1 — embedded setup seed

`SetupFeatureCore` resolves two independent embedded asset families for the selected Android ABI:

```text
bootstrap/<abi>.tar  -> extracted under <filesDir>/ -> usr/bin/proot
alpine19/<abi>.tar   -> extracted under <filesDir>/distro -> bin/busybox + bin/sh
```

Both families are required. `loader.apk` and JNI libraries are not substitutes for either TAR family.

The exact original-upstream objects are pinned in:

```text
configs/embedded_runtime_seed_assets.v1.json
```

Authority/source:

```text
repository: xoureldeen/Vectras-VM-Android
commit: e1faf376b4b034384cdab9a4ab1e608c61a83521
license: GPL-2.0
```

The materializer constructs raw GitHub URLs from that fixed repository, commit, and path; it does not accept arbitrary source URLs. Before atomic publication it checks:

- exact byte size;
- Git blob SHA-1;
- readable TAR;
- no absolute or `..` paths;
- no device nodes;
- no escaping links;
- required family markers.

APK Wizard execution uses the generated asset tree so no downloaded binary is committed to Git:

```bash
python3 tools/bootstrap/materialize_embedded_runtime_seed_assets.py \
  --target-root app/build/generated/bootstrapAssets \
  --abis arm64-v8a,armeabi-v7a \
  --receipt artifacts/apk-wizard/runtime-seed-materialization.json
```

The wizard cleans first, materializes these assets, materializes `loader.apk`, then builds both lanes without another clean. Each resulting APK is independently inspected by `verify_beta_apk_runtime_payload.py`.

## Layer 2 — QEMU distribution/runtime package

The large QEMU 7.2.22 setup archives referenced by upstream `web/data/setupfiles4.json` are a separate layer. They are approximately 137–156 MB and install the QEMU/runtime package into the prepared userland.

They MUST NOT be relabeled as the small `bootstrap/<abi>.tar` seed files.

The existing `configs/bootstrap_assets.production.v1.json` preserves their source archive provenance, but its historical naming must not be interpreted as proof that Layer 1 has been materialized.

Invariant:

```text
embedded_seed_present != qemu_distribution_installed
```

Remaining gate for this layer:

```text
QEMU_DISTRIBUTION_MATERIALIZATION_RECEIPT = TOKEN_VAZIO
```

## Layer 3 — host executable ABI

A QEMU system executable name describes the guest/system target:

```text
qemu-system-x86_64
qemu-system-aarch64
```

It does not prove whether the Android-host executable is ARM32, ARM64, x86, or x86_64.

The preflight now separates those dimensions and inspects the actual ELF header after the resolver locates QEMU. A real ELF class/machine mismatch becomes a blocker; non-ELF wrappers remain a warning until launch evidence exists.

Invariant:

```text
guest_target != host_ELF_ABI
host_compatibility := observed_executable_header + launch_receipt
```

## Layer 4 — physical device receipt

No CI build or static inspection can promote the runtime to device-verified.

Required physical chain:

```text
APK_SHA256
  -> installed package/version
  -> proot exists + executes
  -> distro/bin/busybox exists + executes
  -> distro/bin/sh exists + executes
  -> QEMU binary resolved
  -> QEMU host ELF ABI compatible
  -> post-check clean
  -> VM launch receipt
```

Until this chain is observed on device:

```yaml
claim_allowed: false
device_runtime_verified: false
```

## Provenance and licensing boundary

The repository `LICENSE` is GPL-2.0 and explicitly attributes the original Vectras VM author and the fork maintainer. The embedded seed assets above are pinned to the original upstream repository under the same project license lineage.

That establishes source custody for this integration; it does not erase license obligations of components bundled inside the rootfs/QEMU packages. Third-party notices and redistribution obligations remain a separate review surface.

## Anti-regression sequence

```text
P0 package identity contract
 -> P1 embedded bootstrap + alpine19 seed
 -> P2 APK payload receipt
 -> P3 QEMU distribution materialization
 -> P4 actual host ELF ABI
 -> P5 physical post-check
 -> P6 VM launch
```

Promotion is monotonic only when the evidence chain is monotonic. A later failure reopens the corresponding gate instead of deleting prior receipts.

## R3

```text
F_ok:
  package mismatch root cause identified and guarded;
  embedded bootstrap and alpine19 sources pinned to exact upstream objects;
  APK verifier now requires both seed families;
  QEMU target/host ABI conflation removed and replaced by actual ELF inspection.

F_gap:
  CI receipts for this branch;
  QEMU distribution materialization;
  physical Moto e7 post-fix execution and VM launch receipts.

F_next:
  run PR CI, inspect apk-wizard receipts, then install the generated ARM32-capable APK
  and close the physical chain without promoting any TOKEN_VAZIO by inference.
```
