#!/usr/bin/env python3
"""Verify that the audited Omega ARMv7 ELF is materialized byte-identically in an APK."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import zipfile
from pathlib import Path

TOKEN_VAZIO = "TOKEN_VAZIO"
ELF_ENTRY = "assets/freestanding/armeabi-v7a/omega-core.elf"
MANIFEST_ENTRY = "assets/freestanding/armeabi-v7a/omega-core.manifest.json"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--audit", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", TOKEN_VAZIO))
    args = parser.parse_args()

    errors: list[str] = []
    audit: dict = {}
    staged_manifest: dict = {}
    asset_sha = TOKEN_VAZIO
    asset_size = 0

    if not args.apk.is_file():
        errors.append(f"missing APK: {args.apk}")
    if not args.audit.is_file():
        errors.append(f"missing deployment audit: {args.audit}")
    else:
        try:
            audit = json.loads(args.audit.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"unreadable deployment audit: {exc}")

    if audit and audit.get("result") != "PASS":
        errors.append("deployment audit is not PASS")

    if args.apk.is_file():
        try:
            with zipfile.ZipFile(args.apk, "r") as apk:
                names = set(apk.namelist())
                if ELF_ENTRY not in names:
                    errors.append(f"missing APK entry: {ELF_ENTRY}")
                else:
                    asset_bytes = apk.read(ELF_ENTRY)
                    asset_sha = sha256_bytes(asset_bytes)
                    asset_size = len(asset_bytes)
                    if asset_bytes[:4] != b"\x7fELF":
                        errors.append("APK Omega asset is not ELF")

                if MANIFEST_ENTRY not in names:
                    errors.append(f"missing APK entry: {MANIFEST_ENTRY}")
                else:
                    staged_manifest = json.loads(apk.read(MANIFEST_ENTRY).decode("utf-8"))
        except (zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError) as exc:
            errors.append(f"APK/manifest parse failure: {exc}")

    audit_sha = audit.get("artifact", {}).get("sha256", TOKEN_VAZIO) if audit else TOKEN_VAZIO
    manifest_sha = (
        staged_manifest.get("asset", {}).get("sha256", TOKEN_VAZIO)
        if staged_manifest
        else TOKEN_VAZIO
    )
    manifest_path = (
        staged_manifest.get("asset", {}).get("apk_path", TOKEN_VAZIO)
        if staged_manifest
        else TOKEN_VAZIO
    )

    if asset_sha != TOKEN_VAZIO and asset_sha != audit_sha:
        errors.append(f"APK asset SHA differs from audited ELF: apk={asset_sha} audit={audit_sha}")
    if asset_sha != TOKEN_VAZIO and asset_sha != manifest_sha:
        errors.append(
            f"APK asset SHA differs from staged manifest: apk={asset_sha} manifest={manifest_sha}"
        )
    if staged_manifest and staged_manifest.get("schema_version") != "vectras.omega-freestanding-apk-asset.v1":
        errors.append("unexpected staged asset manifest schema")
    if staged_manifest and manifest_path != ELF_ENTRY:
        errors.append(f"staged manifest APK path mismatch: {manifest_path}")

    result = "PASS" if not errors else "FAIL"
    receipt = {
        "schema_version": "vectras.omega-freestanding-apk-materialization.v1",
        "record_kind": "APK_ASSET_MATERIALIZATION_RECEIPT",
        "result": result,
        "source": {
            "repository": "rafaelmeloreisnovo/Vectras-VM-Android",
            "commit": args.commit or TOKEN_VAZIO,
            "apk_name": args.apk.name if args.apk.is_file() else TOKEN_VAZIO,
            "apk_sha256": sha256_file(args.apk) if args.apk.is_file() else TOKEN_VAZIO,
            "abi": "armeabi-v7a",
        },
        "materialization": {
            "apk_entry": ELF_ENTRY,
            "manifest_entry": MANIFEST_ENTRY,
            "asset_present": asset_sha != TOKEN_VAZIO,
            "asset_size_bytes": asset_size,
            "asset_sha256": asset_sha,
            "audited_elf_sha256": audit_sha,
            "staged_manifest_sha256": manifest_sha,
            "identity_matches": asset_sha != TOKEN_VAZIO
            and asset_sha == audit_sha
            and asset_sha == manifest_sha,
        },
        "boundary": {
            "apk_materialization_verified": result == "PASS",
            "device_install_verified": False,
            "device_runtime_verified": False,
            "physical_execution_verified": False,
            "vm_boot_verified": False,
            "claim_allowed": False,
            "invariant": "APK_ASSET_PASS != DEVICE_INSTALL != DEVICE_EXECUTION != VM_BOOT",
            "next_gate": "DEVICE_INSTALL_AND_EXECUTION_RECEIPT" if result == "PASS" else "APK_ASSET_HASH_VERIFICATION",
        },
        "token_vazio": [
            "DEVICE_INSTALL_RECEIPT",
            "DEVICE_EXECUTION_RECEIPT",
            "PHYSICAL_DEVICE_EXIT_RECEIPT",
            "END_TO_END_VM_BOOT_EVIDENCE",
        ],
        "errors": errors,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"result": result, "asset_sha256": asset_sha, "errors": errors}, sort_keys=True))
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
