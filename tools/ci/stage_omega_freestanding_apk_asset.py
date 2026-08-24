#!/usr/bin/env python3
"""Stage an audited Omega ARMv7 ELF in Android's APK-native executable carrier."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path

TOKEN_VAZIO = "TOKEN_VAZIO"
APK_EXECUTABLE_PATH = "lib/armeabi-v7a/libomega_core_exec.so"
APK_MANIFEST_PATH = "assets/freestanding/armeabi-v7a/omega-core.manifest.json"


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
    parser.add_argument("--jni-root", required=True, type=Path)
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

    # Android 10+ forbids execve from writable app home for targetSdk 29+.
    # Therefore executable bytes are staged under lib/<abi>/lib*.so so the
    # package manager owns their executable deployment. The .so suffix is an APK
    # carrier convention only; the audited payload remains an ELF ET_EXEC.
    destination_native_dir = args.jni_root / "armeabi-v7a"
    destination_native_dir.mkdir(parents=True, exist_ok=True)
    destination_elf = destination_native_dir / "libomega_core_exec.so"
    shutil.copyfile(args.binary, destination_elf)
    staged_sha = sha256(destination_elf)
    if staged_sha != binary_sha:
        raise SystemExit("native-carrier ELF SHA mismatch after copy")

    destination_manifest_dir = args.asset_root / "freestanding" / "armeabi-v7a"
    destination_manifest_dir.mkdir(parents=True, exist_ok=True)
    destination_manifest = destination_manifest_dir / "omega-core.manifest.json"

    manifest = {
        "schema_version": "vectras.omega-freestanding-apk-asset.v2",
        "record_kind": "GENERATED_APK_NATIVE_EXECUTABLE_STAGING",
        "source": {
            "repository": "rafaelmeloreisnovo/Vectras-VM-Android",
            "commit": args.commit or TOKEN_VAZIO,
            "abi": "armeabi-v7a",
            "deployment_audit_schema": audit.get("schema_version", TOKEN_VAZIO),
            "deployment_audit_result": audit.get("result", TOKEN_VAZIO),
        },
        "deployment": {
            "apk_executable_path": APK_EXECUTABLE_PATH,
            "apk_manifest_path": APK_MANIFEST_PATH,
            "carrier_filename": "libomega_core_exec.so",
            "carrier_semantics": "ELF_ET_EXEC_NOT_SHARED_LIBRARY",
            "device_resolution": "ApplicationInfo.nativeLibraryDir/libomega_core_exec.so",
            "android_wx_policy": "EXECUTABLE_CODE_REMAINS_APK_INSTALL_OWNED_NOT_APP_WRITABLE",
            "extract_native_libs_required": True,
            "size_bytes": destination_elf.stat().st_size,
            "sha256": staged_sha,
        },
        "boundary": {
            "staged_for_apk": True,
            "apk_materialization_verified": False,
            "device_install_verified": False,
            "device_runtime_verified": False,
            "physical_execution_verified": False,
            "claim_allowed": False,
            "next_gate": "APK_NATIVE_CARRIER_HASH_VERIFICATION",
        },
        "token_vazio": [
            "APK_MATERIALIZATION_RECEIPT",
            "DEVICE_NATIVE_LIBRARY_DIR_RECEIPT",
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
                "result": "STAGED_NATIVE_EXECUTABLE_CARRIER",
                "native_carrier": str(destination_elf),
                "manifest": str(destination_manifest),
                "sha256": staged_sha,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
