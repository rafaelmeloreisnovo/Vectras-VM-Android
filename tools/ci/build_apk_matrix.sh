#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${SDK_PATH}" ]]; then
  echo "[ERROR] ANDROID_HOME or ANDROID_SDK_ROOT must be set."
  exit 2
fi

if [[ ! -f local.properties ]]; then
  echo "sdk.dir=${SDK_PATH}" > local.properties
fi

COMMON_ARGS=("-PallowLocalHeavyValidationBypass=${ALLOW_LOCAL_HEAVY_VALIDATION_BYPASS:-true}")

# sempre gera trilha unsigned para validação estrutural
./gradlew "${COMMON_ARGS[@]}" -Psigning_mode=unsigned :app:assembleDebug :app:assembleRelease

mkdir -p app/build/outputs/apk/release-unsigned-snapshot
find app/build/outputs/apk/release -maxdepth 1 -type f -name "*.apk" -exec cp {} app/build/outputs/apk/release-unsigned-snapshot/ \;

# trilha signed só executa quando credenciais canônicas VECTRAS_RELEASE_* estão presentes
signing_output="$(mktemp "${TMPDIR:-/tmp}/vectras-signing-output.XXXXXX")"
signing_stderr="$(mktemp "${TMPDIR:-/tmp}/vectras-signing-stderr.XXXXXX")"
if GITHUB_OUTPUT="$signing_output" ./tools/ci/prepare_release_signing.sh --mode auto >/dev/null 2>"$signing_stderr"; then
  cat "$signing_stderr" >&2 || true
  signed_ready="$(awk -F= '$1=="signed_ready" {print $2; exit}' "$signing_output")"
  signing_args="$(awk -F= '$1=="signing_args" {sub(/^signing_args=/, ""); print; exit}' "$signing_output")"
  if [[ "$signed_ready" == "true" && -n "$signing_args" ]]; then
    read -r -a signing_args_array <<< "$signing_args"
    ./gradlew \
      "${COMMON_ARGS[@]}" \
      -Psigning_mode=signed \
      -PciRelease=true \
      "${signing_args_array[@]}" \
      :app:assembleRelease
  fi
else
  cat "$signing_stderr" >&2 || true
  echo "[ERROR] Failed to resolve release signing contract." >&2
  exit 1
fi

echo "[OK] APK build finished"
find app/build/outputs/apk -type f -name '*.apk' -print | sort

echo "[OK] ABI and signature report"
while IFS= read -r apk; do
  echo "--- ${apk}"
  if command -v aapt >/dev/null 2>&1; then
    aapt dump badging "$apk" | rg "native-code|package:"
  fi
  if command -v apksigner >/dev/null 2>&1; then
    apksigner verify --print-certs "$apk" || true
  fi
done < <(find app/build/outputs/apk -type f -name '*.apk' | sort)
