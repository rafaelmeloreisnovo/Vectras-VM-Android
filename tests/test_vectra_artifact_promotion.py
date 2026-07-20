#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


PROMOTION = load_module("promotion_verifier", ROOT / "tools/promotion/verify_promotion_batch.py")
LOWFALA = load_module("lowfala_extractor", ROOT / "tools/compilerlowfala/extract_seeds.py")


class PromotionBatchTests(unittest.TestCase):
    def make_repo(self):
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        source = root / "Incluir/sample.txt"
        source.parent.mkdir(parents=True)
        source.write_text("MARKER\nsource\n", encoding="utf-8")
        adapter = root / "tools/adapter.py"
        adapter.parent.mkdir(parents=True)
        adapter.write_text("# adapter\n", encoding="utf-8")
        batch = {
            "batch_id": "TEST_BATCH",
            "repository": "owner/repo",
            "source_ref": "test",
            "artifacts": [
                {
                    "id": "ART_1",
                    "source_path": "Incluir/sample.txt",
                    "expected_git_blob_sha": PROMOTION.git_blob_sha1(source.read_bytes()),
                    "required_markers": ["MARKER"],
                    "adapter_paths": ["tools/adapter.py"],
                    "promotion_state": "ADAPTER_INTEGRATED_UNEXECUTED",
                    "automatic_move_allowed": False,
                    "claim_allowed": False,
                }
            ],
        }
        return temp, root, source, batch

    def test_valid_batch_passes_without_enabling_claim(self):
        temp, root, _, batch = self.make_repo()
        self.addCleanup(temp.cleanup)
        report, status = PROMOTION.validate_batch(root, batch)
        self.assertEqual(0, status)
        self.assertEqual("PASS", report["state"])
        self.assertFalse(report["claim_allowed"])
        self.assertEqual(1, report["pass_count"])

    def test_blob_tampering_fails(self):
        temp, root, source, batch = self.make_repo()
        self.addCleanup(temp.cleanup)
        source.write_text("MARKER\ntampered\n", encoding="utf-8")
        report, status = PROMOTION.validate_batch(root, batch)
        self.assertEqual(1, status)
        self.assertEqual("FAIL", report["state"])
        self.assertIn("git blob mismatch", " ".join(report["failures"]))

    def test_missing_marker_fails(self):
        temp, root, source, batch = self.make_repo()
        self.addCleanup(temp.cleanup)
        source.write_text("different content\n", encoding="utf-8")
        batch["artifacts"][0]["expected_git_blob_sha"] = PROMOTION.git_blob_sha1(source.read_bytes())
        report, status = PROMOTION.validate_batch(root, batch)
        self.assertEqual(1, status)
        self.assertIn("missing markers", " ".join(report["failures"]))

    def test_unproven_state_cannot_enable_claim(self):
        temp, root, _, batch = self.make_repo()
        self.addCleanup(temp.cleanup)
        batch["artifacts"][0]["claim_allowed"] = True
        report, status = PROMOTION.validate_batch(root, batch)
        self.assertEqual(1, status)
        self.assertIn("claim_allowed=true forbidden", " ".join(report["failures"]))

    def test_automatic_move_is_blocked(self):
        temp, root, _, batch = self.make_repo()
        self.addCleanup(temp.cleanup)
        batch["artifacts"][0]["automatic_move_allowed"] = True
        report, status = PROMOTION.validate_batch(root, batch)
        self.assertEqual(1, status)
        self.assertIn("automatic_move_allowed", " ".join(report["failures"]))


class LowFalaExtractorTests(unittest.TestCase):
    SAMPLE = (
        "seed_S01_V1_alpha() { cat << 'SEED'\n"
        "alpha body\n"
        "SEED\n"
        "}\n"
        "seed_S02_V3_beta() { cat << 'END'\n"
        "beta body\n"
        "END\n"
        "}\n"
    )

    def test_seed_blocks_are_indexed_by_family_and_variant(self):
        records = LOWFALA.parse_seed_blocks(self.SAMPLE)
        self.assertEqual(2, len(records))
        self.assertEqual("S01", records[0]["family"])
        self.assertEqual("V3", records[1]["variant"])
        self.assertEqual(64, len(records[0]["body_sha256"]))

    def test_required_count_is_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "lowfala.txt"
            source.write_text(self.SAMPLE, encoding="utf-8")
            report, status = LOWFALA.build_index(source, required_count=60)
            self.assertEqual(1, status)
            self.assertEqual("FAIL", report["state"])
            self.assertIn("differs from required 60", " ".join(report["failures"]))

    def test_reversible_extraction_writes_seed_bodies_only(self):
        records = LOWFALA.parse_seed_blocks(self.SAMPLE)
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "seeds"
            LOWFALA.extract_blocks(destination, records)
            self.assertEqual("alpha body\n", (destination / "seed_S01_V1_alpha.seed").read_text(encoding="utf-8"))
            self.assertEqual("beta body\n", (destination / "seed_S02_V3_beta.seed").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
