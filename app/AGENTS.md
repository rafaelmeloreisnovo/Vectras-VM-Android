# AGENTS.md — App identity, authorship and About provenance

Scope: everything under `app/**`. This file refines the root `AGENTS.md`; it does not weaken any evidence, privacy, security or federation gate.

## Prime directive

The application is **RAFAELIA-authored only where independently evidenced**, and **explicitly attributed where inherited, modified, influenced or third-party**. Never convert uncertainty into local authorship.

**Origin is not erased by transformation.** A rename, refactor, translation, port, reformat, optimization, generated wrapper or UI redesign does not sever lineage.

For every shipped component or practical provenance unit, classify one of:

- `ORIGINAL_VERIFIED` — local authorship has an auditable chain.
- `UPSTREAM_EXACT` — unchanged upstream component/fragment with source/ref/license verified.
- `UPSTREAM_MODIFIED` — upstream component/fragment modified locally; preserve upstream attribution and identify local modifications.
- `THIRD_PARTY_VERIFIED` — third-party dependency/asset with source and rights/license verified.
- `INFLUENCED_BY_UPSTREAM` — independently written expression materially informed by upstream design/behavior; preserve conceptual lineage under project policy.
- `CLEAN_ROOM_VERIFIED` — independent implementation with documented clean-room boundary.
- `PRO_OFFICIAL_PENDING` — origin is ambiguous; presume upstream/third-party for claims, do not claim RAFAELIA authorship, and open a provenance gate.
- `QUARANTINED` — redistribution or use is blocked pending rights/security/provenance resolution.
- `TOKEN_VAZIO` — evidence is not yet sufficient to classify truthfully.

Legacy labels `UPSTREAM_VERIFIED` and `DERIVATIVE_MODIFIED`, if encountered in older receipts, map respectively to `UPSTREAM_EXACT` and `UPSTREAM_MODIFIED` unless the historical record says otherwise. Do not rewrite old receipts; append a normalization mapping.

`PRO_OFFICIAL_PENDING`, `TOKEN_VAZIO`, and unresolved material ancestry imply `claim_allowed=false` for whole-component local-authorship claims.

## Smallest practical lineage unit

Use the smallest maintainable unit that preserves truth:

`file -> region/span -> symbol/function/block -> packaged artifact`

Literal character-by-character storage is not mandatory. If a known upstream micro-fragment (even punctuation/token/line/layout fragment) was transformed, its containing span/block MUST retain upstream ancestry. If the exact micro-span cannot be reconstructed, retain conservative file/region lineage instead of inventing local authorship.

Canonical detailed rules: `docs/provenance/AGENTS.md`.

## About is the canonical human-facing disclosure surface

The About UI MUST expose, directly or through locally bundled detail pages, these groups:

1. **Current project** — current project name, maintainership and repository identity.
2. **Original work** — only modules whose `ORIGINAL_VERIFIED` evidence exists.
3. **Upstream/base** — project/component name, canonical source, pinned ref/version when available, license and transformation state.
4. **Local modifications** — concise description of material changes without implying ownership of upstream work.
5. **Third-party software/assets** — licenses/notices required for distributed components.
6. **Build & provenance** — app version plus source commit/build identifier; hashes where practical.
7. **Compliance status** — evidence status, not certification marketing. Use typed states such as `VERIFIED`, `PARTIAL`, `TOKEN_VAZIO`, `QUARANTINED`.

A component must not be called “official”, “original”, “ours”, “RAFAELIA-authored”, or equivalent unless the corresponding provenance record supports that exact scope.

If origin is ambiguous, the UI wording MUST be equivalent to:

> Origin pending verification; treated as upstream/third-party for attribution purposes. No local authorship claim is made.

Do not imply endorsement, sponsorship, affiliation or official status with an upstream project unless explicit evidence grants it.

## No hand-maintained attribution drift

The About screen SHOULD be generated from canonical repository records rather than duplicated hard-coded names/URLs. Canonical inputs include:

- `LICENSES_REGISTER.md`
- `THIRD_PARTY_NOTICES.md`
- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` when present
- transformation-lineage records governed by `docs/provenance/AGENTS.md`
- a versioned About/provenance manifest

Hard-coded contributor lists, upstream social links, license text or project identity in activities/layouts MUST be reconciled with those records. Divergence is a release-blocking gap when it can misstate origin or rights.

## File/span provenance

Before adding or materially modifying code, assets, binaries, firmware, VM images or generated files, record when applicable:

`path | span/symbol | origin_class | upstream_source | upstream_ref | source_digest | transformation | current_digest | license/SPDX | local_change | evidence_ref | about_required | claim_allowed`

Do not invent an SPDX identifier. Unknown license/origin is `TOKEN_VAZIO`, `PRO_OFFICIAL_PENDING` or `QUARANTINED`, not a guessed permissive license.

## UI and release gates

A release candidate fails the attribution gate when any shipped item:

- lacks required license/provenance;
- is unresolved `PRO_OFFICIAL_PENDING`, `TOKEN_VAZIO` or `QUARANTINED` without an explicit non-distribution exclusion;
- has About/NOTICE text inconsistent with the shipped artifact set;
- removes upstream attribution required by license or project policy;
- makes a stronger authorship/compliance claim than the evidence allows;
- has transformation lineage whose source/current identity cannot be reconciled with the release artifact.

Every fix emits a receipt containing source commit, affected paths/spans, old/new classification, transformation lineage, license evidence, UI impact, tests, remaining gaps, rollback/supersession ref and `claim_allowed`.

## Security/privacy boundary

About/provenance data must not leak secrets, signing material, private paths, personal guest data, tokens or unnecessary personal identifiers. Prefer public repository refs, component versions and cryptographic digests.

## Non-regression invariant

`attribution transparency ↑` MUST NOT require `security/privacy ↓`, and `local originality ↑` MUST NOT be achieved by deleting lawful upstream history, transformation ancestry or license notices.