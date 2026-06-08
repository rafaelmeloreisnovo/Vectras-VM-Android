#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACT_ROOT="${ROOT_DIR}/ci-artifacts"
STAGING_ROOT="${ARTIFACT_ROOT}/upload-staging"
TIMESTAMP_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
RELEASE_EVIDENCE_LEDGER="docs/RELEASE_EVIDENCE_LEDGER.md"
RELEASE_EVIDENCE_LEDGER_PATH="${ROOT_DIR}/${RELEASE_EVIDENCE_LEDGER}"

LANE="${ABI_PROFILE:-${ARTIFACT_LANE:-}}"
EXPECT_APP_ARTIFACTS="${EXPECT_APP_ARTIFACTS:-false}"
EXPECT_NATIVE_MATRIX="${EXPECT_NATIVE_MATRIX:-false}"
EXPECT_PERF_RESULTS="${EXPECT_PERF_RESULTS:-false}"
APP_ABI_POLICY="${APP_ABI_POLICY:-unknown}"
SUPPORTED_ABIS="${SUPPORTED_ABIS:-unknown}"
PERF_RUNS="${PERF_RUNS:-}"

usage() {
  cat <<USAGE
Uso: $0 --lane <lane> [--expect-app-artifacts true|false] [--expect-native-matrix true|false] [--expect-perf-results true|false]

Materializa manifests de artefatos e aponta para ${RELEASE_EVIDENCE_LEDGER} como ledger esperado para evidência de release.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --lane)
      LANE="$2"
      shift 2
      ;;
    --expect-app-artifacts)
      EXPECT_APP_ARTIFACTS="$2"
      shift 2
      ;;
    --expect-native-matrix)
      EXPECT_NATIVE_MATRIX="$2"
      shift 2
      ;;
    --expect-perf-results)
      EXPECT_PERF_RESULTS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Argumento desconhecido: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${LANE}" ]]; then
  echo "lane não definido. Use --lane <lane> ou ABI_PROFILE/ARTIFACT_LANE." >&2
  exit 1
fi

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "${value}"
}

copy_tree_files() {
  local source_dir="$1"
  local destination_root="$2"
  [[ -d "${source_dir}" ]] || return 0

  while IFS= read -r file_path; do
    local rel_path="${file_path#${ROOT_DIR}/}"
    local target="${destination_root}/files/${rel_path}"
    mkdir -p "$(dirname "${target}")"
    cp -a "${file_path}" "${target}"
  done < <(find "${source_dir}" -type f | sort)
}

write_manifests() {
  local category="$1"
  local required="$2"
  local destination="${STAGING_ROOT}/${category}-${LANE}"
  local manifest_json="${destination}/artifact-manifest.json"
  local manifest_md="${destination}/ARTIFACT_MANIFEST.md"

  mkdir -p "${destination}"
  rm -rf "${destination}/files"
  mkdir -p "${destination}/files"

  shift 2
  local source_dirs=("$@")

  for source_dir in "${source_dirs[@]}"; do
    copy_tree_files "${source_dir}" "${destination}"
  done

  local file_count
  file_count="$(find "${destination}/files" -type f | wc -l | tr -d ' ')"

  local error_classification="ok"
  local abi_mix_detected="false"
  local abi_policy_violation="false"
  local detected_abis_csv=""

  mapfile -t detected_abis < <(
    find "${destination}/files" -type f -print \
      | sed -nE 's#.*(armeabi-v7a|arm64-v8a|x86_64|x86|riscv64).*#\1#p' \
      | sort -u
  )
  if [[ "${#detected_abis[@]}" -gt 0 ]]; then
    detected_abis_csv="$(printf '%s\n' "${detected_abis[@]}" | paste -sd ',' -)"
  fi
  if [[ "${#detected_abis[@]}" -gt 1 ]]; then
    abi_mix_detected="true"
  fi

  if [[ "${APP_ABI_POLICY}" == "arm64-only" && "${#detected_abis[@]}" -gt 0 ]]; then
    for detected_abi in "${detected_abis[@]}"; do
      if [[ "${detected_abi}" != "arm64-v8a" ]]; then
        abi_policy_violation="true"
        break
      fi
    done
  fi

  if [[ "${file_count}" == "0" ]]; then
    if [[ "${required}" == "true" ]]; then
      error_classification="missing_required_artifacts"
    else
      error_classification="empty_optional"
    fi
  elif [[ "${abi_policy_violation}" == "true" ]]; then
    error_classification="abi_policy_violation"
  elif [[ "${abi_mix_detected}" == "true" ]]; then
    error_classification="mixed_abis_with_manifest"
  fi

  {
    local perf_runs_json="null"
    if [[ -n "${PERF_RUNS}" && "${PERF_RUNS}" =~ ^[0-9]+$ ]]; then
      perf_runs_json="${PERF_RUNS}"
    fi
    printf '{\n'
    printf '  "generated_at_utc": "%s",\n' "${TIMESTAMP_UTC}"
    printf '  "category": "%s",\n' "${category}"
    printf '  "lane": "%s",\n' "$(json_escape "${LANE}")"
    printf '  "workflow": "%s",\n' "$(json_escape "${GITHUB_WORKFLOW:-local}")"
    printf '  "run_id": "%s",\n' "$(json_escape "${GITHUB_RUN_ID:-local}")"
    printf '  "run_attempt": "%s",\n' "$(json_escape "${GITHUB_RUN_ATTEMPT:-1}")"
    printf '  "repository": "%s",\n' "$(json_escape "${GITHUB_REPOSITORY:-local}")"
    printf '  "release_evidence_ledger": "%s",\n' "$(json_escape "${RELEASE_EVIDENCE_LEDGER}")"
    printf '  "release_evidence_required_fields": "%s",\n' "data UTC; commit; workflow/lane; ABI profile; signing mode; APK/AAB gerado; SHA-256; relatório ABI; status de upload; observações/bloqueios"
    printf '  "signing_vocabulary": "%s",\n' "unsigned/debug-signed/signed-internal=validação interna; signed=distribuição oficial"
    printf '  "ref": "%s",\n' "$(json_escape "${GITHUB_REF:-local}")"
    printf '  "sha": "%s",\n' "$(json_escape "${GITHUB_SHA:-local}")"
    printf '  "required": %s,\n' "${required}"
    printf '  "app_abi_policy": "%s",\n' "$(json_escape "${APP_ABI_POLICY}")"
    printf '  "supported_abis": "%s",\n' "$(json_escape "${SUPPORTED_ABIS}")"
    printf '  "perf_runs": %s,\n' "${perf_runs_json}"
    printf '  "file_count": %s,\n' "${file_count}"
    printf '  "error_classification": "%s",\n' "${error_classification}"
    printf '  "files": [\n'

    local first=true
    while IFS= read -r staged_file; do
      local rel_file="${staged_file#${destination}/files/}"
      local size
      size="$(stat -c '%s' "${staged_file}")"
      local sha256
      sha256="$(sha256sum "${staged_file}" | awk '{print $1}')"

      if [[ "${first}" == true ]]; then
        first=false
      else
        printf ',\n'
      fi

      printf '    {"path": "%s", "size": %s, "sha256": "%s"}' \
        "$(json_escape "${rel_file}")" "${size}" "${sha256}"
    done < <(find "${destination}/files" -type f | sort)

    printf '\n  ]\n'
    printf '}\n'
  } > "${manifest_json}"

  cat > "${manifest_md}" <<EOF
# Android CI Artifact Manifest

- generated_at_utc: ${TIMESTAMP_UTC}
- category: ${category}
- lane: ${LANE}
- workflow: ${GITHUB_WORKFLOW:-local}
- run_id: ${GITHUB_RUN_ID:-local}
- run_attempt: ${GITHUB_RUN_ATTEMPT:-1}
- ref: ${GITHUB_REF:-local}
- sha: ${GITHUB_SHA:-local}
- required: ${required}
- perf_runs: ${PERF_RUNS:-n/a}
- file_count: ${file_count}
- error_classification: ${error_classification}
- supported_abis: ${SUPPORTED_ABIS}
- detected_abis: ${detected_abis_csv:-none}
- abi_mix_detected: ${abi_mix_detected}
- abi_policy_violation: ${abi_policy_violation}
- json_manifest: artifact-manifest.json
- release_evidence_ledger: ${RELEASE_EVIDENCE_LEDGER}
- release_evidence_required_fields: data UTC; commit; workflow/lane; ABI profile; signing mode; APK/AAB gerado; SHA-256; relatório ABI; status de upload; observações/bloqueios
- signing_vocabulary: unsigned/debug-signed/signed-internal=validação interna; signed=distribuição oficial
EOF

  printf '%s\n' "${destination}"
}

if [[ ! -f "${RELEASE_EVIDENCE_LEDGER_PATH}" ]]; then
  echo "Ledger esperado não encontrado: ${RELEASE_EVIDENCE_LEDGER_PATH}" >&2
  exit 1
fi

mkdir -p "${STAGING_ROOT}"

logs_dir="${ARTIFACT_ROOT}/android-logs"
artifacts_dir="${ARTIFACT_ROOT}/android-artifacts"
matrix_dir="${ARTIFACT_ROOT}/android-cmake-matrix"
perf_dir="${ARTIFACT_ROOT}/perf-results"

logs_out="$(write_manifests "android-logs" "true" "${logs_dir}")"
artifacts_out="$(write_manifests "android-artifacts" "${EXPECT_APP_ARTIFACTS}" "${artifacts_dir}")"
native_out="$(write_manifests "native-matrix" "${EXPECT_NATIVE_MATRIX}" "${matrix_dir}")"
perf_out="$(write_manifests "perf-results" "${EXPECT_PERF_RESULTS}" "${perf_dir}")"

echo "Materialized deterministic Android CI artifact manifests (lane=${LANE})."
echo "Expected release evidence ledger format: ${RELEASE_EVIDENCE_LEDGER}"
echo "Manifests:"
echo " - ${logs_out}/artifact-manifest.json"
echo " - ${artifacts_out}/artifact-manifest.json"
echo " - ${native_out}/artifact-manifest.json"
echo " - ${perf_out}/artifact-manifest.json"
