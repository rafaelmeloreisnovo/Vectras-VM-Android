#!/usr/bin/env python3
"""Fail-closed guard for ProfessionalTools benchmark statistics.

This checker intentionally does not prove statistical validity. It blocks a small set of
known-invalid promotion patterns that conflict with INDUSTRIAL_BENCHMARK_METHODS_V1:

1. pooling rawValue from heterogeneous BenchmarkResult entries into one global series;
2. treating mean == 0 as 100% reproducibility;
3. using the legacy mixed-series reproducibilityScore to promote Industry/Academic/
   Scientific grades.

Passing this guard is necessary, never sufficient, for scientific/industrial claims.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "app/src/main/java/com/vectras/vm/tools/ProfessionalToolsActivity.java"
METHOD = ROOT / "docs/benchmark/INDUSTRIAL_BENCHMARK_METHODS_V1.md"


def main() -> int:
    errors: list[dict[str, str]] = []

    if not TARGET.is_file():
        errors.append({"code": "TARGET_MISSING", "detail": str(TARGET)})
        return emit(errors)
    if not METHOD.is_file():
        errors.append({"code": "METHODOLOGY_CONTRACT_MISSING", "detail": str(METHOD)})
        return emit(errors)

    src = TARGET.read_text(encoding="utf-8")
    methodology = METHOD.read_text(encoding="utf-8")

    if "one homogeneous" not in methodology.lower() and "homogeneous" not in methodology.lower():
        errors.append({
            "code": "METHODOLOGY_HOMOGENEITY_INVARIANT_NOT_FOUND",
            "detail": "Expected homogeneous repeated-series invariant in methodology contract",
        })

    mixed_raw_pattern = re.compile(
        r"Arrays\.stream\(results\).*?mapToLong\(VectraBenchmark\.BenchmarkResult::rawValue\).*?toArray\(\)",
        re.DOTALL,
    )
    mixed_matches = list(mixed_raw_pattern.finditer(src))
    if mixed_matches:
        errors.append({
            "code": "HETEROGENEOUS_RAWVALUE_POOLING_ACTIVE",
            "detail": f"Found {len(mixed_matches)} Arrays.stream(results)->rawValue pooling path(s)",
        })

    zero_perfect_patterns = [
        r"if\s*\(\s*mean\s*==\s*0(?:\.0+)?\s*\)\s*return\s+100",
        r"mean\s*==\s*0(?:\.0+)?\s*\?\s*100",
    ]
    if any(re.search(p, src) for p in zero_perfect_patterns):
        errors.append({
            "code": "ZERO_MEAN_PROMOTED_TO_PERFECT_REPRODUCIBILITY",
            "detail": "A zero mean cannot by itself establish reproducibility",
        })

    grade_links = re.findall(r"report\.reproducibilityScore\s*>=\s*(70|85|95)", src)
    if grade_links:
        errors.append({
            "code": "LEGACY_REPRODUCIBILITY_SCORE_DRIVES_GRADE",
            "detail": "Legacy reproducibilityScore is still a direct grade predicate: " + ",".join(grade_links),
        })

    # The corrected path must be source-visible before this gate can pass.
    if "IndustrialStatistics" not in src:
        errors.append({
            "code": "HOMOGENEOUS_STATISTICS_NOT_WIRED",
            "detail": "ProfessionalToolsActivity does not reference IndustrialStatistics",
        })

    return emit(errors)


def emit(errors: list[dict[str, str]]) -> int:
    payload = {
        "schema": "VECTRAS_PROFESSIONAL_STATISTICS_CONTRACT_GATE_V1",
        "target": str(TARGET.relative_to(ROOT)) if TARGET.is_file() else str(TARGET),
        "claim_allowed": False,
        "status": "FAIL" if errors else "PASS_LIMITED",
        "errors": errors,
        "boundary": (
            "PASS_LIMITED means only that known mixed-metric promotion anti-patterns were not found. "
            "It is not a benchmark, reproducibility, certification, or scientific-grade receipt."
        ),
    }
    print(json.dumps(payload, indent=2, sort_keys=True))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
