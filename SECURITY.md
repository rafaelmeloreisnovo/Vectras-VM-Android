# Security Policy

## Scope

This project is an Android virtualization runtime that integrates Android UI, QEMU execution, Termux-compatible runtime components, native C/C++/ASM acceleration, local VM storage, VNC/X11 display paths, and process supervision.

Security work in this repository must protect:

- user files, disk images, ISO images, VM configuration and local runtime state;
- command construction and execution paths used to start QEMU or related runtime tools;
- native JNI bridges, ABI-specific acceleration paths and freestanding low-level code;
- Android permission boundaries, foreground services, accessibility integrations and file sharing providers;
- release signing material, CI secrets and generated artifacts.

## Supported Branches

| Branch | Status | Security fixes |
|---|---|---|
| `master` | Active development / canonical branch | Yes |

Older forks, overlay ZIPs, ad-hoc exported artifacts and local copies are not supported as security sources of truth. The canonical source is the versioned Git tree.

## Reporting a Vulnerability

Please report security issues privately through the repository maintainer before opening a public issue.

When reporting, include:

- affected commit or release;
- device model, Android version and ABI;
- exact reproduction steps;
- whether the issue requires a malicious VM image, malicious command string, local file access or user interaction;
- expected impact: code execution, command injection, data exposure, privilege boundary bypass, denial of service, build/signing leak or other category.

Do not include real personal data, private disk images, production signing keys, API keys, tokens or credentials in public issues.

## Security Rules for Contributors

### Command execution

- Do not pass user-controlled raw shell strings into execution paths.
- Prefer structured `argv` command construction over shell interpolation.
- Any QEMU command path must be validated before execution.
- Shell operators such as `;`, `|`, `&&`, `||`, `$()`, backticks, redirection and newline injection must remain blocked unless a future design introduces a formally reviewed parser.

### Native code and ABI contracts

- JNI entry points must validate null pointers, array bounds, offsets and lengths before native access.
- Keep Java and C constants aligned when they define the same ABI contract.
- Do not silently downgrade freestanding or low-level ABI gates in release paths.
- Keep ARM32 and ARM64 behavior explicitly tested when changing native code.
- Avoid undefined behavior in C/C++/ASM hot paths; prefer deterministic fallback behavior.

### Android permissions

- Add permissions only when they have a documented runtime purpose.
- Update `docs/PERMISSIONS_RATIONALE.md` whenever adding, removing or changing permissions.
- Dangerous permissions must degrade safely when denied by the user.

### Secrets and signing

- Do not commit keystores, passwords, signing secrets, Firebase private configuration, tokens or API keys.
- Release signing must use CI secrets or another secure secret store.
- Release signing variables must use the central `VECTRAS_RELEASE_*` contract: `VECTRAS_RELEASE_STORE_FILE`, `VECTRAS_RELEASE_STORE_PASSWORD`, `VECTRAS_RELEASE_KEY_ALIAS`, `VECTRAS_RELEASE_KEY_PASSWORD` and optional `VECTRAS_RELEASE_KEYSTORE_B64`.
- New workflows must not introduce a new signing namespace without updating `BUILDING.md`, `docs/OPERATIONS.md`, `CONTRIBUTING.md`, this policy and `tools/ci/prepare_release_signing.sh` in the same review.
- `ANDROID_*` signing names are allowed only as the documented internal compatibility bridge inside `tools/ci/prepare_release_signing.sh`; they must not be used as official release secrets.
- Debug builds must not depend on production signing material.
- Rotate any secret immediately if it is accidentally committed or exposed.

### Third-party components

- Keep third-party license and attribution data current.
- Binary redistributions must have documented origin, license, hash and update path.
- ISO images, firmware blobs, bootstraps and native toolchains require explicit provenance.

## Disclosure Handling

Expected triage order:

1. Confirm reproducibility and affected versions.
2. Classify severity and affected boundary.
3. Patch on a private or minimal branch when appropriate.
4. Add or update regression tests/gates.
5. Release a fixed build or documented mitigation.
6. Publish advisory notes when safe.

## Security-sensitive Areas

High-priority review areas include:

- `app/src/main/java/com/vectras/vm/VMManager.java`
- `app/src/main/java/com/vectras/vm/core/VmCommandSafetyValidator.java`
- `app/src/main/java/com/vectras/vm/core/ProcessBudgetRegistry.java`
- `app/src/main/java/com/vectras/vm/core/NativeFastPath.java`
- `app/src/main/cpp/`
- `engine/rmr/`
- `shell-loader/`
- `terminal-emulator/`
- `.github/workflows/`
- `tools/ci/`

## Non-goals

This project must not be represented as a sandbox boundary against malicious guest operating systems unless the specific threat model and isolation guarantees are documented and tested.

Running untrusted disk images, scripts, bootloaders or guest software may still expose the user to risk through emulated networking, shared folders, UI deception, resource exhaustion or vulnerabilities in bundled components.
