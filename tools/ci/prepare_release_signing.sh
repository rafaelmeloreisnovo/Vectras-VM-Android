#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: prepare_release_signing.sh --mode <signed|unsigned|auto>

Required values for signed mode (canonical namespace):
  VECTRAS_RELEASE_STORE_FILE or VECTRAS_RELEASE_KEYSTORE_B64
  VECTRAS_RELEASE_STORE_PASSWORD
  VECTRAS_RELEASE_KEY_ALIAS
  VECTRAS_RELEASE_KEY_PASSWORD

Compatibility bridge (internal/legacy only):
  ANDROID_KEYSTORE_B64 -> VECTRAS_RELEASE_KEYSTORE_B64
  ANDROID_KEYSTORE_PASSWORD -> VECTRAS_RELEASE_STORE_PASSWORD
  ANDROID_KEY_ALIAS -> VECTRAS_RELEASE_KEY_ALIAS
  ANDROID_KEY_PASSWORD -> VECTRAS_RELEASE_KEY_PASSWORD

Official release lanes must configure VECTRAS_RELEASE_* directly.

Outputs (when GITHUB_ENV exists):
  VECTRAS_CI_RELEASE_FLAGS
  VECTRAS_CI_SIGNING_ARGS
  VECTRAS_RELEASE_STORE_FILE (if signed)
USAGE
}

MODE=""
KEYSTORE_OUT=""
ANDROID_COMPAT_USED="false"

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

bridge_android_compat_signing() {
  # Internal compatibility bridge only. Keep official release secrets in the
  # VECTRAS_RELEASE_* namespace and remove ANDROID_* from new workflows.
  local mapped=()

  if [[ -z "${VECTRAS_RELEASE_KEYSTORE_B64:-}" && -n "${ANDROID_KEYSTORE_B64:-}" ]]; then
    export VECTRAS_RELEASE_KEYSTORE_B64="${ANDROID_KEYSTORE_B64}"
    mapped+=("ANDROID_KEYSTORE_B64->VECTRAS_RELEASE_KEYSTORE_B64")
  fi
  if [[ -z "${VECTRAS_RELEASE_STORE_PASSWORD:-}" && -n "${ANDROID_KEYSTORE_PASSWORD:-}" ]]; then
    export VECTRAS_RELEASE_STORE_PASSWORD="${ANDROID_KEYSTORE_PASSWORD}"
    mapped+=("ANDROID_KEYSTORE_PASSWORD->VECTRAS_RELEASE_STORE_PASSWORD")
  fi
  if [[ -z "${VECTRAS_RELEASE_KEY_ALIAS:-}" && -n "${ANDROID_KEY_ALIAS:-}" ]]; then
    export VECTRAS_RELEASE_KEY_ALIAS="${ANDROID_KEY_ALIAS}"
    mapped+=("ANDROID_KEY_ALIAS->VECTRAS_RELEASE_KEY_ALIAS")
  fi
  if [[ -z "${VECTRAS_RELEASE_KEY_PASSWORD:-}" && -n "${ANDROID_KEY_PASSWORD:-}" ]]; then
    export VECTRAS_RELEASE_KEY_PASSWORD="${ANDROID_KEY_PASSWORD}"
    mapped+=("ANDROID_KEY_PASSWORD->VECTRAS_RELEASE_KEY_PASSWORD")
  fi

  if ((${#mapped[@]} > 0)); then
    ANDROID_COMPAT_USED="true"
    echo "::warning::Using ANDROID_* signing compatibility bridge for internal/legacy CI only: ${mapped[*]}" >&2
  fi
}

bridge_android_compat_signing

missing_signing_values() {
  local missing=()

  if [[ -z "${VECTRAS_RELEASE_STORE_FILE:-}" && -z "${VECTRAS_RELEASE_KEYSTORE_B64:-}" ]]; then
    missing+=("VECTRAS_RELEASE_STORE_FILE or VECTRAS_RELEASE_KEYSTORE_B64")
  fi
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
  missing_signing_values >/dev/null
}

resolve_keystore() {
  local out_path="$KEYSTORE_OUT"

  if [[ -z "$out_path" ]]; then
    local tmp_dir="${RUNNER_TEMP:-/tmp}"
    out_path="$(mktemp "${tmp_dir%/}/vectras-release-keystore.XXXXXX.jks")"
  fi

  if [[ -n "${VECTRAS_RELEASE_STORE_FILE:-}" ]]; then
    if [[ ! -s "${VECTRAS_RELEASE_STORE_FILE}" ]]; then
      echo "::error::VECTRAS_RELEASE_STORE_FILE is set but the keystore does not exist or is empty: ${VECTRAS_RELEASE_STORE_FILE}" >&2
      exit 1
    fi
    return 0
  fi

  umask 077
  printf '%s' "${VECTRAS_RELEASE_KEYSTORE_B64}" | base64 --decode > "$out_path"

  if [[ ! -s "$out_path" ]]; then
    echo "::error::VECTRAS_RELEASE_KEYSTORE_B64 provided but decoded keystore is empty" >&2
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
      missing="$(missing_signing_values || true)"
      if [[ -n "$missing" ]]; then
        echo "::error::signed mode requires the canonical VECTRAS_RELEASE_* signing contract. Missing: ${missing//$'\n'/, }" >&2
      else
        echo "::error::signed mode requires valid VECTRAS_RELEASE_* signing values and keystore resolution" >&2
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
    echo "android_compat_used=${ANDROID_COMPAT_USED}"
  } >> "$GITHUB_OUTPUT"
fi

echo "Resolved release signing mode=${MODE} signed_ready=${signed_ready}"
