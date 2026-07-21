#!/usr/bin/env sh
set -eu

CC_BIN="${CC:-cc}"
OUT="${TMPDIR:-/tmp}/rmr_visual_prototype_selftest"

"$CC_BIN" -O2 -std=c11 -Wall -Wextra -Werror -pedantic \
  -Iengine/rmr/include \
  engine/rmr/src/rmr_stability.c \
  engine/rmr/src/rmr_visual_prototype.c \
  demo_cli/src/rmr_visual_prototype_selftest.c \
  -o "$OUT"

"$OUT"
