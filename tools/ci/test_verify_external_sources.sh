#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERIFY="${SCRIPT_DIR}/verify_external_sources.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

FAKE_BIN="${TMP}/bin"
mkdir -p "${FAKE_BIN}"

cat > "${FAKE_BIN}/git" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${GIT_MOCK_LOG}"

if [[ "${1:-}" == "ls-remote" ]]; then
  if [[ "${GIT_MOCK_MODE:-head}" == "missing" ]]; then
    exit 2
  fi
  printf '%s\trefs/heads/%s\n' "${GIT_MOCK_HEAD}" "${GIT_MOCK_BRANCH:-master}"
  exit 0
fi

echo "unexpected mock git invocation: $*" >&2
exit 98
MOCK
chmod +x "${FAKE_BIN}/git"

HEAD_SHA="ae94fc60aabb0bbe82abb01038b33ecba790e4ce"
MANIFEST_OK="${TMP}/ok.manifest"
MANIFEST_BAD="${TMP}/bad.manifest"
LOG="${TMP}/git.log"

cat > "${MANIFEST_OK}" <<EOF
qemu_rafaelia|https://github.com/example/qemu_rafaelia|master|.third_party_forks/qemu_rafaelia|${HEAD_SHA}
EOF

: > "${LOG}"
output="$({
  PATH="${FAKE_BIN}:${PATH}" \
  GIT_MOCK_LOG="${LOG}" \
  GIT_MOCK_HEAD="${HEAD_SHA}" \
  GIT_MOCK_BRANCH="master" \
  "${VERIFY}" --manifest "${MANIFEST_OK}" --check-remote
} 2>&1)"

grep -Fq "pin matches branch head" <<< "${output}"
if grep -Ev '^ls-remote ' "${LOG}" | grep -q .; then
  echo "head-pin fast path invoked an unexpected git command" >&2
  cat "${LOG}" >&2
  exit 1
fi

: > "${LOG}"
if PATH="${FAKE_BIN}:${PATH}" \
  GIT_MOCK_LOG="${LOG}" \
  GIT_MOCK_HEAD="${HEAD_SHA}" \
  GIT_MOCK_MODE="missing" \
  "${VERIFY}" --manifest "${MANIFEST_OK}" --check-remote >/dev/null 2>&1; then
  echo "missing remote branch was accepted" >&2
  exit 1
fi

cat > "${MANIFEST_BAD}" <<'EOF'
qemu_rafaelia|https://github.com/example/qemu_rafaelia|master|../escape|ae94fc60aabb0bbe82abb01038b33ecba790e4ce
EOF

if PATH="${FAKE_BIN}:${PATH}" \
  GIT_MOCK_LOG="${LOG}" \
  GIT_MOCK_HEAD="${HEAD_SHA}" \
  "${VERIFY}" --manifest "${MANIFEST_BAD}" >/dev/null 2>&1; then
  echo "unsafe destination was accepted" >&2
  exit 1
fi

echo "verify_external_sources contract tests: PASS"
