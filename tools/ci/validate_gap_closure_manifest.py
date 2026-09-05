#!/usr/bin/env python3
import json, sys
from pathlib import Path

ALLOWED={"OBSERVED","WIRED","BUILD_PROVEN","RUNTIME_PROVEN","DEVICE_PROVEN","REPRODUCED","TOKEN_VAZIO","BLOCKED","FALSIFIED","CLOSED"}
SENSITIVE={"physical_runtime","scientific_proof","reproduction","prior_art","coverage"}

def main(path):
    d=json.loads(Path(path).read_text(encoding="utf-8"))
    assert d.get("claim_allowed") is False, "claim_allowed must remain false"
    seen=set()
    for g in d.get("gaps",[]):
        gid=g.get("id"); state=g.get("state"); cls=g.get("class")
        assert gid and gid not in seen, f"invalid/duplicate gap id: {gid}"
        seen.add(gid)
        assert state in ALLOWED, f"{gid}: invalid state {state}"
        if state=="TOKEN_VAZIO":
            assert g.get("next_gate") or g.get("required_evidence"), f"{gid}: TOKEN_VAZIO requires next gate/evidence"
        if cls in SENSITIVE and state=="CLOSED":
            assert g.get("producer") and g.get("evidence"), f"{gid}: sensitive closure requires producer+evidence"
    print(f"PASS {path}: {len(seen)} gaps")

if __name__=="__main__":
    try: main(sys.argv[1])
    except (AssertionError, OSError, json.JSONDecodeError, IndexError) as e:
        print(f"FAIL: {e}", file=sys.stderr); raise SystemExit(1)
