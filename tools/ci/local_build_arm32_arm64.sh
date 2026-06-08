#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

SIGNING_MODE="${1:-unsigned}"
if [[ "$SIGNING_MODE" != "unsigned" && "$SIGNING_MODE" != "signed" ]]; then
  echo "Usage: $0 [unsigned|signed]" >&2
  exit 2
fi

ABI_PROFILE="internal_arm32_arm64"

read_prop() {
  local key="$1"
  awk -F= -v key="$key" '$1==key {gsub(/[[:space:]]/, "", $2); print $2; exit}' gradle.properties
}

COMPILE_API="$(read_prop compile.api)"
TOOLS_VERSION="$(read_prop tools.version)"
NDK_VERSION="$(read_prop ndk.version)"
CMAKE_VERSION="$(read_prop cmake.version)"
JAVA_VERSION="$(read_prop java.language.version)"

if [[ -z "${ANDROID_SDK_ROOT:-}" && -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_SDK_ROOT/ANDROID_HOME não definido."
  echo "Defina o SDK Android e execute novamente." >&2
  exit 1
fi

./tools/ci/prepare_android_env.sh --java-version "$JAVA_VERSION" --ndk-version "$NDK_VERSION"
./tools/ci/verify_android_local_properties_contract.sh

if command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager --licenses >/dev/null || true
  sdkmanager "platform-tools" "platforms;android-${COMPILE_API}" "build-tools;${TOOLS_VERSION}" "cmake;${CMAKE_VERSION}" "ndk;${NDK_VERSION}"
else
  echo "sdkmanager não encontrado no PATH; assumindo SDK já provisionado." >&2
fi

CI_RELEASE=false
EXTRA_FLAGS=()
if [[ "$SIGNING_MODE" == "signed" ]]; then
  CI_RELEASE=true
  signing_output="$(mktemp "${TMPDIR:-/tmp}/vectras-local-signing-output.XXXXXX")"
  GITHUB_OUTPUT="$signing_output" ./tools/ci/prepare_release_signing.sh --mode signed
  signing_args="$(awk -F= '$1=="signing_args" {sub(/^signing_args=/, ""); print; exit}' "$signing_output")"
  if [[ -z "$signing_args" ]]; then
    echo "Falha ao resolver argumentos de assinatura VECTRAS_RELEASE_* para build local signed." >&2
    exit 1
  fi
  read -r -a signing_args_array <<< "$signing_args"
  EXTRA_FLAGS+=("${signing_args_array[@]}")
else
  EXTRA_FLAGS+=("-PALLOW_UNSIGNED_RELEASE=true" "-PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true" "-PCI_INTERNAL_VALIDATION=true")
fi

./tools/gradle_with_jdk21.sh \
  :app:assembleRelease \
  :app:verifyDeliveredCompiledArtifacts \
  -PAPP_ABI_POLICY="$ABI_PROFILE" \
  -Psigning_mode="$SIGNING_MODE" \
  -PciRelease="$CI_RELEASE" \
  -PartifactVariants=release \
  -Pworkflow=local \
  -Plane=local-arm32-arm64 \
  -Pabi_profile="$ABI_PROFILE" \
  "${EXTRA_FLAGS[@]}"

echo "Artifacts gerados:"
find app/build/outputs -type f \( -name "*.apk" -o -name "*.aab" \) | sort
