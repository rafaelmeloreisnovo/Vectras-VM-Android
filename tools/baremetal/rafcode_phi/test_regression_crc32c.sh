#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${ROOT_DIR}/build"

bash "${ROOT_DIR}/build_rafcode_phi.sh" >/tmp/rafphi_build.log

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    echo "assertion failed: missing '${needle}'"
    echo "--- output ---"
    echo "${haystack}"
    exit 1
  fi
}

check_arch() {
  local arch="$1"
  local expected_crc="$2"
  local expected_w0="$3"
  local expected_w1="$4"
  local expected_w2="$5"
  local expected_w3="$6"

  local out
  out="$(${BUILD_DIR}/rafcode_phi_cli --arch "${arch}" NOP RET BRK HLT)"
  assert_contains "${out}" "rafcode_phi.rejected=0"
  assert_contains "${out}" "rafcode_phi.crc32c=${expected_crc}"
  assert_contains "${out}" "word[0]=${expected_w0}"
  assert_contains "${out}" "word[1]=${expected_w1}"
  assert_contains "${out}" "word[2]=${expected_w2}"
  assert_contains "${out}" "word[3]=${expected_w3}"
}

check_layout_files() {
  local out_prefix="${BUILD_DIR}/regression_layout"
  rm -f "${out_prefix}.hex" "${out_prefix}.bin"

  ${BUILD_DIR}/rafcode_phi_cli --arch x86_64 --out-prefix "${out_prefix}" NOP RET BRK HLT >/tmp/rafphi_layout.log

  if [[ ! -f "${out_prefix}.hex" ]]; then
    echo "missing hex output"
    exit 1
  fi
  if [[ ! -f "${out_prefix}.bin" ]]; then
    echo "missing bin output"
    exit 1
  fi

  local first_line
  first_line="$(head -n 1 "${out_prefix}.hex")"
  assert_contains "${first_line}" "RAFCODE_PHI_HEX"

  python - <<'PY' "${out_prefix}.bin"
import struct, sys
path = sys.argv[1]
with open(path, 'rb') as f:
    data = f.read()
if len(data) < 24:
    raise SystemExit("bin layout too short")
magic, version, arch, count, crc32c, flags = struct.unpack('<6I', data[:24])
assert magic == 0x52414650, hex(magic)
assert version == 0x00010000, hex(version)
assert arch == 2, arch
assert count == 4, count
assert flags == 0, flags
words = struct.unpack('<4I', data[24:24+16])
assert words == (0x90, 0xC3, 0xCC, 0xF4), words
print("ok: bin fixed layout")
PY
}

# Valores congelados de regressão (token table + CRC32C determinístico)
check_arch aarch64 0x6E1F1BB8 0xD503201F 0xD65F03C0 0xD4200000 0xD4400000
check_arch armv7   0xE5E01ADD 0xE320F000 0xE12FFF1E 0xE1200070 0xE320F003
check_arch x86_64  0xA82E68BF 0x00000090 0x000000C3 0x000000CC 0x000000F4
check_arch riscv64 0x8D5521D6 0x00000013 0x00008067 0x00100073 0x10500073
check_layout_files

echo "ok: rafcode_phi regression crc32c/token table/layout"
