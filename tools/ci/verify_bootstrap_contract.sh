#!/usr/bin/env bash
set -euo pipefail

STRICT_MODE=0
if [[ "${1:-}" == "--strict" ]]; then
  STRICT_MODE=1
  shift
fi

GRADLE_FLAGS=("$@")

BOOTSTRAP_CONTRACT="official=pinned bootstrap TARs + pinned alpine19 rootfs TARs + loader.apk ; fallback=JNI ZIP compatibility only"
echo "[verify_bootstrap_contract] Bootstrap contract => ${BOOTSTRAP_CONTRACT}"

echo "[verify_bootstrap_contract] Materializing PRoot bootstrap TAR assets from exact original-upstream Git commit..."
python3 tools/ci/materialize_bootstrap_assets.py

echo "[verify_bootstrap_contract] Materializing Alpine19 rootfs TAR assets from exact original-upstream Git commit..."
python3 tools/bootstrap/materialize_embedded_runtime_seed_assets.py \
  --target-root app/build/generated/bootstrapAssets \
  --families alpine19 \
  --abis arm64-v8a,armeabi-v7a,x86,x86_64 \
  --receipt app/build/reports/bootstrap/alpine19-materialization.json

echo "[verify_bootstrap_contract] Running :app:verifyShellLoaderArtifact (strict gate)..."
./tools/gradle_with_jdk21.sh :app:verifyShellLoaderArtifact "${GRADLE_FLAGS[@]}"
echo "[verify_bootstrap_contract] Running :app:syncShellLoaderBootstrap to materialize generated loader.apk..."
./tools/gradle_with_jdk21.sh :app:syncShellLoaderBootstrap "${GRADLE_FLAGS[@]}"

LOADER_PATH="app/build/generated/bootstrapAssets/bootstrap/loader.apk"
if [[ ! -s "${LOADER_PATH}" ]]; then
  echo "::error::Bootstrap contract failed: ${LOADER_PATH} ausente ou vazio. Ação: execute ':shell-loader:assembleRelease' (ou variante com -PloaderVariant), depois ':app:syncShellLoaderBootstrap'." >&2
  exit 1
fi

RECEIPT_PATH="app/build/reports/bootstrap/bootstrap-materialization.json"
ALPINE_RECEIPT_PATH="app/build/reports/bootstrap/alpine19-materialization.json"
if [[ ! -s "${RECEIPT_PATH}" ]]; then
  echo "::error::Bootstrap provenance receipt ausente: ${RECEIPT_PATH}" >&2
  exit 1
fi
if [[ ! -s "${ALPINE_RECEIPT_PATH}" ]]; then
  echo "::error::Alpine19 provenance receipt ausente: ${ALPINE_RECEIPT_PATH}" >&2
  exit 1
fi

echo "[verify_bootstrap_contract] Validating official bootstrap contract via tools/verify_bootstrap_assets.py --strict-generated-assets"
python3 tools/verify_bootstrap_assets.py --strict-generated-assets
if [[ "${STRICT_MODE}" == "1" ]]; then
  echo "[verify_bootstrap_contract] Strict mode enabled: Gradle verifyBootstrapAssets is mandatory."
fi
echo "[verify_bootstrap_contract] Running Gradle verifyBootstrapAssets"
./tools/gradle_with_jdk21.sh verifyBootstrapAssets "${GRADLE_FLAGS[@]}"
