# Protected Subject + Context Integrity Gate V1

Status: `FAIL_CLOSED / EVIDENCE_REQUIRED`  
Claim gate: `claim_allowed=false`

## Purpose

This gate prevents VM/QEMU implementation evidence from silently becoming a privacy, dignity, child-protection, cultural-context or legal-compliance claim.

The repository owns its VM/QEMU consumer implementation. `Mapa` owns federated routing/state. Termux owns the provider side. External law/regulation and technical standards retain their own scope and authority.

## Context integrity

The system must not manufacture cultural or protected-subject meaning from a label.

```text
child_status_unknown != adult
age_threshold != cultural_context_resolved
group_label != cultural_meaning
cultural_reference_missing -> TOKEN_VAZIO_CONTEXT
guardian_role != automatically_valid_consent
best_interest_assessment != compliance_certificate
```

Do not infer age, ethnicity, culture, religion, family/guardian status or vulnerability from a name, group, geography, VM image, account label or repository metadata.

When a feature actually establishes or potentially processes data about a child or vulnerable person, elevate the privacy/dignity gate to P0. Resolve the concrete use case, subject, purpose, data categories, recipients, retention, jurisdiction, authority/consent, alternatives and best-interest analysis before promotion.

## VM/QEMU privacy boundary

Treat these as potentially private until a narrower classification is evidenced:

- VM images and snapshots;
- ISO/disk paths and imported files;
- guest console and runtime logs;
- QEMU arguments;
- VNC/X11 configuration;
- crash diagnostics and benchmark residue;
- IPC request/result payload.

Receipts should carry transaction IDs, digests, bounded status and redacted metadata instead of raw guest/user payload.

## Non-compensatory gate

A green build, static test or VM boot cannot compensate for a critical unresolved privacy/security/protected-subject gate.

```text
P0 privacy/security/protected-subject gap -> HOLD
TOKEN_VAZIO on a gating dimension -> HOLD
static contract PASS != physical execution
physical execution != legal/privacy conformance
standard reference != certification
```

## Priority order

1. P0 unsafe image/command/IPC mutation, secret or raw-user-data exposure.
2. P0 child/vulnerable-subject data with unresolved context or authority.
3. P0 permission/result-integrity/execution-identity boundary.
4. P1 stale normative/privacy inventory, retention, third-party SDK/network flow.
5. UX/polish only after the non-compensatory gates are resolved or explicitly blocked.

## Normative reference policy

The central Mapa normative graph may reference child-rights, privacy and secure-development authorities. A reference is a routing input, not proof that this application complies with that authority.

For each claimed normative relationship record:

```text
authority + version/date
jurisdiction/scope
subject/use case
data flow
evidence
falsifier
review requirement
receipt
claim_allowed=false until gate closes
```

## Falsifier

This gate is falsified if a promotion occurs while a P0 privacy/security/protected-subject state is unresolved, or if raw guest/user data is published where a digest/redacted reference was sufficient.
