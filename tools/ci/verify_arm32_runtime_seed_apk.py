#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-only
"""Fail-closed integrity check for embedded ARMv7 bootstrap/rootfs TARs inside an APK.

This is a packaging integrity check, NOT a proof of extraction, execution or guest boot.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

EXPECTED_FAMILIES = ("bootstrap", "alpine19")
TARGET_ABI = "armeabi-v7a"
TOKEN_VAZIO = "TOKEN_VAZIO"


def verify(apk: Path, manifest_file: Path) -> dict:
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != "vectras.embedded-runtime-seed-assets.v1":
        raise ValueError("unsupported seed manifest")
    expected = {}
    for entry in manifest.get("assets", []):
        if entry.get("abi") != TARGET_ABI or entry.get("family") not in EXPECTED_FAMILIES:
            continue
        family = entry["family"]
        if family in expected:
            raise ValueError(f"duplicate seed manifest family={family}")
        raw_hash = entry.get("sha256", TOKEN_VAZIO)
        size = entry.get("size_bytes")
        if (not isinstance(raw_hash, str) or len(raw_hash) != 64
                or any(c not in "0123456789abcdef" for c in raw_hash.lower())
                or type(size) is not int or size <= 0):
            raise ValueError(f"invalid frozen digest or size for {family}")
        expected[family] = (raw_hash.lower(), size)
    if set(expected) != set(EXPECTED_FAMILIES):
        raise ValueError("incomplete ARMv7 seed manifest")

    entries = []
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("duplicate APK ZIP entries")
        for family in EXPECTED_FAMILIES:
            name = f"assets/{family}/{TARGET_ABI}.tar"
            if name not in names:
                raise ValueError(f"missing required APK seed: {name}")
            info = archive.getinfo(name)
            expected_hash, expected_size = expected[family]
            if info.file_size != expected_size:
                raise ValueError(f"size mismatch for {name}: {info.file_size} != {expected_size}")
            digest = hashlib.sha256()
            size = 0
            with archive.open(name) as input_file:
                while True:
                    data = input_file.read(65536)
                    if not data:
                        break
                    digest.update(data)
                    size += len(data)
                    if size > expected_size:
                        raise ValueError(f"unexpected uncompressed size for {name}")
            actual = digest.hexdigest()
            if size != expected_size or actual != expected_hash:
                raise ValueError(f"SHA-256 mismatch for {name}: {actual}")
            entries.append({"path": name, "size_bytes": size, "sha256": actual, "status": "VERIFIED_PACKAGE_ONLY"})
    return {
        "schema_version": "vectras.arm32-seed-apk-check.v1",
        "source_manifest_sha256": hashlib.sha256(manifest_file.read_bytes()).hexdigest(),
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "abi": TARGET_ABI,
        "assets": entries,
        "packaging_integrity_verified": True,
        "physical_runtime_verified": False,
        "vm_boot_verified": False,
        "claim_allowed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--manifest", type=Path,
                        default=Path("configs/embedded_runtime_seed_assets.v1.json"))
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.apk, args.manifest)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print(f"FAIL_CLOSED_ARM32_SEED_APK: {exc}", file=sys.stderr)
        return 1
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.receipt:
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(payload, encoding="utf-8")
    print("PASS_SCOPED_APK_SEED_INTEGRITY: ARM32 bootstrap+rootfs SHA256 verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
