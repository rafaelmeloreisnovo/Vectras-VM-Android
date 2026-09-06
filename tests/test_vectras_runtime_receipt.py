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


def receipt(receipt_id: str, created: int, *, claim: bool = True, apk: str | None = None) -> dict:
    good = "a" * 64
    return {
        "schema": validator.SCHEMA,
        "receipt_id": receipt_id,
        "created_unix_ms": created,
        "package_name": "com.rafacodephi.app",
        "requested_qemu_binary": "qemu-system-x86_64",
        "apk_sha256": apk or "b" * 64,
        "proot_sha256": "c" * 64,
        "qemu_sha256": "d" * 64,
        "qemu_img_sha256": good,
        "proot_executable": claim,
        "root_shell_executable": claim,
        "qemu_executable": claim,
        "qemu_img_executable": claim,
        "bootstrap_validator_ok": claim,
        "bootstrap_validator_summary": "ok" if claim else "missing-runtime",
        "qemu_probe": probe(claim, 0 if claim else 127),
        "qemu_img_probe": probe(claim, 0 if claim else 127),
        "device_state": "DEVICE_PROVEN" if claim else "TOKEN_VAZIO",
        "reproduced_state": "TOKEN_VAZIO",
        "claim_allowed": claim,
        "reason": "all-private-runtime-and-exec-probes-pass" if claim else "runtime-gap",
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

    def test_rejects_claim_when_qemu_probe_failed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0002", 1001)
            payload["qemu_probe"] = probe(False, 1)
            result = validator.validate(self.write(Path(tmp), "bad.json", payload))
            self.assertFalse(result["valid"])
            self.assertIn("promotion invariant violated", result["errors"])

    def test_token_vazio_is_valid_when_runtime_is_incomplete(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = validator.validate(self.write(Path(tmp), "gap.json", receipt("vectras-run-0003", 1002, claim=False)))
            self.assertTrue(result["valid"], result["errors"])

    def test_two_distinct_hash_bound_runs_reproduce(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a = validator.validate(self.write(root, "a.json", receipt("vectras-run-0004", 1003)))
            b = validator.validate(self.write(root, "b.json", receipt("vectras-run-0005", 1004)))
            reproduced = validator.reproduce(a, b)
            self.assertEqual("REPRODUCED", reproduced["reproduced_state"])
            self.assertTrue(reproduced["claim_allowed"])

    def test_reproduction_rejects_apk_change(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a = validator.validate(self.write(root, "a.json", receipt("vectras-run-0006", 1005, apk="1" * 64)))
            b = validator.validate(self.write(root, "b.json", receipt("vectras-run-0007", 1006, apk="2" * 64)))
            reproduced = validator.reproduce(a, b)
            self.assertEqual("TOKEN_VAZIO", reproduced["reproduced_state"])
            self.assertFalse(reproduced["claim_allowed"])
            self.assertIn("apk_sha256 mismatch", reproduced["errors"])

    def test_single_receipt_cannot_self_promote_reproduced(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = receipt("vectras-run-0008", 1007)
            payload["reproduced_state"] = "REPRODUCED"
            result = validator.validate(self.write(Path(tmp), "self.json", payload))
            self.assertFalse(result["valid"])
            self.assertTrue(any("self-promote" in item for item in result["errors"]))


if __name__ == "__main__":
    unittest.main()
