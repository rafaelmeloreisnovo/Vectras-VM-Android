#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: tools/qemu/import_qemu_rafaelia_artifact.sh --artifact <tar.gz|dir> [--dest-dir DIR]

Verifies and imports a qemu_rafaelia artifact into a deterministic local staging area.
This script is intentionally host-side. Android installation paths must be handled by the app/setup layer.
USAGE
}

ARTIFACT=""
DEST_DIR=".third_party_forks/qemu_rafaelia_artifact"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact)
      ARTIFACT="$2"
      shift 2
      ;;
    --dest-dir)
      DEST_DIR="$2"
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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VERIFY_SCRIPT="${SCRIPT_DIR}/verify_qemu_rafaelia_artifact.sh"

if [[ ! -x "${VERIFY_SCRIPT}" ]]; then
  chmod +x "${VERIFY_SCRIPT}"
fi

"${VERIFY_SCRIPT}" --artifact "${ARTIFACT}"

TMPDIR="$(mktemp -d)"
cleanup() {
  rm -rf "${TMPDIR}"
}
trap cleanup EXIT

if [[ -d "${ARTIFACT}" ]]; then
  SRC_ROOT="${ARTIFACT}"
else
  tar -xzf "${ARTIFACT}" -C "${TMPDIR}"
  SRC_ROOT="${TMPDIR}/qemu-rafaelia-artifact"
fi

DEST_ABS="${REPO_ROOT}/${DEST_DIR}"
rm -rf "${DEST_ABS}"
mkdir -p "$(dirname "${DEST_ABS}")"
cp -a "${SRC_ROOT}" "${DEST_ABS}"

python3 - "${DEST_ABS}" <<'PY'
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
qemu_exec = json.loads((root / "qemu-exec.json").read_text(encoding="utf-8"))
import_record = {
    "imported_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
    "artifact_root": str(root),
    "source_repo": qemu_exec.get("source_repo", ""),
    "source_commit": qemu_exec.get("source_commit", ""),
    "version": qemu_exec.get("version", ""),
    "architectures": sorted(qemu_exec.get("binary", {}).keys()),
}
(root / ".qemu-rafaelia-import.json").write_text(
    json.dumps(import_record, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print("Imported qemu_rafaelia artifact")
print(json.dumps(import_record, indent=2, sort_keys=True))
PY

echo "Artifact imported into ${DEST_ABS}"
