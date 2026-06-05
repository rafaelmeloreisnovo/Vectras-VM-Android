import copy
import json
import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from tools.docs.validate_rafaelia_completion_manifest import DEFAULT_MANIFEST, main, validate_manifest


class RafaeliaCompletionManifestTests(unittest.TestCase):
    def load_manifest(self):
        return json.loads(DEFAULT_MANIFEST.read_text(encoding="utf-8"))

    def test_repository_manifest_is_valid(self) -> None:
        self.assertEqual(validate_manifest(self.load_manifest()), [])

    def test_rejects_missing_required_invariant(self) -> None:
        data = self.load_manifest()
        data["invariants"] = [item for item in data["invariants"] if item != "ATTRACTOR_COUNT=42"]
        errors = validate_manifest(data)
        self.assertTrue(any("ATTRACTOR_COUNT=42" in error for error in errors))

    def test_rejects_less_than_twenty_work_modes(self) -> None:
        data = self.load_manifest()
        data["work_modes"] = data["work_modes"][:19]
        errors = validate_manifest(data)
        self.assertTrue(any("requires at least" in error for error in errors))

    def test_rejects_mode_without_rollback(self) -> None:
        data = self.load_manifest()
        data["work_modes"] = copy.deepcopy(data["work_modes"])
        data["work_modes"][0]["rollback"] = ""
        errors = validate_manifest(data)
        self.assertTrue(any("rollback" in error for error in errors))


    def test_rejects_missing_state_of_art_dimension(self) -> None:
        data = self.load_manifest()
        data["state_of_art_gate"]["dimensions"] = data["state_of_art_gate"]["dimensions"][:11]
        errors = validate_manifest(data)
        self.assertTrue(any("state_of_art_gate.dimensions" in error for error in errors))

    def test_rejects_low_state_of_art_score(self) -> None:
        data = self.load_manifest()
        for dimension in data["state_of_art_gate"]["dimensions"]:
            dimension["score"] = 1
        errors = validate_manifest(data)
        self.assertTrue(any("total score" in error for error in errors))

    def test_cli_passes_valid_manifest_file(self) -> None:
        data = self.load_manifest()
        with tempfile.TemporaryDirectory() as tmpdir:
            path = Path(tmpdir) / "manifest.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(main([str(path)]), 0)


if __name__ == "__main__":
    unittest.main()
