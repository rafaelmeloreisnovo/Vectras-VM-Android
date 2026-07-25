#!/usr/bin/env python3
"""Audit a Vectras freestanding final-link probe and emit a deterministic manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Iterable

TOKEN_VAZIO = "TOKEN_VAZIO"
ENTRY_SYMBOL = "vectra_freestanding_probe_entry"
ARCHIVE_WITNESS_SYMBOL = "abi_entry_validate_interop"

DENY_EXACT = {
    "abort",
    "calloc",
    "dlclose",
    "dlopen",
    "dlsym",
    "exit",
    "fprintf",
    "free",
    "fwrite",
    "malloc",
    "memcmp",
    "memcpy",
    "memmove",
    "memset",
    "printf",
    "putchar",
    "puts",
    "realloc",
    "snprintf",
    "sprintf",
    "strlen",
    "strnlen",
    "vfprintf",
}
DENY_PREFIXES = (
    "JNI_",
    "Java_",
    "__android_log_",
    "__aeabi_",
    "__cxa_",
    "__div",
    "__stack_chk_",
    "__udiv",
    "android_",
    "pthread_",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--map", required=True, dest="map_file", type=Path)
    parser.add_argument("--build-log", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--reference-binary", type=Path)
    parser.add_argument("--abi", required=True)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", TOKEN_VAZIO))
    parser.add_argument("--compiler")
    parser.add_argument("--linker")
    parser.add_argument("--readelf")
    parser.add_argument("--nm")
    parser.add_argument("--objdump")
    parser.add_argument("--b3sum")
    parser.add_argument("--allow-undefined", action="append", default=[])
    parser.add_argument("--require-blake3", action="store_true")
    return parser.parse_args()


def resolve_tool(explicit: str | None, candidates: Iterable[str]) -> str | None:
    if explicit:
        candidate_path = Path(explicit)
        if candidate_path.is_file():
            return str(candidate_path)
        return shutil.which(explicit)
    for candidate in candidates:
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
    return None


def run(tool: str, *arguments: str) -> str:
    completed = subprocess.run(
        [tool, *arguments],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env={**os.environ, "LC_ALL": "C"},
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(
            f"{Path(tool).name} {' '.join(arguments)} failed "
            f"with exit {completed.returncode}: {detail}"
        )
    return completed.stdout


def tool_identity(tool: str | None) -> dict[str, str]:
    if tool is None:
        return {"command": TOKEN_VAZIO, "identity": TOKEN_VAZIO}
    try:
        output = run(tool, "--version")
        identity = next((line.strip() for line in output.splitlines() if line.strip()), TOKEN_VAZIO)
    except RuntimeError as error:
        identity = f"TOKEN_VAZIO_IDENTITY_ERROR:{error}"
    return {"command": Path(tool).name, "identity": identity}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def blake3_file(path: Path, b3sum: str | None) -> str:
    if b3sum is None:
        return TOKEN_VAZIO
    output = run(b3sum, str(path))
    digest = output.split(maxsplit=1)[0] if output.strip() else ""
    if not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        raise RuntimeError(f"invalid BLAKE3 output for {path.name}")
    return digest.lower()


def normalize_symbol(name: str) -> str:
    return name.split("@", 1)[0]


def parse_symbols(readelf_symbols: str) -> tuple[dict[str, int], set[str]]:
    defined: dict[str, int] = {}
    undefined: set[str] = set()
    for line in readelf_symbols.splitlines():
        fields = line.split()
        if len(fields) < 8 or not fields[0].rstrip(":").isdigit():
            continue
        value, index, name = fields[1], fields[6], normalize_symbol(fields[7])
        if not name:
            continue
        if index == "UND":
            undefined.add(name)
            continue
        try:
            defined.setdefault(name, int(value, 16))
        except ValueError:
            continue
    return defined, undefined


def parse_nm_undefined(nm_output: str) -> set[str]:
    undefined: set[str] = set()
    for line in nm_output.splitlines():
        fields = line.split()
        if not fields:
            continue
        candidate = normalize_symbol(fields[-1])
        if candidate and candidate not in {"U", "w", "v"}:
            undefined.add(candidate)
    return undefined


def parse_header(header: str) -> dict[str, str | int]:
    def value(label: str) -> str:
        match = re.search(rf"^\s*{re.escape(label)}:\s*(.+?)\s*$", header, re.MULTILINE)
        if match is None:
            raise RuntimeError(f"readelf header field missing: {label}")
        return match.group(1)

    entry_text = value("Entry point address")
    try:
        entry_value = int(entry_text, 16)
    except ValueError as error:
        raise RuntimeError(f"invalid ELF entry address: {entry_text}") from error
    return {
        "class": value("Class"),
        "type": value("Type"),
        "machine": value("Machine"),
        "entry_address": entry_text,
        "entry_address_value": entry_value,
    }


def parse_objdump_architecture(objdump_output: str) -> str:
    match = re.search(r"architecture:\s*([^,]+)", objdump_output)
    return match.group(1).strip() if match else TOKEN_VAZIO


def normalize_command(line: str, roots: Iterable[Path]) -> str:
    normalized = line.strip()
    normalized = re.sub(r"^\[\d+/\d+\]\s*", "", normalized)
    for root in sorted({str(path.resolve()) for path in roots}, key=len, reverse=True):
        normalized = normalized.replace(root, "<WORKSPACE>")
    return re.sub(r"\s+", " ", normalized)


def extract_effective_commands(
    build_log: str, binary: Path, map_file: Path, build_log_path: Path
) -> dict[str, str]:
    roots = (Path.cwd(), binary.parent, map_file.parent, build_log_path.parent)
    commands = [
        normalize_command(line, roots)
        for line in build_log.splitlines()
        if line.strip()
    ]
    compile_candidates = [
        line
        for line in commands
        if "freestanding_link_probe_entry.c" in line
        and re.search(r"(^|\s)-c(\s|$)", line)
    ]
    archive_candidates = [
        line
        for line in commands
        if "libabi_core_freestanding.a" in line
        and (
            "llvm-ar" in line
            or re.search(r"(^|\s)(?:\S*/)?(?:gcc-)?ar(\s|$)", line)
        )
    ]
    link_candidates = [
        line
        for line in commands
        if "vectra_freestanding_link_probe" in line
        and re.search(r"(^|\s)-o(\s|$)", line)
        and not re.search(r"(^|\s)-c(\s|$)", line)
    ]
    if not compile_candidates:
        raise RuntimeError("effective probe compile command missing from build log")
    if not archive_candidates:
        raise RuntimeError("effective archive command missing from build log")
    if not link_candidates:
        raise RuntimeError("effective probe link command missing from build log")
    return {
        "compile": compile_candidates[-1],
        "archive": archive_candidates[-1],
        "link": link_candidates[-1],
    }


def is_forbidden(symbol: str) -> bool:
    canonical = normalize_symbol(symbol)
    return canonical in DENY_EXACT or canonical.startswith(DENY_PREFIXES)


def initial_manifest(args: argparse.Namespace) -> dict[str, object]:
    return {
        "schema": "vectra.freestanding-link-probe.v1",
        "result": "FAIL",
        "source": {"abi": args.abi, "commit": args.commit or TOKEN_VAZIO},
        "toolchain": {},
        "effective_commands": {
            "compile": TOKEN_VAZIO,
            "archive": TOKEN_VAZIO,
            "link": TOKEN_VAZIO,
        },
        "artifact": {
            "file": args.binary.name,
            "size_bytes": TOKEN_VAZIO,
            "sha256": TOKEN_VAZIO,
            "blake3": TOKEN_VAZIO,
            "reference_sha256": TOKEN_VAZIO,
            "reproducible": TOKEN_VAZIO,
        },
        "map": {
            "file": args.map_file.name,
            "sha256": TOKEN_VAZIO,
            "blake3": TOKEN_VAZIO,
            "archive_witness": False,
        },
        "elf": {},
        "symbols": {
            "allow_undefined": sorted(set(args.allow_undefined)),
            "deny_exact": sorted(DENY_EXACT),
            "deny_prefixes": list(DENY_PREFIXES),
            "undefined": [],
            "needed_libraries": [],
            "forbidden_present": [],
            "entry_symbol": ENTRY_SYMBOL,
            "archive_witness_symbol": ARCHIVE_WITNESS_SYMBOL,
        },
        "checks": {},
        "errors": [],
    }


def main() -> int:
    args = parse_args()
    manifest = initial_manifest(args)
    errors: list[str] = manifest["errors"]  # type: ignore[assignment]

    tools = {
        "compiler": resolve_tool(args.compiler, ("clang", "gcc", "cc")),
        "linker": resolve_tool(args.linker, ("ld.lld", "ld")),
        "readelf": resolve_tool(args.readelf, ("llvm-readelf", "readelf")),
        "nm": resolve_tool(args.nm, ("llvm-nm", "nm")),
        "objdump": resolve_tool(args.objdump, ("llvm-objdump", "objdump")),
        "b3sum": resolve_tool(args.b3sum, ("b3sum",)),
    }
    manifest["toolchain"] = {
        name: tool_identity(tool)
        for name, tool in tools.items()
        if name != "b3sum"
    }
    manifest["toolchain"]["b3sum"] = tool_identity(tools["b3sum"])  # type: ignore[index]

    for path, label in (
        (args.binary, "binary"),
        (args.map_file, "map"),
        (args.build_log, "build log"),
    ):
        if not path.is_file():
            errors.append(f"missing {label}: {path}")
    for name in ("readelf", "nm", "objdump"):
        if tools[name] is None:
            errors.append(f"required inspection tool unavailable: {name}")

    if args.binary.is_file() and args.binary.read_bytes()[:4] != b"\x7fELF":
        errors.append("probe artifact is not ELF")

    if not errors:
        try:
            build_log_text = args.build_log.read_text(encoding="utf-8")
            manifest["effective_commands"] = extract_effective_commands(
                build_log_text, args.binary, args.map_file, args.build_log
            )

            artifact_sha256 = sha256_file(args.binary)
            artifact_blake3 = blake3_file(args.binary, tools["b3sum"])
            map_sha256 = sha256_file(args.map_file)
            map_blake3 = blake3_file(args.map_file, tools["b3sum"])
            manifest["artifact"].update(  # type: ignore[union-attr]
                {
                    "size_bytes": args.binary.stat().st_size,
                    "sha256": artifact_sha256,
                    "blake3": artifact_blake3,
                }
            )
            map_text = args.map_file.read_text(encoding="utf-8", errors="replace")
            archive_witness = (
                "libabi_core_freestanding.a" in map_text
                and ARCHIVE_WITNESS_SYMBOL in map_text
            )
            manifest["map"].update(  # type: ignore[union-attr]
                {
                    "sha256": map_sha256,
                    "blake3": map_blake3,
                    "archive_witness": archive_witness,
                }
            )

            header_output = run(tools["readelf"], "-hW", str(args.binary))  # type: ignore[arg-type]
            dynamic_output = run(tools["readelf"], "-dW", str(args.binary))  # type: ignore[arg-type]
            symbols_output = run(tools["readelf"], "-sW", str(args.binary))  # type: ignore[arg-type]
            nm_output = run(tools["nm"], "-u", str(args.binary))  # type: ignore[arg-type]
            objdump_output = run(tools["objdump"], "-f", str(args.binary))  # type: ignore[arg-type]

            header = parse_header(header_output)
            defined, readelf_undefined = parse_symbols(symbols_output)
            undefined = readelf_undefined | parse_nm_undefined(nm_output)
            allowed_undefined = set(args.allow_undefined)
            unexpected_undefined = sorted(undefined - allowed_undefined)
            needed = sorted(
                set(re.findall(r"\(NEEDED\).*Shared library: \[(.+?)\]", dynamic_output))
            )
            all_symbols = set(defined) | undefined
            forbidden = sorted(symbol for symbol in all_symbols if is_forbidden(symbol))
            entry_address = defined.get(ENTRY_SYMBOL)
            entry_matches = (
                entry_address is not None
                and entry_address == header["entry_address_value"]
            )
            witness_present = ARCHIVE_WITNESS_SYMBOL in defined
            manifest["elf"] = {
                "class": header["class"],
                "type": header["type"],
                "machine": header["machine"],
                "objdump_architecture": parse_objdump_architecture(objdump_output),
                "entry_address": header["entry_address"],
                "entry_symbol_address": (
                    f"0x{entry_address:x}" if entry_address is not None else TOKEN_VAZIO
                ),
                "entry_matches_symbol": entry_matches,
            }
            manifest["symbols"].update(  # type: ignore[union-attr]
                {
                    "undefined": sorted(undefined),
                    "unexpected_undefined": unexpected_undefined,
                    "needed_libraries": needed,
                    "forbidden_present": forbidden,
                    "entry_symbol_present": entry_address is not None,
                    "archive_witness_symbol_present": witness_present,
                }
            )

            reproducible: bool | str = TOKEN_VAZIO
            reference_sha256 = TOKEN_VAZIO
            if args.reference_binary is not None:
                if not args.reference_binary.is_file():
                    errors.append(f"missing reference binary: {args.reference_binary}")
                    reproducible = False
                else:
                    reference_sha256 = sha256_file(args.reference_binary)
                    reproducible = reference_sha256 == artifact_sha256
                    if not reproducible:
                        errors.append("probe binary differs from clean reference build")
            manifest["artifact"].update(  # type: ignore[union-attr]
                {
                    "reference_sha256": reference_sha256,
                    "reproducible": reproducible,
                }
            )

            checks = {
                "archive_consumed": archive_witness and witness_present,
                "blake3_hashes": (
                    True
                    if artifact_blake3 != TOKEN_VAZIO and map_blake3 != TOKEN_VAZIO
                    else TOKEN_VAZIO
                ),
                "controlled_entry": entry_matches,
                "forbidden_symbols_absent": not forbidden,
                "no_needed_libraries": not needed,
                "no_unexpected_undefined_symbols": not unexpected_undefined,
                "reproducible_binary": reproducible,
            }
            manifest["checks"] = checks

            for check, passed in checks.items():
                if passed is False:
                    errors.append(f"failed check: {check}")
            if args.require_blake3 and checks["blake3_hashes"] is not True:
                errors.append("BLAKE3 is required but unavailable")
            if args.reference_binary is not None and reproducible is not True:
                errors.append("reference build reproducibility is required")
        except (OSError, RuntimeError, UnicodeError) as error:
            errors.append(str(error))

    manifest["errors"] = sorted(set(errors))
    manifest["result"] = "PASS" if not manifest["errors"] else "FAIL"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    if manifest["result"] == "PASS":
        print(f"PASS freestanding-link-probe: {args.output}")
        return 0
    print(
        "FAIL freestanding-link-probe: " + "; ".join(manifest["errors"]),  # type: ignore[arg-type]
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
