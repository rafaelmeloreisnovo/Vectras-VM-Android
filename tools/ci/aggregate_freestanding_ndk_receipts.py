#!/usr/bin/env python3
"""Aggregate Vectras freestanding probe manifests into one C05 closure receipt."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

SCHEMA = "vectra.c05-freestanding-closure.v1"
PROBE_SCHEMA = "vectra.freestanding-link-probe.v1"
TOKEN_VAZIO = "TOKEN_VAZIO"
HEX64 = re.compile(r"^[0-9a-f]{64}$")
REQUIRED_CHECKS = (
    "archive_consumed",
    "blake3_hashes",
    "controlled_entry",
    "forbidden_symbols_absent",
    "no_needed_libraries",
    "no_unexpected_undefined_symbols",
    "reproducible_binary",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True, type=Path)
    parser.add_argument("--arm32", required=True, type=Path)
    parser.add_argument("--arm64", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def tokenized_input(path: Path, expected_abi: str) -> dict[str, Any]:
    return {
        "path": str(path),
        "expected_abi": expected_abi,
        "present": False,
        "manifest_sha256": TOKEN_VAZIO,
        "schema": TOKEN_VAZIO,
        "reported_abi": TOKEN_VAZIO,
        "reported_commit": TOKEN_VAZIO,
        "probe_result": TOKEN_VAZIO,
        "artifact_sha256": TOKEN_VAZIO,
        "artifact_blake3": TOKEN_VAZIO,
        "reproducible": TOKEN_VAZIO,
        "checks": {},
        "valid": False,
        "errors": [],
    }


def validate_manifest(path: Path, expected_abi: str, commit: str) -> dict[str, Any]:
    result = tokenized_input(path, expected_abi)
    errors: list[str] = result["errors"]
    if not path.is_file():
        errors.append(f"missing manifest: {path}")
        return result

    result["present"] = True
    result["manifest_sha256"] = sha256_file(path)
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        errors.append(f"invalid JSON: {error}")
        return result

    schema = manifest.get("schema", TOKEN_VAZIO)
    source = manifest.get("source") if isinstance(manifest.get("source"), dict) else {}
    artifact = manifest.get("artifact") if isinstance(manifest.get("artifact"), dict) else {}
    checks = manifest.get("checks") if isinstance(manifest.get("checks"), dict) else {}
    symbols = manifest.get("symbols") if isinstance(manifest.get("symbols"), dict) else {}
    elf = manifest.get("elf") if isinstance(manifest.get("elf"), dict) else {}
    map_info = manifest.get("map") if isinstance(manifest.get("map"), dict) else {}

    reported_abi = source.get("abi", TOKEN_VAZIO)
    reported_commit = source.get("commit", TOKEN_VAZIO)
    probe_result = manifest.get("result", TOKEN_VAZIO)
    artifact_sha256 = artifact.get("sha256", TOKEN_VAZIO)
    artifact_blake3 = artifact.get("blake3", TOKEN_VAZIO)
    reproducible = artifact.get("reproducible", TOKEN_VAZIO)

    result.update(
        {
            "schema": schema,
            "reported_abi": reported_abi,
            "reported_commit": reported_commit,
            "probe_result": probe_result,
            "artifact_sha256": artifact_sha256,
            "artifact_blake3": artifact_blake3,
            "reproducible": reproducible,
            "checks": checks,
        }
    )

    if schema != PROBE_SCHEMA:
        errors.append(f"unexpected schema: {schema}")
    if reported_abi != expected_abi:
        errors.append(f"ABI mismatch: expected {expected_abi}, got {reported_abi}")
    if reported_commit != commit:
        errors.append(f"commit mismatch: expected {commit}, got {reported_commit}")
    if probe_result != "PASS":
        errors.append(f"probe result is not PASS: {probe_result}")
    if not isinstance(artifact_sha256, str) or not HEX64.fullmatch(artifact_sha256):
        errors.append("artifact SHA-256 missing or invalid")
    if not isinstance(artifact_blake3, str) or not HEX64.fullmatch(artifact_blake3):
        errors.append("artifact BLAKE3 missing or invalid")
    if reproducible is not True:
        errors.append("artifact is not reproducible")

    for check in REQUIRED_CHECKS:
        if checks.get(check) is not True:
            errors.append(f"required check not true: {check}")

    for key in ("unexpected_undefined", "needed_libraries", "forbidden_present"):
        value = symbols.get(key, TOKEN_VAZIO)
        if value != []:
            errors.append(f"symbols.{key} must be an empty list")

    if elf.get("entry_matches_symbol") is not True:
        errors.append("ELF entry does not match controlled entry symbol")
    if map_info.get("archive_witness") is not True:
        errors.append("freestanding archive witness missing from map")

    result["valid"] = not errors
    return result


def main() -> int:
    args = parse_args()
    inputs = {
        "host": validate_manifest(args.host, "host-x86_64", args.commit),
        "armeabi-v7a": validate_manifest(args.arm32, "armeabi-v7a", args.commit),
        "arm64-v8a": validate_manifest(args.arm64, "arm64-v8a", args.commit),
    }

    all_errors = [
        f"{name}: {error}"
        for name, item in inputs.items()
        for error in item["errors"]
    ]
    missing = [name for name, item in inputs.items() if not item["present"]]
    if missing:
        result = "INCOMPLETE"
    elif all_errors:
        result = "FAIL"
    else:
        result = "PASS"

    receipt = {
        "schema": SCHEMA,
        "cycle_id": "C05",
        "result": result,
        "claim_allowed": False,
        "source_commit": args.commit,
        "inputs": inputs,
        "checks": {
            "host_manifest_valid": inputs["host"]["valid"],
            "arm32_manifest_valid": inputs["armeabi-v7a"]["valid"],
            "arm64_manifest_valid": inputs["arm64-v8a"]["valid"],
            "all_manifests_present": not missing,
            "all_manifests_same_commit": all(
                item["reported_commit"] == args.commit for item in inputs.values()
            ),
            "android_abis_complete": (
                inputs["armeabi-v7a"]["valid"] and inputs["arm64-v8a"]["valid"]
            ),
        },
        "claim_boundary": {
            "host_final_link": "VERIFIED_BY_EXECUTION" if inputs["host"]["valid"] else "NOT_PROMOTED",
            "arm32_ndk_final_link": (
                "VERIFIED_BY_EXECUTION" if inputs["armeabi-v7a"]["valid"] else "NOT_PROMOTED"
            ),
            "arm64_ndk_final_link": (
                "VERIFIED_BY_EXECUTION" if inputs["arm64-v8a"]["valid"] else "NOT_PROMOTED"
            ),
            "device_runtime": "TOKEN_VAZIO",
            "apk_install": "TOKEN_VAZIO",
            "guest_boot": "TOKEN_VAZIO",
            "performance_claim": "FORBIDDEN_OUT_OF_SCOPE",
        },
        "errors": sorted(all_errors),
        "falsifiers": [
            "manifest_missing",
            "probe_result_not_pass",
            "commit_mismatch",
            "abi_mismatch",
            "invalid_or_missing_sha256",
            "invalid_or_missing_blake3",
            "non_reproducible_binary",
            "unexpected_undefined_symbol",
            "needed_library_present",
            "forbidden_symbol_present",
            "controlled_entry_mismatch",
            "archive_witness_missing",
        ],
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{result} C05 freestanding closure: {args.output}")
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
