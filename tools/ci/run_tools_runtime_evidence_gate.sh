#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ROOT_DIR}/reports/tools-runtime-evidence"
mkdir -p "${OUT_DIR}"

run_gate() {
  local name="$1"
  shift
  local log="${OUT_DIR}/${name}.log"
  echo "[tools-runtime] ${name}: $*"
  "$@" 2>&1 | tee "${log}"
}

cd "${ROOT_DIR}"

run_gate connectivity python3 tools/ci/validate_tools_runtime_connectivity.py
run_gate arena python3 tools/arena/test_arena_schema.py
run_gate lowbasic bash tools/ci/test_lowbasic_handoff_matrix.sh
run_gate rafcode bash tools/ci/run_asm_compiler_refactor_gate.sh

python3 - "${OUT_DIR}" <<'PY'
from __future__ import annotations
import hashlib
import json
import pathlib
import sys
import time

out = pathlib.Path(sys.argv[1])
logs = {}
for path in sorted(out.glob("*.log")):
    raw = path.read_bytes()
    logs[path.name] = {
        "sha256": hashlib.sha256(raw).hexdigest(),
        "size_bytes": len(raw),
    }
receipt = {
    "schema_version": "tools-runtime-evidence-receipt.v1",
    "timestamp_unix": int(time.time()),
    "source_gate": "PASS",
    "build_gate": "PASS",
    "link_gate": "TOKEN_VAZIO",
    "apk_gate": "TOKEN_VAZIO",
    "device_gate": "TOKEN_VAZIO",
    "receipt_gate": "PASS_FOR_HOST_SOURCE_BUILD_ONLY",
    "reproduce_gate": "TOKEN_VAZIO",
    "claim_allowed": False,
    "device_runtime_verified": False,
    "logs": logs,
    "next_gate": "build/link APK and capture device execution receipt without promoting claim",
}
(out / "receipt.json").write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(receipt, indent=2, sort_keys=True))
PY

echo "PASS: tools runtime evidence host gate"
