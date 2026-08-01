#!/usr/bin/env python3
"""Vectras-side quarantine validator for RAFAELIA handoff envelope v1."""
from __future__ import annotations
import hashlib, json, pathlib, re, sys

HEX40=re.compile(r"^[0-9a-f]{40}$"); HEX64=re.compile(r"^[0-9a-f]{64}$")
ALLOWED_FORMATS={"ELF32","ELF64","APK","DEX","ZIP","JSON","OTHER"}
ALLOWED_ABIS={"armeabi-v7a","arm64-v8a","x86","x86_64","any"}

class Reject(Exception): pass

def need(c,m):
    if not c: raise Reject(m)

def inspect(envelope:pathlib.Path, root:pathlib.Path) -> dict:
    d=json.loads(envelope.read_text(encoding="utf-8"))
    required={"schema_version","artifact_id","producer","source_commit","artifact","hashes","target","dependencies","limits","rollback","claim_allowed","epistemic_state"}
    need(set(d)==required,"envelope fields mismatch")
    need(d["schema_version"]=="1.0.0","unsupported schema")
    need(d["claim_allowed"] is False,"claim_allowed must remain false")
    need(bool(HEX40.fullmatch(d["source_commit"])),"invalid source commit")
    art=d["artifact"]; target=d["target"]; limits=d["limits"]
    need(art.get("format") in ALLOWED_FORMATS,"unsupported format")
    need(target.get("runtime")=="Vectras-VM-Android","wrong runtime authority")
    need(target.get("abi") in ALLOWED_ABIS,"unsupported ABI")
    need(limits.get("network_allowed") is False,"network forbidden in quarantine")
    need(1<=limits.get("timeout_seconds",0)<=3600,"invalid timeout")
    need(16<=limits.get("memory_mb",0)<=65536,"invalid memory")
    sha=d.get("hashes",{}).get("sha256","")
    need(bool(HEX64.fullmatch(sha)),"invalid sha256")
    p=(root/art["path"]).resolve(); rr=root.resolve()
    need(str(p).startswith(str(rr)),"artifact escapes quarantine")
    need(p.is_file(),"artifact missing")
    need(p.stat().st_size==art.get("size_bytes"),"size mismatch")
    actual=hashlib.sha256(p.read_bytes()).hexdigest()
    need(actual==sha,"sha256 mismatch")
    for dep in d["dependencies"]:
        need(set(dep)=={"name","version","provenance"},"invalid dependency envelope")
        need(all(isinstance(dep[k],str) and dep[k] for k in dep),"empty dependency field")
    return {"ok":True,"stage":"Q2_COMPATIBILITY","artifact_id":d["artifact_id"],"sha256":actual,"abi":target["abi"],"claim_allowed":False,"next":"Q3_LAUNCH_REQUIRES_EXPLICIT_RUNTIME_GATE"}

def main()->int:
    if len(sys.argv)!=3:
        print("usage: validate_rafaelia_handoff_ingestion.py ENVELOPE.json QUARANTINE_ROOT",file=sys.stderr); return 2
    try: result=inspect(pathlib.Path(sys.argv[1]),pathlib.Path(sys.argv[2]))
    except (OSError,json.JSONDecodeError,Reject) as e:
        print(json.dumps({"ok":False,"stage":"Q0_QUARANTINE","state":"TOKEN_VAZIO","error":str(e)},ensure_ascii=False)); return 1
    print(json.dumps(result,ensure_ascii=False)); return 0

if __name__=="__main__": raise SystemExit(main())
