#!/usr/bin/env python3
"""Build a deterministic C06 provenance/SBOM closure report without network access."""

from __future__ import annotations

import argparse
import csv
import glob
import hashlib
import json
import re
from pathlib import Path
from typing import Any

SCHEMA = "vectra.provenance-closure.v1"
SHA256_RE = re.compile(r"sha256=([0-9a-fA-F]{64})")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
TOKEN_VALUES = {"", "TOKEN_VAZIO", "NOASSERTION", "TODO"}
BLOCKED_PREFIXES = ("blocked", "excluded", "quarantine", "quarentena")
BINARY_SUFFIXES = {".so", ".bin", ".elf", ".apk", ".aab", ".img", ".fd"}
BINARY_MAGICS = (b"\x7fELF", b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument(
        "--register",
        type=Path,
        default=Path("resources/compliance/ASSET_PROVENANCE_REGISTER.csv"),
    )
    parser.add_argument("--sbom", type=Path, default=Path("sbom/SBOM.spdx.json"))
    parser.add_argument("--legal-map", type=Path, default=Path("legal/LEGAL_SCOPE_MAP.yaml"))
    parser.add_argument(
        "--asset-lock", type=Path, default=Path("tools/qemu_rafaelia_assets.lock.yml")
    )
    parser.add_argument("--expected-qemu-commit", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def is_token(value: str | None) -> bool:
    return value is None or value.strip() in TOKEN_VALUES


def blocked_status(value: str) -> bool:
    normalized = value.strip().lower()
    return normalized.startswith(BLOCKED_PREFIXES)


def repository_path(repo: Path, candidate: Path) -> Path:
    return candidate if candidate.is_absolute() else repo / candidate


def binary_candidate(path: Path) -> bool:
    if path.suffix.lower() in BINARY_SUFFIXES:
        return True
    try:
        prefix = path.read_bytes()[:4]
    except OSError:
        return False
    return any(prefix.startswith(magic) for magic in BINARY_MAGICS)


def load_csv(path: Path) -> tuple[list[dict[str, str]], list[str]]:
    errors: list[str] = []
    required = {
        "asset_path",
        "asset_type",
        "author",
        "source_url",
        "license_spdx",
        "permission_proof",
        "risk_class",
        "status",
    }
    if not path.is_file():
        return [], [f"missing provenance register: {path}"]
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        missing = sorted(required - set(reader.fieldnames or []))
        if missing:
            errors.append(f"register missing columns: {', '.join(missing)}")
        rows = [dict(row) for row in reader]
    return rows, errors


def local_matches(repo: Path, asset_path: str) -> tuple[str, list[Path]]:
    if asset_path.startswith("runtime:") or "${" in asset_path:
        return "runtime_external", []
    if any(char in asset_path for char in "*?["):
        matches = [Path(item) for item in glob.glob(str(repo / asset_path), recursive=True)]
        return "glob", [path for path in matches if path.is_file()]
    path = repo / asset_path
    return "exact", [path] if path.is_file() else []


def expected_sha256(permission_proof: str) -> str | None:
    match = SHA256_RE.search(permission_proof or "")
    return match.group(1).lower() if match else None


def audit_register(repo: Path, rows: list[dict[str, str]]) -> tuple[list[dict[str, Any]], list[str]]:
    audited: list[dict[str, Any]] = []
    hard_errors: list[str] = []
    exact_registered: set[str] = set()

    for index, row in enumerate(rows, start=2):
        asset_path = (row.get("asset_path") or "").strip()
        status = (row.get("status") or "").strip()
        risk = (row.get("risk_class") or "").strip().upper()
        mode, matches = local_matches(repo, asset_path)
        if mode == "exact":
            exact_registered.add(asset_path)

        missing_identity = [
            field
            for field in ("author", "source_url", "license_spdx")
            if is_token(row.get(field))
        ]
        expected = expected_sha256(row.get("permission_proof", ""))
        files = []
        hash_mismatch = False
        for path in matches:
            digest = sha256_file(path)
            match = expected is None or digest == expected
            hash_mismatch = hash_mismatch or not match
            files.append(
                {
                    "path": str(path.relative_to(repo)),
                    "size_bytes": path.stat().st_size,
                    "sha256": digest,
                    "expected_sha256": expected or "TOKEN_VAZIO",
                    "hash_matches": match if expected is not None else "TOKEN_VAZIO",
                }
            )

        controlled_unresolved = bool(missing_identity) and blocked_status(status)
        unsafe_approved = bool(missing_identity) and status.lower() == "approved"
        if hash_mismatch:
            hard_errors.append(f"row {index} hash mismatch: {asset_path}")
        if unsafe_approved:
            hard_errors.append(f"row {index} approved with unresolved identity: {asset_path}")
        if not asset_path:
            hard_errors.append(f"row {index} empty asset_path")

        audited.append(
            {
                "row": index,
                "asset_path": asset_path,
                "asset_type": row.get("asset_type", ""),
                "risk_class": risk,
                "status": status,
                "path_mode": mode,
                "matched_file_count": len(matches),
                "files": files,
                "missing_identity_fields": missing_identity,
                "controlled_unresolved": controlled_unresolved,
                "unsafe_approved": unsafe_approved,
                "hash_mismatch": hash_mismatch,
            }
        )

    discovered = []
    for root in (repo / "app/src/main/jniLibs", repo / "_incoming/pending"):
        if not root.is_dir():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or not binary_candidate(path):
                continue
            relative = str(path.relative_to(repo))
            if relative not in exact_registered:
                discovered.append(relative)
    for path in sorted(set(discovered)):
        hard_errors.append(f"unregistered binary candidate: {path}")

    return audited, hard_errors


def audit_sbom(path: Path) -> tuple[dict[str, Any], list[str]]:
    if not path.is_file():
        return {"present": False}, [f"missing SBOM: {path}"]
    try:
        sbom = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        return {"present": True, "valid_json": False}, [f"invalid SBOM JSON: {error}"]

    packages = sbom.get("packages") if isinstance(sbom.get("packages"), list) else []
    unresolved = []
    invalid_checksums = []
    for package in packages:
        if not isinstance(package, dict):
            continue
        package_id = package.get("SPDXID", "TOKEN_VAZIO")
        fields = [
            name
            for name in ("versionInfo", "downloadLocation", "licenseConcluded", "licenseDeclared")
            if is_token(str(package.get(name, "")))
        ]
        checksums = package.get("checksums") if isinstance(package.get("checksums"), list) else []
        checksum_states = []
        for checksum in checksums:
            value = str(checksum.get("checksumValue", "")) if isinstance(checksum, dict) else ""
            checksum_states.append(value)
            if value not in TOKEN_VALUES and not HEX64.fullmatch(value.lower()):
                invalid_checksums.append(f"{package_id}:{value}")
        if fields or any(value in TOKEN_VALUES for value in checksum_states):
            unresolved.append(
                {
                    "spdx_id": package_id,
                    "name": package.get("name", "TOKEN_VAZIO"),
                    "unresolved_fields": fields,
                    "checksum_values": checksum_states,
                }
            )

    errors = [f"invalid SBOM checksum: {item}" for item in invalid_checksums]
    return {
        "present": True,
        "valid_json": True,
        "spdx_version": sbom.get("spdxVersion", "TOKEN_VAZIO"),
        "package_count": len(packages),
        "unresolved_package_count": len(unresolved),
        "unresolved_packages": unresolved,
        "invalid_checksum_count": len(invalid_checksums),
    }, errors


def audit_text_contract(path: Path, label: str) -> tuple[dict[str, Any], list[str]]:
    if not path.is_file():
        return {"present": False}, [f"missing {label}: {path}"]
    text = path.read_text(encoding="utf-8", errors="replace")
    return {
        "present": True,
        "sha256": sha256_file(path),
        "token_vazio_count": text.count("TOKEN_VAZIO"),
        "noassertion_count": text.count("NOASSERTION"),
        "todo_count": len(re.findall(r"\bTODO\b", text)),
        "quarantine_count": len(re.findall(r"QUARANTENA|QUARANTINE", text, re.IGNORECASE)),
    }, []


def observed_qemu_commit(path: Path) -> str:
    if not path.is_file():
        return "TOKEN_VAZIO"
    text = path.read_text(encoding="utf-8", errors="replace")
    match = re.search(r"^source_commit_observed:\s*['\"]?([0-9a-f]{40})", text, re.MULTILINE)
    return match.group(1) if match else "TOKEN_VAZIO"


def main() -> int:
    args = parse_args()
    repo = args.repo.resolve()
    register_path = repository_path(repo, args.register)
    sbom_path = repository_path(repo, args.sbom)
    legal_path = repository_path(repo, args.legal_map)
    lock_path = repository_path(repo, args.asset_lock)

    rows, errors = load_csv(register_path)
    assets, asset_errors = audit_register(repo, rows)
    errors.extend(asset_errors)
    sbom, sbom_errors = audit_sbom(sbom_path)
    errors.extend(sbom_errors)
    legal, legal_errors = audit_text_contract(legal_path, "legal map")
    errors.extend(legal_errors)
    asset_lock, lock_errors = audit_text_contract(lock_path, "asset lock")
    errors.extend(lock_errors)

    lock_commit = observed_qemu_commit(lock_path)
    qemu_pin_matches = lock_commit == args.expected_qemu_commit
    unresolved_critical = [
        item
        for item in assets
        if item["risk_class"] == "CRITICAL" and item["missing_identity_fields"]
    ]
    uncontrolled_critical = [item for item in unresolved_critical if not item["controlled_unresolved"]]
    if uncontrolled_critical:
        errors.append("critical unresolved assets are not blocked")

    registered_blocked = [item for item in assets if blocked_status(item["status"])]
    if errors:
        state = "FAIL"
    elif unresolved_critical or not qemu_pin_matches or sbom.get("unresolved_package_count", 0):
        state = "PASS_WITH_QUARANTINE"
    else:
        state = "PASS"

    report = {
        "schema": SCHEMA,
        "cycle_id": "C06",
        "state": state,
        "claim_allowed": False,
        "source_commit": args.source_commit,
        "expected_qemu_commit": args.expected_qemu_commit,
        "asset_lock_qemu_commit": lock_commit,
        "qemu_pin_matches": qemu_pin_matches,
        "summary": {
            "register_rows": len(rows),
            "blocked_or_excluded_rows": len(registered_blocked),
            "critical_unresolved_rows": len(unresolved_critical),
            "uncontrolled_critical_rows": len(uncontrolled_critical),
            "hard_error_count": len(errors),
            "sbom_unresolved_packages": sbom.get("unresolved_package_count", "TOKEN_VAZIO"),
        },
        "assets": assets,
        "sbom": sbom,
        "legal_map": legal,
        "asset_lock": asset_lock,
        "quarantine": {
            "required": bool(unresolved_critical or not qemu_pin_matches),
            "critical_assets": [item["asset_path"] for item in unresolved_critical],
            "qemu_pin_mismatch": not qemu_pin_matches,
        },
        "claim_boundary": {
            "registered_asset_inventory": "VERIFIED_BY_EXECUTION" if not errors else "NOT_PROMOTED",
            "known_critical_assets_controlled": (
                "VERIFIED_BY_EXECUTION" if not uncontrolled_critical else "NOT_PROMOTED"
            ),
            "all_provenance_resolved": state == "PASS",
            "public_release_allowed": False,
            "binary_distribution_allowed": False,
        },
        "errors": sorted(set(errors)),
        "falsifiers": [
            "unregistered_binary_candidate",
            "registered_file_hash_mismatch",
            "approved_asset_with_unresolved_identity",
            "critical_unresolved_asset_not_blocked",
            "invalid_sbom_json_or_checksum",
            "missing_legal_or_lock_contract",
        ],
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{state} C06 provenance closure: {args.output}")
    return 1 if state == "FAIL" else 0


if __name__ == "__main__":
    raise SystemExit(main())
