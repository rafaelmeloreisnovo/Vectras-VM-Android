#!/usr/bin/env python3
"""Fail-closed validator for Vectras bounded work/evidence units.

Mapa owns federated route/state. This module validates only the local Vectras
consumer boundary and never promotes provider/runtime claims by itself.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

VALID_STATES = {
    "DRAFT",
    "TOKEN_VAZIO",
    "BLOCKED",
    "CONTRADICTION",
    "VERIFIED_LIMITED",
    "REPRODUCED",
    "ROLLBACK_READY",
    "CLOSED",
}

UNRESOLVED_UNCERTAINTY = {"TOKEN_VAZIO", "BLOCKED", "PARTIAL"}

TARGET_EVIDENCE = {
    "STATIC_CONTRACT": {"STATIC_TEST", "REVIEW"},
    "QEMU_EXECUTION": {"QEMU_PROCESS"},
    "GUEST_BOOT": {"GUEST_BOOT"},
    "PERFORMANCE": {"PERFORMANCE"},
    "PHYSICAL_ANDROID_E2E": {"DEVICE_TRANSACTION"},
}

CLASSIFICATIONS = {"PUBLIC", "INTERNAL", "SENSITIVE", "RESTRICTED", "NOT_APPLICABLE"}


def _present(value: Any) -> bool:
    return value is not None and value != ""


def validate_unit(unit: dict[str, Any]) -> list[str]:
    errors: list[str] = []

    required = {
        "id",
        "intent",
        "authority",
        "federated_authority",
        "repo",
        "source_commit",
        "target_claim",
        "sources",
        "context",
        "evidence",
        "contradictions",
        "uncertainty",
        "reproduction",
        "rollback",
        "reconstructibility",
        "state",
        "claim_allowed",
        "next",
    }
    missing = sorted(required - set(unit))
    errors.extend(f"missing:{key}" for key in missing)
    if missing:
        return errors

    if unit["authority"] != "rafaelmeloreisnovo/Vectras-VM-Android":
        errors.append("authority:local_repo_must_own_local_mutation")
    if unit["federated_authority"] != "rafaelmeloreisnovo/Mapa":
        errors.append("authority:mapa_must_own_federated_route_state")
    if unit["repo"] != "rafaelmeloreisnovo/Vectras-VM-Android":
        errors.append("repo:unexpected")

    commit = unit["source_commit"]
    if not isinstance(commit, str) or re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        errors.append("provenance:source_commit_not_exact_sha")

    sources = unit["sources"]
    if not isinstance(sources, list) or not sources:
        errors.append("provenance:sources_empty")
    else:
        for index, source in enumerate(sources):
            if (
                not isinstance(source, dict)
                or not _present(source.get("ref"))
                or not _present(source.get("kind"))
            ):
                errors.append(f"provenance:source_{index}_incomplete")

    context = unit["context"]
    if not isinstance(context, dict):
        errors.append("context:not_object")
        context = {}
    for key in ("scope", "boundary", "observed_at", "governance", "data", "privacy", "security"):
        if not _present(context.get(key)):
            errors.append(f"context:missing_{key}")
    for key in ("data", "privacy", "security"):
        value = context.get(key)
        if _present(value) and value not in CLASSIFICATIONS:
            errors.append(f"context:invalid_{key}_classification")

    target = unit["target_claim"]
    if target not in TARGET_EVIDENCE:
        errors.append("target_claim:invalid")

    evidence = unit["evidence"]
    if not isinstance(evidence, list):
        errors.append("evidence:not_array")
        evidence = []

    evidence_types = set()
    for index, item in enumerate(evidence):
        if (
            not isinstance(item, dict)
            or not _present(item.get("ref"))
            or not _present(item.get("type"))
        ):
            errors.append(f"evidence:item_{index}_incomplete")
            continue
        evidence_types.add(item["type"])

    contradictions = unit["contradictions"]
    if not isinstance(contradictions, list):
        errors.append("contradiction:not_array")
        contradictions = []
    open_contradictions = [
        item
        for item in contradictions
        if isinstance(item, dict) and item.get("state") == "OPEN"
    ]

    uncertainty = unit["uncertainty"]
    if not isinstance(uncertainty, list):
        errors.append("uncertainty:not_array")
        uncertainty = []
    unresolved_uncertainty = [
        item
        for item in uncertainty
        if isinstance(item, dict) and item.get("state") in UNRESOLVED_UNCERTAINTY
    ]

    reproduction = unit["reproduction"] if isinstance(unit["reproduction"], dict) else {}
    rollback = unit["rollback"] if isinstance(unit["rollback"], dict) else {}
    reconstructibility = (
        unit["reconstructibility"]
        if isinstance(unit["reconstructibility"], dict)
        else {}
    )

    reconstructible = (
        reconstructibility.get("status") == "PASS"
        and reconstructibility.get("inputs_pinned") is True
        and reconstructibility.get("versions_pinned") is True
        and reconstructibility.get("outputs_identified") is True
    )

    target_evidence_ok = (
        target in TARGET_EVIDENCE
        and bool(TARGET_EVIDENCE[target] & evidence_types)
    )

    promotable = (
        not errors
        and bool(sources)
        and bool(evidence)
        and target_evidence_ok
        and not open_contradictions
        and not unresolved_uncertainty
        and reproduction.get("status") == "PASS"
        and _present(reproduction.get("procedure"))
        and rollback.get("ready") is True
        and _present(rollback.get("predecessor"))
        and _present(rollback.get("procedure"))
        and reconstructible
    )

    if unit["claim_allowed"] is True and not target_evidence_ok:
        errors.append("evidence:target_specific_proof_missing")
    if unit["claim_allowed"] is True and open_contradictions:
        errors.append("contradiction:open_but_claim_allowed")
    if unit["claim_allowed"] is True and unresolved_uncertainty:
        errors.append("uncertainty:unresolved_but_claim_allowed")
    if unit["claim_allowed"] is True and reproduction.get("status") != "PASS":
        errors.append("reproduction:claim_requires_pass")
    if unit["claim_allowed"] is True and not (
        rollback.get("ready") is True
        and _present(rollback.get("predecessor"))
        and _present(rollback.get("procedure"))
    ):
        errors.append("rollback:claim_requires_ready_predecessor_procedure")
    if unit["claim_allowed"] is True and not reconstructible:
        errors.append("reconstructibility:claim_requires_pass")
    if unit["claim_allowed"] is True and not promotable:
        errors.append("claim_gate:promotion_without_all_guards")

    if unit["state"] not in VALID_STATES:
        errors.append("state:invalid")
    if unit["state"] in {"REPRODUCED", "CLOSED"} and reproduction.get("status") != "PASS":
        errors.append("reproduction:state_requires_pass")
    if unit["state"] == "CLOSED" and not reconstructible:
        errors.append("reconstructibility:closed_requires_pass")

    return errors


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 1:
        print("usage: validate_vectras_work_unit.py UNIT.json", file=sys.stderr)
        return 2

    path = Path(args[0])
    payload = json.loads(path.read_text(encoding="utf-8"))
    units = payload if isinstance(payload, list) else [payload]
    all_errors: list[str] = []

    for index, unit in enumerate(units):
        errors = validate_unit(unit)
        all_errors.extend(f"{index}:{error}" for error in errors)

    if all_errors:
        print(json.dumps({"status": "FAIL", "errors": all_errors}, indent=2, sort_keys=True))
        return 1

    print(json.dumps({"status": "PASS", "units": len(units)}, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
