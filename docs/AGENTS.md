# AGENTS.md — Documentation, governance and evidence semantics

Scope: `docs/**`. Root `AGENTS.md` remains authoritative; this file makes documentation itself auditable.

## Documentation is evidence-bearing infrastructure

A document MUST distinguish:

`OBSERVATION → EVIDENCE → INTERPRETATION → CONTROL → CLAIM`

Never collapse these levels. A plan, aspiration, analogy, roadmap, requirement or passing static check is not runtime evidence.

## Typed epistemic states

Use explicit states whenever truth is incomplete:

- `VERIFIED` — evidence supports the scoped statement.
- `PARTIAL` — some required evidence exists, scope remains incomplete.
- `CONTRADICTED` — credible evidence conflicts; do not average the conflict away.
- `ANOMALOUS` — observation differs from the expected model and needs bounded investigation.
- `ASPIRATIONAL` — target/utopia/desired future state; never present as implemented.
- `DEFERRED` — intentionally postponed with reason and owner/trigger where known.
- `TOKEN_VAZIO` — evidence is insufficient; preserve the question and next verifiable step.
- `QUARANTINED` — unsafe, legally uncertain or provenance-uncertain material isolated from promotion/release.

For contradictions, paradoxes and anomalies: preserve both sides, identify the violated invariant, record a falsifier, and stop claim promotion until resolved.

## Provenance minimum

Every material governance/compliance document SHOULD carry or link:

`document_id | version/date | parent/supersedes | repo/ref/commit | sources | author/maintainer | scope | evidence_refs | unresolved_gaps | next_gate`

Historical documents are not silently rewritten into current truth. Prefer successor records and explicit supersession links when auditability matters.

## Normative language

When writing requirements, use BCP 14 semantics (`MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, `MAY`) only when the document explicitly declares those keywords normative. Otherwise use ordinary prose.

A standard or framework reference MUST include:

`authority | identifier | edition/version | status(final/draft/withdrawn) | applicability | mapped controls | evidence | gap`

Do not say “compliant”, “certified”, “conforms to ISO/NIST/IEEE” unless the exact scope and required assessment evidence justify it. Default wording is “mapped to”, “aligned control”, “readiness evidence”, or “applicability under review”.

## Decision record / receipt

A material transition records:

```text
receipt_id / parent_receipt
timestamp
repo / ref / commit / paths
objective
authority + write scope
origin/authorship class
standard/control mappings
risk + urgency
data/privacy/security classification
observation/evidence
falsifier + exit criterion
action + result
F_ok / F_gap / F_next
uncertainty delta
rollback ref
claim_allowed
```

Receipts are append-oriented. Corrections create a successor pointing to the superseded receipt; do not erase inconvenient evidence.

## Continuous-improvement loop

Use a compact DMAIC-compatible loop without pretending Six Sigma certification:

`DEFINE → MEASURE → ANALYZE → IMPROVE → CONTROL → RECEIPT → REASSESS`

Each improvement needs a baseline, measurable target, bounded change, verification, regression check and control/rollback. Improvement without a baseline remains `ASPIRATIONAL` or `TOKEN_VAZIO`.

## Relationship / rapport semantics

Cross-document and cross-component relationships MUST be typed, not implied by proximity. Recommended edge types:

`IMPLEMENTS | DERIVES_FROM | MODIFIES | DEPENDS_ON | EVIDENCES | FALSIFIES | CONSTRAINS | SUPERSEDES | CONFLICTS_WITH | ANALOGOUS_TO | TESTS | OWNS | ATTRIBUTES_TO`

`ANALOGOUS_TO` never promotes to `DERIVES_FROM`, `IMPLEMENTS` or causal evidence without an explicit bridge.

## Stop rule

Stop reconstruction when additional history cannot change the scoped authority, risk, provenance, applicability, evidence state or next executable gate. Record the remaining uncertainty rather than expanding indefinitely.
