#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cmake_file="${repo_root}/app/src/main/cpp/CMakeLists.txt"
audit_file="${repo_root}/tools/ci/audit_freestanding_link_probe.py"
workflow_file="${repo_root}/.github/workflows/cmake-language-link-contract.yml"

[[ -f "${cmake_file}" ]] || { echo "missing ${cmake_file}"; exit 1; }
[[ -f "${audit_file}" ]] || { echo "missing ${audit_file}"; exit 1; }
[[ -f "${workflow_file}" ]] || { echo "missing ${workflow_file}"; exit 1; }

echo "[verify-freestanding] validating abi_core_freestanding contract"

required_patterns=(
  "add_library(abi_core_freestanding STATIC"
  "target_compile_options(abi_core_freestanding PRIVATE"
  "-ffreestanding"
  "-fno-builtin"
  "-fno-stack-protector"
  "-fno-unwind-tables"
  "-fno-asynchronous-unwind-tables"
  "-fvisibility=hidden"
  "add_executable(vectra_freestanding_link_probe EXCLUDE_FROM_ALL"
  "freestanding_link_probe_entry.c"
  "target_link_libraries(vectra_freestanding_link_probe PRIVATE"
  "target_link_options(vectra_freestanding_link_probe PRIVATE"
  "-nostdlib"
  "-Wl,--gc-sections"
  "-Wl,--build-id=none"
  "-Wl,--no-undefined"
  "-Wl,-e,vectra_freestanding_probe_entry"
  "-Wl,-Map,\${VECTRA_FREESTANDING_PROBE_MAP}"
  "IMPLEMENTED_DEDICATED_LINK_PROBE"
  "target_link_libraries(vectra_core_accel PRIVATE abi_core_freestanding)"
  "message(FATAL_ERROR \"VECTRA_REQUIRE_FREESTANDING_CORE must remain ON for release-safe builds.\")"
)

for pattern in "${required_patterns[@]}"; do
  if ! grep -Fq -- "${pattern}" "${cmake_file}"; then
    echo "missing required freestanding contract pattern: ${pattern}" >&2
    exit 1
  fi
done

if grep -Fq -- "target_link_options(abi_core_freestanding" "${cmake_file}"; then
  echo "STATIC archive must not carry final-link options" >&2
  exit 1
fi

probe_entry="${repo_root}/app/src/main/cpp/freestanding_link_probe_entry.c"
[[ -f "${probe_entry}" ]] || { echo "missing ${probe_entry}" >&2; exit 1; }

for forbidden in '<stdio.h>' '<stdlib.h>' '<string.h>' 'malloc(' 'free(' 'printf('; do
  if grep -Fq -- "${forbidden}" "${probe_entry}"; then
    echo "forbidden hosted dependency in probe entry: ${forbidden}" >&2
    exit 1
  fi
done

audit_patterns=(
  'ENTRY_SYMBOL = "vectra_freestanding_probe_entry"'
  'ARCHIVE_WITNESS_SYMBOL = "abi_entry_validate_interop"'
  '"effective_commands"'
  '"allow_undefined"'
  '"deny_exact"'
  '"sha256"'
  '"blake3"'
  '"reproducible"'
)
for pattern in "${audit_patterns[@]}"; do
  if ! grep -Fq -- "${pattern}" "${audit_file}"; then
    echo "missing audit contract pattern: ${pattern}" >&2
    exit 1
  fi
done

workflow_patterns=(
  "host-probe:"
  "android-ndk-probe:"
  "- arm64-v8a"
  "- armeabi-v7a"
  "--reference-binary"
  "--require-blake3"
  "--readelf"
  "--nm"
  "--objdump"
)
for pattern in "${workflow_patterns[@]}"; do
  if ! grep -Fq -- "${pattern}" "${workflow_file}"; then
    echo "missing workflow contract pattern: ${pattern}" >&2
    exit 1
  fi
done

echo "[verify-freestanding] ok"
