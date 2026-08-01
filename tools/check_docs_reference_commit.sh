#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

MARKER='Commit de referência:'
TARGET_SHA="$(git rev-parse HEAD)"
HEAD_SHA="${TARGET_SHA}"
if git rev-parse --verify HEAD^2 >/dev/null 2>&1; then
  HEAD_SHA="$(git rev-parse HEAD^2)"
fi

mapfile -t docs < <(find docs -type f -name '*.md' -print | sort)
if [[ ${#docs[@]} -eq 0 ]]; then
  echo "ERROR: no Markdown documents found under docs/" >&2
  exit 1
fi

changed_range=""
if [[ -n "${DOCS_REFERENCE_RANGE:-}" ]]; then
  changed_range="${DOCS_REFERENCE_RANGE}"
elif [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" && -n "${GITHUB_BASE_REF:-}" ]]; then
  if git rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    changed_range="origin/${GITHUB_BASE_REF}...HEAD"
  elif git rev-parse --verify HEAD^1 >/dev/null 2>&1; then
    changed_range="HEAD^1...HEAD"
  fi
elif git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
  changed_range="HEAD~1...HEAD"
fi

declare -A changed_docs=()
if [[ -n "${changed_range}" ]]; then
  while IFS= read -r path; do
    [[ -n "${path}" ]] || continue
    changed_docs["${path}"]=1
  done < <(git diff --name-only --diff-filter=ACMR "${changed_range}" -- 'docs/*.md' 'docs/**/*.md' 2>/dev/null || true)
fi

errors=0
explicit_count=0
head_count=0
historical_count=0

for file in "${docs[@]}"; do
  count="$(grep -c "${MARKER}" "${file}" || true)"
  if [[ "${count}" -eq 0 ]]; then
    continue
  fi
  if [[ "${count}" -ne 1 ]]; then
    echo "ERROR: ${file} must contain exactly one '${MARKER}' marker (found ${count})" >&2
    errors=1
    continue
  fi

  value="$(sed -nE 's/.*Commit de referência: `([^`]+)`.*/\1/p' "${file}")"
  if [[ -z "${value}" ]]; then
    echo "ERROR: ${file} has an invalid reference marker format" >&2
    errors=1
    continue
  fi

  is_changed="false"
  if [[ -n "${changed_docs[${file}]:-}" ]]; then
    is_changed="true"
  fi

  if [[ "${value}" == "HEAD" ]]; then
    head_count=$((head_count + 1))
    continue
  fi

  if [[ ! "${value}" =~ ^[0-9a-fA-F]{7,40}$ ]]; then
    echo "ERROR: ${file} reference must be HEAD or a hexadecimal Git commit: ${value}" >&2
    errors=1
    continue
  fi

  if ! git cat-file -e "${value}^{commit}" 2>/dev/null; then
    echo "ERROR: ${file} references a commit unavailable in this checkout: ${value}" >&2
    errors=1
    continue
  fi

  resolved="$(git rev-parse "${value}^{commit}")"
  explicit_count=$((explicit_count + 1))

  if [[ "${is_changed}" == "true" ]]; then
    if [[ "${resolved}" != "${HEAD_SHA}" && "${resolved}" != "${TARGET_SHA}" ]]; then
      echo "ERROR: changed document ${file} references ${resolved}; expected HEAD/${HEAD_SHA}" >&2
      errors=1
    fi
  else
    historical_count=$((historical_count + 1))
  fi
done

if [[ "${errors}" -ne 0 ]]; then
  exit 1
fi

printf 'Documentation reference gate PASS: docs=%d HEAD=%d explicit=%d historical=%d changed=%d head_sha=%s checkout_sha=%s\n' \
  "${#docs[@]}" \
  "${head_count}" \
  "${explicit_count}" \
  "${historical_count}" \
  "${#changed_docs[@]}" \
  "${HEAD_SHA}" \
  "${TARGET_SHA}"
