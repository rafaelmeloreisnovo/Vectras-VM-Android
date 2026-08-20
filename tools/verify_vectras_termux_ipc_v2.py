#!/usr/bin/env python3
"""Static fail-closed contract check for Vectras' external Termux discovery/bridge."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "app/src/main/java/com/vectras/vm/integration/CrossRepoIntegrationManager.kt"
CONTRACT = ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxIpcContract.kt"
BRIDGE = ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxBridge.kt"
RECEIVER = ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxResultReceiver.kt"
MANIFESTS = [ROOT / f"app/src/{name}/AndroidManifest.xml" for name in ("debug", "release", "perfRelease")]

REQUIRED_MANAGER = (
    'TERMUX_PACKAGE = "com.termux.rafacodephi"',
    'TERMUX_RUN_COMMAND_PERMISSION =',
    '"qemu_binary_names"',
    '"qemu_binary_sha256"',
    '"provider_apk_sha256"',
    '"protocol_version"',
    '"private_paths_exposed"',
    'providerApkSha256',
    'qemuBinarySha256',
    'providerBinarySha256Discovery',
    'Context.RECEIVER_EXPORTED',
    'UUID.randomUUID()',
)
FORBIDDEN_MANAGER = (
    '"prefix_path"',
    '"qemu_binary_paths"',
    'Context.RECEIVER_NOT_EXPORTED',
)
REQUIRED_CONTRACT = (
    'SERVICE_CLASS = "com.termux.app.RunCommandService"',
    'ACTION_RUN_COMMAND = "$TERMUX_PACKAGE.RUN_COMMAND"',
    'EXTRA_PENDING_INTENT',
    'RESULT_BUNDLE = "result"',
    'RESULT_EXIT_CODE = "exitCode"',
)
REQUIRED_BRIDGE = (
    'VectrasTermuxIpcContract.SERVICE_CLASS',
    'VectrasTermuxIpcContract.ACTION_RUN_COMMAND',
    'VectrasTermuxIpcContract.EXTRA_PENDING_INTENT',
    'PendingIntent.FLAG_MUTABLE',
    'EXTRA_REQUEST_SHA256',
    'VectrasTermuxIpcContract.sha256(',
    'vmRequired: Boolean',
    'State.VM_NOT_REQUIRED',
    'State.DISPATCHED',
    'executionProven = false',
    'claimAllowed = false',
    '"vm-stopped-no-image-mutation"',
    'provenanceBound',
)
REQUIRED_RECEIVER = (
    '"raf.android-runtime-receipt.v3"',
    'VectrasTermuxIpcContract.RESULT_BUNDLE',
    'VectrasTermuxIpcContract.RESULT_EXIT_CODE',
    '"stdout_sha256"',
    '"stderr_sha256"',
    '"input_sha256"',
    '"output_sha256"',
    '"provider_identity_scope", "DISCOVERY_NOT_EXECUTION"',
    '"provenance_chain_sha256"',
    '"provenance_chain_complete", false',
    '"status"',
    '"guest_boot_artifact_sha256"',
    '"F_ok"',
    '"F_gap"',
    '"F_next"',
    '"claim_allowed", false',
)
REQUIRED_MANIFEST = (
    'com.termux.rafacodephi.permission.RUN_COMMAND',
    '<package android:name="com.termux.rafacodephi"',
    'com.vectras.vm.integration.VectrasTermuxResultReceiver',
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
        (CONTRACT, REQUIRED_CONTRACT, ()),
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
        "protocol": "raf.vectras-termux-ipc.v2-discovery_plus_v3-execution",
        "dispatch_is_execution": False,
        "discovery_identity_is_execution_identity": False,
        "provenance_chain_complete": False,
        "receipt_is_guest_boot": False,
        "claim_allowed": False,
    })
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
