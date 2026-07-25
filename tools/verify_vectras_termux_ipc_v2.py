#!/usr/bin/env python3
"""Static fail-closed contract check for Vectras' external Termux bridge."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/vectras/vm/integration/CrossRepoIntegrationManager.kt"
BRIDGE = ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxBridge.kt"
RECEIVER = ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxResultReceiver.kt"
MANIFESTS = [ROOT / f"app/src/{name}/AndroidManifest.xml" for name in ("debug", "release", "perfRelease")]

REQUIRED_MANAGER = (
    'TERMUX_PACKAGE = "com.termux.rafacodephi"',
    "TERMUX_RUN_COMMAND_PERMISSION =",
    '"qemu_binary_names"',
    '"protocol_version"',
    '"private_paths_exposed"',
    "Context.RECEIVER_EXPORTED",
    "UUID.randomUUID()",
)
FORBIDDEN_MANAGER = (
    '"prefix_path"',
    '"qemu_binary_paths"',
    "Context.RECEIVER_NOT_EXPORTED",
)
REQUIRED_BRIDGE = (
    'SERVICE_CLASS = "com.termux.app.RunCommandService"',
    'ACTION_RUN_COMMAND = "$TERMUX_PACKAGE.RUN_COMMAND"',
    "EXTRA_PENDING_INTENT",
    "vmRequired: Boolean",
    "State.VM_NOT_REQUIRED",
    "State.DISPATCHED",
    "executionProven = false",
    "claimAllowed = false",
    '"vm-stopped-no-image-mutation"',
)
REQUIRED_RECEIVER = (
    '"raf.android-runtime-receipt.v1"',
    'EXTRA_RESULT_BUNDLE = "result"',
    'EXTRA_EXIT_CODE = "exitCode"',
    '"stdout_sha256"',
    '"stderr_sha256"',
    '"guest_boot_artifact_sha256"',
    '"claim_allowed", false',
)
REQUIRED_MANIFEST = (
    "com.termux.rafacodephi.permission.RUN_COMMAND",
    '<package android:name="com.termux.rafacodephi"',
    "com.vectras.vm.integration.VectrasTermuxResultReceiver",
    'android:exported="false"',
)


def check(path: Path, required: tuple[str, ...], forbidden: tuple[str, ...] = ()):
    text = path.read_text(encoding="utf-8")
    missing = [f"{path}:{item}" for item in required if item not in text]
    present_forbidden = [f"{path}:{item}" for item in forbidden if item in text]
    return missing, present_forbidden


def main() -> int:
    missing, forbidden = [], []
    for path, required, denied in (
        (MANAGER, REQUIRED_MANAGER, FORBIDDEN_MANAGER),
        (BRIDGE, REQUIRED_BRIDGE, ()),
        (RECEIVER, REQUIRED_RECEIVER, ()),
    ):
        m, f = check(path, required, denied)
        missing += m
        forbidden += f
    for manifest in MANIFESTS:
        m, f = check(manifest, REQUIRED_MANIFEST)
        missing += m
        forbidden += f
    if missing or forbidden:
        print({"status": "FAIL", "missing": missing, "forbidden": forbidden})
        return 1
    print({
        "status": "PASS",
        "protocol": "raf.vectras-termux-ipc.v2",
        "dispatch_is_execution": False,
        "receipt_is_guest_boot": False,
        "claim_allowed": False,
    })
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
