#!/usr/bin/env python3
"""Fail-closed verifier for the Vectras x32/x64 geometry integration boundary."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


def exact_allowlist_entry(source: str, binary: str) -> bool:
    pattern = rf'^\s*"{re.escape(binary)}",\s*$'
    return re.search(pattern, source, flags=re.MULTILINE) is not None


def verify(repo_root: Path):
    bridge_path = repo_root / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxBridge.kt"
    ipc_path = repo_root / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxIpcContract.kt"
    contract_path = repo_root / "docs/contracts/VECTRAS_X3264_SQUARE_MEDIAN_POC_V1.json"

    bridge = bridge_path.read_text(encoding="utf-8")
    ipc = ipc_path.read_text(encoding="utf-8")
    contract = json.loads(contract_path.read_text(encoding="utf-8"))

    checks = {
        "system_i386_allowed": exact_allowlist_entry(bridge, "qemu-system-i386"),
        "system_x86_64_allowed": exact_allowlist_entry(bridge, "qemu-system-x86_64"),
        "linux_user_i386_not_silently_allowed": not exact_allowlist_entry(bridge, "qemu-i386"),
        "linux_user_x86_64_not_silently_allowed": not exact_allowlist_entry(bridge, "qemu-x86_64"),
        "system_accel_argument_present": '"-accel", "tcg"' in ipc,
        "system_display_argument_present": '"-display", "none"' in ipc,
        "system_serial_argument_present": '"-serial", "stdio"' in ipc,
        "contract_blocks_dispatch": contract.get("dispatch_allowed") is False,
        "contract_blocks_claim": contract.get("claim_allowed") is False,
        "profile_mismatch_typed": contract.get("finding", {}).get("state") == "BLOCKED_PROFILE_MISMATCH",
        "linux_user_profile_is_token_vazio": contract.get("gates", {}).get("linux_user_profile") == "TOKEN_VAZIO_NOT_IMPLEMENTED",
        "future_contract_is_typed": "SYSTEM_VM" in contract.get("finding", {}).get("next_contract", "") and "LINUX_USER" in contract.get("finding", {}).get("next_contract", ""),
    }

    passed = all(checks.values())
    return {
        "schema": "raf.vectras-x3264-square-median-verification.v1",
        "state": "PASS_STATIC_PROFILE_BOUNDARY" if passed else "FAIL_CLOSED",
        "checks": checks,
        "dispatch_allowed": False,
        "claim_allowed": False,
        "next_step": "implement IPC v4 with separate SYSTEM_VM and LINUX_USER profiles",
        "pass": passed,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = verify(args.repo_root.resolve())
    encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
