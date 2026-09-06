# Vectras ↔ RAFCODEPHI Freestanding Termux pkg Bridge v1

## Contract

`TermuxPkgContract` is the Vectras-side deterministic package topology consumed by the RAFCODEPHI execution boundary.

The topology is intentionally staged:

1. `BOOTSTRAP_TOOLCHAIN`
   - base: `bash aria2 tar xterm pulseaudio`
   - repository: `x11-repo`
   - PRoot: `proot proot-distro`
   - freestanding build/test tools: `ninja clang lld cmake make binutils file patchelf`
2. `VECTRAS_QEMU`
   - `qemu-common qemu-system-x86-64-headless qemu-utils`

`AppConfig.neededPkgsTermux()` now points to stage 1, so the existing `LibraryChecker` can bring PRoot/Ninja/toolchain into the Termux package baseline without pretending that QEMU is resolvable before `x11-repo` exists.

Stage 2 is exposed as `AppConfig.neededPkgsTermuxVectrasQemu()` and as a direct argv vector through `TermuxPkgContract.pkgInstallArgv(Stage.VECTRAS_QEMU)`.

## Direct argv invariant

The package contract emits:

```text
["pkg", "install", "-y", ...packages]
```

No package name is accepted from untrusted free-form text at this layer. The freestanding RAFCODEPHI gate performs the final `execve` boundary.

## Cross-repository execution

On the RAFCODEPHI Termux side:

```sh
$PREFIX/libexec/rafproot-fs --probe
$PREFIX/libexec/rafproot-fs --pkg-bootstrap
$PREFIX/libexec/rafproot-fs --probe
$PREFIX/libexec/rafproot-fs --pkg-vectras
$PREFIX/libexec/rafproot-fs --run ninja --version
$PREFIX/libexec/rafproot-fs --run proot --version
$PREFIX/libexec/rafproot-fs --run qemu-system-x86_64 --version
```

## Evidence semantics

- package listed in contract: `SOURCE_OBSERVED/WIRED`;
- executable found by gate: `OBSERVED`;
- static RAFCODEPHI gate built without dynamic dependencies: `BUILD_PROVEN`;
- successful tool execution on Android: `RUNTIME_PROVEN`;
- successful PRoot/QEMU flow on the target physical device: `DEVICE_PROVEN`;
- anything not executed remains `TOKEN_VAZIO`.

This contract does **not** claim that stock PRoot, Ninja, pkg or QEMU are themselves freestanding. The freestanding property belongs to the control/exec gate; third-party payloads remain separately attributable components.
