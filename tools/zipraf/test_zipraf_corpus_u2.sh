#!/usr/bin/env bash
set -euo pipefail

CC_BIN="${CC:-cc}"
BLAKE3_ROOT="${BLAKE3_ROOT:-_deps/blake3}"
BLAKE3_COMMIT="${BLAKE3_COMMIT:-ff6991d8b13f5b4b16dc311b5acc9c63ae835152}"
BUILD_DIR="${ZIPRAF_CORPUS_BUILD_DIR:-build/zipraf-corpus-u2}"
CORPUS_DIR="${BUILD_DIR}/corpus"
SCANNER="${BUILD_DIR}/zipraf-corpus-scanner"
MANIFEST="${BUILD_DIR}/manifest.json"
MANIFEST_REPEAT="${BUILD_DIR}/manifest-repeat.json"

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"

for path in \
  "${BLAKE3_ROOT}/c/blake3.h" \
  "${BLAKE3_ROOT}/c/blake3.c" \
  "${BLAKE3_ROOT}/c/blake3_dispatch.c" \
  "${BLAKE3_ROOT}/c/blake3_portable.c"
do
  test -f "${path}" || { echo "missing pinned BLAKE3 provider file: ${path}" >&2; exit 1; }
done

python3 tools/zipraf/generate_zipraf_corpus_u2_fixture.py --output "${CORPUS_DIR}"

"${CC_BIN}" \
  -std=c11 -Wall -Wextra -Werror -pedantic \
  -DBLAKE3_NO_SSE2 \
  -DBLAKE3_NO_SSE41 \
  -DBLAKE3_NO_AVX2 \
  -DBLAKE3_NO_AVX512 \
  -DRMR_BLAKE3_PROVIDER_COMMIT=\"${BLAKE3_COMMIT}\" \
  -Iengine/rmr/include \
  -I"${BLAKE3_ROOT}/c" \
  engine/rmr/src/rmr_zipraf_archive.c \
  engine/rmr/src/rmr_zipraf_payload_digest.c \
  "${BLAKE3_ROOT}/c/blake3.c" \
  "${BLAKE3_ROOT}/c/blake3_dispatch.c" \
  "${BLAKE3_ROOT}/c/blake3_portable.c" \
  demo_cli/src/zipraf_corpus_scanner.c \
  -o "${SCANNER}"

inputs=(
  "${CORPUS_DIR}/sample.zip"
  "${CORPUS_DIR}/sample.apk"
  "${CORPUS_DIR}/malformed.zip"
)

"${SCANNER}" \
  --output "${MANIFEST}" \
  --mapping-epoch 42 \
  --alignment 4096 \
  "${inputs[@]}"

"${SCANNER}" \
  --output "${MANIFEST_REPEAT}" \
  --mapping-epoch 42 \
  --alignment 4096 \
  "${inputs[@]}"

cmp "${MANIFEST}" "${MANIFEST_REPEAT}"
python3 -m json.tool "${MANIFEST}" >/dev/null
python3 tools/zipraf/validate_zipraf_corpus_u2_manifest.py "${MANIFEST}"
sha256sum "${MANIFEST}" | tee "${MANIFEST}.sha256"

python3 - "${MANIFEST}" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
summary = data["summary"]
print(json.dumps({
    "gate": "ZIPRAF_CORPUS_U2_HARNESS_V1",
    "status": "PASS",
    "archive_total": summary["archive_total"],
    "parsed_archives": summary["parsed_archives"],
    "parse_failures": summary["parse_failures"],
    "entry_total": summary["entry_total"],
    "extraction_performed": data["extraction_performed"],
    "execution_authorized": data["execution_authorized"],
    "u2_real_external_corpus": data["u2_real_external_corpus"],
}, sort_keys=True))
PY
