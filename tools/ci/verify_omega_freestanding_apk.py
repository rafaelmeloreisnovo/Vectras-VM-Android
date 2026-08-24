#!/usr/bin/env python3
"""Verify the packaged Omega ARMv7 carrier without confusing debug-strip with code drift."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import struct
import zipfile
from pathlib import Path

TOKEN_VAZIO = "TOKEN_VAZIO"
ELF_ENTRY = "lib/armeabi-v7a/libomega_core_exec.so"
MANIFEST_ENTRY = "assets/freestanding/armeabi-v7a/omega-core.manifest.json"
PT_LOAD = 1
PT_DYNAMIC = 2
PT_INTERP = 3
ET_EXEC = 2
EM_ARM = 40


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def inspect_elf32_arm(data: bytes) -> tuple[dict, list[str]]:
    errors: list[str] = []
    info = {
        "elf32": False,
        "little_endian": False,
        "type": TOKEN_VAZIO,
        "machine": TOKEN_VAZIO,
        "entry": TOKEN_VAZIO,
        "pt_interp_present": TOKEN_VAZIO,
        "pt_dynamic_present": TOKEN_VAZIO,
        "load_segment_count": 0,
        "load_image_sha256": TOKEN_VAZIO,
    }

    if len(data) < 52 or data[:4] != b"\x7fELF":
        return info, ["not an ELF file"]
    if data[4] != 1:
        return info, [f"expected ELF32 class=1, got {data[4]}"]
    info["elf32"] = True
    if data[5] != 1:
        return info, [f"expected little-endian ELF data=1, got {data[5]}"]
    info["little_endian"] = True

    try:
        (
            e_type,
            e_machine,
            _e_version,
            e_entry,
            e_phoff,
            _e_shoff,
            _e_flags,
            _e_ehsize,
            e_phentsize,
            e_phnum,
            _e_shentsize,
            _e_shnum,
            _e_shstrndx,
        ) = struct.unpack_from("<HHIIIIIHHHHHH", data, 16)
    except struct.error as exc:
        return info, [f"truncated ELF header: {exc}"]

    info["type"] = e_type
    info["machine"] = e_machine
    info["entry"] = f"0x{e_entry:x}"
    if e_type != ET_EXEC:
        errors.append(f"expected ET_EXEC={ET_EXEC}, got {e_type}")
    if e_machine != EM_ARM:
        errors.append(f"expected EM_ARM={EM_ARM}, got {e_machine}")
    if e_entry == 0:
        errors.append("ELF entry point is zero")
    if e_phentsize < 32:
        errors.append(f"invalid ELF32 program-header size: {e_phentsize}")
        return info, errors

    load_records: list[tuple[int, int, int, int, int, bytes]] = []
    has_interp = False
    has_dynamic = False
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        if offset + 32 > len(data):
            errors.append(f"program header {index} exceeds file")
            break
        (
            p_type,
            p_offset,
            p_vaddr,
            _p_paddr,
            p_filesz,
            _p_memsz,
            p_flags,
            p_align,
        ) = struct.unpack_from("<IIIIIIII", data, offset)
        if p_type == PT_INTERP:
            has_interp = True
        elif p_type == PT_DYNAMIC:
            has_dynamic = True
        elif p_type == PT_LOAD:
            end = p_offset + p_filesz
            if end > len(data):
                errors.append(f"PT_LOAD {index} exceeds file")
                continue
            # Runtime identity intentionally ignores file offsets and section tables.
            # It binds the virtual address/flags/alignment plus every byte mapped
            # into memory. AGP may remove debug/symbol sections but may not alter
            # this digest without failing the deployment gate.
            load_records.append(
                (p_vaddr, p_filesz, p_flags, p_align, index, data[p_offset:end])
            )

    info["pt_interp_present"] = has_interp
    info["pt_dynamic_present"] = has_dynamic
    info["load_segment_count"] = len(load_records)
    if has_interp:
        errors.append("PT_INTERP present: packaged carrier requires a userspace dynamic loader")
    if has_dynamic:
        errors.append("PT_DYNAMIC present: packaged carrier is not runtime-independent")
    if not load_records:
        errors.append("no PT_LOAD segments")
    else:
        digest = hashlib.sha256()
        for vaddr, filesz, flags, align, _index, payload in sorted(load_records):
            digest.update(struct.pack("<IIII", vaddr, filesz, flags, align))
            digest.update(payload)
        info["load_image_sha256"] = digest.hexdigest()

    return info, errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--audit", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", TOKEN_VAZIO))
    args = parser.parse_args()

    errors: list[str] = []
    audit: dict = {}
    staged_manifest: dict = {}
    carrier_bytes = b""
    carrier_sha = TOKEN_VAZIO
    carrier_size = 0
    carrier_compression = TOKEN_VAZIO

    if not args.apk.is_file():
        errors.append(f"missing APK: {args.apk}")
    if not args.binary.is_file():
        errors.append(f"missing pre-package ELF: {args.binary}")
    if not args.audit.is_file():
        errors.append(f"missing deployment audit: {args.audit}")
    else:
        try:
            audit = json.loads(args.audit.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"unreadable deployment audit: {exc}")

    if audit and audit.get("result") != "PASS":
        errors.append("deployment audit is not PASS")

    pre_bytes = args.binary.read_bytes() if args.binary.is_file() else b""
    pre_sha = sha256_bytes(pre_bytes) if pre_bytes else TOKEN_VAZIO
    audit_sha = audit.get("artifact", {}).get("sha256", TOKEN_VAZIO) if audit else TOKEN_VAZIO
    if pre_sha != TOKEN_VAZIO and pre_sha != audit_sha:
        errors.append(f"pre-package ELF SHA differs from audited ELF: binary={pre_sha} audit={audit_sha}")

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

                if MANIFEST_ENTRY not in names:
                    errors.append(f"missing APK deployment manifest: {MANIFEST_ENTRY}")
                else:
                    staged_manifest = json.loads(apk.read(MANIFEST_ENTRY).decode("utf-8"))
        except (zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError) as exc:
            errors.append(f"APK/manifest parse failure: {exc}")

    pre_elf, pre_elf_errors = inspect_elf32_arm(pre_bytes) if pre_bytes else ({}, ["pre-package ELF unavailable"])
    final_elf, final_elf_errors = inspect_elf32_arm(carrier_bytes) if carrier_bytes else ({}, ["APK carrier unavailable"])
    errors.extend(f"pre-package: {value}" for value in pre_elf_errors)
    errors.extend(f"packaged-carrier: {value}" for value in final_elf_errors)

    pre_load_sha = pre_elf.get("load_image_sha256", TOKEN_VAZIO)
    final_load_sha = final_elf.get("load_image_sha256", TOKEN_VAZIO)
    load_image_identity = (
        pre_load_sha != TOKEN_VAZIO
        and pre_load_sha == final_load_sha
        and not pre_elf_errors
        and not final_elf_errors
    )
    if not load_image_identity:
        errors.append(
            f"runtime PT_LOAD identity mismatch: pre={pre_load_sha} packaged={final_load_sha}"
        )

    deployment = staged_manifest.get("deployment", {}) if staged_manifest else {}
    manifest_sha = deployment.get("sha256", TOKEN_VAZIO)
    manifest_path = deployment.get("apk_executable_path", TOKEN_VAZIO)
    carrier_semantics = deployment.get("carrier_semantics", TOKEN_VAZIO)

    if manifest_sha != audit_sha:
        errors.append(f"staged manifest does not bind audited ELF: manifest={manifest_sha} audit={audit_sha}")
    if staged_manifest and staged_manifest.get("schema_version") != "vectras.omega-freestanding-apk-asset.v2":
        errors.append("unexpected staged native-carrier manifest schema")
    if staged_manifest and manifest_path != ELF_ENTRY:
        errors.append(f"staged manifest APK path mismatch: {manifest_path}")
    if staged_manifest and carrier_semantics != "ELF_ET_EXEC_NOT_SHARED_LIBRARY":
        errors.append(f"unexpected native carrier semantics: {carrier_semantics}")

    byte_identity = carrier_sha != TOKEN_VAZIO and carrier_sha == pre_sha
    transform = "NONE" if byte_identity else "AGP_NATIVE_STRIP"
    result = "PASS" if not errors else "FAIL"
    receipt = {
        "schema_version": "vectras.omega-freestanding-apk-materialization.v3",
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
            "pre_package_sha256": pre_sha,
            "audited_elf_sha256": audit_sha,
            "packaged_carrier_sha256": carrier_sha,
            "byte_identity": byte_identity,
            "packaging_transform": transform,
            "pre_load_image_sha256": pre_load_sha,
            "packaged_load_image_sha256": final_load_sha,
            "runtime_load_image_identity": load_image_identity,
            "android_install_destination": "ApplicationInfo.nativeLibraryDir/libomega_core_exec.so",
        },
        "packaged_elf": final_elf,
        "boundary": {
            "apk_materialization_verified": result == "PASS",
            "package_transform_allowed": "DEBUG_SYMBOL_SECTION_REMOVAL_ONLY_WHEN_PT_LOAD_IDENTITY_HOLDS",
            "package_manager_extraction_verified": False,
            "device_native_library_dir_verified": False,
            "physical_execution_verified": False,
            "vm_boot_verified": False,
            "claim_allowed": False,
            "invariant": "APK_LOAD_IMAGE_PASS != PACKAGE_INSTALL_EXTRACTION != DEVICE_EXECUTION != VM_BOOT",
            "next_gate": "DEVICE_NATIVE_LIBRARY_DIR_RECEIPT" if result == "PASS" else "APK_NATIVE_CARRIER_RUNTIME_IDENTITY",
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
    print(
        json.dumps(
            {
                "result": result,
                "pre_sha256": pre_sha,
                "carrier_sha256": carrier_sha,
                "byte_identity": byte_identity,
                "runtime_load_image_identity": load_image_identity,
                "errors": errors,
            },
            sort_keys=True,
        )
    )
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
