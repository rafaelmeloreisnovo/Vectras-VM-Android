#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:-ci-artifacts/apk-report}"
shift || true
REQUIRED_ABIS_RAW="${SUPPORTED_ABIS:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --required-abis)
      REQUIRED_ABIS_RAW="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: collect_apk_report.sh [out_dir] [--required-abis 'arm64-v8a,armeabi-v7a']"
      exit 0
      ;;
    *)
      echo "Argumento desconhecido: $1" >&2
      exit 2
      ;;
  esac
done

mkdir -p "$OUT_DIR"
REPORT="$OUT_DIR/apk_report.txt"
JSON="$OUT_DIR/apk_report.json"

mapfile -t apks < <(find app/build/outputs/apk -type f -name '*.apk' 2>/dev/null | sort)
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "Nenhum APK encontrado em app/build/outputs/apk" >&2
  exit 1
fi

python3 - "${apks[@]}" "$REPORT" "$JSON" "$REQUIRED_ABIS_RAW" <<'PY'
import hashlib
import json
import os
import re
import sys
import zipfile

KNOWN_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "riscv64")
apks = sys.argv[1:-3]
report = sys.argv[-3]
json_path = sys.argv[-2]
required_raw = sys.argv[-1]
required = [item for item in re.split(r"[\s,;]+", required_raw.strip()) if item]
unknown = sorted(set(required).difference(KNOWN_ABIS))
if unknown:
    raise SystemExit(f"ABIs requeridas desconhecidas: {', '.join(unknown)}")
required = list(dict.fromkeys(required))

rows = []
for apk in apks:
    with open(apk, "rb") as handle:
        sha = hashlib.sha256(handle.read()).hexdigest()
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
    observed = [
        abi for abi in KNOWN_ABIS
        if any(name.startswith(f"lib/{abi}/") for name in names)
    ]
    rows.append({
        "apk": apk,
        "size_bytes": os.path.getsize(apk),
        "sha256": sha,
        "observed_abis": observed,
        "required_abis": required,
        "required_abis_present": all(abi in observed for abi in required),
        "has_armeabi_v7a": "armeabi-v7a" in observed,
        "has_arm64_v8a": "arm64-v8a" in observed,
        "is_signed_filename": not apk.endswith("-unsigned.apk"),
    })

rows.sort(key=lambda row: row["apk"])
with open(report, "w", encoding="utf-8") as handle:
    handle.write("APK REPORT\n")
    handle.write(f"required_abis={','.join(required) if required else 'OBSERVED_ONLY'}\n")
    for row in rows:
        handle.write(f"{row['apk']}\n")
        handle.write(f"  size_bytes={row['size_bytes']}\n")
        handle.write(f"  sha256={row['sha256']}\n")
        handle.write(f"  observed_abis={','.join(row['observed_abis'])}\n")
        handle.write(f"  required_abis_present={row['required_abis_present']}\n")
        handle.write(f"  is_signed_filename={row['is_signed_filename']}\n")

unsigned = [row for row in rows if row["apk"].endswith("-unsigned.apk")]
signed = [row for row in rows if not row["apk"].endswith("-unsigned.apk")]
payload = {"schema_version": "2.0.0", "required_abis": required, "apks": rows}
if unsigned and signed:
    payload["delta_signed_minus_unsigned_bytes"] = (
        signed[0]["size_bytes"] - unsigned[0]["size_bytes"]
    )
with open(json_path, "w", encoding="utf-8") as handle:
    json.dump(payload, handle, indent=2, sort_keys=True)
    handle.write("\n")

if not any(row["observed_abis"] for row in rows):
    raise SystemExit("Nenhum APK contém bibliotecas em lib/<abi>/")
if required and not any(row["required_abis_present"] for row in rows):
    raise SystemExit(
        "Nenhum APK cobre todas as ABIs requeridas: " + ",".join(required)
    )
PY

cat "$REPORT"
