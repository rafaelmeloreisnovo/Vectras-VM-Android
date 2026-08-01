import copy, hashlib, json, pathlib, tempfile, unittest
from tools.ci.validate_rafaelia_handoff_ingestion import inspect, Reject

BASE={"schema_version":"1.0.0","artifact_id":"ARTIFACT-0001","producer":"RafPolimata","source_commit":"0"*40,"artifact":{"path":"artifact.bin","format":"OTHER","size_bytes":3},"hashes":{"sha256":hashlib.sha256(b"abc").hexdigest()},"target":{"runtime":"Vectras-VM-Android","abi":"armeabi-v7a"},"dependencies":[],"limits":{"timeout_seconds":30,"memory_mb":64,"network_allowed":False},"rollback":{"strategy":"abort_only","previous_artifact_sha256":"TOKEN_VAZIO"},"claim_allowed":False,"epistemic_state":"EVIDENCIADO"}

class IngestionTests(unittest.TestCase):
 def run_case(self,d):
  pathlib.Path(d,"artifact.bin").write_bytes(b"abc")
  e=pathlib.Path(d,"envelope.json"); e.write_text(json.dumps(BASE),encoding="utf-8")
  return inspect(e,pathlib.Path(d))
 def test_accepts_verified_artifact_to_q2_only(self):
  with tempfile.TemporaryDirectory() as d:
   self.assertEqual(self.run_case(d)["stage"],"Q2_COMPATIBILITY")
 def test_rejects_wrong_runtime(self):
  with tempfile.TemporaryDirectory() as d:
   x=copy.deepcopy(BASE); x["target"]["runtime"]="Linux"
   pathlib.Path(d,"artifact.bin").write_bytes(b"abc"); e=pathlib.Path(d,"e.json"); e.write_text(json.dumps(x))
   with self.assertRaises(Reject): inspect(e,pathlib.Path(d))
 def test_rejects_network(self):
  with tempfile.TemporaryDirectory() as d:
   x=copy.deepcopy(BASE); x["limits"]["network_allowed"]=True
   pathlib.Path(d,"artifact.bin").write_bytes(b"abc"); e=pathlib.Path(d,"e.json"); e.write_text(json.dumps(x))
   with self.assertRaises(Reject): inspect(e,pathlib.Path(d))
 def test_rejects_promoted_claim(self):
  with tempfile.TemporaryDirectory() as d:
   x=copy.deepcopy(BASE); x["claim_allowed"]=True
   pathlib.Path(d,"artifact.bin").write_bytes(b"abc"); e=pathlib.Path(d,"e.json"); e.write_text(json.dumps(x))
   with self.assertRaises(Reject): inspect(e,pathlib.Path(d))
 def test_rejects_hash_mismatch(self):
  with tempfile.TemporaryDirectory() as d:
   pathlib.Path(d,"artifact.bin").write_bytes(b"xyz"); e=pathlib.Path(d,"e.json"); e.write_text(json.dumps(BASE))
   with self.assertRaises(Reject): inspect(e,pathlib.Path(d))

if __name__=="__main__": unittest.main()
