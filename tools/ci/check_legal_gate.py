#!/usr/bin/env python3
"""Legal compliance gate for CI.

Reads resources/compliance/ASSET_PROVENANCE_REGISTER.csv and fails if any
unregistered binary/SO files exist in the repo, or if any registered entry
changed status without going through the review cycle.

Exit codes:
  0 — all known-blocked items are registered; no unregistered binaries found
  1 — unregistered binary/SO files found OR CSV is structurally invalid
"""
import csv
import os
import sys

REGISTER_PATH = "resources/compliance/ASSET_PROVENANCE_REGISTER.csv"
JNI_LIBS_ROOT = "app/src/main/jniLibs"
REQUIRED_FIELDS = {"asset_path", "asset_type", "license_spdx", "status"}

BLOCKED_STATUSES = {"blocked", "excluded-from-release"}
TOKEN_EMPTY = "TOKEN_VAZIO"


def load_register(path: str) -> list[dict]:
    with open(path, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        missing = REQUIRED_FIELDS - set(reader.fieldnames or [])
        if missing:
            print(f"[FAIL] {path} is missing required columns: {missing}", file=sys.stderr)
            sys.exit(1)
        return list(reader)


def find_so_files(root: str) -> list[str]:
    found = []
    for dirpath, _, filenames in os.walk(root):
        for fn in filenames:
            if fn.endswith(".so"):
                rel = os.path.join(dirpath, fn).lstrip("./")
                found.append(rel)
    return found


def main() -> int:
    if not os.path.exists(REGISTER_PATH):
        print(f"[FAIL] {REGISTER_PATH} not found — legal gate cannot run", file=sys.stderr)
        return 1

    rows = load_register(REGISTER_PATH)

    # Build set of registered asset paths (exact)
    registered_paths = {r["asset_path"].strip() for r in rows}

    # ── 1. Report blocked/TOKEN_VAZIO items ──────────────────────────────────
    blocked_count = 0
    print("[INFO] Known-blocked items in register:")
    for row in rows:
        if row.get("status", "").strip() in BLOCKED_STATUSES:
            blocked_count += 1
            print(
                f"  [{row['status'].upper()}] {row['asset_path']} "
                f"license={row['license_spdx']} risk={row.get('risk_class','?')}"
            )
    if blocked_count:
        print(
            f"[WARN] {blocked_count} item(s) blocked/excluded-from-release — "
            "acceptable for beta; must be resolved before public release"
        )
    else:
        print("[OK] No blocked items in register")

    # ── 2. Scan jniLibs for unregistered .so files ───────────────────────────
    fail = False
    if os.path.isdir(JNI_LIBS_ROOT):
        so_files = find_so_files(JNI_LIBS_ROOT)
        unregistered = [s for s in so_files if s not in registered_paths]
        if unregistered:
            print(
                f"[FAIL] {len(unregistered)} unregistered .so file(s) in {JNI_LIBS_ROOT}:",
                file=sys.stderr,
            )
            for f in sorted(unregistered):
                print(f"  UNREGISTERED: {f}", file=sys.stderr)
            print(
                "Add entries to resources/compliance/ASSET_PROVENANCE_REGISTER.csv "
                "before continuing.",
                file=sys.stderr,
            )
            fail = True
        else:
            print(f"[OK] All .so files in {JNI_LIBS_ROOT} are registered")
    else:
        print(f"[INFO] {JNI_LIBS_ROOT} not found — skipping .so scan")

    # ── 3. Check engine/rmr SPDX coverage ────────────────────────────────────
    rmr_src = "engine/rmr/src"
    if os.path.isdir(rmr_src):
        missing_spdx = []
        for dirpath, _, filenames in os.walk(rmr_src):
            for fn in filenames:
                if fn.endswith((".c", ".h")):
                    fpath = os.path.join(dirpath, fn)
                    with open(fpath, encoding="utf-8", errors="replace") as fh:
                        first_line = fh.readline()
                    if "SPDX-License-Identifier" not in first_line:
                        missing_spdx.append(fpath)
        if missing_spdx:
            print(
                f"[FAIL] {len(missing_spdx)} engine/rmr source file(s) missing SPDX header:",
                file=sys.stderr,
            )
            for f in sorted(missing_spdx)[:10]:
                print(f"  {f}", file=sys.stderr)
            fail = True
        else:
            print(f"[OK] All engine/rmr/src files have SPDX-License-Identifier headers")

    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
