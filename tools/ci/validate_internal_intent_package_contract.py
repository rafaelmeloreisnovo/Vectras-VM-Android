#!/usr/bin/env python3
"""Fail closed when internal Android preference intents target the wrong package.

The Java/Kotlin namespace is com.vectras.vm, while the installed Android package is
controlled by defaultConfig.applicationId. Explicit preference intents must target
the applicationId, not the source namespace.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD_GRADLE = ROOT / "app" / "build.gradle"
XML_ROOT = ROOT / "app" / "src" / "main" / "res" / "xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def application_id() -> str:
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    match = re.search(r"(?m)^\s*applicationId\s+[\"']([^\"']+)[\"']\s*$", text)
    if not match:
        raise RuntimeError("applicationId not found in app/build.gradle")
    return match.group(1)


def main() -> int:
    app_id = application_id()
    checked = 0
    errors: list[str] = []

    for xml_path in sorted(XML_ROOT.glob("*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError as exc:
            errors.append(f"{xml_path.relative_to(ROOT)}: invalid XML: {exc}")
            continue

        for intent in root.iter("intent"):
            target_class = (intent.get(ANDROID + "targetClass") or "").strip()
            target_package = (intent.get(ANDROID + "targetPackage") or "").strip()
            if not target_class.startswith("com.vectras.vm."):
                continue
            checked += 1
            if target_package != app_id:
                errors.append(
                    f"{xml_path.relative_to(ROOT)}: {target_class} targets "
                    f"package={target_package or '<missing>'}; expected applicationId={app_id}"
                )

    if checked == 0:
        errors.append("no internal com.vectras.vm.* preference intents were found")

    if errors:
        print("INTERNAL_INTENT_PACKAGE_CONTRACT: FAIL", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"INTERNAL_INTENT_PACKAGE_CONTRACT: PASS applicationId={app_id} intents={checked}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
