#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
properties="${repo_root}/gradle.properties"

read_property() {
  local key="$1"
  local value
  value="$(awk -F= -v key="${key}" '$1==key {gsub(/[[:space:]]/, "", $2); print $2; exit}' "${properties}")"
  if [[ -z "${value}" ]]; then
    echo "missing ${key} in gradle.properties" >&2
    return 1
  fi
  printf '%s' "${value}"
}

compile_api="$(read_property compile.api)"
build_tools="$(read_property tools.version)"
cmake_version="$(read_property cmake.version)"
ndk_version="$(read_property ndk.version)"

if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "sdkmanager is unavailable; run android-actions/setup-android first" >&2
  exit 1
fi

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail

sdkmanager \
  "platform-tools" \
  "platforms;android-${compile_api}" \
  "build-tools;${build_tools}" \
  "cmake;${cmake_version}" \
  "ndk;${ndk_version}"

for required in \
  "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/cmake/${cmake_version}" \
  "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/ndk/${ndk_version}"
do
  if [[ ! -d "${required}" ]]; then
    echo "Android native toolchain component missing after sdkmanager: ${required}" >&2
    exit 1
  fi
done

echo "ANDROID_NATIVE_TOOLCHAIN_READY compile_api=${compile_api} build_tools=${build_tools} cmake=${cmake_version} ndk=${ndk_version}"
