#!/usr/bin/env python3
"""Index and optionally extract compilerlowFala seed blocks.

The historical monolith remains the source of record. This adapter turns its
heredoc seed functions into deterministic, hash-addressed records without
executing generated code. Incomplete historical blocks are reported as PARTIAL,
not silently discarded and not promoted to runtime proof.
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

DECLARATION_PATTERN = re.compile(r"(?m)^(?P<name>seed_[A-Za-z0-9_]+)\(\)\s*\{")
SEED_PATTERN = re.compile(
    r"(?ms)^(?P<name>seed_[A-Za-z0-9_]+)\(\)\s*\{\s*"
    r"cat\s*<<\s*['\"]?(?P<delimiter>[A-Za-z_][A-Za-z0-9_]*)['\"]?\s*\n"
    r"(?P<body>.*?)^(?P=delimiter)\s*\n\}"
)
NAME_PATTERN = re.compile(r"^seed_(?P<family>S\d{2})_(?P<variant>V\d+)_(?P<label>[A-Za-z0-9_]+)$")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def declared_seed_names(text: str) -> list[str]:
    return [match.group("name") for match in DECLARATION_PATTERN.finditer(text)]


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


def build_index(
    source: Path,
    required_count: int | None = None,
    strict_count: bool = False,
) -> tuple[dict[str, Any], int]:
    raw = source.read_bytes()
    text = raw.decode("utf-8", errors="strict")
    declarations = declared_seed_names(text)
    records = parse_seed_blocks(text)
    complete_names = [record["name"] for record in records]
    declaration_duplicates = sorted(name for name, count in Counter(declarations).items() if count > 1)
    complete_duplicates = sorted(name for name, count in Counter(complete_names).items() if count > 1)
    unclassified = sorted(name for name in declarations if NAME_PATTERN.match(name) is None)
    unterminated = sorted(set(declarations) - set(complete_names))
    families: dict[str, list[str]] = defaultdict(list)
    for record in records:
        families[record["family"]].append(record["name"])

    failures: list[str] = []
    gaps: list[str] = []
    if not declarations:
        failures.append("no seed function declarations found")
    if declaration_duplicates:
        failures.append(f"duplicate seed declarations: {declaration_duplicates}")
    if complete_duplicates:
        failures.append(f"duplicate complete seed blocks: {complete_duplicates}")
    if unclassified:
        failures.append(f"unclassified seed declarations: {unclassified}")
    if unterminated:
        gaps.append(f"unterminated seed declarations: {unterminated}")
    if required_count is not None and len(declarations) != required_count:
        gaps.append(f"declared seed count {len(declarations)} differs from required {required_count}")
    if required_count is not None and len(records) != required_count:
        gaps.append(f"complete seed count {len(records)} differs from required {required_count}")

    if failures:
        state = "FAIL"
    elif gaps:
        state = "PARTIAL"
    else:
        state = "PASS"

    clean_records = [{key: value for key, value in record.items() if key != "_body"} for record in records]
    report = {
        "schema_version": "1.1.0",
        "source_path": source.as_posix(),
        "source_size_bytes": len(raw),
        "source_sha256": sha256_bytes(raw),
        "required_seed_count": required_count if required_count is not None else "TOKEN_VAZIO",
        "declared_seed_count": len(declarations),
        "complete_seed_count": len(records),
        "unterminated_seed_names": unterminated,
        "families": {key: sorted(value) for key, value in sorted(families.items())},
        "duplicate_declarations": declaration_duplicates,
        "duplicate_complete_blocks": complete_duplicates,
        "unclassified_names": unclassified,
        "state": state,
        "claim_allowed": False,
        "failures": failures,
        "gaps": gaps,
        "seeds": clean_records,
    }
    status = 1 if failures or (strict_count and gaps) else 0
    return report, status


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
    parser.add_argument("--strict-count", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        raw_text = args.source.read_text(encoding="utf-8")
        records = parse_seed_blocks(raw_text)
        report, status = build_index(args.source, args.require_count, args.strict_count)
        if args.extract_dir is not None and not report["failures"]:
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
        "declared_seed_count": report["declared_seed_count"],
        "complete_seed_count": report["complete_seed_count"],
        "claim_allowed": report["claim_allowed"],
    }, ensure_ascii=False))
    return status


if __name__ == "__main__":
    raise SystemExit(main())
