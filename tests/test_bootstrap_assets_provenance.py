#!/usr/bin/env python3
from __future__ import annotations

import copy
import hashlib
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path

from tools.bootstrap.validate_bootstrap_assets_manifest import ContractError, validate

CANONICAL = Path("configs/bootstrap_assets.production.v1.json")
ABIS = {
    "arm64-v8a": "arm64-v8a.tar",
    "armeabi-v7a": "armeabi-v7a.tar",
    "x86": "x86.tar",
    "x86_64": "x86_64.tar",
}


def write_safe_tar(path: Path, abi: str) -> None:
    payload = f"RAFAELIA bootstrap fixture for {abi}\n".encode()
    with tarfile.open(path, "w") as archive:
        info = tarfile.TarInfo(f"bootstrap/{abi}/README.txt")
        info.size = len(payload)
        info.mode = 0o644
        archive.addfile(info, io.BytesIO(payload))


def write_unsafe_tar(path: Path) -> None:
    payload = b"escape\n"
    with tarfile.open(path, "w") as archive:
        info = tarfile.TarInfo("../escape.txt")
        info.size = len(payload)
        archive.addfile(info, io.BytesIO(payload))


def ready_manifest(base: dict, assets_dir: Path) -> dict:
    data = copy.deepcopy(base)
    data["state"] = "BOOTSTRAP_ASSETS_VERIFIED_NOT_DEVICE_TESTED"
    for asset in data["required_assets"]:
        path = assets_dir / asset["filename"]
        write_safe_tar(path, asset["abi"])
        asset.update({
            "state": "VERIFIED",
            "source_uri": f"https://example.invalid/bootstrap/{asset['filename']}",
            "source_ref": "release-2026-08-01",
            "license_or_provenance": "TEST_FIXTURE_ONLY",
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "size_bytes": path.stat().st_size,
        })
    return data


class BootstrapAssetsProvenanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.canonical = json.loads(CANONICAL.read_text(encoding="utf-8"))

    def test_canonical_blocked_state_passes(self) -> None:
        report = validate(copy.deepcopy(self.canonical), "blocked", None)
        self.assertFalse(report["ready"])
        self.assertEqual(set(report["missing"]), set(ABIS.values()))

    def test_blocked_manifest_rejects_file_presence_contradiction(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "arm64-v8a.tar").write_bytes(b"present but unregistered")
            with self.assertRaises(ContractError):
                validate(copy.deepcopy(self.canonical), "blocked", root)

    def test_verified_synthetic_fixture_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = ready_manifest(self.canonical, root)
            report = validate(data, "verified", root)
            self.assertTrue(report["ready"])
            self.assertEqual(len(report["verified"]), 4)

    def test_hash_tampering_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = ready_manifest(self.canonical, root)
            (root / "arm64-v8a.tar").write_bytes(b"tampered")
            with self.assertRaises(ContractError):
                validate(data, "verified", root)

    def test_size_tampering_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = ready_manifest(self.canonical, root)
            data["required_assets"][0]["size_bytes"] += 1
            with self.assertRaises(ContractError):
                validate(data, "verified", root)

    def test_missing_provenance_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = ready_manifest(self.canonical, root)
            data["required_assets"][0]["license_or_provenance"] = "TOKEN_VAZIO_LICENSE"
            with self.assertRaises(ContractError):
                validate(data, "verified", root)

    def test_unsafe_tar_member_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = ready_manifest(self.canonical, root)
            target = root / "arm64-v8a.tar"
            write_unsafe_tar(target)
            data["required_assets"][0]["sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
            data["required_assets"][0]["size_bytes"] = target.stat().st_size
            with self.assertRaises(ContractError):
                validate(data, "verified", root)

    def test_wrong_abi_filename_fails(self) -> None:
        data = copy.deepcopy(self.canonical)
        data["required_assets"][0]["filename"] = "x86_64.tar"
        with self.assertRaises(ContractError):
            validate(data, "blocked", None)

    def test_loader_cannot_substitute_architecture_tar(self) -> None:
        data = copy.deepcopy(self.canonical)
        data["loader"]["substitutes_for_architecture_tar"] = True
        with self.assertRaises(ContractError):
            validate(data, "blocked", None)

    def test_release_and_runtime_cannot_promote_in_v1(self) -> None:
        for field in ("claim_allowed", "release_allowed", "android_runtime_verified"):
            data = copy.deepcopy(self.canonical)
            data[field] = True
            with self.subTest(field=field), self.assertRaises(ContractError):
                validate(data, "blocked", None)


if __name__ == "__main__":
    unittest.main()
