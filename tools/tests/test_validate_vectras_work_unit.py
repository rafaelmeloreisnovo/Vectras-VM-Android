import copy
import unittest

from tools.governance.validate_vectras_work_unit import validate_unit


BASE_COMMIT = "bbbc752c1e5f56875c5d4231d2d20c6bb7fe770f"


def base_unit():
    return {
        "id": "VECTRAS-WU-001",
        "intent": "validate one bounded Vectras consumer work unit",
        "authority": "rafaelmeloreisnovo/Vectras-VM-Android",
        "federated_authority": "rafaelmeloreisnovo/Mapa",
        "repo": "rafaelmeloreisnovo/Vectras-VM-Android",
        "source_commit": BASE_COMMIT,
        "target_claim": "STATIC_CONTRACT",
        "sources": [
            {
                "ref": f"GitHub:rafaelmeloreisnovo/Vectras-VM-Android@{BASE_COMMIT}",
                "kind": "CODE",
                "hash": BASE_COMMIT,
            },
            {
                "ref": "GitHub:rafaelmeloreisnovo/Mapa:CASA_CONHECIMENTO_TRABALHO_V1",
                "kind": "GOVERNANCE",
                "hash": "1d20bf3d0e2426095c36599b00f73570d3af7ad7",
            },
            {
                "ref": "Drive:title:RAFAELIA — NOVOexport Navigation Hub Ω",
                "kind": "INDEX",
                "hash": None,
            },
        ],
        "context": {
            "scope": "tools-only bounded governance gate",
            "boundary": "static validation only; no QEMU/device/guest/performance claim",
            "observed_at": "2026-09-13T15:55:00-03:00",
            "governance": "Vectras owns local mutation; Mapa owns federated route/state",
            "data": "PUBLIC",
            "privacy": "NOT_APPLICABLE",
            "security": "PUBLIC",
            "dependencies": [],
        },
        "evidence": [
            {
                "ref": "unittest:test_promotable_static_unit",
                "type": "STATIC_TEST",
            }
        ],
        "contradictions": [],
        "uncertainty": [],
        "reproduction": {
            "status": "PASS",
            "procedure": "python3 -m unittest tools.tests.test_validate_vectras_work_unit",
            "environment_ref": "python3-stdlib",
        },
        "rollback": {
            "ready": True,
            "predecessor": f"git:master@{BASE_COMMIT}",
            "procedure": "close branch/PR or revert branch commit; master remains unchanged",
        },
        "reconstructibility": {
            "status": "PASS",
            "inputs_pinned": True,
            "versions_pinned": True,
            "outputs_identified": True,
        },
        "state": "REPRODUCED",
        "claim_allowed": True,
        "next": "append bounded receipt and request review",
    }


class VectrasWorkUnitTests(unittest.TestCase):
    def test_promotable_static_unit(self):
        self.assertEqual(validate_unit(base_unit()), [])

    def test_open_contradiction_blocks_claim(self):
        unit = base_unit()
        unit["contradictions"] = [{"id": "C1", "state": "OPEN", "ref": "receipt:C1"}]
        errors = validate_unit(unit)
        self.assertIn("contradiction:open_but_claim_allowed", errors)
        self.assertIn("claim_gate:promotion_without_all_guards", errors)

    def test_token_vazio_blocks_claim(self):
        unit = base_unit()
        unit["uncertainty"] = [
            {"id": "U1", "state": "TOKEN_VAZIO", "needed": "physical Android receipt"}
        ]
        errors = validate_unit(unit)
        self.assertIn("uncertainty:unresolved_but_claim_allowed", errors)

    def test_reproduction_required(self):
        unit = base_unit()
        unit["reproduction"]["status"] = "TOKEN_VAZIO"
        errors = validate_unit(unit)
        self.assertIn("reproduction:claim_requires_pass", errors)
        self.assertIn("reproduction:state_requires_pass", errors)

    def test_rollback_required(self):
        unit = base_unit()
        unit["rollback"]["ready"] = False
        errors = validate_unit(unit)
        self.assertIn("rollback:claim_requires_ready_predecessor_procedure", errors)

    def test_guest_boot_needs_guest_boot_evidence(self):
        unit = base_unit()
        unit["target_claim"] = "GUEST_BOOT"
        errors = validate_unit(unit)
        self.assertIn("evidence:target_specific_proof_missing", errors)

    def test_qemu_process_is_not_performance(self):
        unit = base_unit()
        unit["target_claim"] = "PERFORMANCE"
        unit["evidence"] = [{"ref": "run:qemu-1", "type": "QEMU_PROCESS"}]
        errors = validate_unit(unit)
        self.assertIn("evidence:target_specific_proof_missing", errors)

    def test_provenance_requires_exact_commit(self):
        unit = base_unit()
        unit["source_commit"] = "master"
        errors = validate_unit(unit)
        self.assertIn("provenance:source_commit_not_exact_sha", errors)

    def test_invalid_privacy_classification_fails(self):
        unit = base_unit()
        unit["context"]["privacy"] = "UNKNOWN"
        errors = validate_unit(unit)
        self.assertIn("context:invalid_privacy_classification", errors)

    def test_draft_can_preserve_token_vazio_when_claim_is_false(self):
        unit = base_unit()
        unit["state"] = "BLOCKED"
        unit["claim_allowed"] = False
        unit["evidence"] = []
        unit["uncertainty"] = [
            {"id": "U-REMOTE-CI", "state": "TOKEN_VAZIO", "needed": "exact-head remote CI"}
        ]
        unit["reproduction"] = {
            "status": "TOKEN_VAZIO",
            "procedure": "",
            "environment_ref": None,
        }
        self.assertEqual(validate_unit(unit), [])

    def test_closed_requires_reconstructibility(self):
        unit = base_unit()
        unit["state"] = "CLOSED"
        unit["reconstructibility"]["outputs_identified"] = False
        errors = validate_unit(unit)
        self.assertIn("reconstructibility:closed_requires_pass", errors)


if __name__ == "__main__":
    unittest.main()
