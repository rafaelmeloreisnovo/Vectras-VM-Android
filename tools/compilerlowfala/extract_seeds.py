#!/usr/bin/env python3
"""Index and optionally extract compilerlowFala seed blocks.

The historical monolith remains the source of record. This adapter turns its
heredoc seed functions into deterministic, hash-addressed records without
executing generated code.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

SEED_PATTERN = re.compile(
    r"(?ms)^(?P<name>seed_[A-Za-z0-9_]+)\(\)\s*\{\s*"
    r"cat\s*<<\s*['\"]?(?P<delimiter>[A-Za-z_][A-Za-z0-9_]*)['\"]?\s*\n"
    r"(?P<body>.*?)^(?P=delimiter)\s*\n\}"
)
NAME_PATTERN = re.compile(r"^seed_(?P<family>S\d{2})_(?P<variant>V\d+)_(?P<label>[A-Za-z0-9_]+)$")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_seed_blocks(text: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for match in SEED_PATTERN.finditer(text):
        name = match.group("name")
        body = match.group("body")
        body_bytes = body.encode("utf-8")
        parsed = NAME_PATTERN.match(name)
        records.append(
            {
                "name": name,
                "family": parsed.group("family") if parsed else "UNCLASSIFIED",
                "variant": parsed.group("variant") if parsed else "UNCLASSIFIED",
                "label": parsed.group("label") if parsed else name,
                "delimiter": match.group("delimiter"),
                "line_start": text.count("\n", 0, match.start()) + 1,
                "body_lines": body.count("\n") + (1 if body else 0),
                "body_bytes": len(body_bytes),
                "body_sha256": sha256_bytes(body_bytes),
                "_body": body,
            }
        )
    return records


def build_index(source: Path, required_count: int | None = None) -> tuple[dict[str, Any], int]:
    raw = source.read_bytes()
    text = raw.decode("utf-8", errors="strict")
    records = parse_seed_blocks(text)
    names = [record["name"] for record in records]
    duplicates = sorted(name for name, count in Counter(names).items() if count > 1)
    unclassified = sorted(record["name"] for record in records if record["family"] == "UNCLASSIFIED")
    families: dict[str, list[str]] = defaultdict(list)
    for record in records:
        families[record["family"]].append(record["name"])
    failures: list[str] = []
    if not records:
        failures.append("no seed heredoc functions found")
    if duplicates:
        failures.append(f"duplicate seed names: {duplicates}")
    if unclassified:
        failures.append(f"unclassified seed names: {unclassified}")
    if required_count is not None and len(records) != required_count:
        failures.append(f"seed count {len(records)} differs from required {required_count}")
    clean_records = [{key: value for key, value in record.items() if key != "_body"} for record in records]
    report = {
        "schema_version": "1.0.0",
        "source_path": source.as_posix(),
        "source_size_bytes": len(raw),
        "source_sha256": sha256_bytes(raw),
        "required_seed_count": required_count if required_count is not None else "TOKEN_VAZIO",
        "seed_count": len(records),
        "families": {key: sorted(value) for key, value in sorted(families.items())},
        "duplicate_names": duplicates,
        "unclassified_names": unclassified,
        "state": "PASS" if not failures else "FAIL",
        "claim_allowed": False,
        "failures": failures,
        "seeds": clean_records,
    }
    return report, 0 if not failures else 1


def extract_blocks(destination: Path, records: list[dict[str, Any]]) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    for record in records:
        target = destination / f"{record['name']}.seed"
        target.write_text(record["_body"], encoding="utf-8")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("Incluir/compiladorlowFala.txt"))
    parser.add_argument("--output", type=Path, default=Path("reports/compilerlowfala_seed_index.json"))
    parser.add_argument("--extract-dir", type=Path)
    parser.add_argument("--require-count", type=int)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        raw_text = args.source.read_text(encoding="utf-8")
        records = parse_seed_blocks(raw_text)
        report, status = build_index(args.source, args.require_count)
        if args.extract_dir is not None and status == 0:
            extract_blocks(args.extract_dir, records)
            report["extracted_to"] = args.extract_dir.as_posix()
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"compilerlowFala seed index error: {exc}", file=sys.stderr)
        return 2
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": args.output.as_posix(),
        "state": report["state"],
        "seed_count": report["seed_count"],
        "claim_allowed": report["claim_allowed"],
    }, ensure_ascii=False))
    return status


if __name__ == "__main__":
    raise SystemExit(main())
