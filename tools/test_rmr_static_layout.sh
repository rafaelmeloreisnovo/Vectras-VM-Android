#!/usr/bin/env sh
set -eu

CC_BIN="${CC:-cc}"
OUT="${TMPDIR:-/tmp}/rmr_static_layout_selftest"

"$CC_BIN" -O2 -std=c11 -Wall -Wextra -Werror -pedantic \
  -Iengine/rmr/include \
  engine/rmr/src/rmr_static_layout.c \
  demo_cli/src/rmr_static_layout_selftest.c \
  -o "$OUT"

"$OUT"
