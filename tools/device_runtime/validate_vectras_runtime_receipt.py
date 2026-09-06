#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA = "vectras.runtime-evidence.v1"
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
QEMU_RE = re.compile(r"^qemu-system-[A-Za-z0-9_.-]+$")
HASH_FIELDS = ("apk_sha256", "proot_sha256", "qemu_sha256", "qemu_img_sha256")
BOOL_FIELDS = (
    "proot_executable", "root_shell_executable", "qemu_executable",
    "qemu_img_executable", "bootstrap_validator_ok",
)


def validate(path: Path) -> dict[str, Any]:
    errors: list[str] = []
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        return {"path": str(path), "valid": False, "errors": [f"read/json: {exc}"], "receipt": None}
    if not isinstance(doc, dict):
        return {"path": str(path), "valid": False, "errors": ["object required"], "receipt": None}
    if doc.get("schema") != SCHEMA:
        errors.append("schema mismatch")
    if not isinstance(doc.get("receipt_id"), str) or len(doc["receipt_id"]) < 8:
        errors.append("receipt_id invalid")
    if not isinstance(doc.get("created_unix_ms"), int) or doc["created_unix_ms"] <= 0:
        errors.append("created_unix_ms invalid")
    if not isinstance(doc.get("package_name"), str) or len(doc["package_name"]) < 3:
        errors.append("package_name invalid")
    if not isinstance(doc.get("requested_qemu_binary"), str) or not QEMU_RE.fullmatch(doc["requested_qemu_binary"]):
        errors.append("requested_qemu_binary invalid")
    for field in HASH_FIELDS:
        value = doc.get(field)
        if not isinstance(value, str):
            errors.append(f"{field} must be string")
    for field in BOOL_FIELDS:
        if not isinstance(doc.get(field), bool):
            errors.append(f"{field} must be boolean")
    for field in ("qemu_probe", "qemu_img_probe"):
        probe = doc.get(field)
        if not isinstance(probe, dict):
            errors.append(f"{field} object required")
            continue
        if not isinstance(probe.get("ok"), bool):
            errors.append(f"{field}.ok invalid")
        if not isinstance(probe.get("exit_code"), int):
            errors.append(f"{field}.exit_code invalid")
        if not isinstance(probe.get("status"), str) or not isinstance(probe.get("detail"), str):
            errors.append(f"{field} status/detail invalid")
        if probe.get("ok") is True and probe.get("exit_code") != 0:
            errors.append(f"{field} ok requires exit_code=0")
    if doc.get("device_state") not in {"DEVICE_PROVEN", "TOKEN_VAZIO"}:
        errors.append("device_state invalid")
    if doc.get("reproduced_state") not in {"REPRODUCED", "TOKEN_VAZIO"}:
        errors.append("reproduced_state invalid")
    if not isinstance(doc.get("claim_allowed"), bool):
        errors.append("claim_allowed invalid")
    if doc.get("reproduced_state") == "REPRODUCED":
        errors.append("single receipt cannot self-promote REPRODUCED")

    all_runtime_ok = all(doc.get(field) is True for field in BOOL_FIELDS)
    all_probe_ok = all(
        isinstance(doc.get(field), dict)
        and doc[field].get("ok") is True
        and doc[field].get("exit_code") == 0
        for field in ("qemu_probe", "qemu_img_probe")
    )
    hashes_bound = all(isinstance(doc.get(field), str) and SHA_RE.fullmatch(doc[field]) for field in HASH_FIELDS)
    promoted = doc.get("device_state") == "DEVICE_PROVEN" and doc.get("claim_allowed") is True
    expected_promoted = all_runtime_ok and all_probe_ok and hashes_bound
    if promoted != expected_promoted:
        errors.append("promotion invariant violated")
    return {"path": str(path), "valid": not errors, "errors": errors, "receipt": doc}


def reproduce(a: dict[str, Any], b: dict[str, Any]) -> dict[str, Any]:
    errors: list[str] = []
    if not a.get("valid"): errors.append("first invalid")
    if not b.get("valid"): errors.append("second invalid")
    x = a.get("receipt") or {}
    y = b.get("receipt") or {}
    if x.get("receipt_id") == y.get("receipt_id"):
        errors.append("receipt_id must differ")
    if x.get("created_unix_ms") == y.get("created_unix_ms"):
        errors.append("timestamps must differ")
    for field in ("package_name", "requested_qemu_binary", *HASH_FIELDS):
        if x.get(field) != y.get(field):
            errors.append(f"{field} mismatch")
    for label, doc in (("first", x), ("second", y)):
        if not (doc.get("device_state") == "DEVICE_PROVEN" and doc.get("claim_allowed") is True):
            errors.append(f"{label} not DEVICE_PROVEN")
    return {
        "schema": "vectras.runtime-reproduction.v1",
        "receipt_ids": [x.get("receipt_id"), y.get("receipt_id")],
        "apk_sha256": x.get("apk_sha256") if x.get("apk_sha256") == y.get("apk_sha256") else None,
        "qemu_sha256": x.get("qemu_sha256") if x.get("qemu_sha256") == y.get("qemu_sha256") else None,
        "reproduced_state": "REPRODUCED" if not errors else "TOKEN_VAZIO",
        "claim_allowed": not errors,
        "errors": errors,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("receipts", nargs="+")
    parser.add_argument("--reproduce", action="store_true")
    args = parser.parse_args(argv)
    if args.reproduce and len(args.receipts) != 2:
        parser.error("--reproduce requires exactly two receipts")
    reports = [validate(Path(p)) for p in args.receipts]
    payload: Any = reproduce(reports[0], reports[1]) if args.reproduce else {
        "schema": "vectras.runtime-validation.v1", "reports": reports
    }
    print(json.dumps(payload, indent=2, sort_keys=True))
    ok = payload["claim_allowed"] if args.reproduce else all(r["valid"] for r in reports)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
