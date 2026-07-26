from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/ci/aggregate_freestanding_ndk_receipts.py"
COMMIT = "a" * 40
DIGEST = "b" * 64


def manifest(abi: str, *, commit: str = COMMIT, result: str = "PASS") -> dict:
    checks = {
        "archive_consumed": True,
        "blake3_hashes": True,
        "controlled_entry": True,
        "forbidden_symbols_absent": True,
        "no_needed_libraries": True,
        "no_unexpected_undefined_symbols": True,
        "reproducible_binary": True,
    }
    return {
        "schema": "vectra.freestanding-link-probe.v1",
        "result": result,
        "source": {"abi": abi, "commit": commit},
        "artifact": {
            "sha256": DIGEST,
            "blake3": DIGEST,
            "reproducible": True,
        },
        "map": {"archive_witness": True},
        "elf": {"entry_matches_symbol": True},
        "symbols": {
            "unexpected_undefined": [],
            "needed_libraries": [],
            "forbidden_present": [],
        },
        "checks": checks,
        "errors": [],
    }


class AggregateFreestandingReceiptsTest(unittest.TestCase):
    def run_aggregate(self, root: Path, *, omit: str | None = None, mutate=None):
        paths = {
            "host": root / "host.json",
            "arm32": root / "arm32.json",
            "arm64": root / "arm64.json",
        }
        payloads = {
            "host": manifest("host-x86_64"),
            "arm32": manifest("armeabi-v7a"),
            "arm64": manifest("arm64-v8a"),
        }
        if mutate is not None:
            mutate(payloads)
        for name, path in paths.items():
            if name != omit:
                path.write_text(json.dumps(payloads[name]), encoding="utf-8")

        output = root / "receipt.json"
        completed = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--host",
                str(paths["host"]),
                "--arm32",
                str(paths["arm32"]),
                "--arm64",
                str(paths["arm64"]),
                "--output",
                str(output),
                "--commit",
                COMMIT,
            ],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        return completed, json.loads(output.read_text(encoding="utf-8"))

    def test_passes_with_three_coherent_manifests(self):
        with tempfile.TemporaryDirectory() as directory:
            completed, receipt = self.run_aggregate(Path(directory))
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(receipt["result"], "PASS")
        self.assertTrue(receipt["checks"]["android_abis_complete"])
        self.assertFalse(receipt["claim_allowed"])
        self.assertEqual(receipt["claim_boundary"]["device_runtime"], "TOKEN_VAZIO")

    def test_missing_arm32_is_incomplete(self):
        with tempfile.TemporaryDirectory() as directory:
            completed, receipt = self.run_aggregate(Path(directory), omit="arm32")
        self.assertEqual(completed.returncode, 1)
        self.assertEqual(receipt["result"], "INCOMPLETE")
        self.assertFalse(receipt["checks"]["all_manifests_present"])

    def test_commit_mismatch_fails(self):
        def mutate(payloads):
            payloads["arm64"]["source"]["commit"] = "c" * 40

        with tempfile.TemporaryDirectory() as directory:
            completed, receipt = self.run_aggregate(Path(directory), mutate=mutate)
        self.assertEqual(completed.returncode, 1)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertFalse(receipt["checks"]["all_manifests_same_commit"])

    def test_token_vazio_blake3_fails(self):
        def mutate(payloads):
            payloads["arm32"]["artifact"]["blake3"] = "TOKEN_VAZIO"
            payloads["arm32"]["checks"]["blake3_hashes"] = "TOKEN_VAZIO"

        with tempfile.TemporaryDirectory() as directory:
            completed, receipt = self.run_aggregate(Path(directory), mutate=mutate)
        self.assertEqual(completed.returncode, 1)
        self.assertEqual(receipt["result"], "FAIL")
        self.assertFalse(receipt["checks"]["arm32_manifest_valid"])


if __name__ == "__main__":
    unittest.main()
