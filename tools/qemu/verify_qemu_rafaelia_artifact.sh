#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: tools/qemu/verify_qemu_rafaelia_artifact.sh --artifact <tar.gz|dir>

Verifies a qemu_rafaelia artifact before Vectras consumes it.
Required files:
  - qemu-exec.json
  - BUILD_INFO.json
  - SHA256SUMS.txt
  - at least one executable bin/qemu-system-*
USAGE
}

ARTIFACT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact)
      ARTIFACT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${ARTIFACT}" ]]; then
  echo "ERROR: --artifact is required" >&2
  usage >&2
  exit 2
fi

for cmd in python3 sha256sum find tar; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: required command not found: ${cmd}" >&2
    exit 1
  fi
done

WORKDIR=""
cleanup() {
  if [[ -n "${WORKDIR}" && -d "${WORKDIR}" ]]; then
    rm -rf "${WORKDIR}"
  fi
}
trap cleanup EXIT

if [[ -d "${ARTIFACT}" ]]; then
  ROOT="${ARTIFACT}"
else
  if [[ ! -f "${ARTIFACT}" ]]; then
    echo "ERROR: artifact not found: ${ARTIFACT}" >&2
    exit 1
  fi
  WORKDIR="$(mktemp -d)"
  tar -xzf "${ARTIFACT}" -C "${WORKDIR}"
  ROOT="${WORKDIR}/qemu-rafaelia-artifact"
fi

if [[ ! -d "${ROOT}" ]]; then
  echo "ERROR: artifact root not found: ${ROOT}" >&2
  exit 1
fi

for required in qemu-exec.json BUILD_INFO.json SHA256SUMS.txt; do
  if [[ ! -f "${ROOT}/${required}" ]]; then
    echo "ERROR: missing ${required}" >&2
    exit 1
  fi
done

if ! find "${ROOT}/bin" -maxdepth 1 -type f -name 'qemu-system-*' -perm -111 | grep -q .; then
  echo "ERROR: no executable qemu-system-* binary found in ${ROOT}/bin" >&2
  exit 1
fi

(
  cd "${ROOT}"
  sha256sum -c SHA256SUMS.txt
)

python3 - "${ROOT}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
qemu_exec = json.loads((root / "qemu-exec.json").read_text(encoding="utf-8"))
build_info = json.loads((root / "BUILD_INFO.json").read_text(encoding="utf-8"))

errors = []
for key in ["source_repo", "source_commit", "version", "binary", "sha256"]:
    if key not in qemu_exec:
        errors.append(f"qemu-exec.json missing key: {key}")
for key in ["source_repo", "source_commit", "source_branch", "qemu_version", "binaries"]:
    if key not in build_info:
        errors.append(f"BUILD_INFO.json missing key: {key}")

if qemu_exec.get("source_commit") != build_info.get("source_commit"):
    errors.append("source_commit mismatch between qemu-exec.json and BUILD_INFO.json")

binary_map = qemu_exec.get("binary", {})
sha_map = qemu_exec.get("sha256", {})
if not binary_map:
    errors.append("binary map is empty")

for arch, rel in binary_map.items():
    path = root / rel
    if not path.is_file():
        errors.append(f"binary for {arch} missing: {rel}")
        continue
    if rel not in sha_map:
        errors.append(f"sha256 missing for binary {rel}")
        continue
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != sha_map[rel]:
        errors.append(f"sha256 mismatch for {rel}")

build_paths = {entry.get("path") for entry in build_info.get("binaries", [])}
for rel in binary_map.values():
    if rel not in build_paths:
        errors.append(f"binary {rel} missing from BUILD_INFO.json binaries")

if errors:
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    sys.exit(1)

print("QEMU RAFAELIA artifact contract OK")
print(f"source_repo={qemu_exec['source_repo']}")
print(f"source_commit={qemu_exec['source_commit']}")
print(f"version={qemu_exec['version']}")
print("architectures=" + ",".join(sorted(binary_map)))
PY
