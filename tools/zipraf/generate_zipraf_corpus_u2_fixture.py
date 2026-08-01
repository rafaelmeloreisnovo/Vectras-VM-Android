#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import shutil
import struct
import zipfile
from pathlib import Path

FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def info(name: str, compress_type: int) -> zipfile.ZipInfo:
    value = zipfile.ZipInfo(name, FIXED_TIME)
    value.compress_type = compress_type
    value.create_system = 3
    value.external_attr = 0o100644 << 16
    value.flag_bits = 0x0800
    return value


def write_entry(archive: zipfile.ZipFile, name: str, data: bytes, method: int) -> None:
    archive.writestr(info(name, method), data)


def make_zip(path: Path) -> None:
    with zipfile.ZipFile(path, "w", allowZip64=False) as archive:
        write_entry(archive, "readme.txt", b"ZIPRAF corpus U2\n", zipfile.ZIP_STORED)
        write_entry(
            archive,
            "packed.txt",
            (b"harmonic-cycle-" * 16) + b"\n",
            zipfile.ZIP_DEFLATED,
        )


def make_apk(path: Path) -> None:
    with zipfile.ZipFile(path, "w", allowZip64=False) as archive:
        write_entry(
            archive,
            "AndroidManifest.xml",
            b"\x03\x00\x08\x00ZIPRAF-U2-MANIFEST",
            zipfile.ZIP_STORED,
        )
        write_entry(
            archive,
            "classes.dex",
            b"dex\n035\x00" + bytes(range(64)),
            zipfile.ZIP_STORED,
        )
        write_entry(
            archive,
            "resources.arsc",
            (b"resources-arsc-" * 32),
            zipfile.ZIP_DEFLATED,
        )
        write_entry(
            archive,
            "assets/data.bin",
            bytes((index * 17 + 3) & 0xFF for index in range(257)),
            zipfile.ZIP_STORED,
        )


def make_malformed(path: Path) -> None:
    path.write_bytes(b"PK\x03\x04ZIPRAF-U2-TRUNCATED")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    root = args.output
    if root.exists():
        shutil.rmtree(root)
    root.mkdir(parents=True)

    make_zip(root / "sample.zip")
    make_apk(root / "sample.apk")
    make_malformed(root / "malformed.zip")

    for path in sorted(root.iterdir()):
        os.utime(path, (1767225600, 1767225600))
        print(f"{path.name}\t{path.stat().st_size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
