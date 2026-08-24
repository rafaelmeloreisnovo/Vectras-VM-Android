#!/usr/bin/env python3
"""Audit the ARMv7 Omega deployment ELF without claiming device execution."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

TOKEN_VAZIO = "TOKEN_VAZIO"
REQUIRED_DEFINED = {"_start", "omega_deploy_main", "abi_entry_validate_interop"}
FORBIDDEN_PREFIXES = ("JNI_", "Java_", "__android_log_", "pthread_", "dlopen", "dlsym")


def run(tool: str, *args: str, allow_failure: bool = False) -> str:
    completed = subprocess.run(
        [tool, *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env={**os.environ, "LC_ALL": "C"},
        check=False,
    )
    if completed.returncode != 0 and not allow_failure:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(f"{Path(tool).name} failed ({completed.returncode}): {detail}")
    return completed.stdout + ("\n" + completed.stderr if completed.stderr else "")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def header_value(text: str, label: str) -> str:
    match = re.search(rf"^\s*{re.escape(label)}:\s*(.+?)\s*$", text, re.MULTILINE)
    return match.group(1).strip() if match else TOKEN_VAZIO


def symbol_sets(symbol_table: str) -> tuple[set[str], set[str]]:
    defined: set[str] = set()
    undefined: set[str] = set()
    for line in symbol_table.splitlines():
        fields = line.split()
        if len(fields) < 8 or not fields[0].rstrip(":").isdigit():
            continue
        index = fields[6]
        name = fields[7].split("@", 1)[0]
        if not name:
            continue
        if index == "UND":
            undefined.add(name)
        else:
            defined.add(name)
    return defined, undefined


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--reference-binary", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--readelf", required=True)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", TOKEN_VAZIO))
    args = parser.parse_args()

    errors: list[str] = []
    binary = args.binary
    reference = args.reference_binary

    if not binary.is_file():
        errors.append(f"missing binary: {binary}")
    if not reference.is_file():
        errors.append(f"missing reference binary: {reference}")

    artifact_sha = sha256(binary) if binary.is_file() else TOKEN_VAZIO
    reference_sha = sha256(reference) if reference.is_file() else TOKEN_VAZIO
    reproducible = artifact_sha != TOKEN_VAZIO and artifact_sha == reference_sha
    if not reproducible:
        errors.append("reproducibility gate failed: first and second build SHA-256 differ")

    elf_header = ""
    program_headers = ""
    dynamic = ""
    symbols = ""
    defined: set[str] = set()
    undefined: set[str] = set()

    if binary.is_file():
        if binary.read_bytes()[:4] != b"\x7fELF":
            errors.append("artifact is not ELF")
        else:
            try:
                elf_header = run(args.readelf, "-h", str(binary))
                program_headers = run(args.readelf, "-l", str(binary))
                dynamic = run(args.readelf, "-d", str(binary), allow_failure=True)
                symbols = run(args.readelf, "-Ws", str(binary))
                defined, undefined = symbol_sets(symbols)
            except RuntimeError as exc:
                errors.append(str(exc))

    elf_class = header_value(elf_header, "Class")
    elf_type = header_value(elf_header, "Type")
    machine = header_value(elf_header, "Machine")
    entry = header_value(elf_header, "Entry point address")

    if elf_class != "ELF32":
        errors.append(f"expected ELF32, got {elf_class}")
    if not elf_type.startswith("EXEC"):
        errors.append(f"expected static EXEC ELF, got {elf_type}")
    if "ARM" not in machine:
        errors.append(f"expected ARM machine, got {machine}")
    if entry in {TOKEN_VAZIO, "0x0", "0"}:
        errors.append(f"invalid entry point: {entry}")

    has_interp = bool(re.search(r"\bINTERP\b", program_headers))
    needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", dynamic)
    if has_interp:
        errors.append("PT_INTERP present: final deployment ELF still depends on a userspace loader")
    if needed:
        errors.append(f"DT_NEEDED entries present: {needed}")
    if undefined:
        errors.append(f"undefined symbols present: {sorted(undefined)}")

    missing_required = sorted(REQUIRED_DEFINED - defined)
    if missing_required:
        errors.append(f"required witness symbols missing: {missing_required}")

    forbidden_defined = sorted(
        name for name in defined if any(name.startswith(prefix) for prefix in FORBIDDEN_PREFIXES)
    )
    if forbidden_defined:
        errors.append(f"host/JNI symbols leaked into deployment ELF: {forbidden_defined}")

    result = "PASS" if not errors else "FAIL"
    manifest = {
        "schema_version": "vectras.omega-freestanding-deployment.v1",
        "record_kind": "BUILD_DEPLOYMENT_ELF_AUDIT",
        "result": result,
        "source": {
            "repository": "rafaelmeloreisnovo/Vectras-VM-Android",
            "commit": args.commit or TOKEN_VAZIO,
            "abi": "armeabi-v7a",
            "profile": "internal_arm32_arm64",
        },
        "boundary": {
            "core": "FREESTANDING_C_ASM",
            "android_role": "BUILD_AND_HOST_BOUNDARY_ONLY",
            "device_runtime_verified": False,
            "physical_execution_verified": False,
            "claim_allowed": False,
            "invariant": "BUILD_PASS != APK_MATERIALIZED != DEVICE_EXECUTION != VM_BOOT",
        },
        "artifact": {
            "file": binary.name,
            "size_bytes": binary.stat().st_size if binary.is_file() else 0,
            "sha256": artifact_sha,
            "reference_sha256": reference_sha,
            "reproducible": reproducible,
        },
        "elf": {
            "class": elf_class,
            "type": elf_type,
            "machine": machine,
            "entry": entry,
            "pt_interp_present": has_interp,
            "needed_libraries": needed,
        },
        "symbols": {
            "required_defined": sorted(REQUIRED_DEFINED),
            "missing_required": missing_required,
            "undefined": sorted(undefined),
            "forbidden_host_or_jni": forbidden_defined,
        },
        "checks": {
            "elf32_arm": elf_class == "ELF32" and "ARM" in machine,
            "static_exec": elf_type.startswith("EXEC") and not has_interp and not needed,
            "zero_undefined": not undefined,
            "archive_witness_reachable": "abi_entry_validate_interop" in defined,
            "deterministic_rebuild": reproducible,
        },
        "token_vazio": [
            "APK_MATERIALIZATION_RECEIPT" if result != "PASS" else "DEVICE_EXECUTION_RECEIPT",
            "PHYSICAL_DEVICE_EXIT_RECEIPT",
            "END_TO_END_VM_BOOT_EVIDENCE",
        ],
        "errors": errors,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"result": result, "sha256": artifact_sha, "errors": errors}))
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
