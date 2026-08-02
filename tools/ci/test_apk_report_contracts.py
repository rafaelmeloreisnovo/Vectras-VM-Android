#!/usr/bin/env python3
"""Testes determinísticos dos relatórios APK por perfil ABI."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INVENTORY = ROOT / "tools" / "ci" / "generate_apk_abi_bootstrap_inventory.py"
COLLECTOR = ROOT / "tools" / "ci" / "collect_apk_report.sh"


def create_apk(path: Path, abis: tuple[str, ...]) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr("assets/bootstrap/loader.apk", b"loader")
        for abi in abis:
            archive.writestr(f"lib/{abi}/libtermux-bootstrap.so", b"termux-" + abi.encode())
            archive.writestr(f"lib/{abi}/libvectra_core_accel.so", b"core-" + abi.encode())
            if abi != "riscv64":
                archive.writestr(f"assets/bootstrap/{abi}.tar", b"tar-" + abi.encode())


def run(command: list[str], *, cwd: Path, expect: int = 0) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)
    if completed.returncode != expect:
        raise AssertionError(
            f"returncode {completed.returncode} != {expect}: {' '.join(command)}\n"
            f"stdout:\n{completed.stdout}\nstderr:\n{completed.stderr}"
        )
    return completed


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="apk-report-contract-") as tmp_raw:
        tmp = Path(tmp_raw)
        outputs = tmp / "app" / "build" / "outputs" / "apk" / "debug"
        outputs.mkdir(parents=True)
        arm64_apk = outputs / "arm64-debug.apk"
        create_apk(arm64_apk, ("arm64-v8a",))

        inventory = tmp / "arm64.md"
        run(
            [sys.executable, str(INVENTORY), "--apk", str(arm64_apk), "--out", str(inventory),
             "--required-abis", "arm64-v8a"],
            cwd=tmp,
        )
        text = inventory.read_text(encoding="utf-8")
        assert "STATUS: PASS" in text
        assert "required_abis: `arm64-v8a`" in text

        run(
            [sys.executable, str(INVENTORY), "--apk", str(arm64_apk), "--out", str(tmp / "dual.md"),
             "--required-abis", "arm64-v8a,armeabi-v7a"],
            cwd=tmp,
            expect=2,
        )

        report_dir = tmp / "report-arm64"
        run(
            ["bash", str(COLLECTOR), str(report_dir), "--required-abis", "arm64-v8a"],
            cwd=tmp,
        )
        payload = json.loads((report_dir / "apk_report.json").read_text(encoding="utf-8"))
        assert payload["required_abis"] == ["arm64-v8a"]
        assert payload["apks"][0]["required_abis_present"] is True

        run(
            ["bash", str(COLLECTOR), str(tmp / "report-dual-fail"),
             "--required-abis", "arm64-v8a,armeabi-v7a"],
            cwd=tmp,
            expect=1,
        )

        arm64_apk.unlink()
        dual_apk = outputs / "dual-debug.apk"
        create_apk(dual_apk, ("arm64-v8a", "armeabi-v7a"))
        run(
            ["bash", str(COLLECTOR), str(tmp / "report-dual-pass"),
             "--required-abis", "arm64-v8a,armeabi-v7a"],
            cwd=tmp,
        )

    print("APK report contract tests: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
