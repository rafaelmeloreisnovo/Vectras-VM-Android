#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

GATE_ORDER = ["app_build", "shell", "storage", "proot", "qemu", "image", "command", "process", "guest_boot"]


def is_sha(value: object) -> bool:
    return isinstance(value, str) and len(value) == 64 and all(c in "0123456789abcdef" for c in value)


def validate(data: dict) -> list[str]:
    errors: list[str] = []
    if data.get("schema") != "vectras.guest_boot_evidence.v1":
        errors.append("invalid schema")
    if data.get("claim_allowed") is not False:
        errors.append("claim_allowed must be false")

    gates = data.get("gates", {})
    runtime = data.get("runtime", {})
    safety = data.get("safety", {})
    decision = data.get("decision", {})
    if set(gates) != set(GATE_ORDER):
        errors.append("all lifecycle gates required")
    if safety.get("safe_state") != "vm-stopped-no-image-mutation":
        errors.append("invalid safe state")
    if safety.get("disk_mutated") is True and gates.get("guest_boot") != "PASS":
        errors.append("unverified boot cannot mutate disk")

    if gates.get("guest_boot") == "PASS":
        if any(gates.get(name) != "PASS" for name in GATE_ORDER[:-1]):
            errors.append("guest boot requires every prerequisite gate PASS")
        for field in ("apk_sha256", "qemu_binary_sha256", "image_sha256", "console_log_sha256"):
            if not is_sha(runtime.get(field)):
                errors.append(f"guest boot requires {field}")
        if not runtime.get("device_abi") or runtime.get("device_abi") == "TOKEN_VAZIO":
            errors.append("guest boot requires device ABI")
        if not runtime.get("guest_marker") or runtime.get("guest_marker") == "TOKEN_VAZIO":
            errors.append("guest boot requires guest marker")

    if decision.get("status") in {"TOKEN_VAZIO", "BLOCKED"} and not decision.get("next_action"):
        errors.append("unresolved state requires next action")
    if not safety.get("rollback"):
        errors.append("rollback required")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    try:
        data = json.loads(args.evidence.read_text(encoding="utf-8"))
        errors = validate(data)
    except Exception as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({"status": "PASS" if not errors else "FAIL", "errors": errors, "claim_allowed": False, "guest_boot": data.get("gates", {}).get("guest_boot")}, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
