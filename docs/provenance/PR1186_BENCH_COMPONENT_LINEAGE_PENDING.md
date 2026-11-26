# PR #1186 — benchmark / ABI component provenance (pending gate)

Status: `PRO_OFFICIAL_PENDING` / `claim_allowed=false`. This is a conservative **file/span register**, not a certification of original authorship, licensing rights, Android execution, or redistribution. It records the merged PR source exactly and retains gaps instead of making inferred claims.

Source: [merged PR #1186](https://github.com/rafaelmeloreisnovo/Vectras-VM-Android/pull/1186), source head `5461e7411c3a446dbc79f0f686fa0de0f129aaa5`; merge `b92fd78bc508333d9a3ea0f5f2e82ba9704cac84`.
Parent review finding: [Codex P1](https://github.com/rafaelmeloreisnovo/Vectras-VM-Android/pull/1186#discussion_r4499538712).
Scope: locally merged Vectras consumer; no claims about independent Termux, QEMU or physical ARMv7 device providers.

## Required path/span records

| lineage_id | current_path | current_span / symbol | current_git_blob (at PR head) | origin_class |
| --- | --- | --- | --- | --- |
| PROV-PR1186-C-001 | `app/src/main/cpp/core/raf_bench_probe.c` | `raf_bench_xorshift32` | `88f2ceb0855c8efa9f33c463a69be5951b2cee06` | `PRO_OFFICIAL_PENDING` |
| PROV-PR1186-H-002 | `app/src/main/cpp/core/raf_bench_probe.h` | `raf_bench_xorshift32` declaration + include guard | `cec93f99121c53130582f360d430d119405e5401` | `PRO_OFFICIAL_PENDING` |
| PROV-PR1186-JNI-003 | `app/src/main/cpp/raf_bench_jni.c` | `Java_com_vectras_vm_benchmark_FreestandingMicrobench_nativeFold` | `6cf3ea2151f5d90527bca78b5772a56330a28e6b` | `PRO_OFFICIAL_PENDING` |
| PROV-PR1186-JAVA-004 | `app/src/main/java/com/vectras/vm/benchmark/FreestandingMicrobench.java` | `FreestandingMicrobench` | `9ad1be4629b0b80b2974131c33da8ca2787a6494` | `PRO_OFFICIAL_PENDING` |
| PROV-PR1186-JAVA-005 | `app/src/main/java/com/vectras/vm/runtime/RuntimeAbiSnapshot.java` | `RuntimeAbiSnapshot` | `7f8fbe82bd776609801512c66a68a771e6561cf4` | `PRO_OFFICIAL_PENDING` |

Each row inherits these fields, deliberately conservatively:

- `canonical_source=TOKEN_VAZIO`; `upstream_source=TOKEN_VAZIO`; `upstream_ref=TOKEN_VAZIO`; `source_path/source_span/source_digest=TOKEN_VAZIO` until lineage verification.
- `license_spdx=GPL-2.0-only (source header declared; entitlement and notice verification TOKEN_VAZIO)`. The header is **not** proof of original copyright ownership or relicensing authority.
- `transform_type=TOKEN_VAZIO`; `transform_summary=bounded ALU kernel, JNI adapter and Android observation/benchmark surfaces introduced in PR #1186`; `local_change=files added by the PR (authorship not independently verified)`.
- `evidence_refs=PR #1186 commit and Git blob values in table, app/AGENTS.md, docs/provenance/AGENTS.md, review finding P1`.
- `about_required=true` (disclose as **origin pending**, not locally original); `notice_required=TOKEN_VAZIO` pending license audit; `distribution_allowed=false` under the current repository release gate; `claim_allowed=false`.
- `current_digest=Git blob OID in table` (not SHA-256); exact built-APK hashes and executable/asset binding `TOKEN_VAZIO`.
- `parent_lineage_ids=[]` only because parent identities are unknown; this does **not** assert no upstream ancestry.

## About/NOTICE and release safety

For the new benchmark components, the compliant temporary disclosure is:

> Origin pending verification; treated as upstream/third-party for attribution purposes. No local authorship claim is made.

`THIRD_PARTY_NOTICES.md` may point to this pending register, but a document pointer does **not** prove that the shipped About UI displays it. The existing app About/NOTICE, file/span ancestry, and license rights must be reconciled against the exact APK before promotion.

**P1 outcome:** missing file/span records mitigated at source, but independent ancestry, rights and distributed About disclosure remain `TOKEN_VAZIO` and **the P1 release gate remains BLOCKED**. Do not close the original review thread without evidence meeting those remaining criteria.

## Falsifier / rollback / successor

- Verify each Git blob against the fixed PR head; any mismatch requires a successor record.
- Verify About/NOTICE against a built artifact; static documentation alone is insufficient.
- Reproduce the C/JNI host checks separately from Android JNI load and physical Motorola execution.
- Rollback: revert this follow-up PR commit on its own branch if tests fail; do not rewrite the merged PR or erase earlier lineage.
- Next: origin audit + rights/NOTICE reconciliation + build/device receipts; no release or guest boot claim.

`event_id=MU-VECTRAS-PR1186-REVIEW-P1-PENDING-V1`; `parent=PR1186/review-5809405296`; `claim_allowed=false`; `append_only=true`.
