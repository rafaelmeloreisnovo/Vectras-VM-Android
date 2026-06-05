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

REQUIRED_STATE_OF_ART_FIELDS = {"id", "name", "score", "evidence", "falsification"}

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

    state_gate = data.get("state_of_art_gate")
    if not isinstance(state_gate, dict):
        errors.append("state_of_art_gate must be an object")
    else:
        minimum_dimensions = state_gate.get("minimum_dimensions")
        minimum_total_score = state_gate.get("minimum_total_score")
        dimensions = state_gate.get("dimensions")
        if not isinstance(minimum_dimensions, int) or minimum_dimensions < 12:
            errors.append("state_of_art_gate.minimum_dimensions must be an integer >= 12")
        if not isinstance(minimum_total_score, int) or minimum_total_score < 90:
            errors.append("state_of_art_gate.minimum_total_score must be an integer >= 90")
        if state_gate.get("requires_primary_evidence") is not True:
            errors.append("state_of_art_gate.requires_primary_evidence must be true")
        if state_gate.get("requires_no_unverified_production_claims") is not True:
            errors.append("state_of_art_gate.requires_no_unverified_production_claims must be true")
        if not isinstance(dimensions, list):
            errors.append("state_of_art_gate.dimensions must be a list")
        else:
            if isinstance(minimum_dimensions, int) and len(dimensions) < minimum_dimensions:
                errors.append(
                    f"state_of_art_gate.dimensions has {len(dimensions)} entries but requires at least {minimum_dimensions}"
                )
            total_score = 0
            seen_dimension_ids: set[str] = set()
            for index, dimension in enumerate(dimensions, start=1):
                if not isinstance(dimension, dict):
                    errors.append(f"state_of_art_gate.dimensions[{index}] must be an object")
                    continue
                missing_fields = sorted(REQUIRED_STATE_OF_ART_FIELDS - set(dimension))
                if missing_fields:
                    errors.append(
                        f"state_of_art_gate.dimensions[{index}] missing fields: {', '.join(missing_fields)}"
                    )
                dimension_id = dimension.get("id")
                if not non_empty_text(dimension_id):
                    errors.append(f"state_of_art_gate.dimensions[{index}].id must be non-empty text")
                elif dimension_id in seen_dimension_ids:
                    errors.append(f"duplicate state-of-art dimension id: {dimension_id}")
                else:
                    seen_dimension_ids.add(dimension_id)
                for field in sorted(REQUIRED_STATE_OF_ART_FIELDS - {"score"}):
                    if field in dimension and not non_empty_text(dimension[field]):
                        errors.append(
                            f"{dimension.get('id', f'state_of_art_gate.dimensions[{index}]')}.{field} must be non-empty text"
                        )
                score = dimension.get("score")
                if not isinstance(score, int) or score <= 0:
                    errors.append(f"{dimension.get('id', f'state_of_art_gate.dimensions[{index}]')}.score must be a positive integer")
                else:
                    total_score += score
            if isinstance(minimum_total_score, int) and total_score < minimum_total_score:
                errors.append(
                    f"state_of_art_gate total score is {total_score} but requires at least {minimum_total_score}"
                )

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

    state_gate = data.get("state_of_art_gate", {})
    dimensions = state_gate.get("dimensions", []) if isinstance(state_gate, dict) else []
    score = sum(item.get("score", 0) for item in dimensions if isinstance(item, dict))
    print(
        json.dumps(
            {
                "status": "PASS",
                "work_modes": len(data["work_modes"]),
                "minimum_work_modes": data["minimum_work_modes"],
                "state_of_art_dimensions": len(dimensions),
                "state_of_art_score": score,
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
