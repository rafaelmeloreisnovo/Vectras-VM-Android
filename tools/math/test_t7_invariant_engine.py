#!/usr/bin/env python3
from t7_invariant_engine import invariant_state, spectral_link_energy, attractor_id


def test_basic_state_bounds() -> None:
    r = invariant_state(b"abc", c_prev=0.1, h_prev=0.2, c_in=0.9, state_code=7, steps=5)
    assert 0.0 <= r["C_next"] <= 1.0
    assert 0.0 <= r["H_next"] <= 1.0
    assert 0.0 <= r["phi"] <= 1.0
    assert len(r["s7"]) == 7
    for v in r["s7"]:
        assert 0.0 <= v < 1.0


def test_integrity_changes_on_bitflip() -> None:
    a = invariant_state(b"payload-A", 0.5, 0.5, 0.5, 1)
    b = invariant_state(b"payload-B", 0.5, 0.5, 0.5, 1)
    assert a["fnv1a64"] != b["fnv1a64"]
    assert a["crc32"] != b["crc32"]
    assert a["merkle_root_sha256"] != b["merkle_root_sha256"]


def test_link_energy_range() -> None:
    e = spectral_link_energy(0.3, 0.5)
    assert -0.25 <= e <= 0.25


def test_attractor_range_and_stability() -> None:
    r = invariant_state(b"same payload", 0.2, 0.3, 0.9, 5, steps=20)
    aid = r["attractor_id"]
    assert 0 <= aid < 42
    aid2 = attractor_id(tuple(r["s7"]))
    assert aid == aid2


if __name__ == "__main__":
    test_basic_state_bounds()
    test_integrity_changes_on_bitflip()
    test_link_energy_range()
    test_attractor_range_and_stability()
    print("ok")
