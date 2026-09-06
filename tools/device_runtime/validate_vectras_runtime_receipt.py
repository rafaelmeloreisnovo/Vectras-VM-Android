#!/usr/bin/env python3
"""Validate and reproduce Vectras in-app runtime evidence receipts."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA = "vectras.runtime-evidence.v1"
GUEST_PATH = "/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin"
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
QEMU_RE = re.compile(r"^qemu-system-[A-Za-z0-9_.-]+$")
GUEST_EXEC_RE = re.compile(r"^/(?:usr/local/bin|usr/bin|bin|usr/local/sbin|usr/sbin|sbin)/[A-Za-z0-9_.-]+$")
HASH_FIELDS = ("apk_sha256", "proot_sha256", "qemu_sha256", "qemu_img_sha256")
BOOL_FIELDS = (
    "proot_executable",
    "root_shell_executable",
    "qemu_executable",
    "qemu_img_executable",
    "bootstrap_validator_ok",
)
TOP_KEYS = {
    "schema", "receipt_id", "created_unix_ms", "package_name", "requested_qemu_binary",
    "guest_path", "resolved_qemu_guest_path", "resolved_qemu_img_guest_path",
    "apk_sha256", "proot_sha256", "qemu_sha256", "qemu_img_sha256",
    "proot_executable", "root_shell_executable", "qemu_executable", "qemu_img_executable",
    "bootstrap_validator_ok", "bootstrap_validator_summary", "qemu_probe", "qemu_img_probe",
    "android_fingerprint", "android_model", "android_manufacturer", "android_hardware",
    "android_supported_abis", "device_state", "reproduced_state", "claim_allowed", "reason",
}
PROBE_KEYS = {"ok", "exit_code", "status", "detail"}


def validate_probe(name: str, raw: Any, errors: list[str]) -> None:
    if not isinstance(raw, dict):
        errors.append(f"{name}: object required")
        return
    if set(raw) != PROBE_KEYS:
        errors.append(f"{name}: exact keys required")
    ok = raw.get("ok")
    exit_code = raw.get("exit_code")
    if not isinstance(ok, bool):
        errors.append(f"{name}.ok invalid")
    if not isinstance(exit_code, int) or isinstance(exit_code, bool) or not -1 <= exit_code <= 255:
        errors.append(f"{name}.exit_code invalid")
    if not isinstance(raw.get("status"), str) or not raw.get("status"):
        errors.append(f"{name}.status invalid")
    if not isinstance(raw.get("detail"), str):
        errors.append(f"{name}.detail invalid")
    if ok is True and exit_code != 0:
        errors.append(f"{name}: ok requires exit_code=0")
    if ok is False and exit_code == 0:
        errors.append(f"{name}: failed probe cannot have exit_code=0")


def validate(path: Path) -> dict[str, Any]:
    errors: list[str] = []
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        return {"path": str(path), "valid": False, "errors": [f"read/json: {exc}"], "receipt": None}
    if not isinstance(doc, dict):
        return {"path": str(path), "valid": False, "errors": ["top-level object required"], "receipt": None}
    if set(doc) != TOP_KEYS:
        errors.append(f"exact top-level keys required; got extras/missing={sorted(set(doc) ^ TOP_KEYS)}")
    if doc.get("schema") != SCHEMA:
        errors.append("schema mismatch")
    if not isinstance(doc.get("receipt_id"), str) or not (8 <= len(doc["receipt_id"]) <= 256):
        errors.append("receipt_id invalid")
    if not isinstance(doc.get("created_unix_ms"), int) or isinstance(doc.get("created_unix_ms"), bool) or doc["created_unix_ms"] <= 0:
        errors.append("created_unix_ms invalid")
    if not isinstance(doc.get("package_name"), str) or not doc.get("package_name"):
        errors.append("package_name invalid")
    qemu_token = doc.get("requested_qemu_binary")
    if not isinstance(qemu_token, str) or not QEMU_RE.fullmatch(qemu_token):
        errors.append("requested_qemu_binary invalid")
    if doc.get("guest_path") != GUEST_PATH:
        errors.append("guest_path mismatch")
    qemu_guest = doc.get("resolved_qemu_guest_path")
    qemu_img_guest = doc.get("resolved_qemu_img_guest_path")
    if not isinstance(qemu_guest, str) or not GUEST_EXEC_RE.fullmatch(qemu_guest):
        errors.append("resolved_qemu_guest_path invalid")
    elif isinstance(qemu_token, str) and Path(qemu_guest).name != qemu_token:
        errors.append("resolved_qemu_guest_path basename mismatch")
    if not isinstance(qemu_img_guest, str) or not GUEST_EXEC_RE.fullmatch(qemu_img_guest):
        errors.append("resolved_qemu_img_guest_path invalid")
    elif Path(qemu_img_guest).name != "qemu-img":
        errors.append("resolved_qemu_img_guest_path basename mismatch")

    for field in HASH_FIELDS:
        value = doc.get(field)
        if value != "TOKEN_VAZIO" and (not isinstance(value, str) or not SHA_RE.fullmatch(value)):
            errors.append(f"{field} invalid")
    for field in BOOL_FIELDS:
        if not isinstance(doc.get(field), bool):
            errors.append(f"{field} must be boolean")
    if not isinstance(doc.get("bootstrap_validator_summary"), str):
        errors.append("bootstrap_validator_summary invalid")
    for field in ("android_fingerprint", "android_model", "android_manufacturer", "android_hardware", "reason"):
        if not isinstance(doc.get(field), str):
            errors.append(f"{field} invalid")
    abis = doc.get("android_supported_abis")
    if not isinstance(abis, list) or any(not isinstance(item, str) or not item for item in abis) or len(set(abis)) != len(abis):
        errors.append("android_supported_abis invalid")

    validate_probe("qemu_probe", doc.get("qemu_probe"), errors)
    validate_probe("qemu_img_probe", doc.get("qemu_img_probe"), errors)

    if doc.get("device_state") not in {"DEVICE_PROVEN", "TOKEN_VAZIO"}:
        errors.append("device_state invalid")
    if doc.get("reproduced_state") not in {"REPRODUCED", "TOKEN_VAZIO"}:
        errors.append("reproduced_state invalid")
    if not isinstance(doc.get("claim_allowed"), bool):
        errors.append("claim_allowed invalid")
    if doc.get("reproduced_state") == "REPRODUCED":
        errors.append("single receipt cannot self-promote REPRODUCED")

    hashes_bound = all(isinstance(doc.get(field), str) and SHA_RE.fullmatch(doc[field]) for field in HASH_FIELDS)
    runtime_files_ok = all(doc.get(field) is True for field in BOOL_FIELDS)
    probes_ok = all(
        isinstance(doc.get(field), dict)
        and doc[field].get("ok") is True
        and doc[field].get("exit_code") == 0
        for field in ("qemu_probe", "qemu_img_probe")
    )
    expected_promoted = hashes_bound and runtime_files_ok and probes_ok
    promoted = doc.get("device_state") == "DEVICE_PROVEN" and doc.get("claim_allowed") is True
    if promoted != expected_promoted:
        errors.append("promotion invariant violated")
    if doc.get("device_state") == "DEVICE_PROVEN" and not doc.get("reason"):
        errors.append("DEVICE_PROVEN requires reason")

    return {"path": str(path), "valid": not errors, "errors": errors, "receipt": doc}


def reproduce(first: dict[str, Any], second: dict[str, Any]) -> dict[str, Any]:
    errors: list[str] = []
    if not first.get("valid"):
        errors.append("first receipt invalid")
    if not second.get("valid"):
        errors.append("second receipt invalid")
    a = first.get("receipt") or {}
    b = second.get("receipt") or {}
    if a.get("receipt_id") == b.get("receipt_id"):
        errors.append("receipt_id must differ")
    if a.get("created_unix_ms") == b.get("created_unix_ms"):
        errors.append("timestamps must differ")
    for field in (
        "package_name", "requested_qemu_binary", "guest_path",
        "resolved_qemu_guest_path", "resolved_qemu_img_guest_path",
        "apk_sha256", "proot_sha256", "qemu_sha256", "qemu_img_sha256",
        "android_fingerprint", "android_hardware",
    ):
        if a.get(field) != b.get(field):
            errors.append(f"{field} mismatch")
    for label, doc in (("first", a), ("second", b)):
        if not (doc.get("device_state") == "DEVICE_PROVEN" and doc.get("claim_allowed") is True):
            errors.append(f"{label} not DEVICE_PROVEN")
        if any(not isinstance(doc.get(field), str) or not SHA_RE.fullmatch(doc[field]) for field in HASH_FIELDS):
            errors.append(f"{label} artifact hashes not bound")
    return {
        "schema": "vectras.runtime-reproduction.v1",
        "receipt_ids": [a.get("receipt_id"), b.get("receipt_id")],
        "package_name": a.get("package_name") if a.get("package_name") == b.get("package_name") else None,
        "apk_sha256": a.get("apk_sha256") if a.get("apk_sha256") == b.get("apk_sha256") else None,
        "proot_sha256": a.get("proot_sha256") if a.get("proot_sha256") == b.get("proot_sha256") else None,
        "qemu_sha256": a.get("qemu_sha256") if a.get("qemu_sha256") == b.get("qemu_sha256") else None,
        "qemu_img_sha256": a.get("qemu_img_sha256") if a.get("qemu_img_sha256") == b.get("qemu_img_sha256") else None,
        "android_fingerprint": a.get("android_fingerprint") if a.get("android_fingerprint") == b.get("android_fingerprint") else None,
        "reproduced_state": "REPRODUCED" if not errors else "TOKEN_VAZIO",
        "claim_allowed": not errors,
        "errors": errors,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("receipts", nargs="+")
    parser.add_argument("--reproduce", action="store_true")
    parser.add_argument("--json", dest="json_path", type=Path)
    args = parser.parse_args(argv)
    if args.reproduce and len(args.receipts) != 2:
        parser.error("--reproduce requires exactly two receipts")
    reports = [validate(Path(raw)) for raw in args.receipts]
    if args.reproduce:
        payload: Any = reproduce(reports[0], reports[1])
        ok = payload["claim_allowed"]
    else:
        payload = {"schema": "vectras.runtime-validation.v1", "reports": reports}
        ok = all(item["valid"] for item in reports)
    rendered = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    if args.json_path:
        args.json_path.parent.mkdir(parents=True, exist_ok=True)
        args.json_path.write_text(rendered, encoding="utf-8")
    sys.stdout.write(rendered)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
