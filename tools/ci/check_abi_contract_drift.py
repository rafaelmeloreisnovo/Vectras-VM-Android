#!/usr/bin/env python3
"""Validate ABI contract drift across gradle.properties, qemu config and workflows."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = ROOT / "tools" / "ci" / "abi_profiles_contract.json"
GRADLE_PATH = ROOT / "gradle.properties"
QEMU_PATH = ROOT / "tools" / "qemu_launch.yml"
WORKFLOWS_DIR = ROOT / ".github" / "workflows"


def fail(message: str) -> None:
    print(f"ABI_CONTRACT_DRIFT: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_gradle_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def extract_qemu_abi_filters(path: Path) -> tuple[list[str], list[str]]:
    text = path.read_text(encoding="utf-8")
    scope_match = re.search(r"abi_filters:\n(?:[ \t].*\n)*?\s*official_distribution:\n((?:\s*-\s*[^\n]+\n)+)", text)
    internal_match = re.search(r"internal_validation:\n((?:\s*-\s*[^\n]+\n)+)", text)
    if not scope_match or not internal_match:
        fail("tools/qemu_launch.yml não contém blocos abi_filters.official_distribution/internal_validation válidos")

    def parse_items(block: str) -> list[str]:
        return [re.sub(r"^\s*-\s*", "", line).strip() for line in block.strip().splitlines()]

    return parse_items(scope_match.group(1)), parse_items(internal_match.group(1))


def main() -> int:
    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    profiles: dict[str, dict] = contract["profiles"]

    gradle = parse_gradle_properties(GRADLE_PATH)
    gradle_policy = gradle.get("APP_ABI_POLICY")
    gradle_supported = [abi.strip() for abi in gradle.get("SUPPORTED_ABIS", "").split(",") if abi.strip()]

    if not gradle_policy or not gradle_supported:
        fail("gradle.properties deve definir APP_ABI_POLICY e SUPPORTED_ABIS")

    gradle_matches = [
        name
        for name, cfg in profiles.items()
        if cfg["app_abi_policy"] == gradle_policy and cfg["supported_abis"] == gradle_supported
    ]
    if not gradle_matches:
        fail(
            "gradle.properties APP_ABI_POLICY/SUPPORTED_ABIS não corresponde a nenhum profile em tools/ci/abi_profiles_contract.json"
        )

    qemu_official, qemu_internal = extract_qemu_abi_filters(QEMU_PATH)
    expected_official = profiles["official_arm64"]["supported_abis"]
    expected_internal = profiles["official_arm32_arm64"]["supported_abis"]

    if qemu_official != expected_official:
        fail(
            f"tools/qemu_launch.yml build_env.abi_filters.official_distribution={qemu_official} diverge de official_arm64={expected_official}"
        )

    if qemu_internal != expected_internal:
        fail(
            f"tools/qemu_launch.yml build_env.abi_filters.internal_validation={qemu_internal} diverge de official_arm32_arm64={expected_internal}"
        )

    contract_profiles = set(profiles.keys())
    contract_abis = {abi for cfg in profiles.values() for abi in cfg["supported_abis"]}

    for wf_path in sorted(WORKFLOWS_DIR.glob("*.yml")):
        text = wf_path.read_text(encoding="utf-8")

        if ("abi_profile:" in text and "resolve_abi_profile.py" not in text and "uses: ./.github/workflows/android-ci.yml" not in text
                and wf_path.name not in {"android.yml", "pipeline-orchestrator.yml"}):
            fail(f"{wf_path} declara abi_profile mas não resolve via tools/ci/resolve_abi_profile.py")

        for profile in re.findall(r"\b(official_arm64|official_arm32_arm64|internal_arm64|internal_arm32_arm64|internal_4abi|internal_5abi|internal_riscv64|universal_guarded|generic)\b", text):
            if profile not in contract_profiles:
                fail(f"{wf_path} referencia abi_profile fora do contrato: {profile}")

        matrix_matches = re.findall(r"\babi:\s*\[([^\]]+)\]", text)
        for match in matrix_matches:
            listed = [item.strip() for item in match.split(",") if item.strip()]
            unknown = [abi for abi in listed if abi not in contract_abis]
            if unknown:
                fail(f"{wf_path} matrix.abi contém ABIs fora do contrato: {unknown}")

        for literal in re.findall(r"-PAPP_ABI_POLICY=\"([^\"]+)\"", text):
            if literal and literal not in {"${app_abi_policy}", "${{ needs.resolve.outputs.app_abi_policy }}", "${{ steps.abi.outputs.app_abi_policy }}"}:
                fail(f"{wf_path} usa APP_ABI_POLICY literal ('{literal}'); resolva via resolve_abi_profile.py")

        for literal in re.findall(r"-PSUPPORTED_ABIS=\"([^\"]+)\"", text):
            if literal and literal not in {"${supported_abis}", "${{ needs.resolve.outputs.supported_abis }}", "${{ steps.abi.outputs.supported_abis }}"}:
                fail(f"{wf_path} usa SUPPORTED_ABIS literal ('{literal}'); resolva via resolve_abi_profile.py")

    print("ABI contract drift check: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
