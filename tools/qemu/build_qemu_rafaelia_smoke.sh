#!/usr/bin/env bash
set -euo pipefail

# Build helper for the external source repository.
# This script intentionally writes into ignored local directories only.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

QEMU_REPO_URL="${QEMU_REPO_URL:-https://github.com/rafaelmeloreisnovo/qemu_rafaelia.git}"
QEMU_REF="${QEMU_REF:-master}"
WORKDIR="${WORKDIR:-$ROOT/qemu_rafaelia_build}"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT/qemu_rafaelia_artifacts}"

mkdir -p "$WORKDIR" "$ARTIFACT_DIR"

if [[ ! -d "$WORKDIR/.git" ]]; then
  git clone --depth 1 --branch "$QEMU_REF" "$QEMU_REPO_URL" "$WORKDIR"
else
  git -C "$WORKDIR" fetch --depth 1 origin "$QEMU_REF"
  git -C "$WORKDIR" checkout FETCH_HEAD
fi

cd "$WORKDIR"

./configure \
  --target-list=x86_64-softmmu \
  --disable-werror \
  --disable-docs \
  --disable-gtk \
  --disable-sdl \
  --disable-curses \
  --disable-vnc-jpeg

ninja -C build subprojects/libvhost-user/libvhost-user.a
make -f hw/core/Makefile.integration all
make -f hw/core/Makefile.integration test

cp -v build/subprojects/libvhost-user/libvhost-user.a "$ARTIFACT_DIR/" || true
cp -v build/config.log "$ARTIFACT_DIR/" || true
cp -v build/config-host.h "$ARTIFACT_DIR/" || true

# If a full qemu-system binary is built by a later profile, copy it here.
find build -type f -perm -111 -name 'qemu-system-*' -maxdepth 3 -exec cp -v {} "$ARTIFACT_DIR/" \; 2>/dev/null || true

(
  cd "$ARTIFACT_DIR"
  sha256sum * 2>/dev/null | sort > SHA256SUMS || true
)

echo "qemu_rafaelia smoke/integration artifacts staged at: $ARTIFACT_DIR"
