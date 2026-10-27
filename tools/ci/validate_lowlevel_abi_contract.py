#!/usr/bin/env python3
"""Validate lowlevel ABI contract, stable exports and no-implicit-libc policy."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "tools" / "ci" / "lowlevel_abi_contract.json"
ABI_HEADER = ROOT / "app" / "src" / "main" / "cpp" / "lowlevel_abi.h"
ABI_SOURCE = ROOT / "app" / "src" / "main" / "cpp" / "lowlevel_abi.c"
CRITICAL_LAYER_GLOB = "vectra_lowlevel_backend*.c"
CRITICAL_LAYER_HEADER = ROOT / "app" / "src" / "main" / "cpp" / "vectra_lowlevel_backend.h"

FORBIDDEN_INCLUDES = (
    "#include <stdlib.h>",
    "#include <stdio.h>",
    "#include <malloc.h>",
    "#include <libc",
)
FORBIDDEN_SYMBOLS = ("malloc(", "calloc(", "realloc(", "free(", "posix_memalign(")
MANDATORY_ARCH = "arm64-v8a"
OPTIONAL_INTERNAL_ARCH = "armeabi-v7a"


def fail(msg: str) -> int:
    print(f"LOWLEVEL_ABI_CONTRACT_VIOLATION: {msg}")
    return 1


def _check_forbidden(path: Path, content: str) -> list[str]:
    violations: list[str] = []
    for token in FORBIDDEN_INCLUDES:
        if token in content:
            violations.append(f"{path}: forbidden include detected ({token})")
    for token in FORBIDDEN_SYMBOLS:
        if token in content:
            violations.append(f"{path}: forbidden stdlib symbol detected ({token})")
    return violations


def _find_arch(architectures: list[dict], name: str) -> dict | None:
    for arch in architectures:
        if arch.get("name") == name:
            return arch
    return None


def _extract_declared_abi_entries(header: str) -> set[str]:
    return set(re.findall(r"\b(abi_entry_[a-zA-Z0-9_]+)\s*\(", header))


def _extract_defined_abi_entries(source: str) -> set[str]:
    return set(re.findall(r"\b(abi_entry_[a-zA-Z0-9_]+)\s*\(", source))


def main() -> int:
    if not CONTRACT.exists():
        return fail(f"missing contract file: {CONTRACT}")

    data = json.loads(CONTRACT.read_text(encoding="utf-8"))

    if data.get("schema_version") != "2.0.0":
        return fail("schema_version must be 2.0.0")

    architectures = data.get("architectures", [])
    if not architectures:
        return fail("architectures table cannot be empty")

    required_keys = {
        "name",
        "abi_version",
        "required",
        "internal_only",
        "stack_alignment",
        "frame_policy",
        "calling_convention",
        "prologue",
        "epilogue",
        "syscall_policy",
    }
    for arch in architectures:
        missing = required_keys.difference(arch.keys())
        if missing:
            return fail(f"architecture '{arch.get('name', '<unknown>')}' missing keys: {sorted(missing)}")

    mandatory = _find_arch(architectures, MANDATORY_ARCH)
    if not mandatory:
        return fail(f"missing mandatory architecture '{MANDATORY_ARCH}'")
    if not mandatory.get("required", False):
        return fail(f"'{MANDATORY_ARCH}' must set required=true")

    optional_internal = _find_arch(architectures, OPTIONAL_INTERNAL_ARCH)
    if optional_internal:
        if optional_internal.get("required", True):
            return fail(f"'{OPTIONAL_INTERNAL_ARCH}' must set required=false")
        if not optional_internal.get("internal_only", False):
            return fail(f"'{OPTIONAL_INTERNAL_ARCH}' must set internal_only=true")

    error_codes = data.get("error_codes", {})
    required_errors = {
        "ok",
        "unsupported_arch",
        "unsupported_abi_version",
        "stack_misaligned",
        "bad_frame_policy",
        "calling_convention",
        "forbidden_syscall",
        "boundary_violation",
        "null_ptr",
    }
    missing_errors = required_errors.difference(error_codes.keys())
    if missing_errors:
        return fail(f"missing standard error codes: {sorted(missing_errors)}")

    export_symbols = data.get("export_symbols", [])
    if not export_symbols:
        return fail("export_symbols table cannot be empty")

    if not ABI_HEADER.exists() or not ABI_SOURCE.exists():
        return fail("missing lowlevel ABI source/header files")

    header_content = ABI_HEADER.read_text(encoding="utf-8")
    source_content = ABI_SOURCE.read_text(encoding="utf-8")
    header_entries = _extract_declared_abi_entries(header_content)
    source_entries = _extract_defined_abi_entries(source_content)

    for entry in export_symbols:
        symbol = entry.get("symbol", "")
        if not symbol.startswith("abi_entry_"):
            return fail(f"export symbol '{symbol}' must start with abi_entry_")
        if symbol not in header_entries:
            return fail(f"export symbol '{symbol}' missing declaration in {ABI_HEADER}")
        if symbol not in source_entries:
            return fail(f"export symbol '{symbol}' missing definition in {ABI_SOURCE}")
        if "since_abi" not in entry:
            return fail(f"export symbol '{symbol}' missing since_abi")

    critical_files = [ABI_HEADER, ABI_SOURCE, CRITICAL_LAYER_HEADER]
    critical_files.extend(sorted((ROOT / "app" / "src" / "main" / "cpp").glob(CRITICAL_LAYER_GLOB)))

    for path in critical_files:
        if not path.exists():
            return fail(f"missing critical ABI layer file: {path}")
        content = path.read_text(encoding="utf-8")
        violations = _check_forbidden(path, content)
        if violations:
            return fail("; ".join(violations))

    # JNI is hosted; this gate only checks cross-boundary arity and does not
    # classify the JNI adapter itself as freestanding or prove device execution.
    jni_path = ROOT / "app" / "src" / "main" / "cpp" / "vectra_core_accel.c"
    java_path = ROOT / "app" / "src" / "main" / "java" / "com" / "vectras" / "vm" / "core" / "NativeFastPath.java"
    if not jni_path.is_file() or not java_path.is_file():
        return fail("missing JNI route producer or Java consumer")

    native_text = jni_path.read_text(encoding="utf-8")
    java_text = java_path.read_text(encoding="utf-8")
    native_route = re.search(
        r"\bJNIEXPORT\s+jlongArray\s+JNICALL\s+"
        r"Java_com_vectras_vm_core_NativeFastPath_nativeProcessRoute\s*"
        r"\([^)]*\)\s*\{(?P<body>.*?)\}\s*(?=JNIEXPORT|\Z)",
        native_text, re.DOTALL,
    )
    if not native_route:
        return fail("nativeProcessRoute JNI export not found")
    body = native_route.group("body")
    words = re.search(r"\bjlong\s+out\s*\[\s*(\d+)\s*\]\s*=", body)
    allocated = re.search(r"->NewLongArray\s*\(\s*env\s*,\s*(\d+)\s*\)", body)
    copied = re.search(
        r"->SetLongArrayRegion\s*\(\s*env\s*,\s*arr\s*,\s*0\s*,\s*(\d+)\s*,\s*out\s*\)",
        body,
    )
    counts = [int(match.group(1)) for match in (words, allocated, copied) if match]
    if len(counts) != 3 or len(set(counts)) != 1:
        return fail(f"native route array/allocation/copy cardinality mismatch: {counts}")

    # First five words remain readable by existing VectraCore clients;
    # four extension words must not be silently discarded by the JNI bridge.
    declared = re.findall(r"\bNATIVE_ROUTE_WORDS\s*=\s*(\d+)\s*;", java_text)
    if len(declared) != 1 or int(declared[0]) != counts[0]:
        return fail(f"Java JNI route arity {declared} differs from C producer {counts[0]}")
    if not re.search(
        r"if\s*\(\s*route\s*!=\s*null\s*&&\s*"
        r"route\.length\s*==\s*NATIVE_ROUTE_WORDS\s*\)",
        java_text,
    ):
        return fail("Java must validate the native route array against NATIVE_ROUTE_WORDS")

    print(
        "LOWLEVEL_ABI_CONTRACT_OK: schema=2.0.0, arm64 mandatory, armv7 optional-internal, exports stable, critical layer without implicit libc/stdlib, JNI route arity aligned"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
