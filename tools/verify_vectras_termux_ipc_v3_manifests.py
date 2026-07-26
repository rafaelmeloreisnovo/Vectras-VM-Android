#!/usr/bin/env python3
"""Verify each Vectras manifest exposes only the bounded IPC surface."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VARIANTS = ("debug", "release", "perfRelease")
REQUIRED = (
    'com.termux.rafacodephi.permission.RUN_COMMAND',
    '<package android:name="com.termux.rafacodephi"',
    'com.vectras.vm.integration.VectrasTermuxResultReceiver',
    'android:exported="false"',
)


def main() -> int:
    errors: list[str] = []
    checked: list[str] = []
    for variant in VARIANTS:
        path = ROOT / f"app/src/{variant}/AndroidManifest.xml"
        if not path.is_file():
            errors.append(f"missing manifest: {path}")
            continue
        text = path.read_text(encoding="utf-8")
        checked.append(str(path.relative_to(ROOT)))
        for snippet in REQUIRED:
            if snippet not in text:
                errors.append(f"{variant}: missing {snippet!r}")
        if 'VectrasTermuxResultReceiver' in text:
            receiver_index = text.index('VectrasTermuxResultReceiver')
            window = text[max(0, receiver_index - 300):receiver_index + 500]
            if 'android:exported="false"' not in window:
                errors.append(f"{variant}: receipt receiver is not locally non-exported")

    state = "PASS" if not errors else "FAIL"
    print(json.dumps({"state": state, "checked": checked, "errors": errors}, sort_keys=True))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
