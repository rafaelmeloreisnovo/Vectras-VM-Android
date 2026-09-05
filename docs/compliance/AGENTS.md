# AGENTS.md — Standards, assurance and continuous compliance

Scope: `docs/compliance/**`. This is an **applicability/evidence layer**, not a declaration of certification.

## Control objective

For each standard/framework/practice, maintain:

`reference → applicability → control intent → implementation → test → evidence → gap → owner/next gate → claim state`

No control is “done” because a document mentions it.

## Baseline authorities monitored

Use `STANDARDS_APPLICABILITY_MATRIX.md` as the versioned registry. Baseline as of 2026-09-04:

- ISO/IEC/IEEE 12207:2026 — software life-cycle process baseline.
- ISO/IEC/IEEE 15288:2023 — system life-cycle process baseline where system scope applies.
- IEEE 730-2026 — software quality assurance process reference.
- ISO/IEC 25010:2023 — ICT/software product quality model.
- NIST Cybersecurity Framework (CSF) 2.0 — cybersecurity risk/governance outcomes.
- NIST SP 800-218 SSDF v1.1 — current final secure software development baseline. Later revision drafts are monitor-only until finalized/adopted.
- NIST SP 800-207 — Zero Trust Architecture principles; apply proportionally to identities/resources/trust boundaries relevant to this project.
- RFC 2119 + RFC 8174 (BCP 14) — normative requirement-keyword semantics.
- ISO/IEC 27001:2022 (including published amendments where applicable) — ISMS requirements/readiness mapping.
- ISO/IEC 27002:2022 — information-security control guidance.
- ISO/IEC 27701:2025 — privacy information management system requirements/guidance where PII processing is in scope.
- ISO/IEC 38500:2024 — governance of IT.
- ISO 8000-61:2016 — data-quality management process reference.
- ISO 13053-1/-2:2011 — Six Sigma DMAIC methodology and supporting tools where measurable process improvement is appropriate.
- OWASP SAMM — measurable software-assurance maturity improvement.

Additional frameworks MAY be added when a concrete project risk/control needs them (for example SBOM/software-supply-chain, accessibility, Android platform security, incident response, business continuity or AI governance). Add only with an applicability reason.

## Standards lifecycle rule

Every reference has one lifecycle state:

`ACTIVE_FINAL | ACTIVE_WITH_AMENDMENT | CONFIRMED_CURRENT | DRAFT_MONITOR | SUPERSEDED | WITHDRAWN | NOT_APPLICABLE | TOKEN_VAZIO`

A draft MUST NOT silently replace a final baseline. When a standard changes, create a migration receipt with changed applicability, new gaps, retired mappings and evidence impact.

## Certification honesty

Without independent assessment/certificate for a defined scope, **brand-based certification/conformity claims for ISO, NIST, IEEE or equivalent authorities are prohibited**.

Permitted evidence-bounded wording includes:

- “control mapped to …”
- “implementation evidence exists for …”
- “readiness assessment against …”
- “partially aligned; gaps listed”

Framework/reference mapping is not itself external attestation. Do not invent a certification program.

## Compliance gate model

For each applicable control, track:

1. `APPLICABILITY` — why this control matters to the actual Vectras/RAFAELIA scope.
2. `POLICY` — local requirement and accountable owner/boundary.
3. `IMPLEMENTATION` — exact code/config/process paths.
4. `VERIFICATION` — deterministic test/check/review method and falsifier.
5. `EVIDENCE` — immutable or content-addressed receipt/artifact reference.
6. `RUNTIME` — physical/runtime evidence where the claim requires it.
7. `REGRESSION` — how future changes are prevented from silently violating it.
8. `CLAIM` — allowed wording and remaining uncertainty.

A missing stage is not zero; it is `TOKEN_VAZIO` or another typed gap.

Evidence maturity may be expressed as:

`E0_REFERENCE | E1_POLICY | E2_STATIC | E3_CI | E4_RUNTIME | E5_INDEPENDENT_REVIEW | E6_EXTERNAL_ATTESTATION | TOKEN_VAZIO`

Never promote `E0..E5` to `E6` without actual external-attestation evidence.

## Zero Trust translation for this repository

Apply Zero Trust as “no implicit trust from location/ownership alone”, not as a marketing label. For each trust boundary:

`subject identity → resource identity → requested action → policy → authorization → minimum privilege → integrity/evidence → expiry/revalidation`

Priority boundaries include app↔Termux IPC, QEMU process execution, VM/image mutation, downloaded artifacts, update/build inputs, signing/release assets and any remote service.

Unknown identity, unverified binary provenance or ambiguous authorization blocks privileged mutation.

## Software supply-chain and provenance minimum

For release-bound artifacts:

- source commit/ref is bound;
- build inputs/toolchain versions are recorded where practical;
- distributed third-party components have source/license/provenance;
- binary/asset digests are recorded;
- SBOM SHOULD be generated in a standard machine-readable format when the build can support it;
- release evidence distinguishes source/build/runtime/guest-boot claims;
- unsigned/signed artifacts are not conflated;
- secrets and signing keys never enter public receipts.

Use SPDX identifiers only when verified. SPDX/SBOM metadata supplements, not replaces, license texts/notices required by the actual license.

## Data governance / ISO 8000 translation

For each material data set or registry track:

`owner | purpose | schema/version | source | quality dimensions | validation | lineage | retention | sensitivity | access | correction path | evidence`

Data quality is contextual. Do not assign generic family-level conformity status without selecting the applicable part and evidence.

## Privacy

Minimize collection and retention. Classify whether PII/personal data is processed before logging, telemetry, support export or receipts are enabled. Prefer pseudonymous transaction IDs and hashes over raw identifiers. Record lawful/policy basis separately from technical necessity when legal regimes apply.

## SQA / continuous improvement

Use IEEE 730-2026 as a software-quality-assurance process reference and OWASP SAMM/DMAIC as improvement scaffolding where applicable. Every improvement cycle records:

`baseline → defect/risk → root-cause hypothesis → change → verification → regression → control → receipt`

Metrics without an operational definition are `TOKEN_VAZIO`.

For DMAIC:

`DEFINE → MEASURE → ANALYZE → IMPROVE → CONTROL`

Do not manufacture sigma levels from insufficient or uncontrolled samples.

## RFC applicability

Do not attempt global “RFC compliance”. Maintain an RFC registry only for protocols/formats actually implemented. Each entry must state protocol surface, RFC status, required/optional behavior implemented, test vectors/interoperability evidence and deviations.

When a specification declares BCP 14 usage, uppercase normative keywords are interpreted according to RFC 2119 as updated by RFC 8174. Ordinary lowercase prose is not automatically normative.

## Copyright of standards

Do not copy substantial proprietary/paywalled standards text into this repository. Store identifiers, public metadata, local control mappings and independently written implementation requirements. If a licensed copy is needed for assessment, keep it outside public redistribution and cite it by identifier/edition.

## Release stop conditions

Block compliance promotion/release claims when:

- origin/license of a shipped item is unresolved;
- a critical security/privacy control is `TOKEN_VAZIO`;
- About/NOTICE/provenance disagree with the shipped artifact;
- evidence is stale relative to the source/build being released;
- runtime behavior is claimed from static/CI-only evidence;
- a superseded/draft standard is represented as the active final baseline;
- contradiction/anomaly affecting the claim has no bounded disposition.

## Improvement invariant

`new control → measurable evidence → lower uncertainty/risk`, otherwise the change is documentation growth, not demonstrated assurance improvement.
