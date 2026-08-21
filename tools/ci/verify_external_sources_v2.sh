#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST_PATH="${REPO_ROOT}/tools/ci/external_sources.manifest"
CHECK_REMOTE="false"
SYNC_CLONE="false"
GENERATE_RECEIPT="false"
FETCH_DEPTH="${EXTERNAL_SOURCE_FETCH_DEPTH:-512}"
RECEIPT_OUTPUT="${REPO_ROOT}/reports/external-sources-receipt.json"

usage() {
  cat <<'USAGE'
Usage: verify_external_sources_v2.sh [OPTIONS]

Validates and recovers external integration repositories with evidence receipts:
- manifest format: name|url|branch|dest_dir|pinned_commit_sha
- --check-remote: validates branch reachability and pinned commit ancestry
- --sync-clone: checks out exact pinned commit, with fallback recovery
- --generate-receipt: creates evidence receipt JSON for external source validation
- --manifest <path>: specify custom manifest path (default: tools/ci/external_sources.manifest)

Recovery fallback order when pinned commit is unresolved:
  1. resolve_original_pin: attempt to fetch exact pinned commit
  2. resolve_default_branch_head: use branch HEAD commit
  3. evaluate_upstream_stable_plus_patches: reserved for future
  4. synthesize_minimal_last: reserved for future

Evidence receipt includes:
  - commit identities and resolution attempts
  - success/failure for each fallback rank
  - timestamp and scope
  - ABI validation placeholder
USAGE
}

log_info() {
  echo "[info] $*"
}

log_error() {
  echo "[error] $*" >&2
}

generate_receipt() {
  local name="$1"
  local url="$2"
  local branch="$3"
  local pinned_sha="$4"
  local resolved_sha="$5"
  local recovery_rank="$6"
  local status="$7"

  mkdir -p "${REPO_ROOT}/reports"

  # Create timestamp in ISO 8601 format
  local timestamp
  timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # Create or append to receipt file
  cat >> "${RECEIPT_OUTPUT}" << EOF
{
  "evidence_id": "EVID-EXTERNAL-SOURCE-${name}-$(date +%s)",
  "provider": "github",
  "kind": "external_source_resolution",
  "repository": "${url}",
  "branch": "${branch}",
  "pinned_sha": "${pinned_sha}",
  "resolved_sha": "${resolved_sha}",
  "recovery_rank": "${recovery_rank}",
  "status": "${status}",
  "timestamp": "${timestamp}",
  "scope": "android-ci-external-source-validation",
  "command": "verify_external_sources_v2.sh --check-remote --generate-receipt",
  "environment": {
    "fetch_depth": "${FETCH_DEPTH}",
    "ci_context": "${CI:-false}"
  }
}
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
      [[ $# -ge 2 ]] || { log_error "--manifest requires a path"; exit 2; }
      MANIFEST_PATH="$2"
      shift 2
      ;;
    --check-remote)
      CHECK_REMOTE="true"
      shift
      ;;
    --sync-clone)
      CHECK_REMOTE="true"
      SYNC_CLONE="true"
      shift
      ;;
    --generate-receipt)
      GENERATE_RECEIPT="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      log_error "Unknown arg: $1"
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  log_error "Manifest not found: ${MANIFEST_PATH}"
  exit 1
fi

if [[ ! "${FETCH_DEPTH}" =~ ^[1-9][0-9]*$ ]]; then
  log_error "EXTERNAL_SOURCE_FETCH_DEPTH must be a positive integer: ${FETCH_DEPTH}"
  exit 2
fi

mkdir -p "${REPO_ROOT}/.third_party_forks"
status=0
line_no=0

# Initialize receipt file if generating receipts
if [[ "${GENERATE_RECEIPT}" == "true" ]]; then
  echo "[]" > "${RECEIPT_OUTPUT}"
fi

while IFS='|' read -r name url branch dest pinned_sha extra; do
  line_no=$((line_no + 1))
  [[ -n "${name}" ]] || continue
  [[ "${name}" =~ ^# ]] && continue

  if [[ -z "${url}" || -z "${branch}" || -z "${dest}" || -z "${pinned_sha}" || -n "${extra:-}" ]]; then
    log_error "file=${MANIFEST_PATH},line=${line_no}: Invalid manifest row; expected name|url|branch|dest_dir|pinned_commit_sha"
    status=1
    continue
  fi

  if [[ ! "${url}" =~ ^https://github\.com/[^/]+/[^/]+/?$ ]]; then
    log_error "file=${MANIFEST_PATH},line=${line_no}: Unsupported URL format for ${name}: ${url}"
    status=1
    continue
  fi

  if [[ "${dest}" = /* || "${dest}" == *".."* ]]; then
    log_error "file=${MANIFEST_PATH},line=${line_no}: dest_dir must be relative and cannot contain '..': ${dest}"
    status=1
    continue
  fi

  if [[ ! "${pinned_sha}" =~ ^[0-9a-fA-F]{40}$ ]]; then
    log_error "file=${MANIFEST_PATH},line=${line_no}: pinned_commit_sha must be a full 40-character SHA for ${name}: ${pinned_sha}"
    status=1
    continue
  fi

  dest_abs="${REPO_ROOT}/${dest}"
  log_info "Validating external source: name=${name} branch=${branch} url=${url} pinned_sha=${pinned_sha}"

  if [[ "${CHECK_REMOTE}" == "true" ]]; then
    branch_ref="refs/heads/${branch}"
    resolved_sha="${pinned_sha}"
    recovery_rank="rank_0_original_pin"
    validation_status="TOKEN_VAZIO"

    # Try to resolve branch
    if ! remote_line="$(git ls-remote --heads "${url}" "${branch_ref}" 2>&1)"; then
      log_error "Remote branch not reachable for ${name}: ${url}#${branch}"
      status=1
      if [[ "${GENERATE_RECEIPT}" == "true" ]]; then
        generate_receipt "${name}" "${url}" "${branch}" "${pinned_sha}" "UNRESOLVED" "rank_1_fallback_required" "REMOTE_BRANCH_UNREACHABLE"
      fi
      continue
    fi

    branch_head="$(awk -v ref="${branch_ref}" '$2 == ref {print $1; exit}' <<< "${remote_line}")"
    if [[ ! "${branch_head}" =~ ^[0-9a-fA-F]{40}$ ]]; then
      log_error "Remote branch not reachable for ${name}: ${url}#${branch}"
      status=1
      if [[ "${GENERATE_RECEIPT}" == "true" ]]; then
        generate_receipt "${name}" "${url}" "${branch}" "${pinned_sha}" "UNRESOLVED" "rank_1_fallback_required" "INVALID_BRANCH_HEAD"
      fi
      continue
    fi

    log_info "Branch head resolved: ${name}@${branch} = ${branch_head}"

    # Check if pinned SHA matches branch head (rank 0: exact match)
    if [[ "${pinned_sha,,}" == "${branch_head,,}" ]]; then
      log_info "✓ Exact pin match: ${name} pin matches branch head ${branch_head}"
      validation_status="VERIFIED_ORIGINAL_PIN"
    else
      # Attempt to verify pinned SHA is reachable from branch (rank 0)
      tmp_repo="$(mktemp -d)"
      trap "rm -rf '${tmp_repo}'" RETURN
      git -C "${tmp_repo}" init -q
      git -C "${tmp_repo}" remote add origin "${url}"

      # Try to fetch branch history
      if git -C "${tmp_repo}" fetch --no-tags --filter=blob:none --depth="${FETCH_DEPTH}" \
        origin "${branch_ref}:refs/remotes/origin/${branch}" >/dev/null 2>&1; then

        # Check if pinned SHA exists in fetched history
        if git -C "${tmp_repo}" cat-file -e "${pinned_sha}^{commit}" 2>/dev/null; then
          # Verify it's an ancestor of the branch
          if git -C "${tmp_repo}" merge-base --is-ancestor \
            "${pinned_sha}" "refs/remotes/origin/${branch}" 2>/dev/null; then
            log_info "✓ Original pin verified: ${name} pinned=${pinned_sha} is ancestor of ${branch}"
            validation_status="VERIFIED_ORIGINAL_PIN"
            recovery_rank="rank_0_original_pin"
          else
            log_error "Pinned SHA ${pinned_sha} is not contained in branch ${branch} for ${name} (ancestry check failed)"
            # Fall back to branch HEAD
            recovery_rank="rank_1_fallback_to_branch_head"
            validation_status="TOKEN_VAZIO_PINNED_UNRESOLVED"
            resolved_sha="${branch_head}"
            status=1
          fi
        else
          # Pinned SHA not in shallow fetch history; try direct fetch
          log_info "Pinned SHA not in fetched history; attempting direct fetch for ${name}"
          if git -C "${tmp_repo}" fetch --no-tags --filter=blob:none --depth=1 \
            origin "${pinned_sha}" >/dev/null 2>&1; then
            log_info "✓ Direct pin fetch succeeded: ${name} pinned=${pinned_sha}"
            validation_status="VERIFIED_ORIGINAL_PIN"
            recovery_rank="rank_0_original_pin"
          else
            log_error "Pinned commit cannot be fetched for ${name}: ${pinned_sha}"
            # Fall back to branch HEAD
            recovery_rank="rank_1_fallback_to_branch_head"
            validation_status="TOKEN_VAZIO_PINNED_UNRESOLVED"
            resolved_sha="${branch_head}"
            status=1
          fi
        fi
      else
        log_error "Unable to fetch branch history for ${name}: ${url}#${branch}"
        # Fall back to branch HEAD
        recovery_rank="rank_1_fallback_to_branch_head"
        validation_status="TOKEN_VAZIO_FETCH_FAILED"
        resolved_sha="${branch_head}"
        status=1
      fi
    fi

    if [[ "${GENERATE_RECEIPT}" == "true" ]]; then
      generate_receipt "${name}" "${url}" "${branch}" "${pinned_sha}" "${resolved_sha}" "${recovery_rank}" "${validation_status}"
    fi
  fi

  if [[ "${SYNC_CLONE}" == "true" ]]; then
    if [[ -d "${dest_abs}/.git" ]]; then
      git -C "${dest_abs}" remote set-url origin "${url}"
    else
      rm -rf "${dest_abs}"
      git -C "${REPO_ROOT}" clone --no-checkout --filter=blob:none "${url}" "${dest_abs}"
    fi

    git -C "${dest_abs}" fetch --no-tags --filter=blob:none --depth="${FETCH_DEPTH}" \
      origin "refs/heads/${branch}:refs/remotes/origin/${branch}" || true

    if ! git -C "${dest_abs}" cat-file -e "${pinned_sha}^{commit}" 2>/dev/null; then
      log_info "Fetching pinned commit ${pinned_sha} for ${name}"
      git -C "${dest_abs}" fetch --no-tags --filter=blob:none --depth=1 origin "${pinned_sha}" || true
    fi

    if git -C "${dest_abs}" cat-file -e "${pinned_sha}^{commit}" 2>/dev/null; then
      git -C "${dest_abs}" checkout -f --detach "${pinned_sha}"
      actual_sha="$(git -C "${dest_abs}" rev-parse HEAD)"
      if [[ "${actual_sha,,}" != "${pinned_sha,,}" ]]; then
        log_error "Pinned checkout mismatch for ${name}: expected=${pinned_sha} actual=${actual_sha}"
        status=1
      else
        log_info "✓ Checked out pinned commit for ${name}: ${actual_sha}"
      fi
    else
      log_error "Pinned commit not available after fetch for ${name}: ${pinned_sha}"
      status=1
    fi
  fi
done < "${MANIFEST_PATH}"

if [[ ${status} -ne 0 ]]; then
  log_error "External source validation completed with errors"
  exit ${status}
fi

log_info "External source contract OK: ${MANIFEST_PATH}"
if [[ "${GENERATE_RECEIPT}" == "true" ]]; then
  log_info "Evidence receipt generated: ${RECEIPT_OUTPUT}"
fi
