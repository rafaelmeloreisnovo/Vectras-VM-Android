#!/usr/bin/env python3
import argparse
import itertools
import json
import math
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

PHI = (1 + 5 ** 0.5) / 2
SQRT3_OVER_2 = math.sqrt(3.0) / 2.0
Q16_SCALE = 65536
Q16_SQRT3_OVER_2 = 0xDDB4
FNV64_OFFSET = 0xCBF29CE484222325
FNV64_PRIME = 0x100000001B3


METHODS = [
    "load_seed_digits", "fibonacci_variant_patterns", "prime_fibonacci_graph", "modular_tensor",
    "coexistence_matrices", "phi_pi_index_field", "poincare_sphere_sections", "equilateral_height",
    "poincare_ratio_field", "toroidal_map", "lateral_geometry_metrics", "attractor_field",
    "mandelbrot_escape", "julia_escape", "fractal_spectrum_72", "multilevel_permutations",
    "random_permutations_72", "rgb_cmyb_interpolate", "angular_moments", "polynomial_square_borrow",
    "spiral_matrix_cycles", "inverse_antiderivative_stack", "spectral_64bit_signature", "base_projection",
    "rafaelia_formula_catalog", "rafaelia_toroidal_map7", "rafaelia_triangular_core",
    "grassberger_procaccia_probe", "language_viscosity_metrics", "quantum_link_hamiltonian", "sqrt3_2_kernel",
]
# Backwards-compatible alias used by older scripts.
METHODS_21 = METHODS

FORMULAS = {
    "q16_sqrt3_2": {
        "expr": "Q16_SQRT3_2 = 0xDDB4 = 56756/65536 ≈ 0.86603",
        "params": [],
        "desc": "Contração universal sqrt(3)/2 em Q16.",
        "calc": lambda _: 56756 / 65536,
    },
    "lyapunov_torus": {
        "expr": "λ = ln(sqrt(3)/2)",
        "params": [],
        "desc": "Expoente de Lyapunov para estado TORUS.",
        "calc": lambda _: math.log(math.sqrt(3) / 2),
    },
    "fibonacci_rafael_fixed_point": {
        "expr": "F* = (π·sin(81°))/(1 - sqrt(3)/2)",
        "params": [],
        "desc": "Ponto fixo Fibonacci-Rafael.",
        "calc": lambda _: (math.pi * math.sin(math.radians(81))) / (1 - math.sqrt(3) / 2),
    },
    "equilateral_height": {
        "expr": "h = (sqrt(3)/2)·lado",
        "params": ["side"],
        "desc": "Altura de triângulo equilátero.",
        "calc": lambda p: (math.sqrt(3) / 2) * float(p.get("side", 0)),
    },
    "toroidal_capacity_bits": {
        "expr": "I_Tn = n · log2(65536²) = n · 32",
        "params": ["n"],
        "desc": "Capacidade toroidal em bits no espaço Q16.",
        "calc": lambda p: float(p.get("n", 0)) * 32,
    },
    "semantic_capacity_bits": {
        "expr": "I_sem(L,k) = k · log2(V)",
        "params": ["k", "V"],
        "desc": "Capacidade semântica por vocabulário V e k palavras.",
        "calc": lambda p: float(p.get("k", 0)) * math.log2(float(p.get("V", 1))),
    },
    "n_ball_volume": {
        "expr": "V_n(r) = π^(n/2) / Γ(n/2+1) · r^n",
        "params": ["n", "r"],
        "desc": "Volume da n-bola.",
        "calc": lambda p: (math.pi ** (float(p.get("n", 0)) / 2) / math.gamma(float(p.get("n", 0)) / 2 + 1)) * (float(p.get("r", 1)) ** float(p.get("n", 0))),
    },
    "kaplan_yorke_dimension": {
        "expr": "D_KY = 1 + λ+/|λ−|",
        "params": ["lambda_pos", "lambda_neg_abs"],
        "desc": "Dimensão de Kaplan-Yorke.",
        "calc": lambda p: 1 + float(p.get("lambda_pos", 0.05)) / float(p.get("lambda_neg_abs", 0.1438)),
    },
    "attractor_uniform_fraction": {
        "expr": "fração = 1/42",
        "params": [],
        "desc": "Fração uniforme por atrator em T^7 (42 atratores).",
        "calc": lambda _: 1 / 42,
    },
    "correlation_integral": {
        "expr": "C(ε) = #{pares (i,j): |x_i−x_j|<ε}/N²",
        "params": ["pairs_below_eps", "N"],
        "desc": "Integral de correlação Grassberger-Procaccia.",
        "calc": lambda p: float(p.get("pairs_below_eps", 0)) / (float(p.get("N", 1)) ** 2),
    },
    "sqrt3_2_half_life_cycles": {
        "expr": "n_1/2 = ln(0.5)/ln(sqrt(3)/2)",
        "params": [],
        "desc": "Meia-vida em ciclos do filtro recursivo por sqrt(3)/2.",
        "calc": lambda _: math.log(0.5) / math.log(SQRT3_OVER_2),
    },
    "sqrt3_2_tenth_life_cycles": {
        "expr": "n_10% = ln(0.1)/ln(sqrt(3)/2)",
        "params": [],
        "desc": "Ciclos para o filtro cair a 10%.",
        "calc": lambda _: math.log(0.1) / math.log(SQRT3_OVER_2),
    },
    "sqrt3_2_infinite_sum": {
        "expr": "S = 1/(1 - sqrt(3)/2)",
        "params": [],
        "desc": "Limite da soma geométrica do kernel de decaimento.",
        "calc": lambda _: 1 / (1 - SQRT3_OVER_2),
    },
    "sqrt3_2_reverse_gain": {
        "expr": "g = 2/sqrt(3)",
        "params": [],
        "desc": "Ganho reverso para reconstrução/expansão controlada.",
        "calc": lambda _: 2 / math.sqrt(3),
    },
    "sqrt3_2_cosmology_pivot_z": {
        "expr": "z_h = 1/(sqrt(3)/2) - 1 = 2/sqrt(3) - 1",
        "params": [],
        "desc": "Redshift diagnóstico quando o fator de escala a_h é sqrt(3)/2.",
        "calc": lambda _: (2 / math.sqrt(3)) - 1,
    },
    "sqrt3_2_flat_lcdm_E_at_pivot": {
        "expr": "E(a_h)=sqrt(Ωm/a_h^3 + 1 - Ωm)",
        "params": ["omega_m"],
        "desc": "H(a_h)/H0 em ΛCDM plano para comparar com CPL/RLL sem declarar nova constante cosmológica.",
        "calc": lambda p: math.sqrt(float(p.get("omega_m", 0.315)) / (SQRT3_OVER_2 ** 3) + (1 - float(p.get("omega_m", 0.315)))),
    },
}


def is_prime(n: int) -> bool:
    if n < 2:
        return False
    if n % 2 == 0:
        return n == 2
    d = 3
    while d * d <= n:
        if n % d == 0:
            return False
        d += 2
    return True

# (restante mantido)
def load_seed_digits(path: str) -> List[int]:
    p = Path(path)
    if not p.exists():
        return [0]
    digits: List[int] = []
    for ch in p.read_text(encoding="utf-8"):
        if ch.isdigit():
            digits.append(int(ch))
    return digits or [0]

def fibonacci_variant_patterns(n: int) -> Dict[str, List[int]]:
    seed_map = {"0001123": [0, 0, 0, 1, 1, 2, 3],"001123": [0, 0, 1, 1, 2, 3],"01123": [0, 1, 1, 2, 3],"0123": [0, 1, 2, 3],"123": [1, 2, 3]}
    out = {}
    for name, seed in seed_map.items():
        seq = seed[:]
        while len(seq) < n:
            seq.append(seq[-1] + seq[-2])
        out[name] = seq[:n]
    return out

def prime_fibonacci_graph(n: int) -> Dict[int, List[int]]:
    fib = [0, 1]
    while len(fib) < n:
        fib.append(fib[-1] + fib[-2])
    prime_idx = [i for i, v in enumerate(fib) if is_prime(v)]
    graph = {i: [] for i in prime_idx}
    for a, b in zip(prime_idx, prime_idx[1:]):
        graph[a].append(b)
    return graph

def modular_tensor(values: Sequence[int], mods: Sequence[int]) -> List[List[int]]:
    return [[v % m if m else v for m in mods] for v in values]

def coexistence_matrices(values: Sequence[int], mods: Sequence[int]) -> Dict[int, List[List[int]]]:
    mats: Dict[int, List[List[int]]] = {}
    for m in mods:
        if m <= 0:
            continue
        row = [v % m for v in values]
        mats[m] = [[(row[i] + row[j]) % m for j in range(len(row))] for i in range(len(row))]
    return mats

def phi_pi_index_field(values: Sequence[int]) -> List[Tuple[float, float]]:
    return [((PHI * v) / (i + 1), (math.pi * (i + 1)) / (v + 1)) for i, v in enumerate(values)]

def poincare_sphere_sections(mods: Sequence[int], sections: int = 42) -> List[Tuple[int, float, float]]:
    out: List[Tuple[int, float, float]] = []
    for i in range(sections):
        m = mods[i % len(mods)]
        theta = (2 * math.pi * i) / sections
        phi = math.acos(max(-1.0, min(1.0, 1 - 2 * ((i + 1) / (sections + 1)))))
        angular_momentum = (m * math.sin(phi) * math.cos(theta))
        out.append((m, theta, angular_momentum))
    return out

def sqrt3_2_kernel(samples: int = 42, entry_q16: int = Q16_SCALE) -> Dict[str, object]:
    """Return the measurable sqrt(3)/2 kernel contract without heap-heavy state.

    The Python lab emits lists for inspection; native hot paths should keep the
    same Q16 constants and stream values instead of allocating buffers.
    """
    samples = max(1, min(samples, 256))
    q = int(entry_q16)
    decay: List[int] = []
    for _ in range(samples):
        decay.append(q)
        q = (q * Q16_SQRT3_OVER_2) >> 16
    return {
        "kernel": "RAFAELIA_H_KERNEL",
        "h_float": SQRT3_OVER_2,
        "h_q16": Q16_SQRT3_OVER_2,
        "reverse_gain": 2 / math.sqrt(3),
        "half_life_cycles": math.log(0.5) / math.log(SQRT3_OVER_2),
        "tenth_life_cycles": math.log(0.1) / math.log(SQRT3_OVER_2),
        "hundredth_life_cycles": math.log(0.01) / math.log(SQRT3_OVER_2),
        "infinite_sum": 1 / (1 - SQRT3_OVER_2),
        "cosmology_pivot": {
            "a_h": SQRT3_OVER_2,
            "z_h": (2 / math.sqrt(3)) - 1,
            "role": "diagnostic pivot, not an established cosmological constant",
            "comparators": ["flat_lcdm", "cpl", "rll_rafaelia"],
        },
        "procedures": [
            "Implement sqrt3_2_hex_grid with x=m+n/2 and y=n*h for maps/simulation slices.",
            "Implement recursive stability filter S_next=X_n+h*S_n for noise, memory and residuals.",
            "Use a_h=sqrt(3)/2 and z_h=2/sqrt(3)-1 as cosmology diagnostic pivot.",
            "Compare H_LCDM(z_h), H_CPL(z_h), and H_RLL(z_h); report delta_chi2, not claims.",
            "Use h as Euclidean reference for curved-space triangle-height deviation tests.",
        ],
        "modules": [
            "geometry_hex_grid",
            "recursive_decay_filter",
            "signal_6fold_projection",
            "regression_angle_map",
            "risk_buffer_smoother",
        ],
        "falsification": [
            "Do not map sqrt(3)/2 directly to 1σ or z-score without angular projection evidence.",
            "Do not call sqrt(3)/2 the 2D circle-packing density; use pi/(2*sqrt(3)) for that metric.",
            "Attractor #22 remains VOID/paradox-flagged; this kernel does not patch attractor topology.",
        ],
        "decay_q16": decay,
    }

def spectral_64bit_signature(values: Sequence[int]) -> int:
    mask = (1 << 64) - 1
    sig = 0x9E3779B97F4A7C15
    for v in values:
        sig ^= (v + 0x9E3779B97F4A7C15 + ((sig << 6) & mask) + (sig >> 2)) & mask
    return sig & mask

def demo(max_n: int, mods: List[int], seed_path: str) -> None:
    values = list(range(0, min(max_n, 1196) + 1, 3))
    print("tensor_rows=", len(modular_tensor(values[:64], mods)))

def print_help_header() -> None:
    print("State Geometry Lab CLI")
    print("--formula list : lista fórmulas")
    print("--formula <id> --params 'k=7,V=170000' : calcula fórmula")
    print("--method list : lista métodos")

def parse_params(raw: str) -> Dict[str, float]:
    out: Dict[str, float] = {}
    if not raw.strip():
        return out
    for part in raw.split(","):
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        out[k.strip()] = float(v.strip())
    return out

def main() -> None:
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument("--max", type=int, default=144000)
    ap.add_argument("--mods", nargs="*", type=int, default=[64, 20, 18, 13, 12, 10, 14, 5, 4, 3, 2, 1, 0])
    ap.add_argument("--seed", type=str, default="rmr/rrr/rafaelia_semente.txt")
    ap.add_argument("--demo", action="store_true")
    ap.add_argument("--method", type=str, default="")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--formula", type=str, default="")
    ap.add_argument("--params", type=str, default="")
    ns = ap.parse_args()

    if not any([ns.demo, ns.method, ns.formula]):
        print_help_header()
        return

    if ns.formula:
        if ns.formula == "list":
            rows = [{"id": k, "expr": v["expr"], "params": v["params"], "desc": v["desc"]} for k, v in FORMULAS.items()]
            print(json.dumps(rows, ensure_ascii=False, indent=2) if ns.json else "\n".join([f"{r['id']}: {r['expr']} | params={r['params']}" for r in rows]))
            return
        if ns.formula not in FORMULAS:
            raise SystemExit(f"unknown formula: {ns.formula}")
        f = FORMULAS[ns.formula]
        params = parse_params(ns.params)
        value = f["calc"](params)
        payload = {"formula": ns.formula, "expr": f["expr"], "params_expected": f["params"], "params_received": params, "value": value}
        print(json.dumps(payload, ensure_ascii=False, indent=2) if ns.json else payload)
        return

    if ns.method:
        if ns.method not in METHODS and ns.method != "list":
            raise SystemExit(f"unknown method: {ns.method}")
        if ns.method == "sqrt3_2_kernel":
            payload = sqrt3_2_kernel(samples=42)
            print(json.dumps(payload, ensure_ascii=False, indent=2) if ns.json else payload)
            return
        print(json.dumps(METHODS_21) if ns.json else "\n".join(METHODS_21))
        return

    if ns.demo:
        demo(ns.max, ns.mods, ns.seed)


if __name__ == "__main__":
    main()
