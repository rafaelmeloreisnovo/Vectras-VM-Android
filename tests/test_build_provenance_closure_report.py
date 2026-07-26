from __future__ import annotations

import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/ci/build_provenance_closure_report.py"
SOURCE_COMMIT = "a" * 40
QEMU_COMMIT = "b" * 40


class ProvenanceClosureTest(unittest.TestCase):
    def make_repo(self, root: Path, *, unresolved: bool = False, unregistered: bool = False):
        (root / "resources/compliance").mkdir(parents=True)
        (root / "sbom").mkdir()
        (root / "legal").mkdir()
        (root / "tools").mkdir()
        asset = root / "app/src/main/jniLibs/arm64-v8a/libdemo.so"
        asset.parent.mkdir(parents=True)
        asset.write_bytes(b"demo-binary")
        import hashlib

        digest = hashlib.sha256(asset.read_bytes()).hexdigest()
        register = root / "resources/compliance/ASSET_PROVENANCE_REGISTER.csv"
        fields = [
            "asset_path",
            "asset_type",
            "author",
            "source_url",
            "license_spdx",
            "permission_proof",
            "risk_class",
            "status",
            "notes",
            "last_review_date",
        ]
        author = "TOKEN_VAZIO" if unresolved else "Demo Author"
        source = "TOKEN_VAZIO" if unresolved else "https://example.invalid/source"
        license_spdx = "TOKEN_VAZIO" if unresolved else "MIT"
        status = "blocked-sha256-known" if unresolved else "approved"
        with register.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerow(
                {
                    "asset_path": "app/src/main/jniLibs/arm64-v8a/libdemo.so",
                    "asset_type": "binary-so",
                    "author": author,
                    "source_url": source,
                    "license_spdx": license_spdx,
                    "permission_proof": f"sha256={digest}",
                    "risk_class": "CRITICAL" if unresolved else "A",
                    "status": status,
                    "notes": "fixture",
                    "last_review_date": "2026-07-26",
                }
            )
        if unregistered:
            extra = root / "app/src/main/jniLibs/arm64-v8a/libunknown.so"
            extra.write_bytes(b"unknown")

        sbom = {
            "spdxVersion": "SPDX-2.3",
            "packages": [
                {
                    "SPDXID": "SPDXRef-demo",
                    "name": "demo",
                    "versionInfo": "1",
                    "downloadLocation": "https://example.invalid/source",
                    "licenseConcluded": "MIT",
                    "licenseDeclared": "MIT",
                    "checksums": [{"algorithm": "SHA256", "checksumValue": digest}],
                }
            ],
        }
        (root / "sbom/SBOM.spdx.json").write_text(json.dumps(sbom), encoding="utf-8")
        (root / "legal/LEGAL_SCOPE_MAP.yaml").write_text("demo:\n  license: MIT\n", encoding="utf-8")
        (root / "tools/qemu_rafaelia_assets.lock.yml").write_text(
            f"source_commit_observed: {QEMU_COMMIT}\n", encoding="utf-8"
        )

    def run_report(self, root: Path, *, expected_qemu: str = QEMU_COMMIT):
        output = root / "report.json"
        completed = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--repo",
                str(root),
                "--expected-qemu-commit",
                expected_qemu,
                "--source-commit",
                SOURCE_COMMIT,
                "--output",
                str(output),
            ],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        return completed, json.loads(output.read_text(encoding="utf-8"))

    def test_fully_resolved_inventory_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_repo(root)
            completed, report = self.run_report(root)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["state"], "PASS")
        self.assertTrue(report["qemu_pin_matches"])
        self.assertFalse(report["claim_allowed"])

    def test_registered_critical_token_vazio_is_quarantined(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_repo(root, unresolved=True)
            completed, report = self.run_report(root)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["state"], "PASS_WITH_QUARANTINE")
        self.assertEqual(report["summary"]["critical_unresolved_rows"], 1)
        self.assertTrue(report["quarantine"]["required"])

    def test_qemu_pin_mismatch_is_quarantine_not_false_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_repo(root)
            completed, report = self.run_report(root, expected_qemu="c" * 40)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(report["state"], "PASS_WITH_QUARANTINE")
        self.assertFalse(report["qemu_pin_matches"])

    def test_unregistered_binary_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_repo(root, unregistered=True)
            completed, report = self.run_report(root)
        self.assertEqual(completed.returncode, 1)
        self.assertEqual(report["state"], "FAIL")
        self.assertTrue(any("unregistered binary" in item for item in report["errors"]))


if __name__ == "__main__":
    unittest.main()
