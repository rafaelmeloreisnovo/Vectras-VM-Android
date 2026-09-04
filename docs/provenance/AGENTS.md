# AGENTS.md — Provenance / Authorship / Transformation Lineage

Scope: `docs/provenance/**` and any repository change that can affect authorship, attribution, license, origin, NOTICE/About disclosure, or derivative status.

## Prime directive

**Origin is never erased by transformation.**

If any shipped expression, code fragment, structure, asset, text, binary, configuration, algorithm implementation, UI element, or other artifact is inherited from upstream/third-party material, that ancestry remains part of the record after modification.

Project policy is intentionally stricter than the minimum legal threshold: when lineage is uncertain, classify **pro-upstream / pro-official**, not pro-local-authorship.

`UNKNOWN_ORIGIN => PRO_OFFICIAL_PENDING | TOKEN_VAZIO | claim_allowed=false`

## Provenance classes

Use exactly one primary class per tracked unit, plus parent lineage when needed:

- `ORIGINAL_VERIFIED` — independently authored locally with auditable evidence.
- `UPSTREAM_EXACT` — unchanged upstream/third-party material.
- `UPSTREAM_MODIFIED` — inherited material with local modifications.
- `THIRD_PARTY_VERIFIED` — third-party material outside the main upstream with verified rights/license.
- `INFLUENCED_BY_UPSTREAM` — independently written expression, but design/behavior was materially informed by upstream; preserve conceptual credit and sources according to project policy.
- `CLEAN_ROOM_VERIFIED` — independently implemented under a documented clean-room boundary.
- `PRO_OFFICIAL_PENDING` — ambiguous ancestry; presume non-local for attribution until resolved.
- `TOKEN_VAZIO` — insufficient evidence to classify.
- `QUARANTINED` — origin/license/rights risk blocks release/distribution.

## Smallest practical lineage unit

Track origin at the smallest unit that can be maintained reliably without destroying operability:

`repository -> file -> region/span -> symbol/function/block -> artifact`

A literal character-by-character database is not required. When a transformed comma, token, line, expression, layout fragment, or other micro-fragment came from upstream and that fact is known/material, its containing **span/block lineage** MUST preserve that ancestry.

If exact source span is unknown but the file is historically inherited, classify conservatively at file/region level; do not promote the unknown portion to local authorship.

## Mandatory lineage record

Every material inherited/transformed unit SHOULD be representable as:

```yaml
lineage_id: PROV-...
current_path: path/to/file
current_ref: branch-or-commit
current_span: lines-or-symbol-or-block
current_digest: sha256-or-git-blob
origin_class: UPSTREAM_MODIFIED
canonical_source: upstream-url-or-repo
upstream_ref: tag/commit/version
source_path: original/path
source_span: lines/symbol/block/TOKEN_VAZIO
source_digest: sha256-or-git-blob/TOKEN_VAZIO
license_spdx: verified-id-or-TOKEN_VAZIO
copyright_notice: reference-or-TOKEN_VAZIO
transformation_id: XFORM-...
transform_type: copied|modified|translated|ported|refactored|reformatted|generated_from|influenced_by|clean_room
transform_summary: concise factual description
local_contributors: []
parent_lineage_ids: []
evidence_refs: []
about_required: true|false|TOKEN_VAZIO
notice_required: true|false|TOKEN_VAZIO
distribution_allowed: true|false|TOKEN_VAZIO
claim_allowed: true|false
next_gate: ...
```

## Transformation invariant

For every transformation edge:

`SOURCE_IDENTITY -> TRANSFORMATION -> CURRENT_IDENTITY`

The edge MUST preserve:

1. source identity;
2. source rights/license state;
3. transformation type;
4. local modification authorship;
5. current artifact identity;
6. evidence;
7. attribution/NOTICE/About obligations;
8. uncertainty.

A later refactor, translation, port, optimization, rename, reformat, generated wrapper, or UI redesign does not sever prior lineage.

## Credit rule

When upstream content remains in the distributed work:

- preserve required copyright/license/NOTICE terms;
- name the canonical upstream/base in the project About/credits surface where appropriate;
- state that the current project contains local modifications;
- do not imply endorsement by upstream;
- do not imply local authorship of inherited material;
- if the license requires attribution in a particular place, that requirement wins over the generic About policy.

When material is permissively licensed, permissions do not erase origin or authorship.

## About synchronization

The About surface MUST be derivable from canonical provenance/licensing records, not maintained as an unrelated marketing page.

Minimum sections:

1. **Current project identity / maintainers**
2. **Upstream/base projects**
3. **Local modifications / RAFAELIA-original modules**
4. **Third-party components and licenses**
5. **Source/build identity**
6. **Provenance/compliance status and open gaps**

If a shipped component changes attribution state, About/NOTICE generation becomes stale and release MUST remain blocked until reconciled.

## Authorship claim gate

Local authorship may be claimed only when:

- the unit is `ORIGINAL_VERIFIED` or the local modification portion of `UPSTREAM_MODIFIED` is clearly bounded;
- parent lineage is known enough to avoid claiming inherited expression;
- license obligations are satisfied;
- evidence binds the claim to concrete paths/commits;
- `claim_allowed=true`.

`modified_by_us` is not equivalent to `authored_entirely_by_us`.

## Clean-room honesty

Do not use `CLEAN_ROOM_VERIFIED` if implementation participants had access to the excluded source expression in a way incompatible with the declared clean-room boundary. If uncertain, use `TOKEN_VAZIO` or `INFLUENCED_BY_UPSTREAM` and record what is actually known.

## Release blockers

Block release/provenance promotion when any distributed unit is:

- `TOKEN_VAZIO` for origin/license where the uncertainty is material;
- `PRO_OFFICIAL_PENDING` without adequate distribution rights;
- `QUARANTINED`;
- missing required NOTICE/copyright;
- represented in About as local when lineage says upstream/third-party;
- represented in source records with a digest/ref inconsistent with the built artifact.

## Receipts

Every provenance-changing operation MUST append or update an auditable receipt with:

`event_id | parent_event | source lineage | transform | current artifact | evidence | license state | About/NOTICE impact | F_ok | F_gap | F_next | claim_allowed | rollback/supersession ref`

Never delete historical lineage to make a later state look cleaner. Supersede it.