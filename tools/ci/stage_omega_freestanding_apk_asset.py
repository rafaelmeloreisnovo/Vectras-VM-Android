#!/usr/bin/env python3
"""Stage a previously audited Omega ARMv7 ELF into generated APK assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path

TOKEN_VAZIO = "TOKEN_VAZIO"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--audit", required=True, type=Path)
    parser.add_argument("--asset-root", required=True, type=Path)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", TOKEN_VAZIO))
    args = parser.parse_args()

    if not args.binary.is_file():
        raise SystemExit(f"missing deployment ELF: {args.binary}")
    if not args.audit.is_file():
        raise SystemExit(f"missing deployment audit: {args.audit}")

    audit = json.loads(args.audit.read_text(encoding="utf-8"))
    if audit.get("schema_version") != "vectras.omega-freestanding-deployment.v1":
        raise SystemExit("unexpected deployment audit schema")
    if audit.get("result") != "PASS":
        raise SystemExit("deployment ELF audit is not PASS; staging denied")

    binary_sha = sha256(args.binary)
    audit_sha = audit.get("artifact", {}).get("sha256", TOKEN_VAZIO)
    if binary_sha != audit_sha:
        raise SystemExit(
            f"deployment ELF SHA mismatch: binary={binary_sha} audit={audit_sha}"
        )

    relative_dir = Path("freestanding") / "armeabi-v7a"
    destination_dir = args.asset_root / relative_dir
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination_elf = destination_dir / "omega-core.elf"
    destination_manifest = destination_dir / "omega-core.manifest.json"

    shutil.copyfile(args.binary, destination_elf)
    staged_sha = sha256(destination_elf)
    if staged_sha != binary_sha:
        raise SystemExit("staged ELF SHA mismatch after copy")

    manifest = {
        "schema_version": "vectras.omega-freestanding-apk-asset.v1",
        "record_kind": "GENERATED_APK_ASSET_STAGING",
        "source": {
            "repository": "rafaelmeloreisnovo/Vectras-VM-Android",
            "commit": args.commit or TOKEN_VAZIO,
            "abi": "armeabi-v7a",
            "deployment_audit_schema": audit.get("schema_version", TOKEN_VAZIO),
            "deployment_audit_result": audit.get("result", TOKEN_VAZIO),
        },
        "asset": {
            "apk_path": "assets/freestanding/armeabi-v7a/omega-core.elf",
            "generated_asset_path": "freestanding/armeabi-v7a/omega-core.elf",
            "size_bytes": destination_elf.stat().st_size,
            "sha256": staged_sha,
        },
        "boundary": {
            "staged_for_apk": True,
            "apk_materialization_verified": False,
            "device_runtime_verified": False,
            "physical_execution_verified": False,
            "claim_allowed": False,
            "next_gate": "APK_ASSET_HASH_VERIFICATION",
        },
        "token_vazio": [
            "APK_MATERIALIZATION_RECEIPT",
            "DEVICE_EXECUTION_RECEIPT",
            "PHYSICAL_DEVICE_EXIT_RECEIPT",
            "END_TO_END_VM_BOOT_EVIDENCE",
        ],
    }
    destination_manifest.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    print(
        json.dumps(
            {
                "result": "STAGED",
                "asset": str(destination_elf),
                "manifest": str(destination_manifest),
                "sha256": staged_sha,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
