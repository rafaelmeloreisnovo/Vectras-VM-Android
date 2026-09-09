# VECTRA — Compiler / Preprocessor / Linker / Origin Audit — 2026-09-09

Status: `EVIDENCE_FIRST / APPEND_ONLY_SUCCESSOR / CLAIM_ALLOWED=false`

This document is a technical successor to selected statements in
`VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`. It does **not** delete,
rewrite, or hide the older document. The older text remains part of the custody
history; this file records which claims are superseded and the operational rule
that replaces them.

## 1. Invariants

```text
SOURCE != OBJECT != FINAL_LINK != EVIDENCE != CLAIM
WARNING != LINKER_GC
COMPILE_FLAG != LINK_FLAG
REFACTOR != NEW_AUTHORSHIP
RENAME != NEW_AUTHORSHIP
LICENSE_HEADER != PROOF_OF_ORIGIN
TOKEN_VAZIO_ORIGIN != LOCAL_AUTHORSHIP
```

The goal is not to make the source look low-level. The goal is to make the
**produced machine artifact observable**: effective compiler/linker command,
ELF class/machine/entry, map witness, undefined symbols, dynamic dependencies,
forbidden helpers, hashes and reproducibility state.

## 2. Correction: what `-ffreestanding` actually establishes

The previous document compressed compile-time and final-link behavior into one
statement. The corrected boundary is:

- `-ffreestanding` changes the C compilation environment and removes hosted-C
  assumptions; it does not by itself prove that the final ELF has no libc or
  compiler-runtime dependency.
- `-fno-builtin` prevents selected compiler builtin assumptions/rewrites, but a
  compiler can still lower C operations to ABI helper symbols when required by
  the target ISA.
- `-nostdlib` belongs to the final-link boundary. The result still has to be
  inspected for undefined helpers, `DT_NEEDED` entries and forbidden symbols.
- therefore `COMPILES_FREESTANDING != LINKS_FREESTANDING`.

This is why `abi_core_freestanding` stays a static archive while
`vectra_freestanding_link_probe` is the dedicated final-link witness.

## 3. Correction: frame pointer, stack protector and unwind are separate

The older document associated stack unwinding with stack-protector behavior.
That is not a valid equivalence.

```text
frame-pointer policy     -> register/frame-chain choice
stack protector          -> stack corruption detection
unwind tables            -> unwinding metadata
```

They are separate controls. In the current strict freestanding probe,
unwind tables and asynchronous unwind tables are explicitly disabled; no claim
of debugger stack unwinding is made from `-fstack-protector*`.

## 4. Five-stage reduction strategy — corrected causal model

The useful five-stage idea is preserved, but observation and action are
separated:

1. **Preprocessor / capability gate**
   - select the smallest valid implementation for the observed ABI/toolchain;
   - unavailable optional ISA headers fall back or fail closed according to the
     contract, never silently promote a capability;
   - compile-time constants expose bounded loops to the optimizer when doing so
     preserves semantics.

2. **Section granularity**
   - `-ffunction-sections` and `-fdata-sections` isolate code/data into
     independently collectible ELF sections.

3. **Visibility / public ABI contract**
   - hidden visibility reduces accidental export surface;
   - public symbols are intentional roots, not incidental names;
   - symbol count is measured after link, not inferred from source aesthetics.

4. **Warnings as sensors**
   - `-Wunused-*` is diagnostic evidence about source/compiler reachability;
   - a warning does **not** instruct the linker to remove a section;
   - suppressing or retaining a warning does not, by itself, determine whether
     bytes survive final link.

5. **Linker graph + post-link proof**
   - `--gc-sections` removes eligible unreferenced sections according to the
     linker's reference graph;
   - `--no-undefined` closes unresolved-symbol gaps at final link;
   - `readelf`, `nm`, `objdump`, the link map and artifact hashes decide what
     actually survived.

The production rule is therefore:

```text
warning -> OBSERVATION
section graph -> ELIMINATION DECISION
post-link inspection -> EVIDENCE
```

## 5. Symbol removal: forbid hidden abstractions, do not merely rename them

A source can contain no explicit libc call and still produce helpers such as
`__aeabi_*`, division helpers, stack-check functions or an implicit `memcpy`.
For this reason the final-link audit denies known hosted/runtime symbol families
and reports every undefined symbol and `DT_NEEDED` library.

The PR #1142 audit already demonstrated the class of failure: a 64-bit modulo in
a nominally freestanding ARMv7 path could lower to `__aeabi_uldivmod`. The
correct response was algebraic reduction of the operation, followed by object
symbol inspection — not a source-only grep and not a renamed wrapper.

## 6. Loops: forward-only where semantics permit; no global unroll dogma

The preferred loop transformation is structural:

```text
re-read/restart loop -> single monotonic pass
multiple passes over same byte -> shared load when equivalence is proven
runtime-unbounded loop -> bounded budget/watchdog
```

`-funroll-loops` or manual unrolling is **not** a universal requirement. It can
increase code size, register pressure and I-cache cost. Promotion requires
objdump/size/benchmark evidence for the specific ABI.

Current state:

- topological byte processing: single forward pass;
- vector bytecode: monotonic 2-byte program counter;
- correction work: bounded cumulative watchdog;
- more aggressive unrolling: `TOKEN_VAZIO_CODEGEN_BENCH` until measured.

## 7. Alignment, 16/32/128-bit layout and overlays

Geometry can guide layout hypotheses, but ABI changes require evidence.
`RmR_VectorFieldState` currently contains twelve `u32` fields = 48 bytes of
logical payload before any externally forced alignment/padding.

Forcing `aligned(64)`, overlaying fields with unions, or repacking the public
state solely because the conceptual model uses 16/32/128-bit groupings would
change layout/ABI and may increase memory traffic.

Policy:

```text
BIT_GEOMETRY -> candidate layout
candidate layout -> compile-time size/offset assertions
assertions -> objdump/cache/benchmark receipt
receipt -> promotion
```

Until then:

`RMR_VECTOR_STATE_CACHE_ALIGNMENT = TOKEN_VAZIO_ALIGNMENT_BENCH`.

## 8. ASCII / hexadecimal constants

Hexadecimal constants are useful when they expose masks, opcodes, bit fields,
magic tags or machine-level boundaries. ASCII-looking hex is documentation, not
proof of correctness or provenance.

Rules:

- one canonical definition for cross-language magic constants;
- comment the human-readable ASCII interpretation when useful;
- static/CI equality checks at language boundaries;
- no duplicate literal becomes an independent source of truth;
- a human-friendly hex spelling must not replace the semantic name.

## 9. Performance claims are evidence-gated

Historical statements such as fixed CRC throughput numbers, fixed speedup
multipliers, exact dynamic-symbol counts or percentage binary shrinkage are not
promoted by this successor unless their exact build/ABI/toolchain/artifact
receipt is attached.

Use:

```text
CRC_SW_THROUGHPUT = TOKEN_VAZIO_MEASUREMENT
CRC_HW_THROUGHPUT = TOKEN_VAZIO_MEASUREMENT
GC_SECTIONS_SIZE_REDUCTION = TOKEN_VAZIO_MEASUREMENT
```

until a reproducible benchmark/link receipt closes them.

## 10. Origin and authorship: earliest traceable expression, not mythology

The project already distinguishes authors, source authors, contributors,
reviewers and AI-assisted tools. This audit applies that rule to the PR #1142
surface.

### Vector field

Earliest traceable repository expression observed in this pass:

- PR #982 — `Add deterministic RMR vector field kernel`;
- commit `2e25ec094d351c9aed068711a1f7ad676fc03632`;
- commit author/committer: Rafael mreis;
- PR body explicitly records an OpenAI Codex Task.

Therefore the trace is human project/commit authority **with AI assistance
recorded**, not “AI human author” and not a claim that the underlying
mathematical concepts were globally first invented at that commit.

### Topological guard

Earliest traceable repository expression observed in this pass:

- PR #951 — `Add topological guard module and exercise it in hw detect selftest`;
- head commit `b79c74e6ce010bd1169d38790712c01d8bcd72dc`;
- commit author/committer: Rafael mreis;
- PR body explicitly records an OpenAI Codex Task.

The same attribution boundary applies.

### NEON SIMD file

The broader repository has older NEON work, but this pass did not prove the
exact first expression of `engine/rmr/src/rmr_neon_simd.c`.

`RMR_NEON_SIMD_FIRST_ORIGIN = TOKEN_VAZIO_ORIGIN`.

A current copyright/SPDX header is preserved, but it is not treated as proof of
historical first authorship.

## 11. Hash chain, not a consensus blockchain

For provenance, the required property is tamper-evident append-only custody, not
distributed consensus.

`resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl` is extended with a
v2 hash-chain contract:

```text
previous_record_id
previous_record_sha256
record_sha256
hash_chain_kind = TAMPER_EVIDENT_APPEND_ONLY_NOT_CONSENSUS_BLOCKCHAIN
```

Each v2 record hashes its canonical JSON payload excluding its own
`record_sha256`. A successor stores the predecessor digest. The legacy v1 line
is retained as the genesis predecessor; it is never rewritten merely to fit the
new schema.

This provides:

- mutation detection;
- predecessor navigation;
- origin/transform/source-blob anchors;
- typed `TOKEN_VAZIO` gaps;
- zero requirement to embed provenance metadata into the hot ELF path.

It does **not** provide decentralized consensus, timestamp authority external to
Git/GitHub, or legal certification.

## 12. Production promotion gate

A low-level change is promotable only when the five surfaces agree:

```text
P1 SOURCE/PREPROCESSOR  -> selected implementation is explicit
P2 OBJECT               -> no unexpected compiler helper survives object audit
P3 FINAL LINK           -> no forbidden undefined/NEEDED/export surface
P4 PROVENANCE           -> origin + transformation + rights chain is navigable
P5 EXECUTION            -> requested ABI/device behavior has a receipt
```

Any missing surface becomes a typed `TOKEN_VAZIO_*`; no layer borrows evidence
from another.

## R3

`F_ok`: compiler/preprocessor/linker causal model corrected; historical text
preserved; earliest traceable vector/topological origins anchored; provenance
hash-chain design specified.

`F_gap`: exact NEON-file first origin, physical alignment benefit, aggressive
unroll benefit and performance multipliers remain unproven.

`F_next`: validate the append-only transform hash chain in CI and inspect the
exact PR head through the existing freestanding final-link audit before merge.
