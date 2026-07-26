from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/device/c08_validate_device_evidence.py"
NONCE = "a" * 64
SHA = "b" * 64
TX = "tx-vectras-termux-fixture-0001"


def request_fixture() -> dict:
    now = int(time.time() * 1000) - 2000
    return {
        "schema": "raf.vectras-termux-request.v3",
        "transaction_id": TX,
        "binary_name": "qemu-system-aarch64",
        "arguments": [
            "-accel",
            "tcg",
            "-append",
            f"console=ttyAMA0 rafaelia.boot_nonce={NONCE}",
        ],
        "guest_boot_nonce": NONCE,
        "guest_boot_nonce_argument_present": True,
        "request_sha256": SHA,
        "created_at_epoch_ms": now,
        "claim_allowed": False,
    }


def receipt_fixture() -> dict:
    now = int(time.time() * 1000) - 1000
    return {
        "schema": "raf.android-runtime-receipt.v2",
        "transaction_id": TX,
        "input_sha256": SHA,
        "output_sha256": SHA,
        "stdout_sha256": SHA,
        "stderr_sha256": SHA,
        "termux_error_message_sha256": SHA,
        "guest_boot_nonce": NONCE,
        "result_bundle_present": True,
        "execution_receipt_present": True,
        "termux_error_code": 0,
        "execution_exit_code": 0,
        "stdout_truncated": False,
        "stderr_truncated": False,
        "guest_boot_evidence_schema": "raf.guest-boot-evidence.v1",
        "guest_boot_evidence_state": "COMPLETE_ORDERED_EXIT_ZERO",
        "guest_boot_evidence_requested": True,
        "guest_boot_evidence_complete": True,
        "guest_boot_marker_observed": True,
        "guest_userspace_marker_observed": True,
        "guest_shutdown_marker_observed": True,
        "guest_markers_ordered": True,
        "guest_arch": "aarch64",
        "guest_kernel": "6.6.0-raf",
        "guest_init": "/sbin/init",
        "guest_shutdown_reason": "poweroff",
        "guest_boot_marker_sha256": SHA,
        "guest_userspace_marker_sha256": SHA,
        "guest_shutdown_marker_sha256": SHA,
        "guest_boot_artifact_sha256": SHA,
        "receipt_created_at_epoch_ms": now,
        "claim_allowed": False,
    }


def package_fixture(name: str) -> dict:
    return {
        "package": name,
        "installed": True,
        "version_name": "1.0",
        "version_code": "1",
        "apk_sha256": [SHA],
    }


def device_fixture() -> dict:
    return {
        "schema": "raf.android-device-manifest.v1",
        "collected_at_epoch_ms": int(time.time() * 1000),
        "adb_serial_sha256": SHA,
        "build_fingerprint_sha256": SHA,
        "boot_id_sha256": SHA,
        "supported_abis": ["arm64-v8a", "armeabi-v7a"],
        "run_command_permission_granted": True,
        "run_as_internal_files": True,
        "logcat_cleared": False,
        "exported_test_component_used": False,
        "packages": {
            "com.rafacodephi.app": package_fixture("com.rafacodephi.app"),
            "com.termux.rafacodephi": package_fixture("com.termux.rafacodephi"),
        },
        "claim_allowed": False,
    }


class C08DeviceEvidenceValidatorTest(unittest.TestCase):
    def run_validator(self, root: Path, request: dict, receipt: dict, device: dict):
        request_path = root / "request.json"
        receipt_path = root / "receipt.json"
        device_path = root / "device.json"
        output = root / "closure.json"
        request_path.write_text(json.dumps(request), encoding="utf-8")
        receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
        device_path.write_text(json.dumps(device), encoding="utf-8")
        completed = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--request",
                str(request_path),
                "--receipt",
                str(receipt_path),
                "--device-manifest",
                str(device_path),
                "--output",
                str(output),
            ],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        return completed, json.loads(output.read_text(encoding="utf-8"))

    def test_complete_nonce_bound_packet_passes_limited(self):
        with tempfile.TemporaryDirectory() as directory:
            completed, report = self.run_validator(
                Path(directory), request_fixture(), receipt_fixture(), device_fixture()
            )
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["state"], "PASS_DEVICE_EVIDENCE_LIMITED")
        self.assertTrue(report["guest_boot_evidence_promotable"])
        self.assertFalse(report["claim_allowed"])

    def test_nonce_mismatch_fails(self):
        receipt = receipt_fixture()
        receipt["guest_boot_nonce"] = "c" * 64
        with tempfile.TemporaryDirectory() as directory:
            completed, report = self.run_validator(
                Path(directory), request_fixture(), receipt, device_fixture()
            )
        self.assertEqual(completed.returncode, 1)
        self.assertEqual(report["state"], "FAIL")
        self.assertTrue(any("nonce mismatch" in item for item in report["errors"]))

    def test_truncated_stdout_fails(self):
        receipt = receipt_fixture()
        receipt["stdout_truncated"] = True
        with tempfile.TemporaryDirectory() as directory:
            completed, report = self.run_validator(
                Path(directory), request_fixture(), receipt, device_fixture()
            )
        self.assertEqual(completed.returncode, 1)
        self.assertEqual(report["state"], "FAIL")
        self.assertTrue(any("stdout is truncated" in item for item in report["errors"]))

    def test_nonzero_internal_error_fails(self):
        receipt = receipt_fixture()
        receipt["termux_error_code"] = 5
        with tempfile.TemporaryDirectory() as directory:
            completed, report = self.run_validator(
                Path(directory), request_fixture(), receipt, device_fixture()
            )
        self.assertEqual(completed.returncode, 1)
        self.assertTrue(any("internal error" in item for item in report["errors"]))

    def test_exported_test_component_fails(self):
        device = device_fixture()
        device["exported_test_component_used"] = True
        with tempfile.TemporaryDirectory() as directory:
            completed, report = self.run_validator(
                Path(directory), request_fixture(), receipt_fixture(), device
            )
        self.assertEqual(completed.returncode, 1)
        self.assertTrue(any("exported test component" in item for item in report["errors"]))

    def test_missing_apk_hash_fails(self):
        device = device_fixture()
        device["packages"]["com.termux.rafacodephi"]["apk_sha256"] = []
        with tempfile.TemporaryDirectory() as directory:
            completed, report = self.run_validator(
                Path(directory), request_fixture(), receipt_fixture(), device
            )
        self.assertEqual(completed.returncode, 1)
        self.assertTrue(any("APK hashes invalid" in item for item in report["errors"]))


if __name__ == "__main__":
    unittest.main()
