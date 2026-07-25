#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROOT_CMAKE = ROOT / "CMakeLists.txt"
APP_CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"
PROBE_ENTRY = ROOT / "app/src/main/cpp/freestanding_link_probe_entry.c"
ANDROID_PLATFORM_CMAKE = ROOT / "engine/platform/android/CMakeLists.txt"
AUDIT = ROOT / "tools/ci/audit_freestanding_link_probe.py"
WORKFLOW = ROOT / ".github/workflows/cmake-language-link-contract.yml"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def extract_set_block(text: str, variable: str) -> str:
    match = re.search(rf"set\(\s*{re.escape(variable)}\b(.*?)\)", text, re.DOTALL)
    require(match is not None, f"missing set({variable}) block")
    return match.group(1)


def strip_cmake_comments(text: str) -> str:
    return re.sub(r"(?m)^[ \t]*#.*(?:\n|$)", "", text)


def main() -> int:
    root = ROOT_CMAKE.read_text(encoding="utf-8")
    app = APP_CMAKE.read_text(encoding="utf-8")
    probe = PROBE_ENTRY.read_text(encoding="utf-8")
    platform = ANDROID_PLATFORM_CMAKE.read_text(encoding="utf-8")
    audit = AUDIT.read_text(encoding="utf-8")
    workflow = WORKFLOW.read_text(encoding="utf-8")
    app_code = strip_cmake_comments(app)
    platform_code = strip_cmake_comments(platform)

    require("cmake_minimum_required(VERSION 3.22.1)" in root,
            "root CMake must support cmake_language(DEFER)")
    require("include(${CMAKE_SOURCE_DIR}/engine/platform/android/CMakeLists.txt)" in root,
            "root CMake no longer includes Android platform policy")
    require("project(rafaelia_rmr C ASM)" in root,
            "root project language contract drifted")
    require("project(vectra_core_accel C ASM)" in app,
            "app project language contract drifted")

    require("VECTRA_ANDROID_PLATFORM_CXX_FLAGS" not in platform_code,
            "dead Android platform flag export returned")
    require("function(vectra_apply_android_platform_c_flags target_name)" in platform_code,
            "target-scoped Android flag function missing")
    require("cmake_language(DEFER CALL vectra_apply_android_platform_c_flags rmr)" in platform_code,
            "Android policy is not wired to rmr target")
    require("$<$<COMPILE_LANGUAGE:C>:${_vectra_android_cflags}>" in platform_code,
            "Android flags are not language-scoped to C")
    require("-fno-rtti" not in platform_code and "-fno-exceptions" not in platform_code,
            "C++-only flags returned to C/ASM platform module")
    require("-O2" not in platform_code and "-O3" not in platform_code,
            "platform module must not override optimization profile")

    freestanding = extract_set_block(app, "VECTRA_FREESTANDING_COMPILE_OPTIONS")
    require("-ffreestanding" in freestanding, "freestanding compile flag missing")
    require("-fno-builtin" in freestanding, "no-builtin compile contract missing")
    require("-fno-stack-protector" in freestanding,
            "stack-protector runtime dependency is not disabled")
    require("-fno-rtti" not in freestanding, "C++ RTTI flag present in C-only target")
    require("-fno-exceptions" not in freestanding, "C++ exception flag present in C-only target")
    require("-Werror=implicit-function-declaration" in freestanding,
            "implicit libc/API calls are not blocking")

    require("target_link_options(abi_core_freestanding" not in app_code,
            "STATIC archive still carries a false final-link contract")
    require("VECTRA_FREESTANDING_LINK_STATE" in app,
            "freestanding link evidence state is not explicit")
    require("add_executable(vectra_freestanding_link_probe EXCLUDE_FROM_ALL" in app,
            "dedicated final-link executable target is missing")
    require("target_link_libraries(vectra_freestanding_link_probe PRIVATE" in app
            and "abi_core_freestanding)" in app,
            "dedicated final-link target does not consume the freestanding archive")
    require("target_link_options(vectra_freestanding_link_probe PRIVATE" in app,
            "dedicated final-link options are missing")
    for option in (
        "-nostdlib",
        "-Wl,--gc-sections",
        "-Wl,--build-id=none",
        "-Wl,--no-undefined",
        "-Wl,-e,vectra_freestanding_probe_entry",
        "-Wl,-Map,${VECTRA_FREESTANDING_PROBE_MAP}",
    ):
        require(option in app, f"dedicated final-link option missing: {option}")
    require("IMPLEMENTED_DEDICATED_LINK_PROBE" in app,
            "implemented final-link probe state is not explicit")
    require("target_link_libraries(vectra_core_accel PRIVATE abi_core_freestanding)" in app,
            "JNI artifact no longer consumes freestanding archive")

    require('#include "lowlevel_abi.h"' in probe,
            "probe does not consume the canonical lowlevel ABI")
    require("vectra_freestanding_probe_entry" in probe,
            "controlled probe entry point is missing")
    require("abi_entry_validate_interop" in probe,
            "probe does not retain a real freestanding archive symbol")
    for forbidden in ("<stdio.h>", "<stdlib.h>", "<string.h>", "malloc(", "free(", "printf("):
        require(forbidden not in probe, f"probe contains forbidden hosted dependency: {forbidden}")

    for audit_contract in (
        'ENTRY_SYMBOL = "vectra_freestanding_probe_entry"',
        'ARCHIVE_WITNESS_SYMBOL = "abi_entry_validate_interop"',
        '"allow_undefined": sorted(set(args.allow_undefined))',
        '"deny_exact": sorted(DENY_EXACT)',
        '"needed_libraries": []',
        '"sha256": TOKEN_VAZIO',
        '"blake3": TOKEN_VAZIO',
        '"reproducible": TOKEN_VAZIO',
        '"effective_commands"',
        '"readelf"',
        '"nm"',
        '"objdump"',
    ):
        require(audit_contract in audit, f"probe audit contract missing: {audit_contract}")

    for workflow_contract in (
        "host-probe:",
        "android-ndk-probe:",
        "- arm64-v8a",
        "- armeabi-v7a",
        "--reference-binary",
        "--require-blake3",
        "--readelf",
        "--nm",
        "--objdump",
        "actions/upload-artifact@v4",
    ):
        require(workflow_contract in workflow,
                f"probe workflow contract missing: {workflow_contract}")

    print("PASS cmake-language-link-contract")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as error:
        print(f"FAIL cmake-language-link-contract: {error}", file=sys.stderr)
        raise SystemExit(1)
