#!/usr/bin/env python3
import argparse,json,pathlib,sys
P=argparse.ArgumentParser();P.add_argument("--contract",required=True);P.add_argument("--manifest");a=P.parse_args()
c=json.loads(pathlib.Path(a.contract).read_text());r=json.loads(pathlib.Path(a.manifest).read_text()) if a.manifest else c["fixture"];e=[]
for k in c["required_fields"]:
 if k not in r:e.append("missing:"+k)
if r.get("abi") not in c["allowed_abis"]:e.append("bad_abi")
if r.get("boot_state") not in c["allowed_boot_states"]:e.append("bad_boot_state")
if r.get("rollback_state") not in c["allowed_rollback_states"]:e.append("bad_rollback_state")
if r.get("bind_policy") not in ["LOOPBACK_ONLY","NONE"]:e.append("unsafe_bind")
if r.get("boot_state")=="BOOT_PASS_LIMITED" and not r.get("boot_receipt_sha256"):e.append("boot_receipt_missing")
print(json.dumps({"state":"PASS_LIMITED" if not e else "FAIL","errors":e,"boot_state":r.get("boot_state"),"claim_allowed":False},indent=2))
sys.exit(1 if e else 0)
