#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "device_runtime" / "validate_vectras_runtime_receipt.py"
spec = importlib.util.spec_from_file_location("vectras_runtime_receipt", MODULE_PATH)
validator = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(validator)


def probe(ok: bool = True, exit_code: int = 0) -> dict:
    return {
        "ok": ok,
        "exit_code": exit_code,
        "status": "SUCCESS" if ok else "FAILURE",
        "detail": "ok" if ok else "failed",
    }


def receipt(receipt_id: str, created: int, *, claim: bool = True, apk: str = "b" * 64) -> dict:
    hash_or_gap = (lambda value: value if claim else "TOKEN_VAZIO")
    launch_argv = ["/usr/bin/qemu-system-x86_64", "-machine", "q35", "-m", "512"]
    return {
        "schema": validator.SCHEMA,
        "receipt_id": receipt_id,
        "created_unix_ms": created,
        "package_name": "com.rafacodephi.app",
        "requested_qemu_token": "qemu-system-x86_64",
        "requested_qemu_binary": "qemu-system-x86_64",
        "guest_path": validator.GUEST_PATH,
        "resolved_qemu_guest_path": "/usr/bin/qemu-system-x86_64",
        "resolved_qemu_img_guest_path": "/usr/bin/qemu-img",
        "resolved_launch_argv_sha256": validator.sha256_argv(launch_argv),
        "resolved_launch_argv": launch_argv,
        "apk_sha256": apk if claim else "TOKEN_VAZIO",
        "proot_sha256": hash_or_gap("c" * 64),
        "qemu_sha256": hash_or_gap("d" * 64),
        "qemu_img_sha256": hash_or_gap("e" * 64),
        "proot_executable": claim,
        "root_shell_executable": claim,
        "qemu_executable": claim,
        "qemu_img_executable": claim,
        "bootstrap_validator_ok": claim,
        "bootstrap_validator_summary": "ok" if claim else "runtime-gap",
        "qemu_probe": probe(claim, 0 if claim else 127),
        "qemu_img_probe": probe(claim, 0 if claim else 127),
        "android_fingerprint": "vendor/device/build:15/ABC/123:user/release-keys",
        "android_model": "device",
        "android_manufacturer": "vendor",
        "android_hardware": "soc",
        "android_supported_abis": ["arm64-v8a", "armeabi-v7a"],
        "device_state": "DEVICE_PROVEN" if claim else "TOKEN_VAZIO",
        "reproduced_state": "TOKEN_VAZIO",
        "claim_allowed": claim,
        "reason": "private-runtime-hash-exec-and-argv-probes-pass" if claim else "runtime-gap",
    }


class VectrasRuntimeReceiptTest(unittest.TestCase):
    def write(self, root: Path, name: str, payload: dict) -> Path:
        path = root / name
        path.write_text(json.dumps(payload, sort_keys=True) + "\n", encoding="utf-8")
        return path

    def test_valid_device_proven(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = validator.validate(self.write(Path(tmp), "ok.json", receipt("vectras-run-0001", 1000)))
            self.assertTrue(result["valid"], result["errors"])

    def test_valid_token_vazio_when_runtime_incomplete(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = validator.validate(self.write(Path(tmp), "gap.json", receipt("vectras-run-0002", 1001, claim=False)))
            self.assertTrue(result["valid"], result["errors"])

    def test_rejects_claim_when_qemu_probe_failed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0003", 1002)
            payload["qemu_probe"] = probe(False, 1)
            result = validator.validate(self.write(Path(tmp), "bad.json", payload))
            self.assertFalse(result["valid"])
            self.assertIn("promotion invariant violated", result["errors"])

    def test_rejects_hash_path_vs_requested_binary_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0004", 1003)
            payload["resolved_qemu_guest_path"] = "/usr/bin/qemu-system-aarch64"
            result = validator.validate(self.write(Path(tmp), "mismatch.json", payload))
            self.assertFalse(result["valid"])
            self.assertIn("resolved_qemu_guest_path basename mismatch", result["errors"])

    def test_rejects_resolved_argv_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0005", 1004)
            payload["resolved_launch_argv"][2] = "pc"
            result = validator.validate(self.write(Path(tmp), "argv-tamper.json", payload))
            self.assertFalse(result["valid"])
            self.assertIn("resolved_launch_argv_sha256 mismatch", result["errors"])

    def test_rejects_requested_token_basename_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0006", 1005)
            payload["requested_qemu_token"] = "/usr/bin/qemu-system-aarch64"
            result = validator.validate(self.write(Path(tmp), "token-mismatch.json", payload))
            self.assertFalse(result["valid"])
            self.assertIn("requested_qemu_token basename mismatch", result["errors"])

    def test_single_receipt_cannot_self_promote_reproduced(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0007", 1006)
            payload["reproduced_state"] = "REPRODUCED"
            result = validator.validate(self.write(Path(tmp), "self.json", payload))
            self.assertFalse(result["valid"])
            self.assertTrue(any("self-promote" in item for item in result["errors"]))

    def test_two_distinct_identically_bound_runs_reproduce(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a = validator.validate(self.write(root, "a.json", receipt("vectras-run-0008", 1007)))
            b = validator.validate(self.write(root, "b.json", receipt("vectras-run-0009", 1008)))
            reproduced = validator.reproduce(a, b)
            self.assertEqual("REPRODUCED", reproduced["reproduced_state"])
            self.assertTrue(reproduced["claim_allowed"])

    def test_reproduction_rejects_apk_change(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a = validator.validate(self.write(root, "a.json", receipt("vectras-run-0010", 1009, apk="1" * 64)))
            b = validator.validate(self.write(root, "b.json", receipt("vectras-run-0011", 1010, apk="2" * 64)))
            reproduced = validator.reproduce(a, b)
            self.assertEqual("TOKEN_VAZIO", reproduced["reproduced_state"])
            self.assertFalse(reproduced["claim_allowed"])
            self.assertIn("apk_sha256 mismatch", reproduced["errors"])

    def test_reproduction_rejects_resolved_argv_change(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a_payload = receipt("vectras-run-0012", 1011)
            b_payload = receipt("vectras-run-0013", 1012)
            b_payload["resolved_launch_argv"][-1] = "1024"
            b_payload["resolved_launch_argv_sha256"] = validator.sha256_argv(b_payload["resolved_launch_argv"])
            a = validator.validate(self.write(root, "a.json", a_payload))
            b = validator.validate(self.write(root, "b.json", b_payload))
            reproduced = validator.reproduce(a, b)
            self.assertEqual("TOKEN_VAZIO", reproduced["reproduced_state"])
            self.assertIn("resolved_launch_argv_sha256 mismatch", reproduced["errors"])

    def test_reproduction_rejects_device_fingerprint_change(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a_payload = receipt("vectras-run-0014", 1013)
            b_payload = receipt("vectras-run-0015", 1014)
            b_payload["android_fingerprint"] = "different/fingerprint"
            a = validator.validate(self.write(root, "a.json", a_payload))
            b = validator.validate(self.write(root, "b.json", b_payload))
            reproduced = validator.reproduce(a, b)
            self.assertEqual("TOKEN_VAZIO", reproduced["reproduced_state"])
            self.assertIn("android_fingerprint mismatch", reproduced["errors"])


if __name__ == "__main__":
    unittest.main()
