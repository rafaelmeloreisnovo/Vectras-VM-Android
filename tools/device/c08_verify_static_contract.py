#!/usr/bin/env python3
"""Static fail-closed verifier for the C08 device evidence implementation."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = "raf.c08-static-contract-verification.v1"
FILES = {
    "guest_contract": ROOT / "app/src/main/java/com/vectras/vm/integration/GuestBootEvidenceContract.kt",
    "ipc_contract": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxIpcContract.kt",
    "bridge": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxBridge.kt",
    "store": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxReceiptStore.kt",
    "receiver": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxResultReceiver.kt",
    "collector": ROOT / "tools/device/c08_collect_device_evidence.sh",
    "validator": ROOT / "tools/device/c08_validate_device_evidence.py",
    "guest_marker": ROOT / "tools/device/c08_guest_marker.sh",
    "app_build": ROOT / "app/build.gradle",
}
MANIFESTS = [ROOT / f"app/src/{variant}/AndroidManifest.xml" for variant in ("debug", "release", "perfRelease")]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require(text: str, snippets: list[str], label: str, errors: list[str]) -> None:
    for snippet in snippets:
        if snippet not in text:
            errors.append(f"{label}: missing {snippet!r}")


def forbid(text: str, snippets: list[str], label: str, errors: list[str]) -> None:
    for snippet in snippets:
        if snippet in text:
            errors.append(f"{label}: forbidden {snippet!r}")


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    texts: dict[str, str] = {}
    inputs: dict[str, Any] = {}

    for label, path in FILES.items():
        if not path.is_file():
            errors.append(f"missing {label}: {path}")
            continue
        texts[label] = path.read_text(encoding="utf-8")
        inputs[label] = {"path": str(path.relative_to(ROOT)), "sha256": sha256_file(path)}

    for manifest in MANIFESTS:
        label = f"manifest:{manifest.parent.name}"
        if not manifest.is_file():
            errors.append(f"missing {label}: {manifest}")
            continue
        texts[label] = manifest.read_text(encoding="utf-8")
        inputs[label] = {"path": str(manifest.relative_to(ROOT)), "sha256": sha256_file(manifest)}

    require(
        texts.get("guest_contract", ""),
        [
            'SCHEMA = "raf.guest-boot-evidence.v1"',
            'BOOT_ARGUMENT_PREFIX = "rafaelia.boot_nonce="',
            'SecureRandom()',
            'ByteArray(32)',
            'RAFAELIA_GUEST_BOOT_V1',
            'RAFAELIA_GUEST_USERSPACE_V1',
            'RAFAELIA_GUEST_SHUTDOWN_V1',
            'boot.range.first < userspace.range.first',
            'userspace.range.first < shutdown.range.first',
            '"COMPLETE_ORDERED_EXIT_ZERO"',
            'exitCode == 0',
            'termuxErrorCode != null && termuxErrorCode != 0',
        ],
        "guest_contract",
        errors,
    )

    require(
        texts.get("ipc_contract", ""),
        [
            'guestBootNonce: String? = null',
            'appendField("guest_boot_evidence_schema", GuestBootEvidenceContract.SCHEMA)',
            'appendField("guest_boot_nonce", guestBootNonce ?: "TOKEN_VAZIO_NOT_REQUESTED")',
        ],
        "ipc_contract",
        errors,
    )

    require(
        texts.get("bridge", ""),
        [
            'dispatchQemuWithGuestEvidence(',
            'GuestBootEvidenceContract.containsNonceArgument(arguments, guestBootNonce)',
            'guestBootNonce = guestBootNonce',
            'State.INVALID_GUEST_CHALLENGE',
            'VectrasTermuxReceiptStore.writePending(',
            'canonicalRequest(',
        ],
        "bridge",
        errors,
    )

    require(
        texts.get("store", ""),
        [
            'val guestBootNonce: String?',
            'put("guest_boot_evidence_schema", GuestBootEvidenceContract.SCHEMA)',
            'put("guest_boot_nonce", guestBootNonce ?: JSONObject.NULL)',
            '"guest_boot_nonce_argument_present"',
            'GuestBootEvidenceContract.containsNonceArgument(arguments, it)',
        ],
        "store",
        errors,
    )

    receiver = texts.get("receiver", "")
    require(
        receiver,
        [
            'GuestBootEvidenceContract.analyze(',
            '"guest_boot_evidence_state"',
            '"guest_boot_evidence_complete"',
            '"guest_markers_ordered"',
            '"guest_boot_marker_sha256"',
            '"guest_userspace_marker_sha256"',
            '"guest_shutdown_marker_sha256"',
            '"claim_allowed", false',
        ],
        "receiver",
        errors,
    )
    forbid(
        receiver,
        ['put("stdout", stdout)', 'put("stderr", stderr)', 'put("errmsg", errorMessage)'],
        "receiver",
        errors,
    )

    collector = texts.get("collector", "")
    require(
        collector,
        [
            'exec-out run-as "$VECTRAS_PACKAGE"',
            'rafaelia-runtime-requests/$TRANSACTION_ID.json',
            'rafaelia-runtime-receipts/$TRANSACTION_ID.json',
            '"logcat_cleared": False',
            '"exported_test_component_used": False',
            'c08_validate_device_evidence.py',
            'sha256sum',
        ],
        "collector",
        errors,
    )
    forbid(
        collector,
        [
            "logcat -c",
            "adb logcat",
            "am broadcast",
            "am startservice",
            "adb pull /data/data",
            "su -c",
        ],
        "collector",
        errors,
    )

    require(
        texts.get("validator", ""),
        [
            'SCHEMA = "raf.c08-device-evidence-closure.v1"',
            '"PASS_DEVICE_EVIDENCE_LIMITED"',
            '"ONE_DEVICE_ONE_TRANSACTION_NONCE_BOUND_ORDERED_BOOT_USERSPACE_SHUTDOWN"',
            'receipt.get("guest_boot_evidence_state") == "COMPLETE_ORDERED_EXIT_ZERO"',
            'device.get("run_as_internal_files") is True',
            'device.get("exported_test_component_used") is False',
            '"claim_allowed": False',
        ],
        "validator",
        errors,
    )

    require(
        texts.get("guest_marker", ""),
        [
            'cat /proc/cmdline',
            'rafaelia.boot_nonce=*',
            'RAFAELIA_GUEST_BOOT_V1',
            'RAFAELIA_GUEST_USERSPACE_V1',
            'RAFAELIA_GUEST_SHUTDOWN_V1',
            'if [ "${#nonce}" -ne 64 ]',
        ],
        "guest_marker",
        errors,
    )

    require(texts.get("app_build", ""), ['applicationId "com.rafacodephi.app"'], "app_build", errors)
    for manifest in MANIFESTS:
        label = f"manifest:{manifest.parent.name}"
        text = texts.get(label, "")
        require(
            text,
            [
                'com.vectras.vm.integration.VectrasTermuxResultReceiver',
                'android:exported="false"',
            ],
            label,
            errors,
        )

    state = "FAIL" if errors else "PASS_STATIC_CONTRACT"
    report = {
        "schema": SCHEMA,
        "cycle_id": "C08",
        "state": state,
        "claim_allowed": False,
        "inputs": inputs,
        "checks": {
            "nonce_cryptographically_generated": "SecureRandom()" in texts.get("guest_contract", ""),
            "three_ordered_markers_required": "userspace.range.first < shutdown.range.first" in texts.get("guest_contract", ""),
            "nonce_bound_to_request_hash": 'appendField("guest_boot_nonce"' in texts.get("ipc_contract", ""),
            "request_persisted_before_result": 'put("guest_boot_nonce"' in texts.get("store", ""),
            "raw_output_not_persisted": 'put("stdout", stdout)' not in receiver,
            "collector_uses_run_as": 'exec-out run-as "$VECTRAS_PACKAGE"' in collector,
            "collector_does_not_clear_logcat": "logcat -c" not in collector,
            "collector_does_not_export_test_component": "am broadcast" not in collector,
        },
        "physical_boundary": {
            "static_contract": "VERIFIED_BY_EXECUTION" if not errors else "NOT_PROMOTED",
            "device_collection": "TOKEN_VAZIO",
            "guest_markers": "TOKEN_VAZIO",
            "apk_hashes": "TOKEN_VAZIO",
            "arm32_device": "TOKEN_VAZIO",
            "arm64_device": "TOKEN_VAZIO",
            "claim_allowed": False,
        },
        "errors": errors,
        "falsifiers": [
            "weak_or_reused_nonce",
            "nonce_not_in_canonical_request",
            "marker_sequence_not_ordered",
            "raw_output_persisted",
            "collector_clears_logcat",
            "collector_uses_exported_test_component",
            "device_pass_claimed_without_request_receipt_and_apk_hashes",
        ],
    }

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"state": state, "error_count": len(errors)}, sort_keys=True))
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
