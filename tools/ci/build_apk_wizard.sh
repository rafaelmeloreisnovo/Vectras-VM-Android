#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${REPO_ROOT}/artifacts/apk-wizard"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/workspace/android-sdk}}"
GRADLEW="${REPO_ROOT}/gradlew"
RUNTIME_SEED_MANIFEST="${REPO_ROOT}/configs/embedded_runtime_seed_assets.v1.json"
RUNTIME_SEED_RECEIPT="${OUT_DIR}/alpine19-materialization.json"
BOOTSTRAP_RECEIPT_SRC="${REPO_ROOT}/app/build/reports/bootstrap/bootstrap-materialization.json"
BOOTSTRAP_RECEIPT_DST="${OUT_DIR}/bootstrap-materialization.json"
GENERATED_ASSETS_ROOT="${REPO_ROOT}/app/build/generated/bootstrapAssets"
BUILD_EVIDENCE_TOOL="${REPO_ROOT}/tools/ci/generate_build_evidence_catalog.py"
EMBEDDED_BUILD_CONTEXT="${GENERATED_ASSETS_ROOT}/evidence/build-context.json"
OMEGA_OUT_DIR="${OUT_DIR}/omega-freestanding-armv7"
OMEGA_AUDIT="${OMEGA_OUT_DIR}/elf-audit.json"
OMEGA_APK_RECEIPT="${OUT_DIR}/app-debug-arm32-arm64.omega-materialization.json"

mkdir -p "${OUT_DIR}"

echo "[wizard] bootstrap Android SDK root=${SDK_ROOT}"
"${REPO_ROOT}/tools/ci/bootstrap_local_android_sdk.sh" --sdk-root "${SDK_ROOT}"

# Clean once before generating provenance-bound assets. Do not clean per lane:
# generated bootstrap/alpine/loader/Omega assets are intentionally retained until
# the APK that consumes them has been independently verified.
echo "[wizard] clean app build tree before runtime seed materialization"
"${GRADLEW}" --no-daemon :app:clean \
  -PdevFastPath=true \
  -PCI_INTERNAL_VALIDATION=false

# Layer 1a: existing strict bootstrap contract now points directly to the original
# upstream commit and enforces the already-known SHA-256 values.
echo "[wizard] materialize pinned PRoot bootstrap TARs with SHA-256 enforcement"
python3 "${REPO_ROOT}/tools/ci/materialize_bootstrap_assets.py"
cp -f "${BOOTSTRAP_RECEIPT_SRC}" "${BOOTSTRAP_RECEIPT_DST}"

# Layer 1b: Alpine rootfs/userland is a distinct embedded asset family required
# by SetupFeatureCore for distro/bin/busybox and distro/bin/sh.
echo "[wizard] materialize pinned Alpine19 rootfs TARs for ARM lanes"
python3 "${REPO_ROOT}/tools/bootstrap/materialize_embedded_runtime_seed_assets.py" \
  --manifest "${RUNTIME_SEED_MANIFEST}" \
  --target-root "${GENERATED_ASSETS_ROOT}" \
  --families "alpine19" \
  --abis "arm64-v8a,armeabi-v7a" \
  --receipt "${RUNTIME_SEED_RECEIPT}"

# devFastPath deliberately skips the normal preBuild sync task, so materialize
# loader.apk explicitly once after the clean. This appends loader.apk beside the
# verified TARs without deleting them.
echo "[wizard] materialize shell-loader into generated bootstrap assets"
"${GRADLEW}" --no-daemon :app:syncShellLoaderBootstrap \
  -PdevFastPath=false \
  -PCI_INTERNAL_VALIDATION=false

build_lane() {
  local lane_name="$1"
  local policy="$2"
  local abis="$3"
  local ci_internal="$4"
  local payload_abis="$5"
  local apk_src="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
  local apk_dst="${OUT_DIR}/${lane_name}.apk"
  local payload_receipt="${OUT_DIR}/${lane_name}.runtime-payload.json"
  local build_evidence="${OUT_DIR}/${lane_name}.build-evidence.json"

  echo "[wizard] build evidence context lane=${lane_name} policy=${policy} abis=${abis}"
  python3 "${BUILD_EVIDENCE_TOOL}" \
    --lane "${lane_name}" \
    --policy "${policy}" \
    --abis "${abis}" \
    --embedded-out "${EMBEDDED_BUILD_CONTEXT}"

  echo "[wizard] building lane=${lane_name} policy=${policy} abis=${abis}"
  "${GRADLEW}" --no-daemon :app:assembleDebug \
    -PAPP_ABI_POLICY="${policy}" \
    -PSUPPORTED_ABIS="${abis}" \
    -PCI_INTERNAL_VALIDATION="${ci_internal}" \
    -PdevFastPath=true

  if [[ ! -f "${apk_src}" ]]; then
    echo "[wizard][error] APK not found for lane=${lane_name}: ${apk_src}" >&2
    exit 1
  fi
  cp -f "${apk_src}" "${apk_dst}"

  python3 "${REPO_ROOT}/tools/device_runtime/verify_beta_apk_runtime_payload.py" \
    "${apk_dst}" \
    --abis "${payload_abis}" \
    --json-out "${payload_receipt}"

  python3 "${BUILD_EVIDENCE_TOOL}" \
    --lane "${lane_name}" \
    --policy "${policy}" \
    --abis "${abis}" \
    --embedded-out "${EMBEDDED_BUILD_CONTEXT}" \
    --apk "${apk_dst}" \
    --payload-receipt "${payload_receipt}" \
    --bootstrap-receipt "${BOOTSTRAP_RECEIPT_DST}" \
    --runtime-seed-receipt "${RUNTIME_SEED_RECEIPT}" \
    --out "${build_evidence}"

  local apk_size
  apk_size="$(stat -c '%s' "${apk_dst}")"
  echo "${lane_name}|${policy}|${abis}|${apk_size}" >> "${OUT_DIR}/sizes.tsv"
}

rm -f "${OUT_DIR}/sizes.tsv"

# Keep the arm64-only compatibility artifact free of the ARM32-only Omega asset.
build_lane "app-debug-arm64-v8a" "arm64-only" "arm64-v8a" "false" "arm64-v8a"

# Layer 2: compile/link the existing canonical freestanding ABI core into a
# self-contained ELF32/ARM deployment image. It is audited twice for deterministic
# identity, PT_INTERP/DT_NEEDED/undefined-symbol absence, then staged through the
# debug APK's native executable carrier. This does not execute the ELF.
echo "[wizard] build + audit + stage Omega freestanding ARMv7 deployment ELF"
bash "${REPO_ROOT}/tools/ci/materialize_omega_freestanding_asset.sh" \
  --sdk-root "${SDK_ROOT}" \
  --asset-root "${GENERATED_ASSETS_ROOT}" \
  --jni-root "${REPO_ROOT}/app/src/debug/jniLibs" \
  --out-dir "${OMEGA_OUT_DIR}" \
  --commit "$(git -C "${REPO_ROOT}" rev-parse HEAD)"

build_lane "app-debug-arm32-arm64" "arm32-arm64" "arm64-v8a,armeabi-v7a" "true" "arm64-v8a,armeabi-v7a"

# Close the build->APK materialization edge by comparing the ELF bytes in
# lib/armeabi-v7a/libomega_core_exec.so against the audited standalone ELF and
# generated manifest. Physical nativeLibraryDir extraction remains a device gate.
echo "[wizard] verify Omega native carrier byte identity inside dual-ARM APK"
python3 "${REPO_ROOT}/tools/ci/verify_omega_freestanding_apk.py" \
  --apk "${OUT_DIR}/app-debug-arm32-arm64.apk" \
  --audit "${OMEGA_AUDIT}" \
  --output "${OMEGA_APK_RECEIPT}" \
  --commit "$(git -C "${REPO_ROOT}" rev-parse HEAD)"

source_commit="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["source_commit"])' "${RUNTIME_SEED_MANIFEST}")"
{
  echo "# APK Wizard Report"
  echo
  echo "Embedded runtime seed source: \`xoureldeen/Vectras-VM-Android@${source_commit}\`"
  echo
  echo "Bootstrap TARs: exact SHA-256 enforced by tools/ci/bootstrap-assets.v1.json."
  echo "Alpine19 TARs: exact size + Git blob SHA-1 enforced; SHA-256 emitted in receipt."
  echo "Omega ARMv7: direct ld.lld freestanding ELF; deterministic rebuild audit; APK native-carrier byte identity verified."
  echo "Android 10 W^X: executable code is kept APK/install-owned, not copied into writable app home."
  echo "Build evidence: each lane embeds assets/evidence/build-context.json and emits a post-build *.build-evidence.json."
  echo
  echo "Boundary: APK carrier != PackageManager extraction/nativeLibraryDir receipt != physical execution != VM boot. claim_allowed=false until physical receipts close those gates."
  echo
  echo "| lane | policy | abis | apk_size_bytes |"
  echo "|---|---|---|---:|"
  while IFS='|' read -r lane policy abis size; do
    echo "| ${lane} | ${policy} | ${abis} | ${size} |"
  done < "${OUT_DIR}/sizes.tsv"
} > "${OUT_DIR}/REPORT.md"

echo "[wizard] artifacts in ${OUT_DIR}"