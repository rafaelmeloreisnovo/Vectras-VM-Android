#!/usr/bin/env python3
"""Motor mínimo executável para a invariante geométrica coerente (T7)."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import zlib
from typing import Dict, Iterable, List, Tuple

ALPHA = 0.25
FNV64_PRIME = 0x100000001B3
FNV64_OFFSET = 0xCBF29CE484222325
ATTRACTOR_COUNT = 42


def clamp01(x: float) -> float:
    if x < 0.0:
        return 0.0
    if x > 1.0:
        return 1.0
    return x


def wrap01(x: float) -> float:
    return x - math.floor(x)


def entropy_milli(data: bytes) -> float:
    if len(data) <= 1:
        return 0.0
    hist = [0] * 256
    transitions = 0
    prev = data[0]
    hist[prev] += 1
    for b in data[1:]:
        hist[b] += 1
        if b != prev:
            transitions += 1
        prev = b
    unique = sum(1 for c in hist if c > 0)
    return (unique * 6000.0 / 256.0) + (transitions * 2000.0 / (len(data) - 1))


def coherence_update(c_prev: float, c_in: float, alpha: float = ALPHA) -> float:
    return (1.0 - alpha) * c_prev + alpha * c_in


def entropy_update(h_prev: float, h_in: float, alpha: float = ALPHA) -> float:
    return (1.0 - alpha) * h_prev + alpha * h_in


def phi_score(h: float, c: float) -> float:
    return (1.0 - clamp01(h)) * clamp01(c)


def fnv1a64(data: bytes) -> int:
    h = FNV64_OFFSET
    for b in data:
        h ^= b
        h = (h * FNV64_PRIME) & 0xFFFFFFFFFFFFFFFF
    return h


def xor_acc(data: bytes) -> int:
    a = 0
    for b in data:
        a ^= b
    return a


def crc32_u32(data: bytes) -> int:
    return zlib.crc32(data) & 0xFFFFFFFF


def merkle_root_sha256(chunks: Iterable[bytes]) -> str:
    nodes = [hashlib.sha256(c).digest() for c in chunks]
    if not nodes:
        return hashlib.sha256(b"").hexdigest()
    while len(nodes) > 1:
        nxt = []
        for i in range(0, len(nodes), 2):
            left = nodes[i]
            right = nodes[i + 1] if i + 1 < len(nodes) else left
            nxt.append(hashlib.sha256(left + right).digest())
        nodes = nxt
    return nodes[0].hex()


def toroidal_map(data: bytes, h_norm: float, fnv64: int, state_code: int) -> Tuple[float, ...]:
    digest = hashlib.sha256(data).digest()
    u = wrap01(sum(data) / max(1, 255.0 * len(data)))
    v = wrap01(entropy_milli(data) / 8000.0)
    psi = wrap01(h_norm)
    chi = wrap01((fnv64 & 0xFFFFFFFF) / 2**32)
    rho = wrap01(((fnv64 >> 32) & 0xFFFFFFFF) / 2**32)
    delta = wrap01(state_code / 256.0)
    sigma = wrap01(int.from_bytes(digest[:2], "big") / 65536.0)
    return (u, v, psi, chi, rho, delta, sigma)


def spectral_link_energy(dtheta: float, dphi: float, alpha: float = ALPHA) -> float:
    return alpha * math.sin(dtheta) * math.cos(dphi)


def attractor_id(s7: Tuple[float, ...], count: int = ATTRACTOR_COUNT) -> int:
    key = "|".join(f"{x:.9f}" for x in s7).encode("utf-8")
    h = hashlib.sha256(key).digest()
    return int.from_bytes(h[:4], "big") % count


def iterate_state(payload: bytes, c_prev: float, h_prev: float, c_in: float, steps: int) -> Tuple[float, float, float]:
    c = c_prev
    h = h_prev
    e_in = clamp01(entropy_milli(payload) / 8000.0)
    for _ in range(max(1, steps)):
        c = clamp01(coherence_update(c, c_in))
        h = clamp01(entropy_update(h, e_in))
    return c, h, phi_score(h, c)


def invariant_state(payload: bytes, c_prev: float, h_prev: float, c_in: float, state_code: int, steps: int = 1) -> Dict[str, object]:
    c_next, h_next, phi = iterate_state(payload, c_prev, h_prev, c_in, steps)
    fnv = fnv1a64(payload)
    root = merkle_root_sha256([payload[i:i + 64] for i in range(0, len(payload), 64)])
    s7 = toroidal_map(payload, h_next, fnv, state_code)
    aid = attractor_id(s7)

    return {
        "alpha": ALPHA,
        "steps": steps,
        "C_next": c_next,
        "H_next": h_next,
        "phi": phi,
        "entropy_milli": entropy_milli(payload),
        "acc_xor": xor_acc(payload),
        "fnv1a64": f"0x{fnv:016x}",
        "crc32": f"0x{crc32_u32(payload):08x}",
        "merkle_root_sha256": root,
        "s7": s7,
        "attractor_id": aid,
        "attractor_count": ATTRACTOR_COUNT,
    }


def main() -> None:
    ap = argparse.ArgumentParser(description="Executa motor mínimo T7 + integridade.")
    ap.add_argument("--text", required=True, help="Texto de entrada")
    ap.add_argument("--c-prev", type=float, default=0.5)
    ap.add_argument("--h-prev", type=float, default=0.5)
    ap.add_argument("--c-in", type=float, default=0.8)
    ap.add_argument("--state", type=int, default=1)
    ap.add_argument("--steps", type=int, default=1)
    args = ap.parse_args()

    result = invariant_state(args.text.encode("utf-8"), args.c_prev, args.h_prev, args.c_in, args.state, args.steps)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
