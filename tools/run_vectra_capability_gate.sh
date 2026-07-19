#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

APK_PATH="${1:-}"
OUTPUT="${VECTRA_CAPABILITY_REPORT:-reports/vectra_capability_surface.json}"

python3 -m py_compile tools/audit_vectra_capabilities.py
python3 -m json.tool configs/vectra_capability_contract.json >/dev/null
python3 -m unittest -v tests/test_audit_vectra_capabilities.py

args=(
  --root "$ROOT"
  --contract configs/vectra_capability_contract.json
  --output "$OUTPUT"
)

if [[ -n "$APK_PATH" ]]; then
  args+=(--apk "$APK_PATH")
fi

python3 tools/audit_vectra_capabilities.py "${args[@]}"

python3 - "$OUTPUT" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
report = json.loads(path.read_text(encoding="utf-8"))
print("capability states:")
for state, count in report["capability_summary"].items():
    print(f"  {state}: {count}")
print(f"apk_state: {report['apk_audit']['state']}")
print(f"loose_records: {report['loose_artifacts']['records_count']}")
print("claim_allowed: false")
PY
