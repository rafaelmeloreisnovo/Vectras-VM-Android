#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST_PATH="${REPO_ROOT}/tools/ci/external_sources.manifest"
CHECK_REMOTE="false"
SYNC_CLONE="false"
FETCH_DEPTH="${EXTERNAL_SOURCE_FETCH_DEPTH:-512}"

usage() {
  cat <<'USAGE'
Usage: verify_external_sources.sh [--manifest <path>] [--check-remote] [--sync-clone]

Validates external integration repositories required by Vectras contracts:
- manifest format: name|url|branch|dest_dir|pinned_commit_sha
- --check-remote: validates branch reachability and that the pinned commit is
  an ancestor of that branch within the bounded fetch depth
- --sync-clone: checks out the exact pinned commit into dest_dir
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
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

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  echo "::error::Manifest not found: ${MANIFEST_PATH}" >&2
  exit 1
fi

if [[ ! "${FETCH_DEPTH}" =~ ^[1-9][0-9]*$ ]]; then
  echo "::error::EXTERNAL_SOURCE_FETCH_DEPTH must be a positive integer: ${FETCH_DEPTH}" >&2
  exit 2
fi

mkdir -p "${REPO_ROOT}/.third_party_forks"
status=0
line_no=0

while IFS='|' read -r name url branch dest pinned_sha extra; do
  line_no=$((line_no + 1))
  [[ -n "${name}" ]] || continue
  [[ "${name}" =~ ^# ]] && continue

  if [[ -z "${url}" || -z "${branch}" || -z "${dest}" || -n "${extra:-}" ]]; then
    echo "::error file=${MANIFEST_PATH},line=${line_no}::Invalid manifest row; expected name|url|branch|dest_dir|pinned_commit_sha" >&2
    status=1
    continue
  fi

  if [[ ! "${url}" =~ ^https://github\.com/.+/.+$ ]]; then
    echo "::error file=${MANIFEST_PATH},line=${line_no}::Unsupported URL format for ${name}: ${url}" >&2
    status=1
    continue
  fi

  if [[ "${dest}" = /* || "${dest}" == *".."* ]]; then
    echo "::error file=${MANIFEST_PATH},line=${line_no}::dest_dir must be relative and cannot contain '..': ${dest}" >&2
    status=1
    continue
  fi

  if [[ ! "${pinned_sha}" =~ ^[0-9a-fA-F]{40}$ ]]; then
    echo "::error file=${MANIFEST_PATH},line=${line_no}::pinned_commit_sha must be a full 40-character SHA for ${name}: ${pinned_sha}" >&2
    status=1
    continue
  fi

  dest_abs="${REPO_ROOT}/${dest}"
  echo "[external] name=${name} branch=${branch} url=${url} dest=${dest_abs} pinned_sha=${pinned_sha}"

  if [[ "${CHECK_REMOTE}" == "true" ]]; then
    tmp_repo="$(mktemp -d)"
    cleanup_tmp() { rm -rf "${tmp_repo}"; }
    trap cleanup_tmp RETURN

    git -C "${tmp_repo}" init -q
    git -C "${tmp_repo}" remote add origin "${url}"
    if ! git -C "${tmp_repo}" fetch --no-tags --filter=blob:none --depth="${FETCH_DEPTH}" \
      origin "refs/heads/${branch}:refs/remotes/origin/${branch}" >/dev/null 2>&1; then
      echo "::error::Remote branch not reachable for ${name}: ${url}#${branch}" >&2
      cleanup_tmp
      trap - RETURN
      status=1
      continue
    fi

    if ! git -C "${tmp_repo}" cat-file -e "${pinned_sha}^{commit}" 2>/dev/null; then
      if ! git -C "${tmp_repo}" fetch --no-tags --filter=blob:none --depth=1 \
        origin "${pinned_sha}" >/dev/null 2>&1; then
        echo "::error::Pinned commit cannot be fetched for ${name}: ${pinned_sha}" >&2
        cleanup_tmp
        trap - RETURN
        status=1
        continue
      fi
    fi

    if ! git -C "${tmp_repo}" merge-base --is-ancestor \
      "${pinned_sha}" "refs/remotes/origin/${branch}"; then
      echo "::error::Pinned SHA ${pinned_sha} is not contained in branch ${branch} for ${name}" >&2
      cleanup_tmp
      trap - RETURN
      status=1
      continue
    fi

    branch_head="$(git -C "${tmp_repo}" rev-parse "refs/remotes/origin/${branch}")"
    echo "[external] verified ${name}: pinned=${pinned_sha} branch_head=${branch_head}"
    cleanup_tmp
    trap - RETURN
  fi

  if [[ "${SYNC_CLONE}" == "true" ]]; then
    if [[ -d "${dest_abs}/.git" ]]; then
      git -C "${dest_abs}" remote set-url origin "${url}"
    else
      rm -rf "${dest_abs}"
      git -C "${REPO_ROOT}" clone --no-checkout --filter=blob:none "${url}" "${dest_abs}"
    fi
    git -C "${dest_abs}" fetch --no-tags --filter=blob:none --depth="${FETCH_DEPTH}" \
      origin "refs/heads/${branch}:refs/remotes/origin/${branch}"
    if ! git -C "${dest_abs}" cat-file -e "${pinned_sha}^{commit}" 2>/dev/null; then
      git -C "${dest_abs}" fetch --no-tags --filter=blob:none --depth=1 origin "${pinned_sha}"
    fi
    git -C "${dest_abs}" checkout -f --detach "${pinned_sha}"
    actual_sha="$(git -C "${dest_abs}" rev-parse HEAD)"
    if [[ "${actual_sha}" != "${pinned_sha}" ]]; then
      echo "::error::Pinned checkout mismatch for ${name}: expected=${pinned_sha} actual=${actual_sha}" >&2
      status=1
      continue
    fi
  fi

    git -C "${dest_abs}" fetch --depth=1 origin "${pinned_sha}"
    git -C "${dest_abs}" checkout -f "${pinned_sha}"
  fi
done < "${MANIFEST_PATH}"

if [[ ${status} -ne 0 ]]; then
  exit ${status}
fi

echo "External source contract OK: ${MANIFEST_PATH}"
