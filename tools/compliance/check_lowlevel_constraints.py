#!/usr/bin/env python3
"""Static guardrail checker for low-level clean-room constraints.

Checks selected low-level folders for patterns that violate project goals:
- dynamic allocation (malloc/calloc/realloc/free/new/delete)
- GC references
- high-level stdio/exception-heavy APIs in hot/low-level paths
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SCAN_DIRS = [
    ROOT / "engine/rmr",
    ROOT / "Rafaelia",
    ROOT / "bug/core",
]
EXTS = {".c", ".h", ".S", ".s", ".cpp", ".hpp"}

RULES = {
    "heap_api": re.compile(r"\b(malloc|calloc|realloc|free|\bnew\b|\bdelete\b)\b"),
    "gc_term": re.compile(r"\b(garbage\s*collect|gc\b|jni\s*gc)\b", re.IGNORECASE),
    "stdio_hotpath": re.compile(r"\b(printf|fprintf|snprintf|puts)\b"),
}

ALLOW_COMMENT = "LOWLEVEL_ALLOW"

def iter_files():
    for base in SCAN_DIRS:
        if not base.exists():
            continue
        for p in base.rglob("*"):
            if p.is_file() and p.suffix in EXTS:
                yield p


def main() -> int:
    violations = []
    for path in iter_files():
        rel = path.relative_to(ROOT)
        for idx, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), start=1):
            if ALLOW_COMMENT in line:
                continue
            scan = line.split("//", 1)[0]
            scan = scan.split("/*", 1)[0]
            if not scan.strip():
                continue
            for rule_name, pat in RULES.items():
                if pat.search(scan):
                    violations.append((str(rel), idx, rule_name, line.strip()))

    if violations:
        print("LOWLEVEL CONSTRAINT VIOLATIONS:")
        for file, line, rule, snippet in violations:
            print(f"- {file}:{line} [{rule}] {snippet}")
        print("\nTo allow intentional usage, annotate the line with LOWLEVEL_ALLOW and rationale.")
        return 1

    print("OK: low-level constraints passed (no forbidden patterns found).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
