#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BINDING = ROOT / "docs/federation/ATLAS_OPTIONAL_RUNTIME_BINDING_V1.json"
REPO_CONTRACT = ROOT / "docs/federation/REPOSITORY_CONTRACT_V1.md"
IPC_CONTRACT = ROOT / "docs/contracts/VECTRAS_TERMUX_IPC_V2.md"


def main() -> int:
    doc = json.loads(BINDING.read_text(encoding="utf-8"))
    assert doc["schema"] == "rafaelia.vectras.atlas_optional_runtime_binding.v1"
    assert doc["repository"] == "rafaelmeloreisnovo/Vectras-VM-Android"
    assert len(doc["baseline_commit"]) == 40
    assert doc["role"] == "VECTRAS_OPTIONAL_RUNTIME"
    assert doc["routing_authority"] == "rafaelmeloreisnovo/Mapa"
    assert doc["orchestration_owner"] == "rafaelmeloreisnovo/termux-app-rafacodephi"
    assert doc["input_boundary"] == "GOVERNED_BOUNDED_VM_REQUEST"
    assert doc["requires_upstream_governance"] is True
    for key in ("may_extract_intent", "may_grant_capabilities", "may_bypass_termux_governance", "may_promote_apk_to_guest_boot"):
        assert doc[key] is False, key
    assert doc["physical_android_e2e"] == "TOKEN_VAZIO"
    assert doc["guest_boot"] == "TOKEN_VAZIO"
    assert doc["claim_allowed"] is False
    required = ["proot_ok", "qemu_binary_ok", "process_start_ok", "guest_boot_evidence"]
    assert all(gate in doc["health_gates"] for gate in required)
    assert REPO_CONTRACT.is_file() and IPC_CONTRACT.is_file()
    repo_text = REPO_CONTRACT.read_text(encoding="utf-8")
    ipc_text = IPC_CONTRACT.read_text(encoding="utf-8")
    assert "missing device boot transcript" in repo_text
    assert "DISPATCHED" not in ipc_text or "guest boot" in ipc_text
    print("atlas_optional_runtime_binding_v1: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
