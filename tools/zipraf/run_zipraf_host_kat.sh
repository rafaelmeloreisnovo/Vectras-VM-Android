#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${ROOT_DIR}/build/zipraf-host-kat"
JAR_PATH="${OUT_DIR}/zipraf-host-kat.jar"
RESULT_PATH="${OUT_DIR}/result.json"

command -v kotlinc >/dev/null 2>&1 || {
  echo "kotlinc não encontrado no PATH" >&2
  exit 2
}
command -v java >/dev/null 2>&1 || {
  echo "java não encontrado no PATH" >&2
  exit 2
}

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

kotlinc \
  "${ROOT_DIR}/app/src/main/java/com/vectras/vm/vectra/ZiprafDirectRuntime.kt" \
  "${ROOT_DIR}/app/src/main/java/com/vectras/vm/vectra/ZiprafDirectStoreSession.kt" \
  "${ROOT_DIR}/app/src/main/java/com/vectras/vm/vectra/ZiprafDirectEntryPolicy.kt" \
  "${ROOT_DIR}/tools/zipraf/ZiprafDirectRuntimeKat.kt" \
  -include-runtime \
  -d "${JAR_PATH}"

java -jar "${JAR_PATH}" | tee "${RESULT_PATH}"
grep -q '"status": "PASS"' "${RESULT_PATH}"

echo "ZIPRAF_HOST_KAT=PASS"
echo "RESULT=${RESULT_PATH}"
