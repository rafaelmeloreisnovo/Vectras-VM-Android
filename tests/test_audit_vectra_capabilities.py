import importlib.util
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "tools" / "audit_vectra_capabilities.py"
spec = importlib.util.spec_from_file_location("audit_vectra_capabilities", MODULE_PATH)
mod = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(mod)


def dex_bytes(version=b"035", size=0x70):
    data = bytearray(size)
    data[:8] = b"dex\n" + version + b"\x00"
    struct.pack_into("<I", data, 8, 0)
    data[12:32] = bytes(range(20))
    struct.pack_into("<III", data, 32, size, 0x70, 0x12345678)
    return bytes(data)


def elf_bytes(machine, elf_class, e_type=3):
    data = bytearray(64)
    data[:4] = b"\x7fELF"
    data[4] = elf_class
    data[5] = 1
    data[6] = 1
    struct.pack_into("<H", data, 16, e_type)
    struct.pack_into("<H", data, 18, machine)
    return bytes(data)


class AuditTests(unittest.TestCase):
    def test_valid_apk_dex_and_arm_elf(self):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "ok.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"xml")
                archive.writestr("classes.dex", dex_bytes())
                archive.writestr("lib/armeabi-v7a/libok.so", elf_bytes(40, 1))
            report = mod.inspect_apk(apk)
            self.assertEqual("PASS", report["state"])
            self.assertTrue(report["claim_allowed"])

    def test_elf_directory_mismatch_fails(self):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "bad.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"xml")
                archive.writestr("classes.dex", dex_bytes())
                archive.writestr("lib/armeabi-v7a/libbad.so", elf_bytes(183, 2))
            report = mod.inspect_apk(apk)
            self.assertEqual("FAIL", report["state"])
            self.assertIn("one or more ELF headers/ABI mappings failed", report["failures"])

    def test_missing_tls_source_is_token_vazio(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            contract = {
                "capabilities": [{
                    "id": "TLS",
                    "required_paths": ["src/tls.c"],
                    "required_markers": {},
                    "declared_state": "PROVEN_RUNTIME",
                    "claim_allowed": True,
                }]
            }
            result = mod.audit_capabilities(root, contract)[0]
            self.assertEqual(mod.TOKEN_VAZIO, result["effective_state"])
            self.assertFalse(result["claim_allowed"])

    def test_loose_inventory_routes_and_duplicates(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "Incluir").mkdir()
            (root / "_incoming").mkdir()
            (root / "Incluir" / "a.c").write_text("int x;")
            (root / "_incoming" / "b.c").write_text("int x;")
            policy = {
                "scan_roots": ["Incluir", "_incoming"],
                "route_by_suffix": {".c": "source/c"},
                "default_route": "quarantine/unclassified",
            }
            report = mod.scan_loose_artifacts(root, policy)
            self.assertEqual(2, report["records_count"])
            self.assertEqual(1, len(report["duplicates"]))
            self.assertEqual({"source/c": 2}, report["route_counts"])

    def test_unknown_abi_directory_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "abi.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"xml")
                archive.writestr("classes.dex", dex_bytes())
                # mips64 is not in ABI_MACHINE — should fail closed
                archive.writestr("lib/mips64/libbad.so", elf_bytes(8, 2))
            report = mod.inspect_apk(apk)
            self.assertEqual("FAIL", report["state"])
            elf_failures = [f for e in report["elf"] for f in e.get("failures", [])]
            self.assertTrue(any("unknown ABI directory" in f for f in elf_failures))

    def test_elf_exec_type_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "exec.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"xml")
                archive.writestr("classes.dex", dex_bytes())
                # ET_EXEC = 2, not ET_DYN = 3
                archive.writestr("lib/armeabi-v7a/libexec.so", elf_bytes(40, 1, e_type=2))
            report = mod.inspect_apk(apk)
            self.assertEqual("FAIL", report["state"])
            elf_failures = [f for e in report["elf"] for f in e.get("failures", [])]
            self.assertTrue(any("ET_DYN" in f for f in elf_failures))

    def test_unknown_dex_version_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "dex.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"xml")
                # version 099 is not a known Android DEX version
                archive.writestr("classes.dex", dex_bytes(version=b"099"))
            report = mod.inspect_apk(apk)
            self.assertEqual("FAIL", report["state"])
            dex_failures = [f for d in report["dex"] for f in d.get("failures", [])]
            self.assertTrue(any("not a known Android" in f for f in dex_failures))

    def test_dex_in_subdirectory_ignored(self):
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "subdir.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"xml")
                # DEX in a subdirectory should NOT be treated as valid APK DEX
                archive.writestr("assets/classes.dex", dex_bytes())
                # No root-level DEX → missing DEX failure
            report = mod.inspect_apk(apk)
            self.assertIn("no classes*.dex entries", report["failures"])
            # Subdirectory DEX is not parsed
            self.assertEqual([], report["dex"])

    def test_lowfala_integration_absence_is_explicit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "Incluir").mkdir()
            (root / "Incluir" / "compiladorlowFala.txt").write_text("PIPELINE")
            (root / "CMakeLists.txt").write_text("project(x)")
            contract = {"capabilities": [{
                "id": "LOW",
                "required_paths": ["Incluir/compiladorlowFala.txt"],
                "required_markers": {"Incluir/compiladorlowFala.txt": ["PIPELINE"]},
                "integration_needles": ["compiladorlowFala.txt"],
                "integration_files": ["CMakeLists.txt"],
                "declared_state": "DOCUMENT_GENERATOR_UNINTEGRATED",
                "claim_allowed": False,
            }]}
            result = mod.audit_capabilities(root, contract)[0]
            self.assertEqual("UNREFERENCED_BY_CANONICAL_BUILD", result["integration_state"])


if __name__ == "__main__":
    unittest.main()
