#!/usr/bin/env python3
from __future__ import annotations

import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

import generate_ingress_manifest as ingress


def make_dex(version: bytes = b"035") -> bytes:
    data = bytearray(ingress.DEX_HEADER)
    data[:8] = b"dex\n" + version + b"\x00"
    struct.pack_into("<I", data, 0x20, len(data))
    struct.pack_into("<I", data, 0x24, ingress.DEX_HEADER)
    struct.pack_into("<I", data, 0x28, 0x12345678)
    return bytes(data)


def make_elf(machine: int, elf_class: int = 2) -> bytes:
    data = bytearray(64)
    data[:4] = b"\x7fELF"
    data[4] = elf_class
    data[5] = 1
    data[6] = 1
    struct.pack_into("<H", data, 16, 3)
    struct.pack_into("<H", data, 18, machine)
    return bytes(data)


class IngressScannerTest(unittest.TestCase):
    def test_valid_dex(self) -> None:
        result, findings = ingress.parse_dex(make_dex(), ingress.DEX_HEADER)
        self.assertEqual("035", result["version"])
        self.assertEqual([], findings)

    def test_aarch64_elf(self) -> None:
        result, findings = ingress.parse_elf(make_elf(183))
        self.assertEqual("aarch64", result["machine"])
        self.assertEqual("ELF64", result["class"])
        self.assertEqual([], findings)

    def test_apk_detects_native_abi_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "sample.apk"
            with zipfile.ZipFile(apk, "w", compression=zipfile.ZIP_STORED) as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
                archive.writestr("classes.dex", make_dex())
                archive.writestr("lib/armeabi-v7a/libbad.so", make_elf(183))
            record = ingress.inspect(apk, root)
            codes = {finding["code"] for finding in record["findings"]}
            self.assertEqual("apk", record["classification"]["archive_type"])
            self.assertIn("ELF_ABI_PATH_MISMATCH", codes)

    def test_zip_rejects_path_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive_path = root / "unsafe.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("../escape.txt", b"x")
            record = ingress.inspect(archive_path, root)
            codes = {finding["code"] for finding in record["findings"]}
            self.assertIn("ZIP_UNSAFE_PATH", codes)


if __name__ == "__main__":
    unittest.main()
