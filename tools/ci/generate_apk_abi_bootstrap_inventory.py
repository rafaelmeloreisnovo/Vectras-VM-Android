#!/usr/bin/env python3
"""Gera e valida o inventário ABI/bootstrap de um APK.

O relatório não impõe ARM32 a uma lane ARM64. A política exata da lane é
validada pelos gates Gradle anteriores; este utilitário verifica a coerência
interna do APK para cada ABI nativa realmente observada, ou para uma lista
explícita fornecida por --required-abis.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import zipfile
from pathlib import Path

KNOWN_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "riscv64")
NATIVE_SENTINEL = "libtermux-bootstrap.so"
PAIR_LIBRARY = "libvectra_core_accel.so"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_abis(raw: str | None) -> list[str]:
    if not raw:
        return []
    values = [item for item in re.split(r"[\s,;]+", raw.strip()) if item]
    unknown = sorted(set(values).difference(KNOWN_ABIS))
    if unknown:
        raise SystemExit(f"ABIs desconhecidas em --required-abis: {', '.join(unknown)}")
    return list(dict.fromkeys(values))


def observed_abis(names: set[str]) -> list[str]:
    return [
        abi
        for abi in KNOWN_ABIS
        if f"lib/{abi}/{NATIVE_SENTINEL}" in names
    ]


def requirements_for(abis: list[str]) -> list[str]:
    required = ["assets/bootstrap/loader.apk"]
    for abi in abis:
        required.append(f"lib/{abi}/{NATIVE_SENTINEL}")
        required.append(f"lib/{abi}/{PAIR_LIBRARY}")
        # O bootstrap TAR existe para as quatro ABIs Android históricas. O
        # riscv64 permanece somente nativo até existir asset canônico próprio.
        if abi != "riscv64":
            required.append(f"assets/bootstrap/{abi}.tar")
    return required


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--out", default="reports/APK_ABI_BOOTSTRAP_INVENTORY.md")
    parser.add_argument(
        "--required-abis",
        default=os.environ.get("SUPPORTED_ABIS", ""),
        help="Lista separada por espaço/vírgula. Vazio = derivar das libs observadas.",
    )
    args = parser.parse_args()

    apk = Path(args.apk)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")

    explicit_abis = parse_abis(args.required_abis)
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
        detected = observed_abis(names)
        required_abis = explicit_abis or detected
        mode = "explicit" if explicit_abis else "observed"

        if not required_abis:
            raise SystemExit("Nenhuma ABI com libtermux-bootstrap.so foi observada no APK")

        missing_explicit = sorted(set(explicit_abis).difference(detected))
        required_paths = requirements_for(required_abis)
        missing_paths = [path for path in required_paths if path not in names]
        ok = not missing_explicit and not missing_paths

        lines = [
            "# APK_ABI_BOOTSTRAP_INVENTORY",
            "",
            f"- APK: `{apk}`",
            f"- validation_mode: `{mode}`",
            f"- required_abis: `{','.join(required_abis)}`",
            f"- observed_abis: `{','.join(detected)}`",
            "",
        ]
        for path in required_paths:
            if path in names:
                raw = archive.read(path)
                lines.append(
                    f"- OK `{path}` size={len(raw)} sha256={sha256(raw)[:16]}"
                )
            else:
                lines.append(f"- MISSING `{path}`")

        if missing_explicit:
            lines.extend(
                ["", f"- MISSING_REQUIRED_ABIS: `{','.join(missing_explicit)}`"]
            )
        lines.extend(["", f'- STATUS: {"PASS" if ok else "FAIL"}'])

    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out)
    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
