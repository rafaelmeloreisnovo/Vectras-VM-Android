#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
VALIDATOR="$ROOT/tools/device/c08_validate_device_evidence.py"
VECTRAS_PACKAGE="com.rafacodephi.app"
TERMUX_PACKAGE="com.termux.rafacodephi"
SERIAL=""
TRANSACTION_ID=""
OUT=""

usage() {
  cat <<'EOF'
Usage:
  c08_collect_device_evidence.sh \
    --transaction-id TX \
    --out DIRECTORY \
    [--serial ADB_SERIAL] \
    [--vectras-package PACKAGE] \
    [--termux-package PACKAGE]

The collector never clears logcat and never uses an exported test component.
It reads Vectras internal request/receipt files only through `adb shell run-as`.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="${2:?missing serial}"; shift 2 ;;
    --transaction-id) TRANSACTION_ID="${2:?missing transaction id}"; shift 2 ;;
    --out) OUT="${2:?missing output directory}"; shift 2 ;;
    --vectras-package) VECTRAS_PACKAGE="${2:?missing Vectras package}"; shift 2 ;;
    --termux-package) TERMUX_PACKAGE="${2:?missing Termux package}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 64 ;;
  esac
done

if [[ ! "$TRANSACTION_ID" =~ ^[A-Za-z0-9._:-]{8,128}$ ]]; then
  echo "invalid transaction id" >&2
  exit 64
fi
if [ -z "$OUT" ]; then
  echo "--out is required" >&2
  exit 64
fi
for command in adb python3 sha256sum; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "missing command: $command" >&2
    exit 69
  }
done

mkdir -p "$OUT"
OUT="$(CDPATH= cd -- "$OUT" && pwd)"
mkdir -p "$OUT/packages"

cleanup_sensitive_temporaries() {
  rm -f \
    "$OUT/packages/"*.dumpsys.txt \
    "$OUT/packages/"*.paths.txt \
    "$OUT/packages/"*.hashes.txt \
    "$OUT/"run-as-*.stderr
}
trap cleanup_sensitive_temporaries EXIT

ADB=(adb)
if [ -n "$SERIAL" ]; then
  ADB+=( -s "$SERIAL" )
fi

adb_cmd() {
  "${ADB[@]}" "$@"
}

string_sha256() {
  python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())'
}

trim_cr() {
  tr -d '\r'
}

state="$(adb_cmd get-state 2>/dev/null | trim_cr || true)"
if [ "$state" != "device" ]; then
  echo "ADB device is not ready: ${state:-TOKEN_VAZIO}" >&2
  exit 69
fi

resolved_serial="$(adb_cmd get-serialno | trim_cr)"
adb_serial_sha256="$(printf '%s' "$resolved_serial" | string_sha256)"
manufacturer="$(adb_cmd shell getprop ro.product.manufacturer | trim_cr)"
model="$(adb_cmd shell getprop ro.product.model | trim_cr)"
device_name="$(adb_cmd shell getprop ro.product.device | trim_cr)"
sdk="$(adb_cmd shell getprop ro.build.version.sdk | trim_cr)"
release="$(adb_cmd shell getprop ro.build.version.release | trim_cr)"
fingerprint="$(adb_cmd shell getprop ro.build.fingerprint | trim_cr)"
fingerprint_sha256="$(printf '%s' "$fingerprint" | string_sha256)"
abilist="$(adb_cmd shell getprop ro.product.cpu.abilist | trim_cr)"
boot_id="$(adb_cmd shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | trim_cr || true)"
boot_id_sha256="$(printf '%s' "${boot_id:-TOKEN_VAZIO}" | string_sha256)"
collected_at_epoch_ms="$(python3 -c 'import time; print(time.time_ns() // 1_000_000)')"

collect_package() {
  local package="$1"
  local slug="$2"
  local dump_file="$OUT/packages/${slug}.dumpsys.txt"
  local paths_file="$OUT/packages/${slug}.paths.txt"
  local json_file="$OUT/packages/${slug}.json"

  adb_cmd shell dumpsys package "$package" >"$dump_file" 2>/dev/null || true
  adb_cmd shell pm path "$package" | trim_cr >"$paths_file" 2>/dev/null || true

  local installed=false
  local version_name=""
  local version_code=""
  local hashes_file="$OUT/packages/${slug}.hashes.txt"
  : >"$hashes_file"

  if grep -q '^package:' "$paths_file"; then
    installed=true
    version_name="$(sed -n 's/^[[:space:]]*versionName=//p' "$dump_file" | head -n 1)"
    version_code="$(sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' "$dump_file" | head -n 1)"
    while IFS= read -r line; do
      local remote_path="${line#package:}"
      [ -n "$remote_path" ] || continue
      local digest
      digest="$(adb_cmd exec-out cat "$remote_path" | sha256sum | awk '{print $1}')"
      printf '%s\n' "$digest" >>"$hashes_file"
    done <"$paths_file"
  fi

  PACKAGE="$package" \
  INSTALLED="$installed" \
  VERSION_NAME="$version_name" \
  VERSION_CODE="$version_code" \
  PATHS_FILE="$paths_file" \
  HASHES_FILE="$hashes_file" \
  JSON_FILE="$json_file" \
  python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path

paths = [
    line.removeprefix("package:").strip()
    for line in Path(os.environ["PATHS_FILE"]).read_text().splitlines()
    if line.startswith("package:")
]
hashes = [line.strip() for line in Path(os.environ["HASHES_FILE"]).read_text().splitlines() if line.strip()]
value = {
    "package": os.environ["PACKAGE"],
    "installed": os.environ["INSTALLED"] == "true",
    "version_name": os.environ["VERSION_NAME"] or "TOKEN_VAZIO",
    "version_code": os.environ["VERSION_CODE"] or "TOKEN_VAZIO",
    "apk_path_count": len(paths),
    "apk_paths_sha256": [hashlib.sha256(path.encode()).hexdigest() for path in paths],
    "apk_sha256": hashes,
    "raw_apk_paths_retained": False,
}
Path(os.environ["JSON_FILE"]).write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
PY
}

collect_package "$VECTRAS_PACKAGE" vectras
collect_package "$TERMUX_PACKAGE" termux

permission_name="$TERMUX_PACKAGE.permission.RUN_COMMAND"
permission_granted=false
if grep -Fq "$permission_name: granted=true" "$OUT/packages/vectras.dumpsys.txt"; then
  permission_granted=true
fi

request_file="$OUT/request.json"
receipt_file="$OUT/receipt.json"
run_as_internal_files=false
run_as_error="TOKEN_VAZIO"

if adb_cmd exec-out run-as "$VECTRAS_PACKAGE" \
    cat "files/rafaelia-runtime-requests/$TRANSACTION_ID.json" >"$request_file" 2>"$OUT/run-as-request.stderr" &&
   adb_cmd exec-out run-as "$VECTRAS_PACKAGE" \
    cat "files/rafaelia-runtime-receipts/$TRANSACTION_ID.json" >"$receipt_file" 2>"$OUT/run-as-receipt.stderr"; then
  run_as_internal_files=true
else
  run_as_error="RUN_AS_UNAVAILABLE_OR_INTERNAL_FILE_MISSING"
  rm -f "$request_file" "$receipt_file"
fi

MANUFACTURER="$manufacturer" \
MODEL="$model" \
DEVICE_NAME="$device_name" \
SDK="$sdk" \
RELEASE="$release" \
FINGERPRINT_SHA256="$fingerprint_sha256" \
ABILIST="$abilist" \
BOOT_ID_SHA256="$boot_id_sha256" \
ADB_SERIAL_SHA256="$adb_serial_sha256" \
COLLECTED_AT="$collected_at_epoch_ms" \
PERMISSION_GRANTED="$permission_granted" \
RUN_AS_INTERNAL="$run_as_internal_files" \
RUN_AS_ERROR="$run_as_error" \
VECTRAS_PACKAGE="$VECTRAS_PACKAGE" \
TERMUX_PACKAGE="$TERMUX_PACKAGE" \
OUT="$OUT" \
python3 - <<'PY'
import json
import os
from pathlib import Path

out = Path(os.environ["OUT"])
vectras = json.loads((out / "packages/vectras.json").read_text())
termux = json.loads((out / "packages/termux.json").read_text())
manifest = {
    "schema": "raf.android-device-manifest.v1",
    "cycle_id": "C08",
    "collected_at_epoch_ms": int(os.environ["COLLECTED_AT"]),
    "adb_serial_sha256": os.environ["ADB_SERIAL_SHA256"],
    "manufacturer": os.environ["MANUFACTURER"] or "TOKEN_VAZIO",
    "model": os.environ["MODEL"] or "TOKEN_VAZIO",
    "device": os.environ["DEVICE_NAME"] or "TOKEN_VAZIO",
    "android_sdk": int(os.environ["SDK"]) if os.environ["SDK"].isdigit() else "TOKEN_VAZIO",
    "android_release": os.environ["RELEASE"] or "TOKEN_VAZIO",
    "build_fingerprint_sha256": os.environ["FINGERPRINT_SHA256"],
    "boot_id_sha256": os.environ["BOOT_ID_SHA256"],
    "supported_abis": [item for item in os.environ["ABILIST"].split(",") if item],
    "packages": {
        os.environ["VECTRAS_PACKAGE"]: vectras,
        os.environ["TERMUX_PACKAGE"]: termux,
    },
    "run_command_permission": os.environ["TERMUX_PACKAGE"] + ".permission.RUN_COMMAND",
    "run_command_permission_granted": os.environ["PERMISSION_GRANTED"] == "true",
    "run_as_internal_files": os.environ["RUN_AS_INTERNAL"] == "true",
    "run_as_error": os.environ["RUN_AS_ERROR"],
    "logcat_cleared": False,
    "logcat_used_as_primary_evidence": False,
    "exported_test_component_used": False,
    "raw_adb_serial_persisted": False,
    "raw_build_fingerprint_persisted": False,
    "raw_apk_paths_retained": False,
    "raw_dumpsys_retained": False,
    "raw_run_as_diagnostics_retained": False,
    "claim_allowed": False,
}
(out / "device_manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
PY

collector_state="PASS_COLLECTION"
if [ "$run_as_internal_files" != true ]; then
  collector_state="BLOCKED_RUN_AS_OR_INTERNAL_FILES"
fi

COLLECTOR_STATE="$collector_state" \
TRANSACTION_ID="$TRANSACTION_ID" \
OUT="$OUT" \
python3 - <<'PY'
import json
import os
from pathlib import Path

out = Path(os.environ["OUT"])
status = {
    "schema": "raf.c08-collector-status.v1",
    "state": os.environ["COLLECTOR_STATE"],
    "transaction_id": os.environ["TRANSACTION_ID"],
    "device_manifest_present": (out / "device_manifest.json").is_file(),
    "request_present": (out / "request.json").is_file(),
    "receipt_present": (out / "receipt.json").is_file(),
    "logcat_cleared": False,
    "exported_test_component_used": False,
    "temporary_sensitive_files_retained": False,
    "claim_allowed": False,
}
(out / "collector_status.json").write_text(json.dumps(status, indent=2, sort_keys=True) + "\n")
PY

if [ "$run_as_internal_files" != true ]; then
  echo "$collector_state: evidence remains TOKEN_VAZIO; see $OUT" >&2
  exit 2
fi

python3 "$VALIDATOR" \
  --request "$request_file" \
  --receipt "$receipt_file" \
  --device-manifest "$OUT/device_manifest.json" \
  --vectras-package "$VECTRAS_PACKAGE" \
  --termux-package "$TERMUX_PACKAGE" \
  --output "$OUT/c08_device_evidence_receipt.json"

printf 'PASS C08 device evidence packet: %s\n' "$OUT"
