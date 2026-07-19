#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULT_DIR="${ROOT_DIR}/build/zipraf-local-gate"
SUMMARY_FILE="${RESULT_DIR}/summary.txt"
RUN_GRADLE="${RUN_GRADLE:-0}"
RUN_DEVICE="${RUN_DEVICE:-0}"

rm -rf "${RESULT_DIR}"
mkdir -p "${RESULT_DIR}"
: > "${SUMMARY_FILE}"

record() {
  printf '%s\n' "$1" | tee -a "${SUMMARY_FILE}"
}

record "ZIPRAF_LOCAL_GATE_BEGIN"

bash "${ROOT_DIR}/tools/zipraf/run_zipraf_host_kat.sh"
record "host_kat=PASS"

if [[ "${RUN_GRADLE}" == "1" ]]; then
  "${ROOT_DIR}/tools/gradle_with_jdk21.sh" \
    :app:testDebugUnitTest \
    --tests 'com.vectras.vm.vectra.ZiprafDirectRuntimeTest' \
    --tests 'com.vectras.vm.vectra.ZiprafDirectStoreSessionTest' \
    --tests 'com.vectras.vm.vectra.ZiprafDirectEntryPolicyTest' \
    -PdevFastPath=true \
    --stacktrace
  record "gradle_unit=PASS"
else
  record "gradle_unit=SKIPPED_SET_RUN_GRADLE_1"
fi

if [[ "${RUN_DEVICE}" == "1" ]]; then
  command -v adb >/dev/null 2>&1 || {
    record "android_device=BLOCKED_ADB_NOT_FOUND"
    exit 3
  }
  adb get-state >/dev/null
  "${ROOT_DIR}/tools/gradle_with_jdk21.sh" \
    :app:connectedDebugAndroidTest \
    -PdevFastPath=true \
    -Pandroid.testInstrumentationRunnerArguments.class=com.vectras.vm.vectra.ZiprafDirectRuntimeInstrumentedTest \
    --stacktrace
  record "android_device=PASS"
else
  record "android_device=SKIPPED_SET_RUN_DEVICE_1"
fi

record "ZIPRAF_LOCAL_GATE_END"
record "summary=${SUMMARY_FILE}"
