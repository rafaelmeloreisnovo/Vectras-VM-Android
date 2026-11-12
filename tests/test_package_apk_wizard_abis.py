#!/usr/bin/env python3
"""Adversarial tests for APK-Wizard ABI ZIP distribution (no Android SDK needed)."""
from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.ci.package_apk_wizard_abis import LANES, PackageError, package_lane


COMMIT = "a" * 40


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PackageAbiZipsTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.addCleanup(self.work.cleanup)
        self.root = Path(self.work.name) / "build"
        self.out = Path(self.work.name) / "packages"
        self.root.mkdir()

    def create_lane(self, spec, *, extra_native=(), extra_runtime=()):
        lane, stem, policy, abis = spec
        apk = self.root / f"{stem}.apk"
        native = []
        with zipfile.ZipFile(apk, "w") as z:
            z.writestr("AndroidManifest.xml", b"manifest")
            z.writestr("classes.dex", b"dex")
            z.writestr("assets/bootstrap/loader.apk", b"loader")
            for abi in abis:
                for lib in ("libtermux-bootstrap.so", "libtermux_terminal_jni.so"):
                    entry = f"lib/{abi}/{lib}"
                    native.append(entry)
                    z.writestr(entry, b"ELF test fixture")
                for family in ("bootstrap", "alpine19", "qemu19"):
                    z.writestr(f"assets/{family}/{abi}.tar", b"fixture")
            for abi in extra_native:
                entry = f"lib/{abi}/libforeign.so"
                native.append(entry)
                z.writestr(entry, b"ELF")
            for abi in extra_runtime:
                z.writestr(f"assets/qemu19/{abi}.tar", b"fixture")
        receipt = {
            "sha256": sha(apk), "required_abis": list(abis),
            "runtime_payload_complete": True, "claim_allowed": False,
        }
        (self.root / f"{stem}.runtime-payload.json").write_text(json.dumps(receipt))
        evidence_path = self.root / f"{stem}.build-evidence.json"
        evidence = {
            "record_kind": "BUILD_OUTPUT_EVIDENCE",
            "source": {"git_head": COMMIT},
            "lane": {"name": stem, "abi_policy": policy, "supported_abis": list(abis)},
            "apk": {"sha256": sha(apk), "zip_inventory": {"native_libraries": sorted(native)}},
            "claims": {"claim_allowed": False, "device_runtime_verified": False},
        }
        evidence_path.write_text(json.dumps(evidence))
        (self.root / f"{stem}.build-evidence.json.sha256").write_text(
            f"{sha(evidence_path)}  {evidence_path.name}\n"
        )
        if lane != "arm64-v8a":
            (self.root / f"{stem}.omega-materialization.json").write_text("{}")
        return apk

    def test_three_packages_are_independent_and_lossless(self):
        for spec in LANES:
            apk = self.create_lane(spec)
            archive_path = package_lane(self.root, self.out, spec, COMMIT)
            self.assertEqual(archive_path.name, f"vectras-debug-{spec[0]}.zip")
            with zipfile.ZipFile(archive_path) as z:
                self.assertIsNone(z.testzip())
                manifest = json.loads(z.read("MANIFEST.json"))
                self.assertEqual(manifest["expected_native_abis"], list(spec[3]))
                self.assertFalse(manifest["claim_allowed"])
                self.assertEqual(hashlib.sha256(z.read(apk.name)).hexdigest(), sha(apk))
                self.assertIn(apk.name, z.namelist())
                self.assertIn("SHA256SUMS", z.namelist())
                self.assertIn("README.txt", z.namelist())
        self.assertEqual(len(list(self.out.glob("*.zip"))), 3)

    def test_rejects_wrong_native_abi_even_with_valid_receipts(self):
        spec = LANES[0]
        self.create_lane(spec, extra_native=("armeabi-v7a",))
        with self.assertRaisesRegex(PackageError, "ABI contamination"):
            package_lane(self.root, self.out, spec, COMMIT)

    def test_extra_runtime_assets_are_disclosed_not_misrepresented(self):
        spec = LANES[0]
        self.create_lane(spec, extra_runtime=("armeabi-v7a",))
        out = package_lane(self.root, self.out, spec, COMMIT)
        with zipfile.ZipFile(out) as z:
            manifest = json.loads(z.read("MANIFEST.json"))
        self.assertTrue(manifest["runtime_assets_may_include_other_abis"])
        self.assertEqual(manifest["embedded_runtime_abis"]["qemu19"],
                         ["arm64-v8a", "armeabi-v7a"])

    def test_rejects_mismatched_runtime_receipt(self):
        spec = LANES[0]
        self.create_lane(spec)
        path = self.root / f"{spec[1]}.runtime-payload.json"
        receipt = json.loads(path.read_text())
        receipt["sha256"] = "0" * 64
        path.write_text(json.dumps(receipt))
        with self.assertRaisesRegex(PackageError, "hash mismatch"):
            package_lane(self.root, self.out, spec, COMMIT)

    def test_rejects_forged_build_evidence_and_checksum(self):
        spec = LANES[1]
        self.create_lane(spec)
        evidence = self.root / f"{spec[1]}.build-evidence.json"
        obj = json.loads(evidence.read_text())
        obj["source"]["git_head"] = "b" * 40
        evidence.write_text(json.dumps(obj))
        with self.assertRaisesRegex(PackageError, "source commit mismatch"):
            package_lane(self.root, self.out, spec, COMMIT)

    def test_rejects_missing_arm32_omega_receipt(self):
        spec = LANES[1]
        self.create_lane(spec)
        (self.root / f"{spec[1]}.omega-materialization.json").unlink()
        with self.assertRaisesRegex(PackageError, "Omega ARMv7"):
            package_lane(self.root, self.out, spec, COMMIT)

    def test_does_not_overwrite_existing_packages(self):
        spec = LANES[0]
        self.create_lane(spec)
        package_lane(self.root, self.out, spec, COMMIT)
        with self.assertRaisesRegex(PackageError, "no overwrite"):
            package_lane(self.root, self.out, spec, COMMIT)


if __name__ == "__main__":
    unittest.main()
