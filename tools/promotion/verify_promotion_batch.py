#!/usr/bin/env python3
"""Validate one reversible loose-artifact promotion batch.

Validation is fail-closed and stdlib-only. The script proves identity,
boundaries, and adapter presence; it never claims runtime execution.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

TOKEN_VAZIO = "TOKEN_VAZIO"
ALLOWED_STATES = {
    "ADAPTER_INTEGRATED_UNEXECUTED",
    "REGISTERED_LOCAL_RENDERER",
    "REGISTERED_CODEGEN_UNPROVEN_RUNTIME",
    "GATE_IMPLEMENTED_AWAITS_ARTIFACT",
}
CLAIMABLE_STATES = {"PROVEN_RUNTIME", "CERTIFIED_INTEROPERABLE"}


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"expected JSON object in {path}")
    return value


def git_blob_sha1(data: bytes) -> str:
    prefix = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(prefix + data).hexdigest()


def validate_batch(root: Path, batch: dict[str, Any]) -> tuple[dict[str, Any], int]:
    failures: list[str] = []
    records: list[dict[str, Any]] = []
    artifacts = batch.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        failures.append("artifacts must be a non-empty list")
        artifacts = []

    ids = [str(item.get("id", "")) for item in artifacts if isinstance(item, dict)]
    duplicate_ids = sorted(key for key, count in Counter(ids).items() if key and count > 1)
    if duplicate_ids:
        failures.append(f"duplicate artifact ids: {duplicate_ids}")

    for index, item in enumerate(artifacts):
        item_failures: list[str] = []
        if not isinstance(item, dict):
            failures.append(f"artifact[{index}] is not an object")
            continue
        artifact_id = str(item.get("id", f"artifact[{index}]"))
        source_rel = str(item.get("source_path", ""))
        source = root / source_rel
        expected_blob = str(item.get("expected_git_blob_sha", ""))
        state = str(item.get("promotion_state", TOKEN_VAZIO))
        claim_allowed = bool(item.get("claim_allowed", False))

        if not source_rel or not source.is_file():
            item_failures.append(f"missing source_path: {source_rel or TOKEN_VAZIO}")
            actual_blob = TOKEN_VAZIO
            data = b""
        else:
            data = source.read_bytes()
            actual_blob = git_blob_sha1(data)
            if not expected_blob:
                item_failures.append("expected_git_blob_sha missing")
            elif actual_blob != expected_blob:
                item_failures.append(f"git blob mismatch: expected {expected_blob}, got {actual_blob}")

        if state not in ALLOWED_STATES and state not in CLAIMABLE_STATES:
            item_failures.append(f"unsupported promotion_state: {state}")
        if claim_allowed and state not in CLAIMABLE_STATES:
            item_failures.append("claim_allowed=true forbidden for unproven promotion state")
        if item.get("automatic_move_allowed", False):
            item_failures.append("automatic_move_allowed must remain false in batch 001")

        text = data.decode("utf-8", errors="replace")
        missing_markers = [str(marker) for marker in item.get("required_markers", []) if str(marker) not in text]
        if missing_markers:
            item_failures.append(f"missing markers: {missing_markers}")

        adapter_paths = [str(path) for path in item.get("adapter_paths", [])]
        missing_adapters = [path for path in adapter_paths if not (root / path).is_file()]
        if missing_adapters:
            item_failures.append(f"missing adapters: {missing_adapters}")

        records.append(
            {
                "id": artifact_id,
                "source_path": source_rel,
                "expected_git_blob_sha": expected_blob or TOKEN_VAZIO,
                "actual_git_blob_sha": actual_blob,
                "promotion_state": state,
                "claim_allowed": claim_allowed and not item_failures,
                "automatic_move_allowed": bool(item.get("automatic_move_allowed", False)),
                "adapter_paths": adapter_paths,
                "failures": item_failures,
                "state": "PASS" if not item_failures else "FAIL",
            }
        )
        failures.extend(f"{artifact_id}: {message}" for message in item_failures)

    report = {
        "schema_version": "1.0.0",
        "batch_id": batch.get("batch_id", TOKEN_VAZIO),
        "repository": batch.get("repository", TOKEN_VAZIO),
        "source_ref": batch.get("source_ref", TOKEN_VAZIO),
        "artifact_count": len(records),
        "pass_count": sum(record["state"] == "PASS" for record in records),
        "fail_count": sum(record["state"] == "FAIL" for record in records),
        "state": "PASS" if not failures else "FAIL",
        "claim_allowed": False,
        "failures": failures,
        "artifacts": records,
    }
    return report, 0 if not failures else 1


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--batch", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("reports/vectra-artifact-promotion/batch-001.json"))
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    batch_path = args.batch if args.batch.is_absolute() else root / args.batch
    output = args.output if args.output.is_absolute() else root / args.output
    try:
        batch = load_json(batch_path)
        report, status = validate_batch(root, batch)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"artifact promotion validation error: {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": output.as_posix(),
        "state": report["state"],
        "artifacts": report["artifact_count"],
        "claim_allowed": report["claim_allowed"],
    }, ensure_ascii=False))
    return status


if __name__ == "__main__":
    raise SystemExit(main())
