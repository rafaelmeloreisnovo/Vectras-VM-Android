#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: prepare_android_env.sh [--java-version <major>] [--sdk-root <path>] [--ndk-version <version>] [--local-properties <path>] [--require-sdkmanager]
USAGE
}

JAVA_VERSION_EXPECTED=""
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
LOCAL_PROPERTIES_PATH="${REPO_ROOT}/local.properties"
REQUIRE_SDKMANAGER="false"
NDK_VERSION_EXPECTED="${NDK_VERSION:-}"
DEFAULT_SDK_FALLBACKS=(
  "${REPO_ROOT}/.android-sdk"
  "/workspace/android-sdk"
  "/usr/lib/android-sdk"
  "/opt/android-sdk"
  "/opt/android-sdk-linux"
  "$HOME/Android/Sdk"
)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --java-version)
      JAVA_VERSION_EXPECTED="$2"
      shift 2
      ;;
    --sdk-root)
      SDK_ROOT="$2"
      shift 2
      ;;
    --local-properties)
      LOCAL_PROPERTIES_PATH="$2"
      shift 2
      ;;
    --ndk-version)
      NDK_VERSION_EXPECTED="$2"
      shift 2
      ;;
    --require-sdkmanager)
      REQUIRE_SDKMANAGER="true"
      shift
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

if [[ -n "$JAVA_VERSION_EXPECTED" ]]; then
  JAVA_BIN="${JAVA_HOME:-}/bin/java"
  if [[ ! -x "${JAVA_BIN}" ]]; then
    JAVA_BIN="$(command -v java || true)"
  fi
  if [[ -z "${JAVA_BIN}" || ! -x "${JAVA_BIN}" ]]; then
    for candidate in \
      "$HOME/.local/share/mise/installs/java/21/bin/java" \
      "$HOME/.local/share/mise/installs/java/17/bin/java" \
      "/usr/lib/jvm/java-21-openjdk-amd64/bin/java" \
      "/usr/lib/jvm/java-21-openjdk/bin/java" \
      "/usr/lib/jvm/temurin-21-jdk-amd64/bin/java" \
      "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" \
      "/usr/lib/jvm/java-17-openjdk/bin/java"; do
      if [[ -x "$candidate" ]]; then
        JAVA_BIN="$candidate"
        export JAVA_HOME="$(dirname "$(dirname "$candidate")")"
        export PATH="$JAVA_HOME/bin:$PATH"
        break
      fi
    done
  fi
  if [[ -z "${JAVA_BIN}" || ! -x "${JAVA_BIN}" ]]; then
    echo "::error::java not found in PATH, JAVA_HOME, or known local JDK candidates" >&2
    exit 1
  fi
  JAVA_DETECTED="$("${JAVA_BIN}" -XshowSettings:properties -version 2>&1 | awk -F= '/java\.specification\.version/ {gsub(/ /,"",$2); print $2; exit}')"
  if [[ -z "${JAVA_DETECTED}" ]]; then
    JAVA_DETECTED="$("${JAVA_BIN}" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1)"
  fi
  echo "Detected Java major: ${JAVA_DETECTED} (expected: ${JAVA_VERSION_EXPECTED})"
  if [[ "$JAVA_DETECTED" != "$JAVA_VERSION_EXPECTED" ]]; then
    echo "::error::Java version mismatch: expected ${JAVA_VERSION_EXPECTED}, got ${JAVA_DETECTED}" >&2
    exit 1
  fi
fi

if [[ -z "$SDK_ROOT" ]]; then
  for candidate in "${DEFAULT_SDK_FALLBACKS[@]}"; do
    if [[ -d "$candidate" ]]; then
      SDK_ROOT="$candidate"
      echo "ANDROID_SDK_ROOT/ANDROID_HOME not set; using fallback SDK root: ${SDK_ROOT}"
      break
    fi
  done
fi

if [[ -z "$SDK_ROOT" ]]; then
  echo "::error::ANDROID_SDK_ROOT/ANDROID_HOME not defined, --sdk-root not provided, and no fallback SDK root found (${DEFAULT_SDK_FALLBACKS[*]})" >&2
  exit 1
fi

if [[ ! -d "$SDK_ROOT" ]]; then
  echo "::error::Android SDK root does not exist: $SDK_ROOT" >&2
  exit 1
fi

if [[ -z "${NDK_VERSION_EXPECTED}" && -f "${REPO_ROOT}/gradle.properties" ]]; then
  NDK_VERSION_EXPECTED="$(awk -F= '
    /^[[:space:]]*#/ { next }
    {
      k=$1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", k)
      if (k == "ndk.version" || k == "NDK_VERSION") {
        sub(/^[^=]*=/, "", $0)
        v=$0
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", v)
        print v
        exit
      }
    }
  ' "${REPO_ROOT}/gradle.properties")"
fi

NDK_DIR_RESOLVED=""
if [[ -n "${NDK_VERSION_EXPECTED}" && -d "${SDK_ROOT}/ndk/${NDK_VERSION_EXPECTED}" ]]; then
  NDK_DIR_RESOLVED="${SDK_ROOT}/ndk/${NDK_VERSION_EXPECTED}"
elif [[ -n "${NDK_VERSION_EXPECTED}" ]]; then
  echo "::warning::Expected NDK version not found under SDK root: ${SDK_ROOT}/ndk/${NDK_VERSION_EXPECTED}" >&2
fi

if [[ "$REQUIRE_SDKMANAGER" == "true" ]] && ! command -v sdkmanager >/dev/null 2>&1; then
  echo "::error::sdkmanager not found in PATH" >&2
  exit 1
fi

if [[ -f "$LOCAL_PROPERTIES_PATH" ]]; then
  tmp_file="$(mktemp)"
  trap 'rm -f "$tmp_file"' EXIT
  awk -v sdk_dir="$SDK_ROOT" '
      BEGIN {
        replaced = 0
      }
      /^[[:space:]]*sdk\.dir[[:space:]]*=/ {
        if (!replaced) {
          print "sdk.dir=" sdk_dir
          replaced = 1
        }
        next
      }
      /^[[:space:]]*ndk\.dir[[:space:]]*=/ { next }
      {
        print
      }
      END {
        if (!replaced) {
          print "sdk.dir=" sdk_dir
        }
      }
    ' "$LOCAL_PROPERTIES_PATH" > "$tmp_file"
  mv "$tmp_file" "$LOCAL_PROPERTIES_PATH"
  echo "Updated sdk.dir in ${LOCAL_PROPERTIES_PATH} to ${SDK_ROOT} (ndk.dir removido por contrato AGP)"
else
  printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$LOCAL_PROPERTIES_PATH"
  echo "Created ${LOCAL_PROPERTIES_PATH} with sdk.dir=${SDK_ROOT} (sem ndk.dir; NDK controlado por ndk.version)"
fi

if [[ -n "${NDK_DIR_RESOLVED}" ]]; then
  echo "NDK version validated via android.ndkVersion/ndkVersion contract: ${NDK_DIR_RESOLVED}"
fi
