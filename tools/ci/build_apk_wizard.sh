#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${REPO_ROOT}/artifacts/apk-wizard"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/workspace/android-sdk}}"
GRADLEW="${REPO_ROOT}/gradlew"
RUNTIME_SEED_MANIFEST="${REPO_ROOT}/configs/embedded_runtime_seed_assets.v1.json"
RUNTIME_SEED_RECEIPT="${OUT_DIR}/runtime-seed-materialization.json"

mkdir -p "${OUT_DIR}"

echo "[wizard] bootstrap Android SDK root=${SDK_ROOT}"
"${REPO_ROOT}/tools/ci/bootstrap_local_android_sdk.sh" --sdk-root "${SDK_ROOT}"

echo "[wizard] materialize pinned embedded runtime seed assets for ARM lanes"
python3 "${REPO_ROOT}/tools/bootstrap/materialize_embedded_runtime_seed_assets.py" \
  --manifest "${RUNTIME_SEED_MANIFEST}" \
  --target-root "${REPO_ROOT}/app/src/main/assets" \
  --abis "arm64-v8a,armeabi-v7a" \
  --receipt "${RUNTIME_SEED_RECEIPT}"

build_lane() {
  local lane_name="$1"
  local policy="$2"
  local abis="$3"
  local ci_internal="$4"
  local payload_abis="$5"
  local apk_src="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
  local apk_dst="${OUT_DIR}/${lane_name}.apk"
  local payload_receipt="${OUT_DIR}/${lane_name}.runtime-payload.json"

  echo "[wizard] building lane=${lane_name} policy=${policy} abis=${abis}"
  "${GRADLEW}" --no-daemon :app:clean :app:assembleDebug \
    -PAPP_ABI_POLICY="${policy}" \
    -PSUPPORTED_ABIS="${abis}" \
    -PCI_INTERNAL_VALIDATION="${ci_internal}" \
    -PdevFastPath=true

  if [[ ! -f "${apk_src}" ]]; then
    echo "[wizard][error] APK not found for lane=${lane_name}: ${apk_src}" >&2
    exit 1
  fi
  cp -f "${apk_src}" "${apk_dst}"

  python3 "${REPO_ROOT}/tools/device_runtime/verify_beta_apk_runtime_payload.py" \
    "${apk_dst}" \
    --abis "${payload_abis}" \
    --json-out "${payload_receipt}"

  local apk_size
  apk_size="$(stat -c '%s' "${apk_dst}")"
  echo "${lane_name}|${policy}|${abis}|${apk_size}" >> "${OUT_DIR}/sizes.tsv"
}

rm -f "${OUT_DIR}/sizes.tsv"
build_lane "app-debug-arm64-v8a" "arm64-only" "arm64-v8a" "false" "arm64-v8a"
build_lane "app-debug-arm32-arm64" "arm32-arm64" "arm64-v8a,armeabi-v7a" "true" "arm64-v8a,armeabi-v7a"

source_commit="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["source_commit"])' "${RUNTIME_SEED_MANIFEST}")"
{
  echo "# APK Wizard Report"
  echo
  echo "Embedded runtime seed source: \`xoureldeen/Vectras-VM-Android@${source_commit}\`"
  echo
  echo "Boundary: embedded PRoot+Alpine seed verified in APK != QEMU distribution installed != physical VM boot."
  echo
  echo "| lane | policy | abis | apk_size_bytes |"
  echo "|---|---|---|---:|"
  while IFS='|' read -r lane policy abis size; do
    echo "| ${lane} | ${policy} | ${abis} | ${size} |"
  done < "${OUT_DIR}/sizes.tsv"
} > "${OUT_DIR}/REPORT.md"

echo "[wizard] artifacts in ${OUT_DIR}"
