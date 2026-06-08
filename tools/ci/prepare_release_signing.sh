#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: prepare_release_signing.sh --mode <signed|unsigned|auto>

Required secrets for signed mode (canonical namespace):
  VECTRAS_RELEASE_KEYSTORE_BASE64
  VECTRAS_RELEASE_STORE_PASSWORD
  VECTRAS_RELEASE_KEY_ALIAS
  VECTRAS_RELEASE_KEY_PASSWORD

Legacy ANDROID_* signing secret names are intentionally not accepted here.
Use the VECTRAS_RELEASE_* namespace for official release lanes.

Outputs (when GITHUB_ENV exists):
  VECTRAS_CI_RELEASE_FLAGS
  VECTRAS_CI_SIGNING_ARGS
  VECTRAS_RELEASE_STORE_FILE (if signed)
USAGE
}

MODE=""
KEYSTORE_OUT=""
ALLOW_LEGACY_FALLBACK="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$MODE" ]]; then
  echo "::error::--mode is required" >&2
  exit 1
fi

if [[ "$ALLOW_LEGACY_FALLBACK" == "true" ]]; then
  echo "::warning::--allow-legacy-fallback is ignored; legacy signing fallbacks are disabled" >&2
fi

missing_android_signing_secrets() {
  local missing=()

  [[ -n "${VECTRAS_RELEASE_KEYSTORE_BASE64:-}" ]] || missing+=("VECTRAS_RELEASE_KEYSTORE_BASE64")
  [[ -n "${VECTRAS_RELEASE_STORE_PASSWORD:-}" ]] || missing+=("VECTRAS_RELEASE_STORE_PASSWORD")
  [[ -n "${VECTRAS_RELEASE_KEY_ALIAS:-}" ]] || missing+=("VECTRAS_RELEASE_KEY_ALIAS")
  [[ -n "${VECTRAS_RELEASE_KEY_PASSWORD:-}" ]] || missing+=("VECTRAS_RELEASE_KEY_PASSWORD")

  if ((${#missing[@]} > 0)); then
    printf '%s\n' "${missing[@]}"
    return 1
  fi

  return 0
}

has_required_signing_secrets() {
  missing_android_signing_secrets >/dev/null
}

resolve_keystore() {
  local out_path="$KEYSTORE_OUT"

  if [[ -z "$out_path" ]]; then
    local tmp_dir="${RUNNER_TEMP:-/tmp}"
    out_path="$(mktemp "${tmp_dir%/}/vectras-release-keystore.XXXXXX.jks")"
  fi

  umask 077
  printf '%s' "${VECTRAS_RELEASE_KEYSTORE_BASE64}" | base64 --decode > "$out_path"

  if [[ ! -s "$out_path" ]]; then
    echo "::error::VECTRAS_RELEASE_KEYSTORE_BASE64 provided but decoded keystore is empty" >&2
    exit 1
  fi

  chmod 600 "$out_path"

  export VECTRAS_RELEASE_STORE_FILE="$out_path"
  export VECTRAS_RELEASE_STORE_PASSWORD="$VECTRAS_RELEASE_STORE_PASSWORD"
  export VECTRAS_RELEASE_KEY_ALIAS="$VECTRAS_RELEASE_KEY_ALIAS"
  export VECTRAS_RELEASE_KEY_PASSWORD="$VECTRAS_RELEASE_KEY_PASSWORD"
}

signed_ready="false"
if has_required_signing_secrets; then
  resolve_keystore
  signed_ready="true"
fi

RELEASE_FLAGS=""
SIGNING_ARGS=""

case "$MODE" in
  signed)
    if [[ "$signed_ready" != "true" ]]; then
      missing="$(missing_android_signing_secrets || true)"
      if [[ -n "$missing" ]]; then
        echo "::error::signed mode requires all Android signing secrets. Missing: ${missing//$'\n'/, }" >&2
      else
        echo "::error::signed mode requires valid Android signing secrets and keystore decoding" >&2
      fi
      exit 1
    fi
    ;;
  unsigned)
    RELEASE_FLAGS="-PALLOW_UNSIGNED_RELEASE=true -PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true -PCI_INTERNAL_VALIDATION=true"
    ;;
  auto)
    if [[ "$signed_ready" != "true" ]]; then
      RELEASE_FLAGS="-PALLOW_UNSIGNED_RELEASE=true -PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true -PCI_INTERNAL_VALIDATION=true"
    fi
    ;;
  *)
    echo "::error::Invalid mode: $MODE" >&2
    exit 1
    ;;
esac

if [[ -z "$RELEASE_FLAGS" ]]; then
  SIGNING_ARGS="-Pandroid.injected.signing.store.file=${VECTRAS_RELEASE_STORE_FILE} -Pandroid.injected.signing.store.password=${VECTRAS_RELEASE_STORE_PASSWORD} -Pandroid.injected.signing.key.alias=${VECTRAS_RELEASE_KEY_ALIAS} -Pandroid.injected.signing.key.password=${VECTRAS_RELEASE_KEY_PASSWORD}"
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "VECTRAS_CI_RELEASE_FLAGS=${RELEASE_FLAGS}"
    echo "VECTRAS_CI_SIGNING_ARGS=${SIGNING_ARGS}"
    if [[ -n "${VECTRAS_RELEASE_STORE_FILE:-}" ]]; then
      echo "VECTRAS_RELEASE_STORE_FILE=${VECTRAS_RELEASE_STORE_FILE}"
    fi
  } >> "$GITHUB_ENV"
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "release_flags=${RELEASE_FLAGS}"
    echo "signing_args=${SIGNING_ARGS}"
    echo "signed_ready=${signed_ready}"
  } >> "$GITHUB_OUTPUT"
fi

echo "Resolved release signing mode=${MODE} signed_ready=${signed_ready}"
