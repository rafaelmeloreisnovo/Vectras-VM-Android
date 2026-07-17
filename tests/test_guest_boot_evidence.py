from __future__ import annotations

import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = ROOT / "tools" / "validate_guest_boot_evidence.py"
spec = importlib.util.spec_from_file_location("guest_boot", VALIDATOR)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def fixture() -> dict:
    return json.loads((ROOT / "examples" / "guest_boot_evidence.token-vazio.json").read_text())


def test_fixture_valid() -> None:
    assert module.validate(fixture()) == []


def test_false_boot_promotion_rejected() -> None:
    data = fixture()
    data["gates"]["guest_boot"] = "PASS"
    assert module.validate(data)


def test_unverified_disk_mutation_rejected() -> None:
    data = fixture()
    data["safety"]["disk_mutated"] = True
    assert module.validate(data)


def test_missing_gate_rejected() -> None:
    data = fixture()
    del data["gates"]["storage"]
    assert module.validate(data)


def test_claim_promotion_rejected() -> None:
    data = fixture()
    data["claim_allowed"] = True
    assert module.validate(data)
