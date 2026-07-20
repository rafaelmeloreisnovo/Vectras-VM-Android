# VECTRA Artifact Promotion — Batch 001

Date: 2026-07-20  
Repository: `rafaelmeloreisnovo/Vectras-VM-Android`  
State: `IMPLEMENTED_AWAITS_REMOTE_EXECUTION`  
Claim: `claim_allowed=false`

## Purpose

Batch 001 promotes four previously dispersed capabilities into an auditable control plane without renaming source presence as runtime proof.

```text
source identity
→ marker verification
→ canonical adapter
→ deterministic report
→ later runtime evidence
```

No source is automatically moved or deleted in this batch.

## Promoted units

| ID | Source | Effective role after batch | Boundary |
|---|---|---|---|
| `ART_LOWFALA_MONOLITH` | `Incluir/compiladorlowFala.txt` | deterministic seed source behind an extractor/indexer | not yet an APK compiler runtime |
| `ART_TBROWSER_LOCAL` | `Incluir/vectras_bbs.c` | registered hosted local renderer | not ASM-only; no HTML/CSS/JS/network engine |
| `ART_NET_RAW_HTTP` | `conjunto_de_conceitos/src/net.c` | registered raw HTTP/DNS prototype | ARM runtime and TLS remain unproven/absent |
| `ART_DEX_ELF_AUDITOR` | `tools/audit_vectra_capabilities.py` | canonical DEX/ELF/ABI gate | requires a real APK receipt |

## LowFala transition

The monolith declares sixty mini-seeds. Batch 001 adds:

```text
tools/compilerlowfala/extract_seeds.py
```

The adapter parses heredoc functions named `seed_Sxx_Vy_*`, computes per-body SHA-256, rejects duplicate/malformed names and may extract bodies into a build directory without executing them.

Promotion state:

```text
ADAPTER_INTEGRATED_UNEXECUTED
```

Required next gates:

1. syntax classification for every extracted seed;
2. isolated compilation for C/ASM candidates;
3. bytecode and VM semantic equivalence tests;
4. canonical Android build integration;
5. APK generation;
6. DEX/ELF audit receipt;
7. installation and device runtime evidence.

## Browser and network boundary

`TBROWSER` navigates an in-memory `rmr://` page table. It is not a network browser. The raw network module provides HTTP/1.0 and minimal DNS separately.

The future browser stack must therefore be composed explicitly:

```text
renderer/parser
+ transport
+ TLS 1.2/1.3
+ certificate validation
+ content limits
+ tests
```

Until those layers exist and interoperate, the following remain blocked:

```text
fully ASM browser
TLS 1.2 certified
TLS 1.3 certified
HTML/CSS/DOM/JavaScript engine
remote web navigation proven on Android
```

## Reversibility and custody

The batch manifest seals each original source by Git blob SHA-1 and required markers. The verifier fails on:

- source replacement;
- missing marker;
- missing adapter;
- duplicate artifact ID;
- unsupported promotion state;
- automatic movement enabled;
- `claim_allowed=true` on an unproven state.

Canonical command:

```bash
python3 tools/promotion/verify_promotion_batch.py \
  --root . \
  --batch configs/vectra_artifact_promotion_batch_001.json \
  --output reports/vectra-artifact-promotion/batch-001.json
```

## State vector

```text
F_ok:
  sealed source identities
  deterministic promotion verifier
  LowFala seed adapter
  CI gate integration

F_gap:
  real APK receipt
  device execution
  TLS 1.2/1.3
  browser engine composition
  per-seed compilation and semantics

F_next:
  BATCH_002 = classify and test extracted seeds without executing unknown bodies by default
```
