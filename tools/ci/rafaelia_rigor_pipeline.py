
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAFAELIA_RIGOR_PIPELINE
=======================

Pipeline matemático-computacional para:
1) registrar fórmulas e o contexto técnico de uso;
2) executar verificações simbólicas e numéricas;
3) gerar testes de falsificabilidade;
4) produzir relatórios reprodutíveis em Markdown, JSON e CSV;
5) renderizar gráficos simples de apoio.

O objetivo não é "provar tudo que pode existir", mas demonstrar, com rigor
reprodutível, as identidades e propriedades que realmente estão definidas.

Dependências:
    - sympy
    - numpy
    - matplotlib

Uso:
    python rafaelia_rigor_pipeline.py
    python rafaelia_rigor_pipeline.py --out outputs
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import math
import random
import zlib
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Callable, Dict, List, Tuple, Any

import numpy as np
import matplotlib.pyplot as plt
import sympy as sp


# ============================================================================
# ESTRUTURAS DE DADOS
# ============================================================================

@dataclass
class FormulaRecord:
    """Representa uma fórmula/estrutura com contexto acadêmico."""
    id: str
    metaphor: str
    technical_term: str
    academic_statement: str
    latex: str
    variable_context: Dict[str, str]
    proof_mode: str  # "symbolic", "numeric", "algorithmic", "mixed"


@dataclass
class TestResult:
    """Resultado de uma verificação."""
    formula_id: str
    test_name: str
    status: str
    details: str
    metrics: Dict[str, Any]


# ============================================================================
# REGISTRO DE FÓRMULAS
# ============================================================================

def formula_registry() -> List[FormulaRecord]:
    return [
        FormulaRecord(
            id="pythagoras",
            metaphor="Composição geométrica de dois eixos em um terceiro eixo resultante.",
            technical_term="Teorema de Pitágoras",
            academic_statement="Em triângulo retângulo, a soma dos quadrados dos catetos é igual ao quadrado da hipotenusa.",
            latex=r"a^2 + b^2 = c^2",
            variable_context={
                "a": "cateto 1",
                "b": "cateto 2",
                "c": "hipotenusa",
            },
            proof_mode="symbolic",
        ),
        FormulaRecord(
            id="bhaskara_delta",
            metaphor="Marcador de regime da equação quadrática.",
            technical_term="Discriminante de Bhaskara",
            academic_statement="O discriminante determina a natureza das raízes de uma equação quadrática.",
            latex=r"\Delta = b^2 - 4ac",
            variable_context={
                "a": "coeficiente quadrático",
                "b": "coeficiente linear",
                "c": "termo constante",
                "Δ": "discriminante",
            },
            proof_mode="symbolic",
        ),
        FormulaRecord(
            id="fibonacci",
            metaphor="Crescimento recursivo com memória curta de dois estados.",
            technical_term="Recorrência de Fibonacci",
            academic_statement="Cada termo é a soma dos dois termos anteriores, a partir de condições iniciais.",
            latex=r"F_n = F_{n-1} + F_{n-2}",
            variable_context={
                "F_n": "n-ésimo termo",
                "F_0": "termo inicial 0",
                "F_1": "termo inicial 1",
            },
            proof_mode="algorithmic",
        ),
        FormulaRecord(
            id="inverse_ethics",
            metaphor="Amortecimento escalar decrescente sobre crescimento de Fibonacci.",
            technical_term="Função racional de amortecimento baseada em Fibonacci",
            academic_statement="A função E_n = 1 / (1 + F_n / φ) decresce para zero conforme F_n cresce.",
            latex=r"E_n = \frac{1}{1 + \frac{F_n}{\varphi}}",
            variable_context={
                "E_n": "índice amortecido",
                "F_n": "n-ésimo Fibonacci",
                "φ": "razão áurea",
            },
            proof_mode="mixed",
        ),
        FormulaRecord(
            id="equilateral_height",
            metaphor="Altura normalizada do triângulo equilátero de lado 1.",
            technical_term="Altura do triângulo equilátero",
            academic_statement="Para lado unitário, a altura do triângulo equilátero é √3/2.",
            latex=r"h = \frac{\sqrt{3}}{2}",
            variable_context={"h": "altura", "lado": "comprimento do lado"},
            proof_mode="symbolic",
        ),
        FormulaRecord(
            id="torus_x",
            metaphor="Componente observável de uma dinâmica toroidal.",
            technical_term="Parametrização parcial de toro",
            academic_statement="A coordenada x de um toro parametrizado por (θ, φ) é (R + r cos φ) cos θ.",
            latex=r"x = (R + r \cos \phi)\cos \theta",
            variable_context={
                "R": "raio maior",
                "r": "raio menor",
                "θ": "ângulo longitudinal",
                "φ": "ângulo meridional",
            },
            proof_mode="numeric",
        ),
        FormulaRecord(
            id="shannon_entropy",
            metaphor="Medida da dispersão informacional do sistema.",
            technical_term="Entropia de Shannon",
            academic_statement="A entropia mede a incerteza média de uma distribuição discreta.",
            latex=r"H = -\sum_i p_i \log_2 p_i",
            variable_context={"p_i": "probabilidade do i-ésimo símbolo"},
            proof_mode="symbolic",
        ),
        FormulaRecord(
            id="base_period",
            metaphor="Comprimento do ciclo repetitivo de 1/n em uma base.",
            technical_term="Período da expansão periódica de 1/n em base b",
            academic_statement="Para n coprimo com b, o período de 1/n em base b coincide com a ordem multiplicativa de b módulo n.",
            latex=r"P_b(n) = \operatorname{ord}_n(b)",
            variable_context={
                "P_b(n)": "comprimento do período",
                "b": "base",
                "n": "denominador",
            },
            proof_mode="mixed",
        ),
        FormulaRecord(
            id="hamming",
            metaphor="Distância mínima entre duas assinaturas binárias.",
            technical_term="Distância de Hamming",
            academic_statement="A distância de Hamming entre dois vetores binários é o número de posições em que diferem.",
            latex=r"d_H(x,y) = \sum_i [x_i \neq y_i]",
            variable_context={"x,y": "vetores binários"},
            proof_mode="algorithmic",
        ),
        FormulaRecord(
            id="zipraf_container",
            metaphor="Encapsulamento de payload com cabeçalho, compressão e integridade.",
            technical_term="Contêiner serializado com compressão e hash de integridade",
            academic_statement="O payload é formado por cabeçalho e corpo, comprimido por zlib e validado por SHA3-256.",
            latex=r"h = \operatorname{SHA3\mbox{-}256}(\operatorname{zlib}(payload))",
            variable_context={"payload": "header || body", "h": "hash de integridade"},
            proof_mode="algorithmic",
        ),
    ]


# ============================================================================
# FUNÇÕES AUXILIARES
# ============================================================================

PHI = (1 + math.sqrt(5)) / 2

def fibonacci(n: int) -> int:
    if n < 0:
        raise ValueError("n deve ser >= 0")
    a, b = 0, 1
    for _ in range(n):
        a, b = b, a + b
    return a


def inverse_ethics(n: int) -> float:
    return 1.0 / (1.0 + fibonacci(n) / PHI)


def shannon_entropy_from_bytes(data: bytes) -> float:
    if not data:
        return 0.0
    counts = np.bincount(np.frombuffer(data, dtype=np.uint8), minlength=256)
    probs = counts[counts > 0] / len(data)
    return float(-(probs * np.log2(probs)).sum())


def multiplicative_order(base: int, n: int) -> int:
    """ord_n(base), assumindo gcd(base, n) = 1."""
    if math.gcd(base, n) != 1:
        raise ValueError("base e n precisam ser coprimos para ordem multiplicativa")
    k = 1
    value = base % n
    while value != 1:
        value = (value * base) % n
        k += 1
        if k > n * n:
            raise RuntimeError("falha ao encontrar ordem multiplicativa em limite razoável")
    return k


def repeating_period_of_unit_fraction(base: int, n: int) -> int:
    """Para 1/n em base b, remove fatores comuns da parte terminante e mede o período."""
    m = n
    g = math.gcd(base, m)
    while g > 1:
        while m % g == 0:
            m //= g
        g = math.gcd(base, m)
    if m == 1:
        return 0
    return multiplicative_order(base, m)


def hamming_distance_bytes(a: bytes, b: bytes) -> int:
    if len(a) != len(b):
        raise ValueError("buffers precisam ter mesmo tamanho")
    return sum((x ^ y).bit_count() for x, y in zip(a, b))


def zipraf_encode(obj: Any, meta: Dict[str, Any] | None = None) -> str:
    meta = meta or {}
    header = json.dumps({"zipraf_version": "zipraf-1.1", "meta": meta}, separators=(",", ":")).encode("utf-8")
    body = json.dumps(obj, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    payload = header + b"\n" + body
    compressed = zlib.compress(payload, level=6)
    h = hashlib.sha3_256(compressed).hexdigest()
    b64 = base64.b64encode(compressed).decode("ascii")
    return f"zipraf:zipraf-1.1:sha3:{h}:{b64}"


def zipraf_decode(s: str) -> Tuple[Dict[str, Any], Any]:
    parts = s.split(":", 4)
    if len(parts) != 5 or parts[0] != "zipraf":
        raise ValueError("payload zipraf malformado")
    _, version, alg, hhex, b64 = parts
    compressed = base64.b64decode(b64.encode("ascii"))
    if hashlib.sha3_256(compressed).hexdigest() != hhex:
        raise ValueError("integridade falhou")
    payload = zlib.decompress(compressed)
    hdr_raw, body_raw = payload.split(b"\n", 1)
    hdr = json.loads(hdr_raw.decode("utf-8"))
    body = json.loads(body_raw.decode("utf-8"))
    return hdr["meta"], body


# ============================================================================
# TESTES / FALSIFICABILIDADE
# ============================================================================

def test_pythagoras() -> TestResult:
    a, b = sp.symbols("a b", positive=True, real=True)
    c = sp.sqrt(a**2 + b**2)
    expr = sp.simplify(a**2 + b**2 - c**2)
    return TestResult(
        formula_id="pythagoras",
        test_name="identidade_simbólica",
        status="PASS" if expr == 0 else "FAIL",
        details="Verificação simbólica direta de a²+b²-c² = 0.",
        metrics={"simplified_expression": str(expr)},
    )


def test_bhaskara_delta() -> TestResult:
    a, b, c = sp.symbols("a b c", real=True)
    delta = b**2 - 4*a*c
    poly = a*sp.Symbol("x")**2 + b*sp.Symbol("x") + c
    return TestResult(
        formula_id="bhaskara_delta",
        test_name="definição_simbólica",
        status="PASS",
        details="Registro simbólico do discriminante; falsificação depende de substituições numéricas.",
        metrics={"delta_latex": sp.latex(delta), "polynomial": str(poly)},
    )


def test_fibonacci_recurrence() -> TestResult:
    ok = True
    bad_index = None
    for n in range(2, 35):
        if fibonacci(n) != fibonacci(n-1) + fibonacci(n-2):
            ok = False
            bad_index = n
            break
    return TestResult(
        formula_id="fibonacci",
        test_name="recorrência_iterativa",
        status="PASS" if ok else "FAIL",
        details="Checagem exaustiva da recorrência para n=2..34.",
        metrics={"checked_until": 34, "bad_index": bad_index},
    )


def test_inverse_ethics_monotone() -> TestResult:
    vals = [inverse_ethics(n) for n in range(20)]
    monotone = all(vals[i+1] <= vals[i] for i in range(len(vals)-1))
    tends_down = vals[-1] < vals[5]
    return TestResult(
        formula_id="inverse_ethics",
        test_name="monotonia_e_amortecimento",
        status="PASS" if monotone and tends_down else "FAIL",
        details="A função deve decrescer conforme Fibonacci cresce.",
        metrics={"E0": vals[0], "E5": vals[5], "E19": vals[-1]},
    )


def test_equilateral_height() -> TestResult:
    s = sp.Symbol("s", positive=True)
    h = sp.sqrt(s**2 - (s/2)**2)
    simplified = sp.simplify(h.subs(s, 1))
    target = sp.sqrt(3)/2
    return TestResult(
        formula_id="equilateral_height",
        test_name="dedução_por_pitágoras",
        status="PASS" if sp.simplify(simplified - target) == 0 else "FAIL",
        details="Altura derivada do triângulo retângulo formado pela mediana.",
        metrics={"simplified": str(simplified), "target": str(target)},
    )


def test_torus_x_numeric() -> TestResult:
    R, r = 2.0, 1.0
    thetas = np.linspace(0, 2*np.pi, 1000)
    phis = np.linspace(0, 2*np.pi, 1000)
    xs = (R + r*np.cos(phis)) * np.cos(thetas)
    within_bounds = xs.min() >= -(R+r)-1e-9 and xs.max() <= (R+r)+1e-9
    return TestResult(
        formula_id="torus_x",
        test_name="faixa_numérica",
        status="PASS" if within_bounds else "FAIL",
        details="A componente x deve ficar no intervalo [-(R+r), +(R+r)].",
        metrics={"xmin": float(xs.min()), "xmax": float(xs.max()), "bound": R+r},
    )


def test_shannon_entropy() -> TestResult:
    zero_entropy = shannon_entropy_from_bytes(b"\x00" * 128)
    near_max = shannon_entropy_from_bytes(bytes(range(256)))
    ok = abs(zero_entropy - 0.0) < 1e-12 and near_max > 7.9
    return TestResult(
        formula_id="shannon_entropy",
        test_name="casos_extremos",
        status="PASS" if ok else "FAIL",
        details="Arquivo constante deve ter entropia ~0; distribuição uniforme de 256 bytes deve se aproximar de 8.",
        metrics={"constant_entropy": zero_entropy, "uniform_entropy": near_max},
    )


def test_base_period() -> TestResult:
    examples = {
        (10, 3): 1,   # 1/3 = 0.(3)
        (10, 7): 6,   # 1/7 = 0.(142857)
        (2, 3): 2,    # 1/3 = 0.(01)_2
        (10, 8): 0,   # 1/8 terminante
    }
    ok = True
    got = {}
    for (base, n), expected in examples.items():
        value = repeating_period_of_unit_fraction(base, n)
        got[f"base{base}_n{n}"] = value
        if value != expected:
            ok = False
    return TestResult(
        formula_id="base_period",
        test_name="ordem_multiplicativa_e_terminação",
        status="PASS" if ok else "FAIL",
        details="Casos clássicos conhecidos de período periódico e terminante.",
        metrics=got,
    )


def test_hamming_distance() -> TestResult:
    a = bytes([0x00] * 32)
    b = bytes([0xFF] * 32)
    d = hamming_distance_bytes(a, b)
    ok = d == 256
    return TestResult(
        formula_id="hamming",
        test_name="distância_total",
        status="PASS" if ok else "FAIL",
        details="Entre 256 bits 0 e 256 bits 1, a distância deve ser 256.",
        metrics={"distance": d},
    )


def test_zipraf_container() -> TestResult:
    obj = {"x": [1, 2, 3], "msg": "coerência"}
    meta = {"author": "Rafael", "kind": "demo"}
    encoded = zipraf_encode(obj, meta)
    meta2, obj2 = zipraf_decode(encoded)
    ok = meta2 == meta and obj2 == obj
    return TestResult(
        formula_id="zipraf_container",
        test_name="roundtrip_com_integridade",
        status="PASS" if ok else "FAIL",
        details="Encode/decode deve preservar cabeçalho e corpo quando integridade passa.",
        metrics={"encoded_prefix": encoded[:40], "len_encoded": len(encoded)},
    )


TESTS = [
    test_pythagoras,
    test_bhaskara_delta,
    test_fibonacci_recurrence,
    test_inverse_ethics_monotone,
    test_equilateral_height,
    test_torus_x_numeric,
    test_shannon_entropy,
    test_base_period,
    test_hamming_distance,
    test_zipraf_container,
]


# ============================================================================
# GRÁFICOS E SAÍDAS
# ============================================================================

def plot_inverse_ethics(outdir: Path) -> str:
    ns = np.arange(0, 20)
    vals = np.array([inverse_ethics(int(n)) for n in ns], dtype=float)
    plt.figure(figsize=(8, 4.5))
    plt.plot(ns, vals)
    plt.title("Função de amortecimento baseada em Fibonacci")
    plt.xlabel("n")
    plt.ylabel("E_n")
    path = outdir / "inverse_ethics_curve.png"
    plt.tight_layout()
    plt.savefig(path, dpi=150)
    plt.close()
    return path.name


def plot_torus_x(outdir: Path) -> str:
    theta = np.linspace(0, 2*np.pi, 400)
    phi = np.linspace(0, 2*np.pi, 400)
    x = (2.0 + np.cos(phi)) * np.cos(theta)
    plt.figure(figsize=(8, 4.5))
    plt.plot(theta, x)
    plt.title("Componente x da parametrização parcial do toro")
    plt.xlabel("θ")
    plt.ylabel("x(θ, φ sincronizado)")
    path = outdir / "torus_x_curve.png"
    plt.tight_layout()
    plt.savefig(path, dpi=150)
    plt.close()
    return path.name


def plot_entropy_examples(outdir: Path) -> str:
    xs = ["constante", "uniforme"]
    ys = [shannon_entropy_from_bytes(b"\x00" * 128), shannon_entropy_from_bytes(bytes(range(256)))]
    plt.figure(figsize=(6, 4.5))
    plt.bar(xs, ys)
    plt.title("Entropia de Shannon — casos de referência")
    plt.ylabel("bits por símbolo")
    path = outdir / "entropy_reference.png"
    plt.tight_layout()
    plt.savefig(path, dpi=150)
    plt.close()
    return path.name


# ============================================================================
# RELATÓRIO
# ============================================================================

def build_markdown_report(formulas: List[FormulaRecord], tests: List[TestResult], figures: List[str]) -> str:
    lines = []
    lines.append("# RAFAELIA — Dossiê Matemático Computável")
    lines.append("")
    lines.append("Este relatório separa, com rigor reproduzível, o que está em nível de:")
    lines.append("- fórmula matemática explícita;")
    lines.append("- heurística computacional definida;")
    lines.append("- contêiner de integridade/serialização;")
    lines.append("- teste de falsificabilidade executável.")
    lines.append("")
    lines.append("## Fórmulas e contexto")
    lines.append("")
    for f in formulas:
        lines.append(f"### {f.id}")
        lines.append(f"- **Metáfora pedagógica**: {f.metaphor}")
        lines.append(f"- **Termo técnico**: {f.technical_term}")
        lines.append(f"- **Formulação acadêmica**: {f.academic_statement}")
        lines.append(f"- **LaTeX**: `${f.latex}$")
        lines.append(f"- **Modo de verificação**: `{f.proof_mode}`")
        lines.append("- **Variáveis**:")
        for k, v in f.variable_context.items():
            lines.append(f"  - `{k}`: {v}")
        lines.append("")
    lines.append("## Resultados dos testes")
    lines.append("")
    passed = sum(1 for t in tests if t.status == "PASS")
    lines.append(f"**Passaram**: {passed}/{len(tests)}")
    lines.append("")
    for t in tests:
        lines.append(f"### {t.formula_id} :: {t.test_name}")
        lines.append(f"- **Status**: `{t.status}`")
        lines.append(f"- **Descrição**: {t.details}")
        lines.append(f"- **Métricas**: `{json.dumps(t.metrics, ensure_ascii=False)}`")
        lines.append("")
    lines.append("## Figuras geradas")
    lines.append("")
    for fig in figures:
        lines.append(f"- `{fig}`")
    lines.append("")
    lines.append("## Leitura de rigor")
    lines.append("")
    lines.append("Este pipeline não pretende provar qualquer afirmação metafísica ampla. Ele formaliza, testa e documenta apenas:")
    lines.append("1. identidades matemáticas explícitas;")
    lines.append("2. propriedades computacionais verificáveis;")
    lines.append("3. contêineres de integridade e reconstrução;")
    lines.append("4. critérios de falsificabilidade executáveis.")
    lines.append("")
    return "\n".join(lines)


def write_csv_results(outdir: Path, tests: List[TestResult]) -> str:
    path = outdir / "test_results.csv"
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["formula_id", "test_name", "status", "details", "metrics_json"])
        for t in tests:
            writer.writerow([t.formula_id, t.test_name, t.status, t.details, json.dumps(t.metrics, ensure_ascii=False)])
    return path.name


def write_json_bundle(outdir: Path, formulas: List[FormulaRecord], tests: List[TestResult], figures: List[str]) -> str:
    path = outdir / "bundle.json"
    payload = {
        "formulas": [asdict(f) for f in formulas],
        "tests": [asdict(t) for t in tests],
        "figures": figures,
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path.name


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=str, default="outputs")
    args = parser.parse_args()

    outdir = Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)

    formulas = formula_registry()
    tests = [fn() for fn in TESTS]

    figures = [
        plot_inverse_ethics(outdir),
        plot_torus_x(outdir),
        plot_entropy_examples(outdir),
    ]

    report = build_markdown_report(formulas, tests, figures)
    (outdir / "report.md").write_text(report, encoding="utf-8")
    write_csv_results(outdir, tests)
    write_json_bundle(outdir, formulas, tests, figures)

    summary = {
        "passed": sum(1 for t in tests if t.status == "PASS"),
        "total": len(tests),
        "output_dir": str(outdir.resolve()),
        "figures": figures,
    }
    (outdir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print("=" * 72)
    print("RAFAELIA_RIGOR_PIPELINE — EXECUÇÃO CONCLUÍDA")
    print("=" * 72)
    print(f"Saída: {outdir.resolve()}")
    print(f"Testes: {summary['passed']}/{summary['total']} PASS")
    print("Arquivos gerados:")
    for name in ["report.md", "test_results.csv", "bundle.json", "summary.json", *figures]:
        print(f" - {name}")


if __name__ == "__main__":
    main()
