#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROOT_CMAKE = ROOT / "CMakeLists.txt"
APP_CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"
ANDROID_PLATFORM_CMAKE = ROOT / "engine/platform/android/CMakeLists.txt"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def extract_set_block(text: str, variable: str) -> str:
    match = re.search(rf"set\(\s*{re.escape(variable)}\b(.*?)\n\)", text, re.DOTALL)
    require(match is not None, f"missing set({variable}) block")
    return match.group(1)


def main() -> int:
    root = ROOT_CMAKE.read_text(encoding="utf-8")
    app = APP_CMAKE.read_text(encoding="utf-8")
    platform = ANDROID_PLATFORM_CMAKE.read_text(encoding="utf-8")

    require("cmake_minimum_required(VERSION 3.22.1)" in root,
            "root CMake must support cmake_language(DEFER)")
    require("include(${CMAKE_SOURCE_DIR}/engine/platform/android/CMakeLists.txt)" in root,
            "root CMake no longer includes Android platform policy")
    require("project(rafaelia_rmr C ASM)" in root,
            "root project language contract drifted")
    require("project(vectra_core_accel C ASM)" in app,
            "app project language contract drifted")

    require("VECTRA_ANDROID_PLATFORM_CXX_FLAGS" not in platform,
            "dead Android platform flag export returned")
    require("function(vectra_apply_android_platform_c_flags target_name)" in platform,
            "target-scoped Android flag function missing")
    require("cmake_language(DEFER CALL vectra_apply_android_platform_c_flags rmr)" in platform,
            "Android policy is not wired to rmr target")
    require("$<$<COMPILE_LANGUAGE:C>:${_vectra_android_cflags}>" in platform,
            "Android flags are not language-scoped to C")
    require("-fno-rtti" not in platform and "-fno-exceptions" not in platform,
            "C++-only flags returned to C/ASM platform module")
    require("-O2" not in platform and "-O3" not in platform,
            "platform module must not override optimization profile")

    freestanding = extract_set_block(app, "VECTRA_FREESTANDING_COMPILE_OPTIONS")
    require("-ffreestanding" in freestanding, "freestanding compile flag missing")
    require("-fno-builtin" in freestanding, "no-builtin compile contract missing")
    require("-fno-rtti" not in freestanding, "C++ RTTI flag present in C-only target")
    require("-fno-exceptions" not in freestanding, "C++ exception flag present in C-only target")
    require("-Werror=implicit-function-declaration" in freestanding,
            "implicit libc/API calls are not blocking")

    require("target_link_options(abi_core_freestanding" not in app,
            "STATIC archive still carries a false final-link contract")
    require("VECTRA_FREESTANDING_LINK_STATE" in app,
            "freestanding link evidence state is not explicit")
    require("TOKEN_VAZIO_DEDICATED_LINK_PROBE" in app,
            "dedicated final-link probe gap is not preserved")
    require("target_link_libraries(vectra_core_accel PRIVATE abi_core_freestanding)" in app,
            "JNI artifact no longer consumes freestanding archive")

    print("PASS cmake-language-link-contract")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as error:
        print(f"FAIL cmake-language-link-contract: {error}", file=sys.stderr)
        raise SystemExit(1)
