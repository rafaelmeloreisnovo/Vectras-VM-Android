#!/usr/bin/env python3
"""Verify audited Omega ARMv7 ELF identity in Android's APK-native carrier."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import zipfile
from pathlib import Path

TOKEN_VAZIO = "TOKEN_VAZIO"
ELF_ENTRY = "lib/armeabi-v7a/libomega_core_exec.so"
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
    carrier_sha = TOKEN_VAZIO
    carrier_size = 0
    carrier_compression = TOKEN_VAZIO

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
                    errors.append(f"missing APK native carrier: {ELF_ENTRY}")
                else:
                    info = apk.getinfo(ELF_ENTRY)
                    carrier_bytes = apk.read(ELF_ENTRY)
                    carrier_sha = sha256_bytes(carrier_bytes)
                    carrier_size = len(carrier_bytes)
                    carrier_compression = (
                        "STORED" if info.compress_type == zipfile.ZIP_STORED else f"ZIP_METHOD_{info.compress_type}"
                    )
                    if carrier_bytes[:4] != b"\x7fELF":
                        errors.append("APK Omega native carrier is not ELF")

                if MANIFEST_ENTRY not in names:
                    errors.append(f"missing APK deployment manifest: {MANIFEST_ENTRY}")
                else:
                    staged_manifest = json.loads(apk.read(MANIFEST_ENTRY).decode("utf-8"))
        except (zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError) as exc:
            errors.append(f"APK/manifest parse failure: {exc}")

    audit_sha = audit.get("artifact", {}).get("sha256", TOKEN_VAZIO) if audit else TOKEN_VAZIO
    deployment = staged_manifest.get("deployment", {}) if staged_manifest else {}
    manifest_sha = deployment.get("sha256", TOKEN_VAZIO)
    manifest_path = deployment.get("apk_executable_path", TOKEN_VAZIO)
    carrier_semantics = deployment.get("carrier_semantics", TOKEN_VAZIO)

    if carrier_sha != TOKEN_VAZIO and carrier_sha != audit_sha:
        errors.append(f"APK carrier SHA differs from audited ELF: apk={carrier_sha} audit={audit_sha}")
    if carrier_sha != TOKEN_VAZIO and carrier_sha != manifest_sha:
        errors.append(
            f"APK carrier SHA differs from staged manifest: apk={carrier_sha} manifest={manifest_sha}"
        )
    if staged_manifest and staged_manifest.get("schema_version") != "vectras.omega-freestanding-apk-asset.v2":
        errors.append("unexpected staged native-carrier manifest schema")
    if staged_manifest and manifest_path != ELF_ENTRY:
        errors.append(f"staged manifest APK path mismatch: {manifest_path}")
    if staged_manifest and carrier_semantics != "ELF_ET_EXEC_NOT_SHARED_LIBRARY":
        errors.append(f"unexpected native carrier semantics: {carrier_semantics}")

    result = "PASS" if not errors else "FAIL"
    receipt = {
        "schema_version": "vectras.omega-freestanding-apk-materialization.v2",
        "record_kind": "APK_NATIVE_EXECUTABLE_CARRIER_RECEIPT",
        "result": result,
        "source": {
            "repository": "rafaelmeloreisnovo/Vectras-VM-Android",
            "commit": args.commit or TOKEN_VAZIO,
            "apk_name": args.apk.name if args.apk.is_file() else TOKEN_VAZIO,
            "apk_sha256": sha256_file(args.apk) if args.apk.is_file() else TOKEN_VAZIO,
            "abi": "armeabi-v7a",
        },
        "materialization": {
            "apk_native_entry": ELF_ENTRY,
            "manifest_entry": MANIFEST_ENTRY,
            "carrier_present": carrier_sha != TOKEN_VAZIO,
            "carrier_size_bytes": carrier_size,
            "carrier_zip_compression": carrier_compression,
            "carrier_sha256": carrier_sha,
            "audited_elf_sha256": audit_sha,
            "staged_manifest_sha256": manifest_sha,
            "identity_matches": carrier_sha != TOKEN_VAZIO
            and carrier_sha == audit_sha
            and carrier_sha == manifest_sha,
            "android_install_destination": "ApplicationInfo.nativeLibraryDir/libomega_core_exec.so",
        },
        "boundary": {
            "apk_materialization_verified": result == "PASS",
            "package_manager_extraction_verified": False,
            "device_native_library_dir_verified": False,
            "physical_execution_verified": False,
            "vm_boot_verified": False,
            "claim_allowed": False,
            "invariant": "APK_NATIVE_CARRIER_PASS != PACKAGE_INSTALL_EXTRACTION != DEVICE_EXECUTION != VM_BOOT",
            "next_gate": "DEVICE_NATIVE_LIBRARY_DIR_RECEIPT" if result == "PASS" else "APK_NATIVE_CARRIER_HASH_VERIFICATION",
        },
        "token_vazio": [
            "DEVICE_INSTALL_RECEIPT",
            "DEVICE_NATIVE_LIBRARY_DIR_RECEIPT",
            "DEVICE_EXECUTION_RECEIPT",
            "PHYSICAL_DEVICE_EXIT_RECEIPT",
            "END_TO_END_VM_BOOT_EVIDENCE",
        ],
        "errors": errors,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"result": result, "carrier_sha256": carrier_sha, "errors": errors}, sort_keys=True))
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
