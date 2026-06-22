#!/usr/bin/env python3
import importlib.util
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD = ROOT / "tools/state_geometry_lab/py/state_geometry_lab.py"
spec = importlib.util.spec_from_file_location("state_geometry_lab", MOD)
sgl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sgl)


def test_q16_constant_rounds_to_sqrt3_over_2():
    assert sgl.Q16_SQRT3_OVER_2 == round((math.sqrt(3) / 2) * sgl.Q16_SCALE)


def test_kernel_cycles_and_limits():
    payload = sgl.sqrt3_2_kernel(samples=42)
    assert payload["h_q16"] == 56756
    assert math.isclose(payload["half_life_cycles"], 4.8188416793064205, rel_tol=0, abs_tol=1e-12)
    assert math.isclose(payload["tenth_life_cycles"], 16.007845559302183, rel_tol=0, abs_tol=1e-12)
    assert math.isclose(payload["infinite_sum"], 7.464101615137759, rel_tol=0, abs_tol=1e-12)
    assert payload["decay_q16"][0] == sgl.Q16_SCALE
    assert all(a >= b for a, b in zip(payload["decay_q16"], payload["decay_q16"][1:]))


def test_kernel_falsification_guards_are_explicit():
    text = "\n".join(sgl.sqrt3_2_kernel()["falsification"])
    assert "1σ" in text
    assert "packing density" in text or "empacotamento" in text
    assert "Attractor #22" in text


def test_cosmology_pivot_is_diagnostic_not_constant_claim():
    payload = sgl.sqrt3_2_kernel(samples=4)
    pivot = payload["cosmology_pivot"]
    assert math.isclose(pivot["a_h"], math.sqrt(3) / 2, rel_tol=0, abs_tol=1e-15)
    assert math.isclose(pivot["z_h"], (2 / math.sqrt(3)) - 1, rel_tol=0, abs_tol=1e-15)
    assert "not an established cosmological constant" in pivot["role"]
    assert pivot["comparators"] == ["flat_lcdm", "cpl", "rll_rafaelia"]


def test_cosmology_formula_flat_lcdm_at_pivot():
    value = sgl.FORMULAS["sqrt3_2_flat_lcdm_E_at_pivot"]["calc"]({"omega_m": 0.315})
    expected = math.sqrt(0.315 / ((math.sqrt(3) / 2) ** 3) + 0.685)
    assert math.isclose(value, expected, rel_tol=0, abs_tol=1e-15)
