#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${ROOT_DIR}/build"
CC_BIN="${CC:-cc}"
CFLAGS="${CFLAGS:--O2 -Wall -Wextra}"

mkdir -p "${BUILD_DIR}"

HOST_ARCH="${RAFPHI_FORCE_ARCH:-$(uname -m 2>/dev/null || echo unknown)}"
HWCAPS=""
if [[ -r /proc/cpuinfo ]]; then
  HWCAPS="$(grep -m1 -E 'Features|flags' /proc/cpuinfo | sed 's/^[^:]*:[[:space:]]*//' || true)"
fi

BACKEND_KIND="c"
BACKEND_SRC="${ROOT_DIR}/c/rafcode_phi_emit_word_c.c"
BACKEND_ARCH="unknown"

case "${HOST_ARCH}" in
  aarch64|arm64)
    BACKEND_KIND="asm"
    BACKEND_SRC="${ROOT_DIR}/asm/rafcode_phi_emit_word.S"
    BACKEND_ARCH="aarch64"
    ;;
  armv7l|armv7*|armhf|armeabi-v7a)
    BACKEND_KIND="asm"
    BACKEND_SRC="${ROOT_DIR}/asm/rafcode_phi_emit_word.S"
    BACKEND_ARCH="armv7"
    ;;
  x86_64|amd64)
    BACKEND_KIND="asm"
    BACKEND_SRC="${ROOT_DIR}/asm/rafcode_phi_emit_word.S"
    BACKEND_ARCH="x86_64"
    ;;
  riscv64)
    BACKEND_KIND="c"
    BACKEND_SRC="${ROOT_DIR}/c/rafcode_phi_emit_word_c.c"
    BACKEND_ARCH="riscv64"
    ;;
  *)
    BACKEND_KIND="c"
    BACKEND_SRC="${ROOT_DIR}/c/rafcode_phi_emit_word_c.c"
    BACKEND_ARCH="unknown"
    ;;
esac

printf 'rafcode_phi.build.host_arch=%s\n' "${HOST_ARCH}"
printf 'rafcode_phi.build.backend_kind=%s\n' "${BACKEND_KIND}"
printf 'rafcode_phi.build.backend_arch=%s\n' "${BACKEND_ARCH}"
printf 'rafcode_phi.build.backend_src=%s\n' "${BACKEND_SRC}"
printf 'rafcode_phi.build.hwcaps=%s\n' "${HWCAPS}"

if [[ ! -f "${BACKEND_SRC}" ]]; then
  printf 'erro: backend fonte ausente: %s\n' "${BACKEND_SRC}" >&2
  printf 'dica: o caminho legado "asm/rafaelia_core.S" não existe neste repo.\n' >&2
  printf 'use: %s\n' "${ROOT_DIR}/asm/rafcode_phi_emit_word.S" >&2
  exit 2
fi

"${CC_BIN}" ${CFLAGS} -I"${ROOT_DIR}/include" -c "${ROOT_DIR}/c/rafcode_phi_front_shell.c" -o "${BUILD_DIR}/rafcode_phi_front_shell.o"
"${CC_BIN}" ${CFLAGS} -I"${ROOT_DIR}/include" -c "${ROOT_DIR}/c/rafcode_phi_vecbit.c" -o "${BUILD_DIR}/rafcode_phi_vecbit.o"
"${CC_BIN}" ${CFLAGS} -I"${ROOT_DIR}/include" -c "${BACKEND_SRC}" -o "${BUILD_DIR}/rafcode_phi_emit_word.o"
"${CC_BIN}" ${CFLAGS} -I"${ROOT_DIR}/include" -c "${ROOT_DIR}/c/rafcode_phi_cli.c" -o "${BUILD_DIR}/rafcode_phi_cli.o"

"${CC_BIN}" ${CFLAGS} \
  "${BUILD_DIR}/rafcode_phi_front_shell.o" \
  "${BUILD_DIR}/rafcode_phi_vecbit.o" \
  "${BUILD_DIR}/rafcode_phi_emit_word.o" \
  "${BUILD_DIR}/rafcode_phi_cli.o" \
  -o "${BUILD_DIR}/rafcode_phi_cli"

printf 'ok: %s\n' "${BUILD_DIR}/rafcode_phi_cli"
