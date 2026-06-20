#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${REPO_ROOT}/artifacts/apk-wizard"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/workspace/android-sdk}}"
GRADLEW="${REPO_ROOT}/gradlew"

mkdir -p "${OUT_DIR}"

echo "[wizard] bootstrap Android SDK root=${SDK_ROOT}"
"${REPO_ROOT}/tools/ci/bootstrap_local_android_sdk.sh" --sdk-root "${SDK_ROOT}"

build_lane() {
  local lane_name="$1"
  local policy="$2"
  local abis="$3"
  local ci_internal="$4"
  local apk_src="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
  local apk_dst="${OUT_DIR}/${lane_name}.apk"

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
  local apk_size
  apk_size="$(stat -c '%s' "${apk_dst}")"
  echo "${lane_name}|${policy}|${abis}|${apk_size}" >> "${OUT_DIR}/sizes.tsv"
}

rm -f "${OUT_DIR}/sizes.tsv"
build_lane "app-debug-arm64-v8a" "arm64-only" "arm64-v8a" "false"
build_lane "app-debug-arm32-arm64" "arm32-arm64" "arm64-v8a,armeabi-v7a" "true"

{
  echo "# APK Wizard Report"
  echo
  echo "| lane | policy | abis | apk_size_bytes |"
  echo "|---|---|---|---:|"
  while IFS='|' read -r lane policy abis size; do
    echo "| ${lane} | ${policy} | ${abis} | ${size} |"
  done < "${OUT_DIR}/sizes.tsv"
} > "${OUT_DIR}/REPORT.md"

echo "[wizard] artifacts in ${OUT_DIR}"
