#!/usr/bin/env sh
set -eu

CC_BIN="${CC:-cc}"
BLAKE3_ROOT="${BLAKE3_ROOT:-_deps/blake3}"
BLAKE3_COMMIT="${BLAKE3_COMMIT:-ff6991d8b13f5b4b16dc311b5acc9c63ae835152}"
RECEIPT="${ZIPRAF_DIGEST_RECEIPT_PATH:-zipraf-payload-digest-u1.json}"
KAT="${TMPDIR:-/tmp}/zipraf_payload_digest_u1.$$"
trap 'rm -f "$KAT"' EXIT INT TERM

for path in \
  "$BLAKE3_ROOT/c/blake3.h" \
  "$BLAKE3_ROOT/c/blake3.c" \
  "$BLAKE3_ROOT/c/blake3_dispatch.c" \
  "$BLAKE3_ROOT/c/blake3_portable.c"
do
  if [ ! -f "$path" ]; then
    echo "missing pinned BLAKE3 provider file: $path" >&2
    exit 1
  fi
done

"$CC_BIN" \
  -std=c11 -Wall -Wextra -Werror -pedantic \
  -DBLAKE3_NO_SSE2 \
  -DBLAKE3_NO_SSE41 \
  -DBLAKE3_NO_AVX2 \
  -DBLAKE3_NO_AVX512 \
  -DRMR_BLAKE3_PROVIDER_COMMIT=\"$BLAKE3_COMMIT\" \
  -Iengine/rmr/include \
  -I"$BLAKE3_ROOT/c" \
  engine/rmr/src/rmr_zipraf_payload_digest.c \
  "$BLAKE3_ROOT/c/blake3.c" \
  "$BLAKE3_ROOT/c/blake3_dispatch.c" \
  "$BLAKE3_ROOT/c/blake3_portable.c" \
  demo_cli/src/zipraf_payload_digest_selftest.c \
  -o "$KAT"

"$KAT" | tee "$RECEIPT"
python3 -m json.tool "$RECEIPT" >/dev/null
sha256sum "$RECEIPT" | tee "${RECEIPT}.sha256"
