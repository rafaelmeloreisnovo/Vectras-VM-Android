#!/usr/bin/env bash
set -euo pipefail

mkdir -p reports
ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
apk="$(find dist/apk-matrix/signed dist/apk-matrix/unsigned app/build/outputs/apk/debug -name '*.apk' 2>/dev/null | head -n1 || true)"

apk_size="TOKEN_VAZIO"
apk_sha256="TOKEN_VAZIO"
apk_status="not_collected_no_apk"
if [[ -n "$apk" ]]; then
  apk_size="$(stat -c%s "$apk" 2>/dev/null || stat -f%z "$apk")"
  apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
  apk_status="measured"
fi

rmr_value="TOKEN_VAZIO"
rmr_status="not_collected"
if [[ -f reports/rmr_equivalence.json ]]; then
  readarray -t rmr_fields < <(python3 - reports/rmr_equivalence.json <<'PY'
import json, sys
p = sys.argv[1]
try:
    data = json.load(open(p, encoding='utf-8'))
except Exception:
    print('TOKEN_VAZIO')
    print('invalid_receipt')
    raise SystemExit(0)
state = str(data.get('status', 'TOKEN_VAZIO'))
print(state)
if state == 'MATCH_FALLBACK':
    print('fallback_not_verified')
elif state in {'MATCH', 'VERIFIED', 'PASS'}:
    print('measured')
elif state.startswith('BLOCKED_'):
    print('blocked')
else:
    print('observed_unverified')
PY
  )
  rmr_value="${rmr_fields[0]:-TOKEN_VAZIO}"
  rmr_status="${rmr_fields[1]:-invalid_receipt}"
fi

python3 - "$ts" "$apk" "$apk_size" "$apk_sha256" "$apk_status" "$rmr_value" "$rmr_status" <<'PY'
import csv
import json
import sys
from pathlib import Path

ts, apk, apk_size, apk_sha256, apk_status, rmr_value, rmr_status = sys.argv[1:]

metrics = [
    {"id":"build_clean_time","category":"build_metrics","value":"TOKEN_VAZIO","status":"not_executed_in_audit_workflow","scope":"ci"},
    {"id":"apk_size","category":"binary_metrics","value":apk_size,"status":apk_status,"scope":"ci"},
    {"id":"apk_sha256","category":"binary_metrics","value":apk_sha256,"status":apk_status,"scope":"ci"},
    {"id":"runtime_cold_start","category":"runtime_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"cpu_scalar_c","category":"cpu_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"memory_rss","category":"memory_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"io_random_4k","category":"io_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"stability_crash_count","category":"stability_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"jitter_p99","category":"jitter_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"jni_overhead","category":"jni_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
    {"id":"rmr_equivalence","category":"rmr_equivalence_metrics","value":rmr_value,"status":rmr_status,"scope":"ci"},
    {"id":"bootstrap_blake3","category":"bootstrap_metrics","value":"documented","status":"documented","scope":"ci"},
    {"id":"device_runtime","category":"device_runtime_metrics","value":"TOKEN_VAZIO","status":"device_required","scope":"device"},
]

ci = [m for m in metrics if m["scope"] == "ci"]
device = [m for m in metrics if m["scope"] == "device"]
verified_status = {"measured"}
evidence_status = {"measured", "documented", "fallback_not_verified", "observed_unverified"}
ci_verified = sum(m["status"] in verified_status for m in ci)
ci_evidence = sum(m["status"] in evidence_status for m in ci)
summary = {
    "status": "scoped_evidence",
    "total_metric_definitions": len(metrics),
    "ci_scope_total": len(ci),
    "ci_verified_count": ci_verified,
    "ci_verified_percent": round((100.0 * ci_verified / len(ci)) if ci else 0.0, 2),
    "ci_evidence_present_count": ci_evidence,
    "ci_evidence_present_percent": round((100.0 * ci_evidence / len(ci)) if ci else 0.0, 2),
    "device_scope_total": len(device),
    "device_required_count": sum(m["status"] == "device_required" for m in device),
    "global_percent_intentionally_omitted": True,
    "reason": "CI-observable and physical-device metrics have different evidence boundaries; pending/total is not a valid benchmark score.",
}

payload = {
    "timestamp_utc": ts,
    "iso_status": "not_certified",
    "benchmark_summary": summary,
    "benchmark_metrics": metrics,
}
Path("reports/vectra_grade_benchmarks.json").write_text(
    json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
)

with open("reports/vectra_grade_benchmarks.csv", "w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=["id", "category", "value", "status", "scope"])
    w.writeheader()
    w.writerows(metrics)

lines = [
    "# Vectra-grade Benchmarks",
    "",
    f"- Timestamp (UTC): {ts}",
    "- ISO status: not_certified",
    f"- APK: {apk or 'not_collected_in_this_workflow'}",
    f"- CI scope: {len(ci)} definitions; verified={ci_verified}; evidence-present={ci_evidence}",
    f"- Device scope: {len(device)} definitions; requires physical receipt",
    "- Global completion percentage: intentionally omitted (different evidence boundaries)",
    "",
    "Status language: measured/documented/fallback_not_verified/not_collected/device_required/TOKEN_VAZIO.",
]
Path("reports/vectra_grade_benchmarks.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
