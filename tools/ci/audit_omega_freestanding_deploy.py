#!/usr/bin/env python3
"""Audit the ARMv7 standalone carrier without promoting build evidence to device execution."""
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
FORBIDDEN_PREFIXES = (
    "JNI_",
    "Java_",
    "__android_log_",
    "pthread_",
    "dlopen",
    "dlsym",
    "malloc",
    "calloc",
    "realloc",
    "free",
)


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
        name = fields[7].split("@", 1)[0]
        if not name:
            continue
        if fields[6] == "UND":
            undefined.add(name)
        else:
            defined.add(name)
    return defined, undefined


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--symbols-binary", required=True, type=Path)
    parser.add_argument("--reference-binary", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--readelf", required=True)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", TOKEN_VAZIO))
    args = parser.parse_args()

    errors: list[str] = []
    for path, label in (
        (args.binary, "binary"),
        (args.symbols_binary, "symbols binary"),
        (args.reference_binary, "reference binary"),
    ):
        if not path.is_file():
            errors.append(f"missing {label}: {path}")

    artifact_sha = sha256(args.binary) if args.binary.is_file() else TOKEN_VAZIO
    reference_sha = sha256(args.reference_binary) if args.reference_binary.is_file() else TOKEN_VAZIO
    reproducible = artifact_sha != TOKEN_VAZIO and artifact_sha == reference_sha
    if not reproducible:
        errors.append("reproducibility gate failed: stripped carrier SHA-256 differs")

    elf_header = ""
    program_headers = ""
    dynamic = ""
    final_symbols = ""
    audit_symbols = ""
    final_defined: set[str] = set()
    final_undefined: set[str] = set()
    audit_defined: set[str] = set()
    audit_undefined: set[str] = set()

    if args.binary.is_file() and args.binary.read_bytes()[:4] == b"\x7fELF":
        try:
            elf_header = run(args.readelf, "-h", str(args.binary))
            program_headers = run(args.readelf, "-l", str(args.binary))
            dynamic = run(args.readelf, "-d", str(args.binary), allow_failure=True)
            final_symbols = run(args.readelf, "-Ws", str(args.binary), allow_failure=True)
            final_defined, final_undefined = symbol_sets(final_symbols)
            audit_symbols = run(args.readelf, "-Ws", str(args.symbols_binary))
            audit_defined, audit_undefined = symbol_sets(audit_symbols)
        except RuntimeError as exc:
            errors.append(str(exc))
    else:
        errors.append("artifact is not ELF")

    elf_class = header_value(elf_header, "Class")
    elf_type = header_value(elf_header, "Type")
    machine = header_value(elf_header, "Machine")
    entry = header_value(elf_header, "Entry point address")
    has_interp = bool(re.search(r"\bINTERP\b", program_headers))
    needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", dynamic)

    if elf_class != "ELF32":
        errors.append(f"expected ELF32, got {elf_class}")
    if not elf_type.startswith("EXEC"):
        errors.append(f"expected static EXEC ELF, got {elf_type}")
    if "ARM" not in machine:
        errors.append(f"expected ARM machine, got {machine}")
    if entry in {TOKEN_VAZIO, "0x0", "0"}:
        errors.append(f"invalid entry point: {entry}")
    if has_interp:
        errors.append("PT_INTERP present")
    if needed:
        errors.append(f"DT_NEEDED present: {needed}")
    if final_undefined or audit_undefined:
        errors.append(
            f"undefined symbols present: final={sorted(final_undefined)} audit={sorted(audit_undefined)}"
        )

    missing_required = sorted(REQUIRED_DEFINED - audit_defined)
    if missing_required:
        errors.append(f"required freestanding symbols missing from audit image: {missing_required}")

    forbidden_defined = sorted(
        name for name in audit_defined if any(name.startswith(prefix) for prefix in FORBIDDEN_PREFIXES)
    )
    if forbidden_defined:
        errors.append(f"hosted/JNI/heap symbols leaked: {forbidden_defined}")

    final_named = sorted(final_defined | final_undefined)
    if final_named:
        errors.append(f"final carrier still has named symbols: {final_named}")

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
            "core": "FULL_FREESTANDING_C_ASM_BINARY_PROTOCOL",
            "android_role": "BUILD_AND_HOST_BOUNDARY_ONLY",
            "device_runtime_verified": False,
            "physical_execution_verified": False,
            "claim_allowed": False,
            "invariant": "BUILD_PASS != APK_MATERIALIZED != DEVICE_EXECUTION != VM_BOOT",
        },
        "artifact": {
            "file": args.binary.name,
            "size_bytes": args.binary.stat().st_size if args.binary.is_file() else 0,
            "sha256": artifact_sha,
            "reference_sha256": reference_sha,
            "reproducible": reproducible,
            "symbols_source_file": args.symbols_binary.name,
            "symbols_source_sha256": sha256(args.symbols_binary)
            if args.symbols_binary.is_file()
            else TOKEN_VAZIO,
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
            "final_named": final_named,
            "required_defined": sorted(REQUIRED_DEFINED),
            "missing_required": missing_required,
            "audit_undefined": sorted(audit_undefined),
            "forbidden_host_or_jni_or_heap": forbidden_defined,
        },
        "checks": {
            "elf32_arm": elf_class == "ELF32" and "ARM" in machine,
            "static_exec": elf_type.startswith("EXEC") and not has_interp and not needed,
            "zero_undefined": not final_undefined and not audit_undefined,
            "zero_named_symbols_in_carrier": not final_named,
            "archive_reachable": "abi_entry_validate_interop" in audit_defined,
            "deterministic_rebuild": reproducible,
        },
        "token_vazio": [
            "DEVICE_EXECUTION_RECEIPT",
            "PHYSICAL_DEVICE_EXIT_RECEIPT",
            "END_TO_END_VM_BOOT_EVIDENCE",
        ],
        "errors": errors,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"result": result, "sha256": artifact_sha, "errors": errors}, sort_keys=True))
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
