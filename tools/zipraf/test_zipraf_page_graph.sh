#!/usr/bin/env sh
set -eu

CC_BIN="${CC:-cc}"
OUT="${TMPDIR:-/tmp}/zipraf_page_graph_kat.$$"
trap 'rm -f "$OUT"' EXIT INT TERM

"$CC_BIN" \
  -std=c11 -Wall -Wextra -Werror -pedantic \
  -Iengine/rmr/include \
  engine/rmr/src/rmr_zipraf_page_graph.c \
  demo_cli/src/zipraf_page_graph_selftest.c \
  -o "$OUT"

"$OUT"
