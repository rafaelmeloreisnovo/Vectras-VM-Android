#!/usr/bin/env python3
"""Validate one physical Vectras -> Termux -> QEMU evidence packet."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

SCHEMA = "raf.c08-device-evidence-closure.v1"
HEX64 = re.compile(r"^[0-9a-f]{64}$")
TX = re.compile(r"^[A-Za-z0-9._:-]{8,128}$")
BOOT_TOKEN_PREFIX = "rafaelia.boot_nonce="


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--request", required=True, type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    parser.add_argument("--device-manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--vectras-package", default="com.rafacodephi.app")
    parser.add_argument("--termux-package", default="com.termux.rafacodephi")
    return parser.parse_args()


def load_object(path: Path) -> tuple[dict[str, Any] | None, str | None]:
    if not path.is_file():
        return None, f"missing JSON: {path}"
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        return None, f"invalid JSON {path}: {error}"
    if not isinstance(value, dict):
        return None, f"JSON root is not object: {path}"
    return value, None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def valid_sha(value: Any) -> bool:
    return isinstance(value, str) and HEX64.fullmatch(value) is not None


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def package_entry(device: dict[str, Any], package_name: str) -> dict[str, Any] | None:
    packages = device.get("packages")
    if not isinstance(packages, dict):
        return None
    value = packages.get(package_name)
    return value if isinstance(value, dict) else None


def main() -> int:
    args = parse_args()
    errors: list[str] = []

    request, request_error = load_object(args.request)
    receipt, receipt_error = load_object(args.receipt)
    device, device_error = load_object(args.device_manifest)
    for error in (request_error, receipt_error, device_error):
        if error:
            errors.append(error)

    request = request or {}
    receipt = receipt or {}
    device = device or {}

    require(request.get("schema") == "raf.vectras-termux-request.v3", "request schema mismatch", errors)
    require(receipt.get("schema") == "raf.android-runtime-receipt.v2", "receipt schema mismatch", errors)
    require(device.get("schema") == "raf.android-device-manifest.v1", "device schema mismatch", errors)
    require(request.get("claim_allowed") is False, "request claim_allowed must be false", errors)
    require(receipt.get("claim_allowed") is False, "receipt claim_allowed must be false", errors)
    require(device.get("claim_allowed") is False, "device claim_allowed must be false", errors)

    transaction = request.get("transaction_id")
    require(isinstance(transaction, str) and TX.fullmatch(transaction) is not None, "invalid request transaction", errors)
    require(receipt.get("transaction_id") == transaction, "request/receipt transaction mismatch", errors)

    request_sha = request.get("request_sha256")
    require(valid_sha(request_sha), "request_sha256 invalid", errors)
    require(receipt.get("input_sha256") == request_sha, "receipt input hash mismatch", errors)
    require(valid_sha(receipt.get("output_sha256")), "receipt output hash invalid", errors)
    require(valid_sha(receipt.get("stdout_sha256")), "stdout hash invalid", errors)
    require(valid_sha(receipt.get("stderr_sha256")), "stderr hash invalid", errors)
    require(valid_sha(receipt.get("termux_error_message_sha256")), "errmsg hash invalid", errors)

    nonce = request.get("guest_boot_nonce")
    require(isinstance(nonce, str) and HEX64.fullmatch(nonce) is not None, "guest nonce invalid or absent", errors)
    require(receipt.get("guest_boot_nonce") == nonce, "guest nonce mismatch", errors)
    require(request.get("guest_boot_nonce_argument_present") is True, "nonce argument not recorded", errors)
    arguments = request.get("arguments")
    require(isinstance(arguments, list), "request arguments missing", errors)
    if isinstance(arguments, list) and isinstance(nonce, str):
        token = BOOT_TOKEN_PREFIX + nonce
        require(
            any(
                isinstance(argument, str) and token in argument.split()
                for argument in arguments
            ),
            "nonce token absent from argument vector",
            errors,
        )

    require(receipt.get("result_bundle_present") is True, "Termux result bundle absent", errors)
    require(receipt.get("execution_receipt_present") is True, "process exit receipt absent", errors)
    require(receipt.get("termux_error_code") == 0, "Termux internal error is not zero", errors)
    require(receipt.get("execution_exit_code") == 0, "QEMU process exit is not zero", errors)
    require(receipt.get("stdout_truncated") is False, "stdout is truncated or unknown", errors)
    require(receipt.get("stderr_truncated") in (False, None), "stderr is truncated", errors)

    require(
        receipt.get("guest_boot_evidence_schema") == "raf.guest-boot-evidence.v1",
        "guest evidence schema mismatch",
        errors,
    )
    require(
        receipt.get("guest_boot_evidence_state") == "COMPLETE_ORDERED_EXIT_ZERO",
        "guest evidence state is not complete ordered exit-zero",
        errors,
    )
    for key in (
        "guest_boot_evidence_requested",
        "guest_boot_evidence_complete",
        "guest_boot_marker_observed",
        "guest_userspace_marker_observed",
        "guest_shutdown_marker_observed",
        "guest_markers_ordered",
    ):
        require(receipt.get(key) is True, f"{key} is not true", errors)
    for key in (
        "guest_boot_marker_sha256",
        "guest_userspace_marker_sha256",
        "guest_shutdown_marker_sha256",
        "guest_boot_artifact_sha256",
    ):
        require(valid_sha(receipt.get(key)), f"{key} invalid", errors)
    require(bool(receipt.get("guest_arch")), "guest arch absent", errors)
    require(bool(receipt.get("guest_kernel")), "guest kernel absent", errors)
    require(bool(receipt.get("guest_init")), "guest init absent", errors)
    require(receipt.get("guest_shutdown_reason") in ("poweroff", "halt", "reboot"), "shutdown reason invalid", errors)

    require(device.get("run_as_internal_files") is True, "internal request/receipt collection not proven", errors)
    require(device.get("logcat_cleared") is False, "collector must not clear logcat", errors)
    require(device.get("exported_test_component_used") is False, "exported test component was used", errors)
    require(valid_sha(device.get("adb_serial_sha256")), "ADB serial hash invalid", errors)
    require(valid_sha(device.get("build_fingerprint_sha256")), "build fingerprint hash invalid", errors)
    require(valid_sha(device.get("boot_id_sha256")), "boot id hash invalid", errors)
    abis = device.get("supported_abis")
    require(isinstance(abis, list) and bool(abis), "device ABI list missing", errors)
    require(device.get("run_command_permission_granted") is True, "RUN_COMMAND permission not granted", errors)

    for package_name in (args.vectras_package, args.termux_package):
        package = package_entry(device, package_name)
        require(package is not None, f"package manifest missing: {package_name}", errors)
        if package is None:
            continue
        require(package.get("installed") is True, f"package not installed: {package_name}", errors)
        require(bool(package.get("version_name")), f"version name absent: {package_name}", errors)
        require(str(package.get("version_code", "")).isdigit(), f"version code invalid: {package_name}", errors)
        hashes = package.get("apk_sha256")
        require(
            isinstance(hashes, list) and bool(hashes) and all(valid_sha(item) for item in hashes),
            f"APK hashes invalid: {package_name}",
            errors,
        )

    request_time = request.get("created_at_epoch_ms")
    receipt_time = receipt.get("receipt_created_at_epoch_ms")
    collected_time = device.get("collected_at_epoch_ms")
    require(isinstance(request_time, int) and request_time > 0, "request timestamp invalid", errors)
    require(isinstance(receipt_time, int) and receipt_time >= request_time, "receipt timestamp ordering invalid", errors)
    require(isinstance(collected_time, int) and collected_time >= receipt_time, "collection timestamp ordering invalid", errors)

    state = "PASS_DEVICE_EVIDENCE_LIMITED" if not errors else "FAIL"
    report = {
        "schema": SCHEMA,
        "cycle_id": "C08",
        "state": state,
        "claim_allowed": False,
        "guest_boot_evidence_promotable": not errors,
        "promotion_scope": (
            "ONE_DEVICE_ONE_TRANSACTION_NONCE_BOUND_ORDERED_BOOT_USERSPACE_SHUTDOWN"
            if not errors
            else "NOT_PROMOTED"
        ),
        "transaction_id": transaction or "TOKEN_VAZIO",
        "request_sha256": request_sha or "TOKEN_VAZIO",
        "receipt_output_sha256": receipt.get("output_sha256", "TOKEN_VAZIO"),
        "guest_boot_artifact_sha256": receipt.get("guest_boot_artifact_sha256", "TOKEN_VAZIO"),
        "input_files": {
            "request": {"path": str(args.request), "sha256": sha256_file(args.request) if args.request.is_file() else "TOKEN_VAZIO"},
            "receipt": {"path": str(args.receipt), "sha256": sha256_file(args.receipt) if args.receipt.is_file() else "TOKEN_VAZIO"},
            "device_manifest": {"path": str(args.device_manifest), "sha256": sha256_file(args.device_manifest) if args.device_manifest.is_file() else "TOKEN_VAZIO"},
        },
        "checks": {
            "request_receipt_bound": receipt.get("input_sha256") == request_sha,
            "nonce_bound": isinstance(nonce, str) and receipt.get("guest_boot_nonce") == nonce,
            "ordered_markers": receipt.get("guest_markers_ordered") is True,
            "userspace_ready": receipt.get("guest_userspace_marker_observed") is True,
            "clean_process_exit": receipt.get("execution_exit_code") == 0,
            "no_termux_internal_error": receipt.get("termux_error_code") == 0,
            "output_complete": receipt.get("stdout_truncated") is False,
            "device_and_packages_identified": not any("package" in error for error in errors),
        },
        "runtime_boundary": {
            "guest_boot_on_observed_device": "VERIFIED_LIMITED" if not errors else "NOT_PROMOTED",
            "other_devices": "TOKEN_VAZIO",
            "arm32_device": "TOKEN_VAZIO_UNLESS_DEVICE_MANIFEST_ARM32",
            "arm64_device": "TOKEN_VAZIO_UNLESS_DEVICE_MANIFEST_ARM64",
            "vm_correctness_beyond_markers": "TOKEN_VAZIO",
            "performance_claim": "FORBIDDEN_OUT_OF_SCOPE",
        },
        "errors": sorted(set(errors)),
        "falsifiers": [
            "transaction_or_request_hash_mismatch",
            "nonce_absent_or_not_in_argument_vector",
            "marker_missing_or_out_of_order",
            "stdout_truncated",
            "termux_internal_error_nonzero",
            "qemu_exit_nonzero_or_absent",
            "package_or_apk_hash_missing",
            "run_as_internal_collection_not_proven",
            "exported_test_component_used",
            "claim_expanded_beyond_observed_device_and_transaction",
        ],
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{state} C08 device evidence: {args.output}")
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
