#!/usr/bin/env python3
from pathlib import Path
from repo_audit_and_plan import audit


def test_audit_runs() -> None:
    root = Path(__file__).resolve().parents[1]
    report = audit(root)
    assert report["total_files"] > 0
    assert report["pending_count"] >= 0
    assert "summary_by_status" in report


if __name__ == "__main__":
    test_audit_runs()
    print("ok")
