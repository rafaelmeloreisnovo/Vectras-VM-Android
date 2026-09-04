# Standards Applicability Matrix — Vectras / RAFAELIA

Registry date: **2026-09-04**  
Purpose: version/status/applicability index. This is **not a certification statement**.

## State vocabulary

`ACTIVE_FINAL | ACTIVE_WITH_AMENDMENT | DRAFT_MONITOR | SUPERSEDED | NOT_APPLICABLE | TOKEN_VAZIO`

Evidence vocabulary:

`POLICY_ONLY | IMPLEMENTED_UNVERIFIED | STATIC_VERIFIED | CI_VERIFIED | RUNTIME_VERIFIED | INDEPENDENTLY_ASSESSED | TOKEN_VAZIO`

## Baseline matrix

| Authority / reference | Lifecycle state | Project use | Current evidence state | Claim rule / next gate |
|---|---|---|---|---|
| NIST CSF 2.0 (2024) | ACTIVE_FINAL | Cybersecurity governance/risk outcome taxonomy | POLICY_ONLY | Map existing security/risk receipts to relevant outcomes; never call this “NIST certification”. |
| NIST SP 800-218 SSDF v1.1 (2022) | ACTIVE_FINAL | Secure SDLC / software supply-chain baseline | PARTIAL / TOKEN_VAZIO by practice | Build practice/task mapping to code, CI and evidence. |
| NIST SP 800-218 Rev.1, SSDF v1.2 (2025 draft) | DRAFT_MONITOR | Watch future SSDF changes | MONITOR_ONLY | Do not replace v1.1 final baseline until final publication/adoption review. |
| NIST SP 800-207 Zero Trust Architecture (2020) | ACTIVE_FINAL | Trust-boundary design for identities/resources/actions | PARTIAL | Inventory trust boundaries and bind policy + authorization + evidence. |
| RFC 2119 + RFC 8174 / BCP 14 | ACTIVE_FINAL | Normative keywords in project specifications | POLICY_ONLY | Declare BCP 14 semantics in normative specs; do not apply keywords accidentally. |
| IEEE 730-2026 Software Quality Assurance Processes | ACTIVE_FINAL | SQA process reference | POLICY_ONLY | Map SQA responsibilities, reviews, verification and records; no IEEE certification claim. |
| ISO/IEC 27001:2022 + applicable published amendment(s) | ACTIVE_WITH_AMENDMENT | ISMS/readiness framework where organizational scope is defined | TOKEN_VAZIO assessment scope | Define ISMS scope, applicability and evidence before any conformity wording. |
| ISO/IEC 27002:2022 | ACTIVE_FINAL | Security-control implementation guidance | PARTIAL | Map only controls relevant to actual risk/scope; retain implementation evidence. |
| ISO/IEC 27701:2025 | ACTIVE_FINAL | PIMS/privacy management when PII is in scope | TOKEN_VAZIO scope | Data/PII inventory and controller/processor context first. |
| ISO/IEC 38500:2024 | ACTIVE_FINAL | Governance of IT | POLICY_ONLY | Map decision rights, accountability, performance and conformance evidence. |
| ISO 8000 family | ACTIVE_FINAL BY PART | Data quality/governance | TOKEN_VAZIO part selection | Select specific applicable part(s); never claim generic family compliance. |
| OWASP SAMM | MAINTAINED FRAMEWORK | Secure-software maturity / improvement | TOKEN_VAZIO version pin | Pin model/version, baseline maturity with evidence, then define target increments. |
| Six Sigma / DMAIC | INTERNAL METHOD | Continuous improvement | ADOPTED_AS_METHOD | Use Define/Measure/Analyze/Improve/Control with baselines and receipts; no certification claim. |
| ICT governance (domain label) | NOT_A_SINGLE_STANDARD | Routing label only | N/A | Route concrete needs to standards such as ISO/IEC 38500, security/privacy/lifecycle references; do not cite “ICT” as one normative authority. |

## Official metadata sources used for this registry

- NIST CSF 2.0: https://www.nist.gov/publications/nist-cybersecurity-framework-csf-20
- NIST SSDF project: https://csrc.nist.gov/projects/ssdf
- NIST SP 800-218 final: https://csrc.nist.gov/pubs/sp/800/218/final
- NIST SP 800-207: https://www.nist.gov/publications/zero-trust-architecture
- RFC 2119: https://www.rfc-editor.org/info/rfc2119/
- RFC 8174: https://www.rfc-editor.org/info/rfc8174/
- IEEE 730-2026: https://standards.ieee.org/ieee/730/10854/
- ISO/IEC 27001: https://www.iso.org/standard/27001
- ISO/IEC 27002: https://www.iso.org/standard/75652.html
- ISO/IEC 27701: https://www.iso.org/standard/27701
- ISO/IEC 38500: https://www.iso.org/standard/81684.html
- ISO catalogue / ISO 8000 family: https://www.iso.org/
- OWASP SAMM: https://owaspsamm.org/

## Required record for a new reference

```yaml
reference_id: TOKEN_VAZIO
edition_or_version: TOKEN_VAZIO
lifecycle_state: TOKEN_VAZIO
official_source: TOKEN_VAZIO
scope: TOKEN_VAZIO
applicability_reason: TOKEN_VAZIO
local_controls: []
implementation_paths: []
tests: []
evidence_refs: []
gaps: []
claim_allowed: false
last_verified: TOKEN_VAZIO
```

## Change protocol

When a source is revised, withdrawn or superseded:

1. verify status against the authoritative publisher;
2. append a migration receipt;
3. compare changed requirements/intent without copying protected standards text;
4. mark affected local controls/evidence;
5. open `TOKEN_VAZIO` for unmapped deltas;
6. update this matrix only after the new status is verified;
7. keep prior evidence bound to the prior edition rather than silently relabeling it.

## Certification boundary

A standards mapping is an engineering control system. Certification/conformity assessment, when relevant, is a separate evidence layer with explicit assessor, scope, edition, date and certificate/report identity. Until then: `claim_allowed=false` for certification language.
