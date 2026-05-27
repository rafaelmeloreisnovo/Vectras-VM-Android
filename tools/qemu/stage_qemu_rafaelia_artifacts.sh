#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
stage_qemu_rafaelia_artifacts.sh

Stages externally produced qemu_rafaelia artifacts into a Vectra runtime layout.
This script does not download artifacts and does not commit binaries.

Usage:
  tools/qemu/stage_qemu_rafaelia_artifacts.sh --artifact-dir DIR --runtime-root DIR [--lockfile FILE]

Example:
  tools/qemu/stage_qemu_rafaelia_artifacts.sh \
    --artifact-dir qemu_rafaelia_artifacts \
    --runtime-root /data/data/com.vectras.vm/files

Expected artifact names, when present:
  qemu-system-x86_64-rafaelia
  qemu-system-aarch64-rafaelia
  qemu-system-i386-rafaelia
  qemu-system-ppc-rafaelia
  RELEASEX64_OVMF.fd
  RELEASEX64_OVMF_VARS.fd
  QEMU_EFI.img
  QEMU_VARS.img
  bios-vectras.bin

EOF
}

artifact_dir=""
runtime_root=""
lockfile="tools/qemu_rafaelia_assets.lock.yml"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact-dir)
      artifact_dir="${2:-}"; shift 2 ;;
    --runtime-root)
      runtime_root="${2:-}"; shift 2 ;;
    --lockfile)
      lockfile="${2:-}"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2 ;;
  esac
done

if [[ -z "$artifact_dir" || -z "$runtime_root" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -d "$artifact_dir" ]]; then
  echo "Artifact directory not found: $artifact_dir" >&2
  exit 1
fi

if [[ ! -f "$lockfile" ]]; then
  echo "Lockfile not found: $lockfile" >&2
  exit 1
fi

bin_dir="$runtime_root/distro/usr/bin"
firmware_dir="$runtime_root/firmware"
mkdir -p "$bin_dir" "$firmware_dir"

sha256_of() {
  sha256sum "$1" | awk '{print $1}'
}

copy_if_present() {
  local src="$1"
  local dst="$2"
  local mode="$3"

  if [[ ! -f "$src" ]]; then
    echo "SKIP missing $(basename "$src")"
    return 0
  fi

  install -m "$mode" "$src" "$dst"
  echo "STAGED $src -> $dst sha256=$(sha256_of "$dst")"
}

# QEMU binaries.
for bin in \
  qemu-system-x86_64-rafaelia \
  qemu-system-aarch64-rafaelia \
  qemu-system-i386-rafaelia \
  qemu-system-ppc-rafaelia; do
  copy_if_present "$artifact_dir/$bin" "$bin_dir/$bin" 0755
done

# Firmware/runtime templates.
for fw in \
  RELEASEX64_OVMF.fd \
  RELEASEX64_OVMF_VARS.fd \
  QEMU_EFI.img \
  QEMU_VARS.img \
  bios-vectras.bin; do
  copy_if_present "$artifact_dir/$fw" "$firmware_dir/$fw" 0644
done

echo "Runtime staging complete. No files were added to git."
