#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail=0

# Files matching these globs must not be versioned in the source tree.
# They must be produced by CI/build scripts or fetched from a manifest with SHA-256.
patterns=(
  "app/src/main/assets/roms/*.img"
  "app/src/main/assets/roms/*.fd"
  "app/src/main/assets/roms/*.bin"
  "app/src/main/assets/alpine19/*.tar"
  "app/src/main/assets/bootstrap/*.tar"
  "app/src/main/jniLibs/**/*.so"
  "3dfx/*.iso"
  "Incluir/*.zip"
  "Incluir/*.docx"
  ".ci/*.zip"
  "*.apk"
  "*.aab"
  "*.dex"
  "*.jks"
  "*.qcow2"
  "*.raw"
  "*.7z"
  "*.rar"
)

allowed_paths=(
  "tools/qemu_rafaelia_assets.lock.yml"
  "docs/qemu/RAFAELIA_QEMU_VECTRA_INTEGRATION.md"
)

is_allowed() {
  local path="$1"
  for allowed in "${allowed_paths[@]}"; do
    [[ "$path" == "./$allowed" ]] && return 0
  done
  return 1
}

echo "[Vectra] checking forbidden repository binaries..."

shopt -s globstar nullglob
for pat in "${patterns[@]}"; do
  for f in $pat; do
    [[ -f "$f" ]] || continue
    case "./$f" in
      ./.git/*|./build/*|./app/build/*|./shell-loader/build/*|./qemu_rafaelia_build/*|./qemu_rafaelia_artifacts/*)
        continue
        ;;
    esac
    if is_allowed "./$f"; then
      continue
    fi
    echo "FORBIDDEN_BINARY: $f"
    fail=1
  done
done
shopt -u globstar nullglob

if [[ "$fail" -ne 0 ]]; then
  cat >&2 <<'EOF'

Binary artifact gate failed.
Keep QEMU/firmware/rootfs/APK/shared-object blobs out of git.
Use:
  - tools/qemu_rafaelia_assets.lock.yml for provenance/hash/runtime path
  - qemu_rafaelia repository or CI artifacts for source/build outputs
  - generated build directories for local artifacts

EOF
  exit 1
fi

echo "OK: no forbidden binary artifacts found."
