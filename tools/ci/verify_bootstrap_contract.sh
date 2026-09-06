#!/usr/bin/env bash
set -euo pipefail

STRICT_MODE=0
if [[ "${1:-}" == "--strict" ]]; then
  STRICT_MODE=1
  shift
fi

GRADLE_FLAGS=("$@")
GENERATED_ROOT="app/build/generated/bootstrapAssets"
BOOTSTRAP_LAYOUT_RECEIPT="app/build/reports/bootstrap/bootstrap-layout-normalization.json"
ALPINE_RECEIPT_PATH="app/build/reports/bootstrap/alpine19-materialization.json"
ROOTFS_NORMALIZATION_RECEIPT="app/build/reports/bootstrap/rootfs-symlink-normalization.json"
QEMU_RECEIPT_PATH="app/build/reports/bootstrap/qemu19-materialization.json"

BOOTSTRAP_CONTRACT="official=pinned bootstrap TARs + pinned alpine19 rootfs TARs + loader.apk ; CI-installable=pinned base + embedded qemu19 + APK-safe derived layouts ; fallback=JNI ZIP compatibility only"
echo "[verify_bootstrap_contract] Bootstrap contract => ${BOOTSTRAP_CONTRACT}"

echo "[verify_bootstrap_contract] Materializing PRoot bootstrap TAR assets from exact original-upstream Git commit..."
python3 tools/ci/materialize_bootstrap_assets.py

echo "[verify_bootstrap_contract] Materializing Alpine19 rootfs TAR assets from exact original-upstream Git commit..."
python3 tools/bootstrap/materialize_embedded_runtime_seed_assets.py \
  --target-root "${GENERATED_ROOT}" \
  --families alpine19 \
  --abis arm64-v8a,armeabi-v7a,x86,x86_64 \
  --receipt "${ALPINE_RECEIPT_PATH}"

# Build qemu19 from the exact, still-unmodified Alpine source bytes so the frozen
# QEMU runtime witnesses remain meaningful. CI app artifacts are installable
# artifacts, therefore they must carry the third runtime layer as well.
if [[ -n "${CI:-}" || -n "${GITHUB_ACTIONS:-}" ]]; then
  echo "[verify_bootstrap_contract] CI detected: materializing embedded QEMU19 ARM runtime..."
  bash tools/bootstrap/materialize_alpine_qemu_runtime.sh \
    --target-root "${GENERATED_ROOT}" \
    --abis arm64-v8a,armeabi-v7a \
    --receipt "${QEMU_RECEIPT_PATH}"
else
  echo "[verify_bootstrap_contract] Non-CI verification: qemu19 build-time materialization skipped (Docker-backed lane)."
fi

# Derive the actual APK-carried TARs only after all source/witness hashes have
# been verified. This closes two device-side ordering/path gaps without mutating
# provenance: usr/tmp is guaranteed for PRoot post-check, and absolute Alpine
# symlinks become equivalent rootfs-relative links for Android host-side checks.
echo "[verify_bootstrap_contract] Normalizing PRoot bootstrap runtime layout..."
python3 tools/bootstrap/normalize_bootstrap_tar_layout.py \
  --root "${GENERATED_ROOT}/bootstrap" \
  --abis arm64-v8a,armeabi-v7a,x86,x86_64 \
  --receipt "${BOOTSTRAP_LAYOUT_RECEIPT}"

echo "[verify_bootstrap_contract] Normalizing Alpine19 rootfs symlinks..."
python3 tools/bootstrap/normalize_rootfs_tar_symlinks.py \
  --root "${GENERATED_ROOT}" \
  --families alpine19 \
  --abis arm64-v8a,armeabi-v7a,x86,x86_64 \
  --receipt "${ROOTFS_NORMALIZATION_RECEIPT}"

if [[ -s "${QEMU_RECEIPT_PATH}" ]]; then
  echo "[verify_bootstrap_contract] Normalizing QEMU19 rootfs symlinks..."
  python3 tools/bootstrap/normalize_rootfs_tar_symlinks.py \
    --root "${GENERATED_ROOT}" \
    --families qemu19 \
    --abis arm64-v8a,armeabi-v7a \
    --receipt "app/build/reports/bootstrap/qemu19-rootfs-symlink-normalization.json"
fi

echo "[verify_bootstrap_contract] Running :app:verifyShellLoaderArtifact (strict gate)..."
./tools/gradle_with_jdk21.sh :app:verifyShellLoaderArtifact "${GRADLE_FLAGS[@]}"
echo "[verify_bootstrap_contract] Running :app:syncShellLoaderBootstrap to materialize generated loader.apk..."
./tools/gradle_with_jdk21.sh :app:syncShellLoaderBootstrap "${GRADLE_FLAGS[@]}"

LOADER_PATH="${GENERATED_ROOT}/bootstrap/loader.apk"
if [[ ! -s "${LOADER_PATH}" ]]; then
  echo "::error::Bootstrap contract failed: ${LOADER_PATH} ausente ou vazio. Ação: execute ':shell-loader:assembleRelease' (ou variante com -PloaderVariant), depois ':app:syncShellLoaderBootstrap'." >&2
  exit 1
fi

RECEIPT_PATH="app/build/reports/bootstrap/bootstrap-materialization.json"
if [[ ! -s "${RECEIPT_PATH}" ]]; then
  echo "::error::Bootstrap provenance receipt ausente: ${RECEIPT_PATH}" >&2
  exit 1
fi
if [[ ! -s "${ALPINE_RECEIPT_PATH}" ]]; then
  echo "::error::Alpine19 provenance receipt ausente: ${ALPINE_RECEIPT_PATH}" >&2
  exit 1
fi
if [[ ! -s "${BOOTSTRAP_LAYOUT_RECEIPT}" ]]; then
  echo "::error::Bootstrap layout receipt ausente: ${BOOTSTRAP_LAYOUT_RECEIPT}" >&2
  exit 1
fi
if [[ -n "${CI:-}" || -n "${GITHUB_ACTIONS:-}" ]]; then
  if [[ ! -s "${QEMU_RECEIPT_PATH}" ]]; then
    echo "::error::CI installable runtime contract failed: QEMU19 receipt ausente: ${QEMU_RECEIPT_PATH}" >&2
    exit 1
  fi
fi

echo "[verify_bootstrap_contract] Validating official bootstrap contract via tools/verify_bootstrap_assets.py --strict-generated-assets"
python3 tools/verify_bootstrap_assets.py --strict-generated-assets
if [[ "${STRICT_MODE}" == "1" ]]; then
  echo "[verify_bootstrap_contract] Strict mode enabled: Gradle verifyBootstrapAssets is mandatory."
fi
echo "[verify_bootstrap_contract] Running Gradle verifyBootstrapAssets"
./tools/gradle_with_jdk21.sh verifyBootstrapAssets "${GRADLE_FLAGS[@]}"
