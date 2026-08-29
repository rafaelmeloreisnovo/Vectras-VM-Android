#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_ROOT="${ROOT}/app/build/generated/bootstrapAssets"
OUT_RECEIPT="${ROOT}/artifacts/qemu19-materialization.json"
ABIS="arm64-v8a,armeabi-v7a"
ALPINE_VERSION="3.19"
QEMU_VERSION="8.1.5-r0"

# Bit-for-bit witnesses promoted from a successful APK Wizard build.
# Any transitive Alpine/Docker/repository drift that changes the final runtime
# must fail closed instead of silently producing a different embedded QEMU.
QEMU19_SHA256_ARM64_V8A="415f2a1bbe434707a8c4841a16019bd05bc8b68d2ba6bd0b6736376a4842eec5"
QEMU19_SHA256_ARMEABI_V7A="fb35e9481de32a8534413744c2479068b5252cfa715dea5f42e5f9325f20909a"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-root) TARGET_ROOT="$2"; shift 2 ;;
    --receipt) OUT_RECEIPT="$2"; shift 2 ;;
    --abis) ABIS="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

command -v docker >/dev/null 2>&1 || { echo "docker is required to assemble cross-arch Alpine runtime" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "tar is required" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }

mkdir -p "${TARGET_ROOT}/qemu19" "$(dirname "${OUT_RECEIPT}")"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
JSONL="${TMP}/assets.jsonl"
: > "${JSONL}"

IFS=',' read -r -a ABI_LIST <<< "${ABIS}"
for ABI in "${ABI_LIST[@]}"; do
  case "${ABI}" in
    arm64-v8a)
      APK_ARCH="aarch64"
      EXPECTED_SHA="${QEMU19_SHA256_ARM64_V8A}"
      ;;
    armeabi-v7a)
      APK_ARCH="armv7"
      EXPECTED_SHA="${QEMU19_SHA256_ARMEABI_V7A}"
      ;;
    *) echo "unsupported qemu19 ABI: ${ABI}" >&2; exit 2 ;;
  esac

  BASE_TAR="${TARGET_ROOT}/alpine19/${ABI}.tar"
  [[ -f "${BASE_TAR}" ]] || { echo "missing pinned Alpine seed: ${BASE_TAR}" >&2; exit 1; }

  WORK="${TMP}/${ABI}"
  ROOTFS="${WORK}/rootfs"
  mkdir -p "${ROOTFS}"
  tar -xf "${BASE_TAR}" -C "${ROOTFS}"

  HOST_UID="$(id -u)"
  HOST_GID="$(id -g)"
  docker run --rm \
    -e APK_ARCH="${APK_ARCH}" \
    -e QEMU_VERSION="${QEMU_VERSION}" \
    -e HOST_UID="${HOST_UID}" \
    -e HOST_GID="${HOST_GID}" \
    -v "${ROOTFS}:/rootfs" \
    "alpine:${ALPINE_VERSION}" \
    /bin/sh -euxc '
      mkdir -p /rootfs/etc/apk/keys
      cp -f /etc/apk/keys/* /rootfs/etc/apk/keys/
      printf "%s\n%s\n" \
        "https://dl-cdn.alpinelinux.org/alpine/v3.19/main" \
        "https://dl-cdn.alpinelinux.org/alpine/v3.19/community" \
        > /rootfs/etc/apk/repositories
      apk --root /rootfs --arch "$APK_ARCH" --no-cache --no-scripts add \
        "qemu-system-x86_64=$QEMU_VERSION" \
        "qemu-system-i386=$QEMU_VERSION" \
        "qemu-system-arm=$QEMU_VERSION" \
        "qemu-system-aarch64=$QEMU_VERSION" \
        "qemu-system-ppc=$QEMU_VERSION" \
        "qemu-img=$QEMU_VERSION"
      for b in x86_64 i386 arm aarch64 ppc; do
        test -x "/rootfs/usr/bin/qemu-system-$b"
      done
      test -x /rootfs/usr/bin/qemu-img
      chown -R "$HOST_UID:$HOST_GID" /rootfs
    '

  OUT_TAR="${TARGET_ROOT}/qemu19/${ABI}.tar"
  tar --sort=name \
    --mtime='UTC 1970-01-01' \
    --owner=0 --group=0 --numeric-owner \
    -cf "${OUT_TAR}" -C "${ROOTFS}" .

  SHA="$(sha256sum "${OUT_TAR}" | awk '{print $1}')"
  SIZE="$(stat -c '%s' "${OUT_TAR}")"
  if [[ "${SHA}" != "${EXPECTED_SHA}" ]]; then
    echo "[qemu19] HASH_MISMATCH abi=${ABI} expected=${EXPECTED_SHA} actual=${SHA}" >&2
    echo "[qemu19] refusing non-reproducible runtime; update witness only from independently audited evidence" >&2
    exit 1
  fi

  PKG_DB="${ROOTFS}/lib/apk/db/installed"
  PKG_COUNT="0"
  if [[ -f "${PKG_DB}" ]]; then
    PKG_COUNT="$(grep -c '^P:' "${PKG_DB}" || true)"
  fi

  python3 - "${ABI}" "${APK_ARCH}" "${OUT_TAR}" "${SHA}" "${EXPECTED_SHA}" "${SIZE}" "${PKG_COUNT}" "${QEMU_VERSION}" >> "${JSONL}" <<'PY'
import json, os, sys
abi, apk_arch, path, sha, expected_sha, size, pkg_count, qemu_version = sys.argv[1:]
print(json.dumps({
    "abi": abi,
    "alpine_arch": apk_arch,
    "target_path": f"qemu19/{abi}.tar",
    "sha256": sha,
    "expected_sha256": expected_sha,
    "sha256_enforced": True,
    "size_bytes": int(size),
    "package_count": int(pkg_count),
    "qemu_version": qemu_version,
    "required_binaries": [
        "usr/bin/qemu-system-x86_64",
        "usr/bin/qemu-system-i386",
        "usr/bin/qemu-system-arm",
        "usr/bin/qemu-system-aarch64",
        "usr/bin/qemu-system-ppc",
        "usr/bin/qemu-img",
    ],
}, sort_keys=True))
PY
  echo "[qemu19] MATERIALIZED_VERIFIED abi=${ABI} arch=${APK_ARCH} bytes=${SIZE} sha256=${SHA}"
done

python3 - "${JSONL}" "${OUT_RECEIPT}" "${ALPINE_VERSION}" "${QEMU_VERSION}" <<'PY'
import json, pathlib, sys
jsonl, output, alpine_version, qemu_version = sys.argv[1:]
assets = [json.loads(line) for line in pathlib.Path(jsonl).read_text().splitlines() if line.strip()]
receipt = {
    "schema_version": "vectras.embedded-qemu19-materialization.v2",
    "status": "MATERIALIZED_SHA256_ENFORCED_BUILD_TIME_NOT_DEVICE_TESTED",
    "alpine_branch": f"v{alpine_version}",
    "qemu_version": qemu_version,
    "source_repositories": [
        "https://dl-cdn.alpinelinux.org/alpine/v3.19/main",
        "https://dl-cdn.alpinelinux.org/alpine/v3.19/community",
    ],
    "determinism_contract": {
        "archive_normalization": "tar_sort_name_mtime_epoch_owner_group_zero",
        "final_sha256_fail_closed": True,
        "all_asset_hashes_enforced": all(asset.get("sha256_enforced") for asset in assets),
    },
    "assets": assets,
    "device_runtime_verified": False,
    "vm_boot_verified": False,
    "claim_allowed": False,
}
path = pathlib.Path(output)
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(receipt, sort_keys=True))
PY
