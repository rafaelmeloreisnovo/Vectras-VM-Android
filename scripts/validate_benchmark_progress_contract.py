#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
manager = (ROOT / "app/src/main/java/com/vectras/vm/benchmark/BenchmarkManager.java").read_text(encoding="utf-8")
watchdog = (ROOT / "app/src/main/java/com/vectras/vm/benchmark/BenchmarkSuiteProgressWatchdog.java").read_text(encoding="utf-8")
collector = (ROOT / "scripts/collect_vectra_grade_benchmarks.sh").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/audit-benchmark-contract.yml").read_text(encoding="utf-8")

errors = []

if "BenchmarkSuiteProgressWatchdog.run(" not in manager:
    errors.append("BenchmarkManager must route the 79-metric suite through BenchmarkSuiteProgressWatchdog")

method_match = re.search(
    r"private\s+VectraBenchmark\.BenchmarkResult\[\]\s+runBenchmarksWithProgress\([^)]*\)\s+throws\s+Exception\s*\{(?P<body>.*?)\n\s*\}",
    manager,
    flags=re.S,
)
if not method_match:
    errors.append("runBenchmarksWithProgress method not found")
else:
    body = method_match.group("body")
    direct_calls = [
        line.strip() for line in body.splitlines()
        if "VectraBenchmark.runAllBenchmarks()" in line
    ]
    if direct_calls:
        errors.append("direct monolithic runAllBenchmarks call reintroduced: " + "; ".join(direct_calls))

required_progress = {
    "PROGRESS_INITIALIZING": 25,
    "PROGRESS_CPU_SINGLE": 30,
    "PROGRESS_CPU_MULTI": 42,
    "PROGRESS_MEMORY": 52,
    "PROGRESS_STORAGE": 66,
    "PROGRESS_INTEGRITY": 76,
    "PROGRESS_EMULATION": 82,
}
observed = []
for name, minimum in required_progress.items():
    m = re.search(rf"{name}\s*=\s*(\d+)", watchdog)
    if not m:
        errors.append(f"missing watchdog phase {name}")
        continue
    value = int(m.group(1))
    observed.append(value)
    if value != minimum:
        errors.append(f"{name} expected {minimum}, found {value}")

if observed and observed != sorted(observed):
    errors.append(f"watchdog phase progress must be monotonic, got {observed}")
if observed and observed[0] <= 24:
    errors.append("first execution liveness phase must advance beyond legacy 24% boundary")
if observed and observed[-1] >= 86:
    errors.append("watchdog execution phases must remain below output-collection stage 86")

if '"global_percent_intentionally_omitted": True' not in collector:
    errors.append("collector must explicitly omit invalid global CI+device percentage")
if '"status":"pending"' in collector or ",pending,pending" in collector:
    errors.append("legacy undifferentiated pending status reintroduced in collector")
if '"device_required"' not in collector or '"TOKEN_VAZIO"' not in collector:
    errors.append("collector must preserve device_required/TOKEN_VAZIO evidence boundary")

rmr_pos = workflow.find("Validate RMR equivalence")
collect_pos = workflow.find("Collect vectra-grade benchmarks")
if rmr_pos < 0 or collect_pos < 0 or rmr_pos > collect_pos:
    errors.append("RMR equivalence must run before benchmark collection")

if errors:
    for err in errors:
        print(f"BENCHMARK_PROGRESS_CONTRACT_FAIL: {err}", file=sys.stderr)
    raise SystemExit(1)

print("BENCHMARK_PROGRESS_CONTRACT_PASS")
print("phases=" + ",".join(map(str, observed)))
print("scope=79_metrics_preserved; ui_phase_progress_truthful; ci_device_denominator_separated")
