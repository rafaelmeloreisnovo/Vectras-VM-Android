#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-only
"""Offline packaging-gate falsifiers using a tiny synthetic ZIP (not a VM/APK test)."""
import hashlib
import json
import tempfile
import zipfile
from pathlib import Path

from verify_arm32_runtime_seed_apk import verify


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        path = root / "test.apk"
        manifest_path = root / "manifest.json"
        payloads = {
            "bootstrap": b"bootstrap-known-test-bytes",
            "alpine19": b"alpine-known-test-bytes",
        }
        manifest = {
            "schema_version": "vectras.embedded-runtime-seed-assets.v1",
            "assets": [
                {"family": family, "abi": "armeabi-v7a", "size_bytes": len(data),
                 "sha256": hashlib.sha256(data).hexdigest()}
                for family, data in payloads.items()
            ],
        }
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

        def build_zip(missing="", corrupt=""):
            with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for family, data in payloads.items():
                    if family == missing:
                        continue
                    if family == corrupt:
                        data = b"X" + data[1:]
                    archive.writestr(f"assets/{family}/armeabi-v7a.tar", data)

        build_zip()
        result = verify(path, manifest_path)
        assert result["packaging_integrity_verified"] is True
        assert result["physical_runtime_verified"] is False
        assert result["claim_allowed"] is False

        build_zip(missing="bootstrap")
        try:
            verify(path, manifest_path)
        except ValueError as exc:
            assert "missing required" in str(exc)
        else:
            raise AssertionError("missing bootstrap must fail closed")

        build_zip(corrupt="alpine19")
        try:
            verify(path, manifest_path)
        except ValueError as exc:
            assert "SHA-256 mismatch" in str(exc)
        else:
            raise AssertionError("modified rootfs must fail closed")

        print("PASS_SCOPED_HOST: synthetic APK positive + 2 negative hash/missing-asset gates")


if __name__ == "__main__":
    main()
