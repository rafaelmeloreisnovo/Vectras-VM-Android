#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST_PATH="${REPO_ROOT}/tools/ci/external_sources.manifest"
CHECK_REMOTE="false"
SYNC_CLONE="false"

usage() {
  cat <<'USAGE'
Usage: verify_external_sources.sh [--manifest <path>] [--check-remote] [--sync-clone]

Validates external integration repositories required by Vectras contracts:
- manifest format (required): name|url|branch|dest_dir|pinned_commit_sha
- --check-remote: validates remote branch and pinned commit reachability
- --sync-clone: shallow clone/fetch into dest_dir and checkout the pinned commit
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

  if [[ -z "${pinned_sha}" ]]; then
    echo "::error file=${MANIFEST_PATH},line=${line_no}::pinned_commit_sha is mandatory for ${name}" >&2
    status=1
    continue
  fi

  if [[ ! "${pinned_sha}" =~ ^[0-9a-fA-F]{7,40}$ ]]; then
    echo "::error file=${MANIFEST_PATH},line=${line_no}::Invalid pinned commit SHA for ${name}: ${pinned_sha}" >&2
    status=1
    continue
  fi

  dest_abs="${REPO_ROOT}/${dest}"
  echo "[external] name=${name} branch=${branch} url=${url} dest=${dest_abs} pinned_sha=${pinned_sha}"

  if [[ "${CHECK_REMOTE}" == "true" ]]; then
    branch_ref="refs/heads/${branch}"
    branch_head="$(git ls-remote --exit-code --heads "${url}" "${branch_ref}" 2>/dev/null | awk 'NR == 1 {print $1}')"

    if [[ -z "${branch_head}" ]]; then
      echo "::error::Remote branch not reachable for ${name}: ${url}#${branch}" >&2
      status=1
      continue
    fi

    if [[ "${pinned_sha,,}" == "${branch_head,,}" ]]; then
      echo "[external] pin matches branch head for ${name}: ${branch_head}"
    else
      tmp_repo="$(mktemp -d)"
      remote_branch_ref="refs/remotes/origin/${branch}"
      git -C "${tmp_repo}" init -q
      git -C "${tmp_repo}" remote add origin "${url}"

      if ! git -C "${tmp_repo}" fetch --quiet --no-tags --filter=blob:none --depth=256 \
        origin "+${branch_ref}:${remote_branch_ref}"; then
        echo "::error::Failed to fetch branch for ${name}: ${branch}" >&2
        rm -rf "${tmp_repo}"
        status=1
        continue
      fi

      if ! git -C "${tmp_repo}" cat-file -e "${pinned_sha}^{commit}" 2>/dev/null; then
        if ! git -C "${tmp_repo}" fetch --quiet --no-tags --filter=blob:none --depth=1 origin "${pinned_sha}"; then
          echo "::error::Pinned commit cannot be fetched for ${name}: ${pinned_sha}" >&2
          rm -rf "${tmp_repo}"
          status=1
          continue
        fi
      fi

      if ! git -C "${tmp_repo}" merge-base --is-ancestor "${pinned_sha}" "${remote_branch_ref}"; then
        echo "::error::Pinned SHA ${pinned_sha} is not contained in branch ${branch} for ${name}" >&2
        rm -rf "${tmp_repo}"
        status=1
        continue
      fi

      echo "[external] pin is an ancestor of ${branch} for ${name}: ${pinned_sha}"
      rm -rf "${tmp_repo}"
    fi
  fi

  if [[ "${SYNC_CLONE}" == "true" ]]; then
    if [[ -d "${dest_abs}/.git" ]]; then
      git -C "${dest_abs}" remote set-url origin "${url}"
      git -C "${dest_abs}" fetch --depth=1 origin "${branch}"
      git -C "${dest_abs}" checkout -f "FETCH_HEAD"
    else
      rm -rf "${dest_abs}"
      git clone --depth=1 --branch "${branch}" "${url}" "${dest_abs}"
    fi

    git -C "${dest_abs}" fetch --depth=1 origin "${pinned_sha}"
    git -C "${dest_abs}" checkout -f "${pinned_sha}"
  fi
done < "${MANIFEST_PATH}"

if [[ ${status} -ne 0 ]]; then
  exit ${status}
fi

echo "External source contract OK: ${MANIFEST_PATH}"
