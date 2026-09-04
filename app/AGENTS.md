# AGENTS.md — App identity, authorship and About provenance

Scope: everything under `app/**`. This file refines the root `AGENTS.md`; it does not weaken any evidence, privacy, security or federation gate.

## Prime directive

The application is **RAFAELIA-authored where independently evidenced**, and **explicitly attributed where inherited, derived or third-party**. Never convert uncertainty into local authorship.

For every shipped component, classify one of:

- `ORIGINAL_VERIFIED` — local authorship has an auditable chain.
- `UPSTREAM_VERIFIED` — unchanged upstream component with source/ref/license verified.
- `DERIVATIVE_MODIFIED` — upstream component modified locally; preserve upstream attribution and identify local modifications.
- `THIRD_PARTY_VERIFIED` — third-party dependency/asset with source and compatible license verified.
- `PRO_OFFICIAL_PENDING` — origin is ambiguous; presume upstream/third-party for claims, do not claim RAFAELIA authorship, and open a provenance gap.
- `QUARANTINED` — redistribution or use is blocked pending rights/security/provenance resolution.
- `TOKEN_VAZIO` — evidence is not yet sufficient to classify truthfully.

`PRO_OFFICIAL_PENDING` and `TOKEN_VAZIO` imply `claim_allowed=false` for authorship/origin claims.

## About is the canonical human-facing disclosure surface

The About UI MUST expose, directly or through locally bundled detail pages, these groups:

1. **Current project** — current project name, maintainership and repository identity.
2. **Original work** — only modules whose `ORIGINAL_VERIFIED` evidence exists.
3. **Upstream/base** — project/component name, canonical source, pinned ref/version when available, license and whether modified.
4. **Local modifications** — concise description of material changes without implying ownership of upstream work.
5. **Third-party software/assets** — licenses/notices required for distributed components.
6. **Build & provenance** — app version plus source commit/build identifier; hashes where practical.
7. **Compliance status** — evidence status, not certification marketing. Use `VERIFIED`, `PARTIAL`, `TOKEN_VAZIO`, `QUARANTINED`, or equivalent typed states.

A component must not be called “official”, “original”, “ours”, “RAFAELIA-authored”, or equivalent unless the corresponding provenance record supports that claim.

If origin is ambiguous, the UI wording MUST be equivalent to:

> Origin pending verification; treated as upstream/third-party for attribution purposes. No local authorship claim is made.

Do not imply endorsement, sponsorship, affiliation or official status with an upstream project unless explicit evidence grants it.

## No hand-maintained attribution drift

The About screen SHOULD be generated from canonical repository records rather than duplicated hard-coded names/URLs. Canonical inputs are:

- `LICENSES_REGISTER.md`
- `THIRD_PARTY_NOTICES.md`
- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` when present
- a versioned About/provenance manifest introduced by this governance layer

Hard-coded contributor lists, upstream social links, license text or project identity in activities/layouts MUST be reconciled with those records. Divergence is a release-blocking gap when it can misstate origin or rights.

## File-level provenance

Before adding or materially modifying code, assets, binaries, firmware, VM images or generated files, record when applicable:

`path | origin_class | upstream_source | upstream_ref | license/SPDX | local_author | local_change | digest | evidence_ref | about_required | claim_allowed`

Do not invent an SPDX identifier. Unknown license/origin is `TOKEN_VAZIO` or `QUARANTINED`, not a guessed permissive license.

## UI and release gates

A release candidate fails the attribution gate when any shipped item:

- lacks required license/provenance;
- is `PRO_OFFICIAL_PENDING`, `TOKEN_VAZIO` or `QUARANTINED` without an explicit non-distribution exclusion;
- has About/NOTICE text inconsistent with the shipped artifact set;
- removes upstream attribution required by license;
- makes a stronger authorship/compliance claim than the evidence allows.

Every fix emits a receipt containing source commit, affected paths, old/new classification, license evidence, UI impact, tests, remaining gaps, rollback ref and `claim_allowed`.

## Security/privacy boundary

About/provenance data must not leak secrets, signing material, private paths, personal guest data, tokens or unnecessary personal identifiers. Prefer public repository refs, component versions and cryptographic digests.

## Non-regression invariant

`attribution transparency ↑` MUST NOT require `security/privacy ↓`, and `local originality ↑` MUST NOT be achieved by deleting lawful upstream history or license notices.
