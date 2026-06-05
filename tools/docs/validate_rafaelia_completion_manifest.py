#!/usr/bin/env python3
"""Validate the RAFAELIA enterprise completion manifest.

This is a documentation/governance validator. It does not execute Android,
native, assembly, or runtime hot paths; it checks that the real-use promotion
manifest is complete enough to prevent unsupported production claims.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

REQUIRED_INVARIANTS = {
    "nomalloc=true",
    "freestanding=true",
    "deterministic=true",
    "Q16.16=true",
    "branchless_preferable=true",
    "audit_trail=mandatory",
    "ATTRACTOR_COUNT=42",
    "period(BitOmega)=42",
    "phi=(1-H)*C",
}

REQUIRED_GATE_FLAGS = {
    "requires_validation_command",
    "requires_rollback",
    "requires_failover",
    "requires_falsification",
    "requires_no_hidden_failures",
}

REQUIRED_MODE_FIELDS = {
    "id",
    "title",
    "layer",
    "purpose",
    "acceptance",
    "validation_command",
    "rollback",
    "failover",
    "falsification",
    "deterministic",
    "no_heap_hot_path",
}

DEFAULT_MANIFEST = Path("docs/rafaelia_reference/rafaelia_enterprise_completion_manifest_2026-06-05.json")


def non_empty_text(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate_manifest(data: dict[str, Any]) -> list[str]:
    errors: list[str] = []

    if data.get("schema_version") != 1:
        errors.append("schema_version must be 1")

    minimum = data.get("minimum_work_modes")
    if not isinstance(minimum, int) or minimum < 20:
        errors.append("minimum_work_modes must be an integer >= 20")

    invariants = set(data.get("invariants", []))
    missing_invariants = sorted(REQUIRED_INVARIANTS - invariants)
    if missing_invariants:
        errors.append("missing invariants: " + ", ".join(missing_invariants))

    gate = data.get("completion_gate")
    if not isinstance(gate, dict):
        errors.append("completion_gate must be an object")
    else:
        for name in sorted(REQUIRED_GATE_FLAGS):
            if gate.get(name) is not True:
                errors.append(f"completion_gate.{name} must be true")

    modes = data.get("work_modes")
    if not isinstance(modes, list):
        errors.append("work_modes must be a list")
        return errors

    if isinstance(minimum, int) and len(modes) < minimum:
        errors.append(f"work_modes has {len(modes)} entries but requires at least {minimum}")

    seen_ids: set[str] = set()
    for index, mode in enumerate(modes, start=1):
        if not isinstance(mode, dict):
            errors.append(f"work_modes[{index}] must be an object")
            continue
        missing_fields = sorted(REQUIRED_MODE_FIELDS - set(mode))
        if missing_fields:
            errors.append(f"work_modes[{index}] missing fields: {', '.join(missing_fields)}")
        mode_id = mode.get("id")
        if not non_empty_text(mode_id):
            errors.append(f"work_modes[{index}].id must be non-empty text")
        elif mode_id in seen_ids:
            errors.append(f"duplicate work mode id: {mode_id}")
        else:
            seen_ids.add(mode_id)
        for field in sorted(REQUIRED_MODE_FIELDS - {"deterministic", "no_heap_hot_path"}):
            if field in mode and not non_empty_text(mode[field]):
                errors.append(f"{mode.get('id', f'work_modes[{index}]')}.{field} must be non-empty text")
        if mode.get("deterministic") is not True:
            errors.append(f"{mode.get('id', f'work_modes[{index}]')}.deterministic must be true")
        if mode.get("no_heap_hot_path") is not True:
            errors.append(f"{mode.get('id', f'work_modes[{index}]')}.no_heap_hot_path must be true")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate RAFAELIA enterprise completion manifest")
    parser.add_argument("manifest", nargs="?", default=DEFAULT_MANIFEST, type=Path)
    args = parser.parse_args(argv)

    try:
        data = json.loads(args.manifest.read_text(encoding="utf-8"))
    except OSError as exc:
        print(json.dumps({"status": "FAIL", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 1
    except json.JSONDecodeError as exc:
        print(json.dumps({"status": "FAIL", "error": f"invalid JSON: {exc}"}, ensure_ascii=False), file=sys.stderr)
        return 1

    if not isinstance(data, dict):
        print(json.dumps({"status": "FAIL", "error": "manifest root must be an object"}, ensure_ascii=False), file=sys.stderr)
        return 1

    errors = validate_manifest(data)
    if errors:
        print(json.dumps({"status": "FAIL", "errors": errors}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1

    print(json.dumps({"status": "PASS", "work_modes": len(data["work_modes"]), "minimum_work_modes": data["minimum_work_modes"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
