# Standards Applicability Matrix — Vectras / RAFAELIA

Registry date: **2026-09-04**  
Purpose: version/status/applicability index. This is **not a certification statement**.

## State vocabulary

Lifecycle:

`ACTIVE_FINAL | ACTIVE_WITH_AMENDMENT | CONFIRMED_CURRENT | DRAFT_MONITOR | SUPERSEDED | WITHDRAWN | NOT_APPLICABLE | TOKEN_VAZIO`

Applicability:

`APPLICABLE_REQUIRED | APPLICABLE_TARGET | INFORMATIVE | NOT_APPLICABLE_WITH_REASON | SUPERSEDED_REFERENCE | TOKEN_VAZIO`

Evidence maturity:

`E0_REFERENCE | E1_POLICY | E2_STATIC | E3_CI | E4_RUNTIME | E5_INDEPENDENT_REVIEW | E6_EXTERNAL_ATTESTATION | TOKEN_VAZIO`

Invariant:

`REFERENCE != IMPLEMENTATION != VERIFICATION != CERTIFICATION`

## Baseline matrix

| Authority / reference | Current status verified 2026-09-04 | Project applicability | Current evidence | Claim boundary / next gate |
|---|---|---|---|---|
| **ISO/IEC/IEEE 12207:2026** — Software life cycle processes | ACTIVE_FINAL; Edition 2; published 2026-04; supersedes 12207:2017 | APPLICABLE_TARGET — canonical software life-cycle/process vocabulary | E1_POLICY | Map repository lifecycle, maintenance, operation, release and retirement processes. Do not relabel old evidence as 2026 conformance without delta review. |
| **ISO/IEC/IEEE 15288:2023** — System life cycle processes | ACTIVE_FINAL; Edition 2; published 2023-05; supersedes 15288:2015 | INFORMATIVE/APPLICABLE_TARGET for system-of-systems, VM↔Termux↔QEMU boundaries | E1_POLICY | Use where system-level lifecycle differs from software-only scope. |
| **IEEE 730-2026** — Software Quality Assurance Processes | ACTIVE_FINAL; published 2026-08-21; supersedes IEEE 730-2014 | APPLICABLE_TARGET — SQA process reference | E1_POLICY | Map SQA responsibilities, reviews, verification, independence and records. No IEEE certification claim. |
| **ISO/IEC 25010:2023** — Product quality model (SQuaRE) | ACTIVE_FINAL; Edition 2; published 2023-11; supersedes 25010:2011 | APPLICABLE_TARGET — ICT/software quality characteristics | E0_REFERENCE | Build measurable quality profile for actual product characteristics; avoid generic quality score without operational metrics. |
| **NIST CSF 2.0 / CSWP 29 (2024)** | ACTIVE_FINAL | APPLICABLE_TARGET — cybersecurity governance/risk outcomes | E1_POLICY | Crosswalk security/risk receipts to CSF outcomes. NIST does not equal certification. |
| **NIST SP 800-218 SSDF v1.1 (2022)** | ACTIVE_FINAL | APPLICABLE_TARGET — secure SDLC/software supply chain | PARTIAL; E1-E3 by practice; otherwise TOKEN_VAZIO | Build practice/task mapping to code, CI, build and release evidence. |
| **NIST SSDF v1.2 / SP 800-218 revision drafts** | DRAFT_MONITOR | INFORMATIVE monitor-only | E0_REFERENCE | Do not replace final v1.1 baseline until final publication and adoption review. |
| **NIST SP 800-207 Zero Trust Architecture (2020)** | ACTIVE_FINAL | APPLICABLE_TARGET — trust boundaries, identity, authorization, resource protection | E1_POLICY/PARTIAL | Inventory app↔Termux, QEMU, artifact, signing/update and remote-service trust boundaries. |
| **RFC 2119 + RFC 8174 / BCP 14** | ACTIVE_FINAL BCP | APPLICABLE_REQUIRED for project specifications that declare normative keywords | E1_POLICY | Uppercase MUST/SHOULD/MAY semantics only where the document declares BCP 14 usage. |
| **ISO/IEC 27001:2022 + Amd 1:2024** | ACTIVE_WITH_AMENDMENT | APPLICABLE_TARGET — ISMS/readiness only after organizational scope is defined | TOKEN_VAZIO scope / E0_REFERENCE | Define ISMS scope, interested parties, risk method, SoA/evidence before conformity wording. |
| **ISO/IEC 27002:2022** | ACTIVE_FINAL | APPLICABLE_TARGET — security control guidance | PARTIAL E1-E3 | Map relevant controls to actual risks, owners, implementation and evidence. |
| **ISO/IEC 27701:2025** — PIMS | ACTIVE_FINAL; Edition 2 | APPLICABLE_TARGET when PII/personal-data processing is in scope | TOKEN_VAZIO scope | Data/PII inventory, role/context and privacy obligations first. No privacy-compliance marketing without evidence. |
| **ISO/IEC 38500:2024** — Governance of IT | ACTIVE_FINAL; Edition 3 | APPLICABLE_TARGET — governance/decision rights/accountability | E1_POLICY | Map authority, accountability, performance, conformance and decision receipts. |
| **ISO 8000-61:2016** — Data quality management process reference model | CONFIRMED_CURRENT; reviewed/confirmed 2022 | APPLICABLE_TARGET for provenance/registry/data-quality processes | E1_POLICY | Define dataset owner, purpose, schema, quality dimensions, lineage, validation and correction path. |
| **ISO 13053-1:2011** — Six Sigma DMAIC | CONFIRMED_CURRENT | INFORMATIVE/APPLICABLE_TARGET for measurable process improvement | E1_POLICY | Use DMAIC only with defined metrics, samples, confounders and falsifiers; no invented sigma level. |
| **ISO 13053-2:2011** — Six Sigma tools and techniques | CONFIRMED_CURRENT; reviewed/confirmed 2022 | INFORMATIVE | E0_REFERENCE | Select tools only when statistically and operationally appropriate. |
| **OWASP SAMM** | MAINTAINED FRAMEWORK | APPLICABLE_TARGET — software assurance maturity | TOKEN_VAZIO version pin | Pin version before scoring; evidence each maturity claim. |
| **ICT governance** (domain label) | NOT_A_SINGLE_STANDARD | ROUTING_ONLY | N/A | Route to concrete standards (38500, 270xx, 12207/15288, 25010 etc.); never cite “ICT” as one normative authority. |

## Authoritative metadata sources

- ISO/IEC/IEEE 12207:2026: https://www.iso.org/standard/90219.html
- ISO/IEC/IEEE 15288:2023: https://www.iso.org/standard/81702.html
- IEEE 730-2026: https://standards.ieee.org/ieee/730/10854/
- ISO/IEC 25010:2023: https://www.iso.org/standard/78176.html
- NIST CSF 2.0: https://www.nist.gov/publications/nist-cybersecurity-framework-csf-20
- NIST SP 800-218 final: https://csrc.nist.gov/pubs/sp/800/218/final
- NIST SP 800-207: https://csrc.nist.gov/pubs/sp/800/207/final
- RFC 2119: https://www.rfc-editor.org/info/rfc2119/
- RFC 8174: https://www.rfc-editor.org/info/rfc8174/
- ISO/IEC 27001:2022: https://www.iso.org/standard/27001
- ISO/IEC 27002:2022: https://www.iso.org/standard/75652.html
- ISO/IEC 27701:2025: https://www.iso.org/standard/27701
- ISO/IEC 38500:2024: https://www.iso.org/standard/81684.html
- ISO 8000-61:2016: https://www.iso.org/standard/63086.html
- ISO 13053-1:2011: https://www.iso.org/standard/52901.html
- ISO 13053-2:2011: https://www.iso.org/standard/52902.html
- OWASP SAMM: https://owaspsamm.org/

## Required record for a new reference

```yaml
reference_id: TOKEN_VAZIO
edition_or_version: TOKEN_VAZIO
lifecycle_state: TOKEN_VAZIO
publication_date: TOKEN_VAZIO
supersedes: []
official_source: TOKEN_VAZIO
verified_at: TOKEN_VAZIO
applicability: TOKEN_VAZIO
applicability_reason: TOKEN_VAZIO
local_controls: []
implementation_paths: []
tests: []
evidence_level: E0_REFERENCE
evidence_refs: []
gaps: []
claim_allowed: false
certified: false
owner: TOKEN_VAZIO
```

## Change protocol

When a source is revised, withdrawn or superseded:

1. verify status against the authoritative publisher;
2. retain the prior row/evidence as historical lineage;
3. append a migration receipt;
4. compare changed requirement intent without copying protected/paywalled standards text;
5. mark affected local controls/evidence;
6. open `TOKEN_VAZIO` for unmapped deltas;
7. add the replacement baseline only after status is verified;
8. never silently upgrade prior evidence to the new edition.

## Certification boundary

A standards mapping is an engineering control system. Certification/conformity assessment, when relevant, is a separate evidence layer with explicit assessor, scope, edition, date and certificate/report identity. Until that layer exists, `certified=false` and certification language is not allowed.