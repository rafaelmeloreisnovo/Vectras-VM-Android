#!/usr/bin/env bash
set -euo pipefail

STRICT_MODE=0
if [[ "${1:-}" == "--strict" ]]; then
  STRICT_MODE=1
  shift
fi

GRADLE_FLAGS=("$@")

BOOTSTRAP_CONTRACT="official=pinned TAR assets + loader.apk ; fallback=JNI ZIP compatibility only"
echo "[verify_bootstrap_contract] Bootstrap contract => ${BOOTSTRAP_CONTRACT}"

echo "[verify_bootstrap_contract] Materializing TAR assets from exact external Git commit..."
python3 tools/ci/materialize_bootstrap_assets.py

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
if [[ ! -s "${RECEIPT_PATH}" ]]; then
  echo "::error::Bootstrap provenance receipt ausente: ${RECEIPT_PATH}" >&2
  exit 1
fi

echo "[verify_bootstrap_contract] Validating official contract via tools/verify_bootstrap_assets.py --strict-generated-assets"
python3 tools/verify_bootstrap_assets.py --strict-generated-assets
if [[ "${STRICT_MODE}" == "1" ]]; then
  echo "[verify_bootstrap_contract] Strict mode enabled: Gradle verifyBootstrapAssets is mandatory."
fi
echo "[verify_bootstrap_contract] Running Gradle verifyBootstrapAssets"
./tools/gradle_with_jdk21.sh verifyBootstrapAssets "${GRADLE_FLAGS[@]}"
