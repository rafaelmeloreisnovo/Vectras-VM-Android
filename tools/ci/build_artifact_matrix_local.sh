#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

GRADLE_CMD="${GRADLE_CMD:-./tools/gradle_with_jdk21.sh}"
OUT_DIR="${OUT_DIR:-${ROOT_DIR}/artifacts/local-matrix}"
KEYSTORE_PATH="${KEYSTORE_PATH:-${OUT_DIR}/internal-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-vectras-internal}"
KEYSTORE_PASS="${KEYSTORE_PASS:-changeit}"
KEY_PASS="${KEY_PASS:-changeit}"
KEY_DNAME="${KEY_DNAME:-CN=Vectras Internal,O=Vectras,OU=Engineering,L=San Francisco,ST=CA,C=US}"
ABI_POLICY="${ABI_POLICY:-arm32-arm64}"
SUPPORTED_ABIS="${SUPPORTED_ABIS:-arm64-v8a,armeabi-v7a}"
ABI_PROFILE="${ABI_PROFILE:-internal_arm32_arm64}"
MANIFEST_PATH="${OUT_DIR}/manifest.json"
PRETEST_PATH="${OUT_DIR}/pretest-report.txt"

mkdir -p "${OUT_DIR}"
: > "${PRETEST_PATH}"

log() {
  printf '[local-matrix] %s\n' "$*"
}

record_pretest() {
  local status="$1"
  local command="$2"
  local detail="$3"
  printf '%s | %s | %s\n' "${status}" "${command}" "${detail}" >> "${PRETEST_PATH}"
}

run_checked() {
  local description="$1"
  shift
  log "${description}"
  "$@"
  record_pretest "PASS" "$*" "${description}"
}

ensure_keystore() {
  local keytool_cmd="${KEYTOOL_CMD:-keytool}"
  local use_openssl_fallback="false"
  if ! command -v "${keytool_cmd}" >/dev/null 2>&1; then
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/keytool" ]]; then
      keytool_cmd="${JAVA_HOME}/bin/keytool"
    else
      use_openssl_fallback="true"
      if ! command -v openssl >/dev/null 2>&1; then
        echo "[local-matrix][error] keytool e openssl indisponíveis para gerar keystore local." >&2
        exit 1
      fi
    fi
  fi

  if [[ -f "${KEYSTORE_PATH}" ]]; then
    log "keystore interno já existe em ${KEYSTORE_PATH}"
    record_pretest "PASS" "test -f ${KEYSTORE_PATH}" "keystore interno reutilizado"
    return 0
  fi

  if [[ "${use_openssl_fallback}" == "false" ]]; then
    log "gerando keystore interno para validação local assinada (keytool)"
    "${keytool_cmd}" -genkeypair \
      -keystore "${KEYSTORE_PATH}" \
      -storepass "${KEYSTORE_PASS}" \
      -keypass "${KEY_PASS}" \
      -alias "${KEY_ALIAS}" \
      -keyalg RSA \
      -keysize 4096 \
      -validity 3650 \
      -dname "${KEY_DNAME}" >/dev/null
    record_pretest "PASS" "${keytool_cmd} -genkeypair ..." "keystore interno gerado por keytool"
  else
    log "gerando keystore interno para validação local assinada (fallback openssl/pkcs12)"
    local tmp_dir
    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "${tmp_dir}"' RETURN
    local key_file="${tmp_dir}/internal-key.pem"
    local cert_file="${tmp_dir}/internal-cert.pem"
    local subject="/CN=Vectras Internal/O=Vectras/OU=Engineering/L=San Francisco/ST=CA/C=US"
    openssl req -x509 -newkey rsa:4096 \
      -keyout "${key_file}" \
      -out "${cert_file}" \
      -days 3650 \
      -sha256 \
      -passout "pass:${KEY_PASS}" \
      -subj "${subject}" >/dev/null 2>&1
    openssl pkcs12 -export \
      -name "${KEY_ALIAS}" \
      -inkey "${key_file}" \
      -passin "pass:${KEY_PASS}" \
      -in "${cert_file}" \
      -out "${KEYSTORE_PATH}" \
      -passout "pass:${KEYSTORE_PASS}" >/dev/null 2>&1
    record_pretest "PASS" "openssl pkcs12 -export ..." "keystore interno gerado por fallback openssl"
  fi
}

copy_required_artifact() {
  local src="$1"
  local dst="$2"
  if [[ ! -f "${src}" ]]; then
    echo "[local-matrix][error] artefato esperado não encontrado: ${src}" >&2
    exit 1
  fi
  cp -f "${src}" "${dst}"
}

copy_debug_artifact() {
  copy_required_artifact "app/build/outputs/apk/debug/app-debug.apk" "${OUT_DIR}/app-debug-beta.apk"
  sha256sum "${OUT_DIR}/app-debug-beta.apk" > "${OUT_DIR}/sha256-debug-beta.txt"
}

copy_release_artifacts() {
  local flavor="$1"
  local src_apk_signed="app/build/outputs/apk/release/app-release.apk"
  local src_apk_unsigned="app/build/outputs/apk/release/app-release-unsigned.apk"
  local src_apk=""
  local src_aab="app/build/outputs/bundle/release/app-release.aab"
  local dst_apk="${OUT_DIR}/app-release-${flavor}.apk"
  local dst_aab="${OUT_DIR}/app-release-${flavor}.aab"

  if [[ -f "${src_apk_signed}" ]]; then
    src_apk="${src_apk_signed}"
  elif [[ -f "${src_apk_unsigned}" ]]; then
    src_apk="${src_apk_unsigned}"
  else
    echo "[local-matrix][error] APK release não encontrado (${src_apk_signed} | ${src_apk_unsigned})" >&2
    exit 1
  fi

  copy_required_artifact "${src_apk}" "${dst_apk}"
  copy_required_artifact "${src_aab}" "${dst_aab}"
  sha256sum "${dst_apk}" "${dst_aab}" > "${OUT_DIR}/sha256-${flavor}.txt"
}

verify_beta_apk() {
  local apk_path="$1"
  run_checked "pré-teste de ABIs do APK ${apk_path}" \
    tools/ci/verify_apk_abi_set.sh --apk "${apk_path}" --expected-abis "${SUPPORTED_ABIS}"
}

artifact_json_entry() {
  local lane="$1"
  local kind="$2"
  local path="$3"
  local signed_state="$4"
  local comma="$5"
  local rel_path="${path#${ROOT_DIR}/}"
  local size_bytes
  local sha
  size_bytes="$(stat -c '%s' "${path}")"
  sha="$(sha256sum "${path}" | awk '{print $1}')"
  cat <<JSON
    {
      "lane": "${lane}",
      "kind": "${kind}",
      "signed_state": "${signed_state}",
      "file": "$(basename "${path}")",
      "path": "${rel_path}",
      "upload_path": "${rel_path}",
      "size_bytes": ${size_bytes},
      "sha256": "${sha}",
      "supported_abis": ["arm64-v8a", "armeabi-v7a"]
    }${comma}
JSON
}

write_manifest() {
  local generated_at
  generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  cat > "${MANIFEST_PATH}" <<JSON
{
  "generated_at_utc": "${generated_at}",
  "profile": "${ABI_PROFILE}",
  "app_abi_policy": "${ABI_POLICY}",
  "supported_abis": ["arm64-v8a", "armeabi-v7a"],
  "upload_dir": "${OUT_DIR#${ROOT_DIR}/}",
  "pretest_report": "${PRETEST_PATH#${ROOT_DIR}/}",
  "artifacts": [
JSON
  artifact_json_entry "debugger-beta" "apk" "${OUT_DIR}/app-debug-beta.apk" "debug-signed" "," >> "${MANIFEST_PATH}"
  artifact_json_entry "release-beta" "apk" "${OUT_DIR}/app-release-unsigned.apk" "unsigned" "," >> "${MANIFEST_PATH}"
  artifact_json_entry "release-beta" "aab" "${OUT_DIR}/app-release-unsigned.aab" "unsigned" "," >> "${MANIFEST_PATH}"
  artifact_json_entry "release-beta" "apk" "${OUT_DIR}/app-release-signed-internal.apk" "signed-internal-beta" "," >> "${MANIFEST_PATH}"
  artifact_json_entry "release-beta" "aab" "${OUT_DIR}/app-release-signed-internal.aab" "signed-internal-beta" "" >> "${MANIFEST_PATH}"
  cat >> "${MANIFEST_PATH}" <<JSON
  ],
  "pretests": [
JSON
  python3 - "${PRETEST_PATH}" >> "${MANIFEST_PATH}" <<'PYJSON'
import json
import sys

rows = []
with open(sys.argv[1], encoding="utf-8") as handle:
    for line in handle:
        parts = line.rstrip("\n").split(" | ", 2)
        if len(parts) != 3:
            continue
        rows.append({"status": parts[0], "command": parts[1], "detail": parts[2]})
for index, row in enumerate(rows):
    suffix = "," if index + 1 < len(rows) else ""
    print("    " + json.dumps(row, ensure_ascii=False) + suffix)
PYJSON
  cat >> "${MANIFEST_PATH}" <<JSON
  ],
  "notes": [
    "debugger-beta usa assinatura debug padrão do Gradle/AGP; APK debug unsigned não é instalável de forma normal no Android.",
    "release unsigned é somente para validação interna; release signed-internal usa keystore local de beta, não chave oficial de loja.",
    "Cada APK é validado para conter arm64-v8a e armeabi-v7a antes da entrega local."
  ]
}
JSON
}

run_checked "limpeza inicial do build" "${GRADLE_CMD}" clean

run_checked "build debugger beta arm32+arm64" \
  "${GRADLE_CMD}" :app:assembleDebug \
  -PciRelease=false \
  -Psigning_mode=unsigned \
  -PCI_INTERNAL_VALIDATION=true \
  -PAPP_ABI_POLICY="${ABI_POLICY}" \
  -PSUPPORTED_ABIS="${SUPPORTED_ABIS}" \
  -Pabi_profile="${ABI_PROFILE}" \
  -Pworkflow=local \
  -Plane=debugger-beta
copy_debug_artifact
verify_beta_apk "${OUT_DIR}/app-debug-beta.apk"

run_checked "build release beta unsigned arm32+arm64" \
  "${GRADLE_CMD}" :app:assembleRelease :app:bundleRelease \
  -PciRelease=false \
  -Psigning_mode=unsigned \
  -PCI_INTERNAL_VALIDATION=true \
  -PAPP_ABI_POLICY="${ABI_POLICY}" \
  -PSUPPORTED_ABIS="${SUPPORTED_ABIS}" \
  -Pabi_profile="${ABI_PROFILE}" \
  -Pworkflow=local \
  -Plane=release-beta-unsigned
copy_release_artifacts "unsigned"
verify_beta_apk "${OUT_DIR}/app-release-unsigned.apk"

ensure_keystore

run_checked "build release beta signed-internal arm32+arm64" \
  "${GRADLE_CMD}" :app:assembleRelease :app:bundleRelease \
  -PciRelease=true \
  -Psigning_mode=signed \
  -PCI_INTERNAL_VALIDATION=true \
  -PAPP_ABI_POLICY="${ABI_POLICY}" \
  -PSUPPORTED_ABIS="${SUPPORTED_ABIS}" \
  -Pabi_profile="${ABI_PROFILE}" \
  -Pworkflow=local \
  -Plane=release-beta-signed-internal \
  -Pandroid.injected.signing.store.file="${KEYSTORE_PATH}" \
  -Pandroid.injected.signing.store.password="${KEYSTORE_PASS}" \
  -Pandroid.injected.signing.key.alias="${KEY_ALIAS}" \
  -Pandroid.injected.signing.key.password="${KEY_PASS}"
copy_release_artifacts "signed-internal"
verify_beta_apk "${OUT_DIR}/app-release-signed-internal.apk"

run_checked "relatório Gradle de artefatos compilados debug/release" \
  "${GRADLE_CMD}" :app:verifyDeliveredCompiledArtifacts \
  -PartifactVariants=debug,release \
  -PciRelease=false \
  -Psigning_mode=unsigned \
  -PCI_INTERNAL_VALIDATION=true \
  -PAPP_ABI_POLICY="${ABI_POLICY}" \
  -PSUPPORTED_ABIS="${SUPPORTED_ABIS}" \
  -Pabi_profile="${ABI_PROFILE}" \
  -Pworkflow=local \
  -Plane=local-beta-matrix

write_manifest

log "artefatos beta gerados em ${OUT_DIR}"
log "manifesto: ${MANIFEST_PATH}"
log "pré-testes: ${PRETEST_PATH}"
