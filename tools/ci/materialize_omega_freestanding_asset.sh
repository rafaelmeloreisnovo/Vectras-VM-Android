#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/workspace/android-sdk}}"
ASSET_ROOT="${ROOT}/app/build/generated/bootstrapAssets"
JNI_ROOT="${ROOT}/app/src/debug/jniLibs"
OUT_DIR="${ROOT}/artifacts/omega-freestanding-armv7"
SOURCE_COMMIT="${GITHUB_SHA:-$(git -C "${ROOT}" rev-parse HEAD 2>/dev/null || printf TOKEN_VAZIO)}"
NDK_VERSION="27.2.12479018"
CMAKE_VERSION="3.22.1"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --sdk-root) SDK_ROOT="$2"; shift 2 ;;
    --asset-root) ASSET_ROOT="$2"; shift 2 ;;
    --jni-root) JNI_ROOT="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --commit) SOURCE_COMMIT="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

cmake_dir="${SDK_ROOT}/cmake/${CMAKE_VERSION}"
ndk_dir="${SDK_ROOT}/ndk/${NDK_VERSION}"
ndk_bin="${ndk_dir}/toolchains/llvm/prebuilt/linux-x86_64/bin"
build_dir="${ROOT}/build/omega-armv7"
elf="${build_dir}/out/omega-core-armv7.elf"
raw="${build_dir}/out/omega-core-armv7.unstripped.elf"
map_file="${build_dir}/out/omega-core-armv7.map"

[[ -x "${cmake_dir}/bin/cmake" ]] || { echo "missing CMake ${CMAKE_VERSION}: ${cmake_dir}" >&2; exit 1; }
[[ -f "${ndk_dir}/build/cmake/android.toolchain.cmake" ]] || { echo "missing NDK ${NDK_VERSION}: ${ndk_dir}" >&2; exit 1; }
[[ -x "${ndk_bin}/llvm-readelf" ]] || { echo "missing llvm-readelf: ${ndk_bin}" >&2; exit 1; }
[[ -x "${ndk_bin}/llvm-strip" ]] || { echo "missing llvm-strip: ${ndk_bin}" >&2; exit 1; }

mkdir -p "${OUT_DIR}"
rm -rf "${build_dir}"

"${cmake_dir}/bin/cmake" \
  -S "${ROOT}/deployment/freestanding" \
  -B "${build_dir}" \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DCMAKE_MAKE_PROGRAM="${cmake_dir}/bin/ninja" \
  -DCMAKE_TOOLCHAIN_FILE="${ndk_dir}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi-v7a \
  -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=none

"${cmake_dir}/bin/cmake" --build "${build_dir}" \
  --target omega_freestanding_deploy_armv7 \
  --verbose 2>&1 | tee "${OUT_DIR}/build-first.log"
[[ -f "${elf}" && -f "${raw}" ]] || { echo "Omega stripped/raw ELF missing after first build" >&2; exit 1; }
cp -f "${elf}" "${OUT_DIR}/reference.elf"

rm -f "${elf}" "${raw}" "${map_file}"
"${cmake_dir}/bin/cmake" --build "${build_dir}" \
  --target omega_freestanding_deploy_armv7 \
  --verbose 2>&1 | tee "${OUT_DIR}/build-second.log"
[[ -f "${elf}" && -f "${raw}" ]] || { echo "Omega stripped/raw ELF missing after second build" >&2; exit 1; }

python3 "${ROOT}/tools/ci/audit_omega_freestanding_deploy.py" \
  --binary "${elf}" \
  --symbols-binary "${raw}" \
  --reference-binary "${OUT_DIR}/reference.elf" \
  --readelf "${ndk_bin}/llvm-readelf" \
  --output "${OUT_DIR}/elf-audit.json" \
  --commit "${SOURCE_COMMIT}"

python3 "${ROOT}/tools/ci/stage_omega_freestanding_apk_asset.py" \
  --binary "${elf}" \
  --audit "${OUT_DIR}/elf-audit.json" \
  --asset-root "${ASSET_ROOT}" \
  --jni-root "${JNI_ROOT}" \
  --commit "${SOURCE_COMMIT}"

cp -f "${elf}" "${OUT_DIR}/omega-core-armv7.elf"
cp -f "${raw}" "${OUT_DIR}/omega-core-armv7.unstripped.elf"
cp -f "${map_file}" "${OUT_DIR}/omega-core-armv7.map"

printf '%s\n' \
  'OMEGA_FREESTANDING_FULL_V2=BUILT_AND_AUDITED' \
  'FINAL_NAMED_SYMBOLS=0' \
  'PT_INTERP=0' \
  'DT_NEEDED=0' \
  'UNDEFINED_SYMBOLS=0' \
  'HEAP_LIBC_JNI_RUNTIME_DEPS=0' \
  'APK_NATIVE_CARRIER=STAGED_NOT_YET_VERIFIED' \
  'DEVICE_NATIVE_LIBRARY_DIR=TOKEN_VAZIO' \
  'DEVICE_EXECUTION=TOKEN_VAZIO' \
  'CLAIM_ALLOWED=false' \
  > "${OUT_DIR}/boundary.txt"

echo "[omega-freestanding] full-v2 carrier ${JNI_ROOT}/armeabi-v7a/libomega_core_exec.so"
echo "[omega-freestanding] fixed protocol=32 bytes; only service loop retained"
