#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=${TMPDIR:-/tmp}/raf_b7_orchestrator_selftest
CC_BIN=${CC:-cc}

"$CC_BIN" -std=c11 -O2 -Wall -Wextra -Werror -pedantic \
  -I"$ROOT/engine/rmr/include" \
  "$ROOT/engine/rmr/src/raf_b7_orchestrator.c" \
  "$ROOT/demo_cli/src/raf_b7_orchestrator_selftest.c" \
  -o "$OUT"

"$OUT"
