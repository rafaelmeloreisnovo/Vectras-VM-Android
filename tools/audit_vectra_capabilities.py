#!/usr/bin/env python3
"""Audit Vectras capability claims, APK DEX/ELF coherence, and loose artifacts.

The tool is intentionally stdlib-only and fail-closed. It never promotes a
source file, design note, or historical report to runtime evidence.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

TOKEN_VAZIO = "TOKEN_VAZIO"
DEX_HEADER_SIZE = 0x70
DEX_ENDIAN = {0x12345678, 0x78563412}
ELF_MACHINE = {3: "x86", 40: "ARM", 62: "x86_64", 183: "AArch64", 243: "RISC-V"}
ABI_MACHINE = {
    "armeabi-v7a": {40},
    "arm64-v8a": {183},
    "x86": {3},
    "x86_64": {62},
    "riscv64": {243},
}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"expected object in {path}")
    return value


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def audit_capabilities(root: Path, contract: dict[str, Any]) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for item in contract.get("capabilities", []):
        required = [str(x) for x in item.get("required_paths", [])]
        missing_paths = [rel for rel in required if not (root / rel).is_file()]
        marker_failures: list[dict[str, str]] = []
        for rel, markers in item.get("required_markers", {}).items():
            path = root / rel
            if not path.is_file():
                for marker in markers:
                    marker_failures.append({"path": rel, "marker": marker})
                continue
            text = read_text(path)
            for marker in markers:
                if marker not in text:
                    marker_failures.append({"path": rel, "marker": marker})

        integration_hits: list[dict[str, str]] = []
        for integration_file in item.get("integration_files", []):
            path = root / integration_file
            if not path.is_file():
                continue
            text = read_text(path)
            for needle in item.get("integration_needles", []):
                if needle in text:
                    integration_hits.append({"path": integration_file, "needle": needle})

        declared = str(item.get("declared_state", TOKEN_VAZIO))
        evidence_ok = not missing_paths and not marker_failures
        effective = declared if evidence_ok else TOKEN_VAZIO
        result = {
            "id": item.get("id"),
            "title": item.get("title"),
            "declared_state": declared,
            "effective_state": effective,
            "claim_allowed": bool(item.get("claim_allowed", False)) and evidence_ok,
            "boundary": item.get("boundary", ""),
            "missing_paths": missing_paths,
            "marker_failures": marker_failures,
            "integration_hits": integration_hits,
        }
        if item.get("integration_needles") and not integration_hits:
            result["integration_state"] = "UNREFERENCED_BY_CANONICAL_BUILD"
        results.append(result)
    return results


def parse_dex_header(data: bytes, name: str) -> dict[str, Any]:
    report: dict[str, Any] = {"entry": name, "status": "FAIL", "failures": []}
    if len(data) < DEX_HEADER_SIZE:
        report["failures"].append("entry shorter than DEX header")
        return report
    magic = data[:8]
    report["magic_hex"] = magic.hex()
    if not (magic.startswith(b"dex\n") and magic[7] == 0):
        report["failures"].append("invalid DEX magic")
    version_raw = magic[4:7]
    report["version"] = version_raw.decode("ascii", errors="replace")
    if not version_raw.isdigit():
        report["failures"].append("DEX version is not numeric")
    checksum = struct.unpack_from("<I", data, 8)[0]
    file_size, header_size, endian_tag = struct.unpack_from("<III", data, 32)
    report.update(
        checksum=f"0x{checksum:08x}",
        signature_sha1=data[12:32].hex(),
        declared_file_size=file_size,
        actual_file_size=len(data),
        header_size=header_size,
        endian_tag=f"0x{endian_tag:08x}",
    )
    if file_size != len(data):
        report["failures"].append("DEX declared file_size differs from ZIP entry size")
    if header_size != DEX_HEADER_SIZE:
        report["failures"].append("DEX header_size is not 0x70")
    if endian_tag not in DEX_ENDIAN:
        report["failures"].append("invalid DEX endian_tag")
    report["status"] = "PASS" if not report["failures"] else "FAIL"
    return report


def parse_elf_header(data: bytes, name: str, abi: str | None) -> dict[str, Any]:
    report: dict[str, Any] = {"entry": name, "abi_directory": abi, "status": "FAIL", "failures": []}
    if len(data) < 20:
        report["failures"].append("entry shorter than ELF identification/header prefix")
        return report
    if data[:4] != b"\x7fELF":
        report["failures"].append("invalid ELF magic")
        return report
    elf_class, endian = data[4], data[5]
    report["elf_class"] = {1: "ELF32", 2: "ELF64"}.get(elf_class, f"UNKNOWN({elf_class})")
    report["endianness"] = {1: "little", 2: "big"}.get(endian, f"UNKNOWN({endian})")
    if elf_class not in {1, 2}:
        report["failures"].append("invalid ELF class")
    if endian not in {1, 2}:
        report["failures"].append("invalid ELF data encoding")
        return report
    fmt = "<H" if endian == 1 else ">H"
    machine = struct.unpack_from(fmt, data, 18)[0]
    report["machine"] = machine
    report["machine_name"] = ELF_MACHINE.get(machine, "UNKNOWN")
    allowed = ABI_MACHINE.get(abi or "", set())
    if abi and allowed and machine not in allowed:
        report["failures"].append(f"ELF e_machine={machine} conflicts with lib/{abi}/")
    if abi == "armeabi-v7a" and elf_class != 1:
        report["failures"].append("armeabi-v7a requires ELF32")
    if abi in {"arm64-v8a", "x86_64", "riscv64"} and elf_class != 2:
        report["failures"].append(f"{abi} requires ELF64")
    report["status"] = "PASS" if not report["failures"] else "FAIL"
    return report


def inspect_apk(apk_path: Path | None) -> dict[str, Any]:
    if apk_path is None:
        return {"state": TOKEN_VAZIO, "claim_allowed": False, "reason": "no APK supplied"}
    report: dict[str, Any] = {
        "state": "FAIL",
        "claim_allowed": False,
        "apk_path": str(apk_path),
        "apk_sha256": TOKEN_VAZIO,
        "dex": [],
        "elf": [],
        "failures": [],
    }
    if not apk_path.is_file():
        report["failures"].append("APK path does not exist")
        return report
    raw = apk_path.read_bytes()
    report["apk_sha256"] = sha256_bytes(raw)
    try:
        with zipfile.ZipFile(apk_path) as archive:
            names = archive.namelist()
            dex_names = sorted(n for n in names if Path(n).name.startswith("classes") and n.endswith(".dex"))
            so_names = sorted(n for n in names if n.startswith("lib/") and n.endswith(".so"))
            if "AndroidManifest.xml" not in names:
                report["failures"].append("missing AndroidManifest.xml")
            if not dex_names:
                report["failures"].append("no classes*.dex entries")
            for name in dex_names:
                report["dex"].append(parse_dex_header(archive.read(name), name))
            for name in so_names:
                parts = name.split("/")
                abi = parts[1] if len(parts) > 2 else None
                report["elf"].append(parse_elf_header(archive.read(name), name, abi))
    except zipfile.BadZipFile:
        report["failures"].append("APK is not a valid ZIP container")
        return report
    if any(item["status"] != "PASS" for item in report["dex"]):
        report["failures"].append("one or more DEX headers failed")
    if any(item["status"] != "PASS" for item in report["elf"]):
        report["failures"].append("one or more ELF headers/ABI mappings failed")
    report["state"] = "PASS" if not report["failures"] else "FAIL"
    report["claim_allowed"] = report["state"] == "PASS"
    return report


def route_for(path: Path, policy: dict[str, Any]) -> str:
    mapping = policy.get("route_by_suffix", {})
    suffix = path.suffix
    return str(mapping.get(suffix, mapping.get(suffix.lower(), policy.get("default_route", "quarantine/unclassified"))))


def scan_loose_artifacts(root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    records: list[dict[str, Any]] = []
    by_digest: dict[str, list[str]] = defaultdict(list)
    ignored = set(policy.get("ignore_names", []))
    for rel_root in policy.get("scan_roots", []):
        scan_root = root / rel_root
        if not scan_root.exists():
            continue
        for path in sorted(p for p in scan_root.rglob("*") if p.is_file() and p.name not in ignored):
            data = path.read_bytes()
            digest = sha256_bytes(data)
            rel = path.relative_to(root).as_posix()
            by_digest[digest].append(rel)
            records.append(
                {
                    "path": rel,
                    "size_bytes": len(data),
                    "sha256": digest,
                    "suffix": path.suffix,
                    "suggested_route": route_for(path, policy),
                    "automatic_move_allowed": False,
                }
            )
    duplicates = [
        {"sha256": digest, "paths": paths}
        for digest, paths in sorted(by_digest.items())
        if len(paths) > 1
    ]
    route_counts = Counter(record["suggested_route"] for record in records)
    return {
        "state": "INVENTORIED" if records else TOKEN_VAZIO,
        "claim_allowed": False,
        "records_count": len(records),
        "route_counts": dict(sorted(route_counts.items())),
        "duplicates": duplicates,
        "records": records,
    }


def build_report(root: Path, contract_path: Path, apk_path: Path | None) -> dict[str, Any]:
    contract = load_json(contract_path)
    capabilities = audit_capabilities(root, contract)
    return {
        "schema_version": "1.0.0",
        "repository": contract.get("repository"),
        "source_ref": contract.get("source_ref"),
        "principle": contract.get("principle"),
        "capability_summary": dict(sorted(Counter(x["effective_state"] for x in capabilities).items())),
        "capabilities": capabilities,
        "apk_audit": inspect_apk(apk_path),
        "loose_artifacts": scan_loose_artifacts(root, contract.get("loose_artifact_policy", {})),
        "overall_claim_allowed": False,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--contract", type=Path, default=Path("configs/vectra_capability_contract.json"))
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--output", type=Path, default=Path("reports/vectra_capability_surface.json"))
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    contract_path = args.contract if args.contract.is_absolute() else root / args.contract
    output = args.output if args.output.is_absolute() else root / args.output
    apk = None if args.apk is None else (args.apk if args.apk.is_absolute() else root / args.apk)
    try:
        report = build_report(root, contract_path, apk)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"capability audit error: {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(output),
        "capability_summary": report["capability_summary"],
        "apk_state": report["apk_audit"]["state"],
        "loose_records": report["loose_artifacts"]["records_count"],
        "claim_allowed": report["overall_claim_allowed"],
    }, ensure_ascii=False))
    return 1 if report["apk_audit"]["state"] == "FAIL" else 0


if __name__ == "__main__":
    raise SystemExit(main())
