#!/usr/bin/env sh
set -eu

CC_BIN="${CC:-cc}"
KAT="${TMPDIR:-/tmp}/zipraf_archive_binding_kat.$$"
PROBE="${TMPDIR:-/tmp}/zipraf_host_mmap_probe.$$"
trap 'rm -f "$KAT" "$PROBE"' EXIT INT TERM

"$CC_BIN" \
  -std=c11 -Wall -Wextra -Werror -pedantic \
  -Iengine/rmr/include \
  engine/rmr/src/rmr_zipraf_archive.c \
  engine/rmr/src/rmr_zipraf_binding.c \
  demo_cli/src/zipraf_archive_selftest.c \
  -o "$KAT"

"$KAT"

"$CC_BIN" \
  -std=c11 -Wall -Wextra -Werror -pedantic \
  -Iengine/rmr/include \
  engine/rmr/src/rmr_zipraf_archive.c \
  demo_cli/src/zipraf_host_mmap_probe.c \
  -o "$PROBE"

if [ -n "${ZIPRAF_RECEIPT_PATH:-}" ]; then
  "$PROBE" | tee "$ZIPRAF_RECEIPT_PATH"
else
  "$PROBE"
fi
