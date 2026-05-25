#!/usr/bin/env python3
import csv
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[2]
register = root / "resources/compliance/ASSET_PROVENANCE_REGISTER.csv"

if not register.exists():
    print(f"ERROR: missing register: {register}")
    sys.exit(2)

fail = False
with register.open(newline='', encoding='utf-8') as f:
    rows = list(csv.DictReader(f))

required = ["asset_path", "license_spdx", "permission_proof", "status"]
for i, row in enumerate(rows, start=2):
    for k in required:
        if not row.get(k):
            print(f"ERROR line {i}: missing {k}")
            fail = True
    if row.get("license_spdx", "").upper() == "UNKNOWN":
        print(f"ERROR line {i}: UNKNOWN license_spdx for {row.get('asset_path')}")
        fail = True
    if row.get("status", "").lower() == "quarantine":
        print(f"ERROR line {i}: quarantined asset {row.get('asset_path')}")
        fail = True
    if row.get("permission_proof", "").lower() == "missing" and row.get("source_url", "") not in ("internal", ""):
        print(f"ERROR line {i}: missing permission proof for {row.get('asset_path')}")
        fail = True

if fail:
    sys.exit(1)

print("OK: asset provenance register passed")
