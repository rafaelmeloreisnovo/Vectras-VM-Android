#!/usr/bin/env python3
"""Fail-closed audit for RAFAELIA expression -> Q16.16 constants."""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HEADER = ROOT / "engine/rmr/include/rafaelia_formulas_core.h"
CORE = ROOT / "engine/rmr/src/rafaelia_formulas_core.c"
FABRIC = ROOT / "engine/rmr/src/rmr_math_fabric.c"


def q16_nearest(value: float) -> int:
    if value < 0:
        raise ValueError("q16_nearest expects a non-negative magnitude")
    return int(math.floor(value * 65536.0 + 0.5))


def macro(text: str, name: str) -> int:
    m = re.search(rf"^#define\s+{re.escape(name)}\s+(\d+)u?\b", text, re.MULTILINE)
    if not m:
        raise ValueError(f"missing macro {name}")
    return int(m.group(1))


def assignment(text: str, field: str) -> int:
    m = re.search(rf"out->{re.escape(field)}\s*=\s*(\d+)u?\s*;", text)
    if not m:
        raise ValueError(f"missing assignment {field}")
    return int(m.group(1))


def main() -> int:
    h = HEADER.read_text(encoding="utf-8")
    c = CORE.read_text(encoding="utf-8")
    f = FABRIC.read_text(encoding="utf-8")

    phi = (1.0 + math.sqrt(5.0)) / 2.0
    spiral = math.sqrt(3.0) / 2.0
    expected = {
        "spiral_q16": q16_nearest(spiral),
        "phi_q16": q16_nearest(phi),
        "pi_q16": q16_nearest(math.pi),
        "spiral_pi_phi_q16": q16_nearest(spiral ** (math.pi * phi)),
        "theta_999_sin_pi_q16": q16_nearest(abs(math.pi * math.sin(math.radians(999.0)))),
    }

    observed = {
        "header.spiral_q16": macro(h, "RAF_SPIRAL_Q16"),
        "header.phi_q16": macro(h, "RAF_PHI_Q16"),
        "header.pi_q16": macro(h, "RAF_PI_Q16"),
        "header.spiral_pi_phi_q16": macro(h, "RAF_SPIRAL_PI_PHI_Q16"),
        "core.theta_999_sin_pi_q16": macro(c, "SIN_THETA999_PI_MAG_Q16"),
        "fabric.spiral_q16": assignment(f, "spiral_q16"),
        "fabric.phi_q16": assignment(f, "phi_q16"),
        "fabric.pi_q16": assignment(f, "pi_q16"),
        "fabric.spiral_pi_phi_q16": assignment(f, "spiral_pi_phi_q16"),
        "fabric.theta_999_sin_pi_q16": assignment(f, "theta_999_sin_pi_q16"),
    }

    mapping = {
        "header.spiral_q16": "spiral_q16",
        "header.phi_q16": "phi_q16",
        "header.pi_q16": "pi_q16",
        "header.spiral_pi_phi_q16": "spiral_pi_phi_q16",
        "core.theta_999_sin_pi_q16": "theta_999_sin_pi_q16",
        "fabric.spiral_q16": "spiral_q16",
        "fabric.phi_q16": "phi_q16",
        "fabric.pi_q16": "pi_q16",
        "fabric.spiral_pi_phi_q16": "spiral_pi_phi_q16",
        "fabric.theta_999_sin_pi_q16": "theta_999_sin_pi_q16",
    }

    mismatches = []
    for key, actual in observed.items():
        target = expected[mapping[key]]
        if actual != target:
            mismatches.append({"surface": key, "observed": actual, "expected": target})

    receipt = {
        "schema": "RAFAELIA_FORMULA_CONSTANT_PARITY_RECEIPT_V1",
        "state": "PASS" if not mismatches else "FAIL",
        "claim_allowed": False,
        "q16_rounding": "nearest_half_up_for_nonnegative_magnitude",
        "expressions": {
            "spiral": "sqrt(3)/2",
            "spiral_pi_phi": "(sqrt(3)/2)^(pi*phi)",
            "theta_999_sin_pi_magnitude": "abs(pi*sin(radians(999)))",
        },
        "expected": expected,
        "observed": observed,
        "mismatches": mismatches,
        "boundary": "Numerical representation parity only; no physical/scientific/runtime claim.",
    }
    print(json.dumps(receipt, ensure_ascii=False, indent=2))
    return 0 if not mismatches else 1


if __name__ == "__main__":
    raise SystemExit(main())
