#!/usr/bin/env python3
"""Fail-closed validation for tools -> engine -> JNI -> APK connectivity claims."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "contracts/tools_runtime_connectivity.v1.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def validate_markers(node: dict) -> None:
    node_id = str(node.get("id", "<unknown>"))
    for rel in node.get("required_paths", []):
        path = ROOT / rel
        if not path.is_file():
            fail(f"{node_id}: required path missing: {rel}")

    for rel, markers in node.get("required_markers", {}).items():
        path = ROOT / rel
        if not path.is_file():
            fail(f"{node_id}: marker source missing: {rel}")
        body = text(path)
        for marker in markers:
            if marker not in body:
                fail(f"{node_id}: required marker missing in {rel}: {marker!r}")

    for rel, markers in node.get("forbidden_markers", {}).items():
        path = ROOT / rel
        if not path.is_file():
            fail(f"{node_id}: forbidden-marker source missing: {rel}")
        body = text(path)
        for marker in markers:
            if marker in body:
                fail(f"{node_id}: forbidden marker present in {rel}: {marker!r}")


def validate_unreferenced(node: dict) -> None:
    symbols = [str(value) for value in node.get("symbols", [])]
    if not symbols:
        return
    allowed_prefixes = tuple(str(value) for value in node.get("allowed_reference_prefixes", []))
    unexpected: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT).as_posix()
        if rel.startswith(".git/") or any(part in {"build", ".gradle"} for part in path.parts):
            continue
        try:
            body = text(path)
        except OSError:
            continue
        if not any(symbol in body for symbol in symbols):
            continue
        if allowed_prefixes and rel.startswith(allowed_prefixes):
            continue
        unexpected.append(rel)
    if unexpected:
        fail(
            f"{node.get('id')}: state is UNREFERENCED_TOKEN_VAZIO but symbols appear in runtime/build files: "
            + ", ".join(sorted(set(unexpected)))
        )


def validate_scaffolding_contract() -> None:
    path = ROOT / "tools/state_geometry_lab/modules/classification.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("default_numbered_module_state") != "SCAFFOLDING":
        fail("numbered state-geometry modules must remain SCAFFOLDING by default")
    if data.get("claim_allowed") is not False:
        fail("state-geometry module classification must keep claim_allowed=false")


def validate_arena_contract() -> None:
    header = text(ROOT / "tools/arena/raf_arena_format.h")
    required = [
        "RAF_RECORD_V1_SIZE 64U",
        "RAF_RECORD_V2_SIZE 80U",
        "sizeof(RafRecordV1) == RAF_RECORD_V1_SIZE",
        "sizeof(RafRecordV2) == RAF_RECORD_V2_SIZE",
    ]
    for marker in required:
        if marker not in header:
            fail(f"arena format contract missing marker {marker!r}")


def main() -> int:
    if not CONTRACT.is_file():
        fail(f"contract missing: {CONTRACT.relative_to(ROOT)}")
    data = json.loads(CONTRACT.read_text(encoding="utf-8"))
    if data.get("claim_allowed") is not False:
        fail("top-level claim_allowed must stay false before device/reproduce receipts")
    if data.get("device_runtime_verified") is not False:
        fail("device_runtime_verified must remain false in source-connectivity contract")

    nodes = data.get("nodes", [])
    if not isinstance(nodes, list) or not nodes:
        fail("connectivity contract has no nodes")

    seen: set[str] = set()
    for node in nodes:
        node_id = str(node.get("id", ""))
        if not node_id or node_id in seen:
            fail(f"invalid/duplicate node id: {node_id!r}")
        seen.add(node_id)
        if node.get("claim_allowed") is not False:
            fail(f"{node_id}: claim_allowed must remain false at this gate")
        validate_markers(node)
        if node.get("state") == "UNREFERENCED_TOKEN_VAZIO":
            validate_unreferenced(node)

    validate_scaffolding_contract()
    validate_arena_contract()
    print(f"PASS: tools-runtime connectivity contract validated ({len(nodes)} nodes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
