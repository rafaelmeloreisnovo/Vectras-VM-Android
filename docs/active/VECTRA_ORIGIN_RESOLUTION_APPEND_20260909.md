# VECTRA — Origin Resolution Append — 2026-09-09

Status: `APPEND_ONLY_SUCCESSOR / EVIDENCE_FIRST / CLAIM_ALLOWED=false`

This document does not rewrite the earlier `TOKEN_VAZIO_ORIGIN` observations.
It records the later evidence that closes two of those gaps.

## 1. `engine/rmr/src/rmr_neon_simd.c`

The repository path history was walked backwards rather than inferred from the
current copyright header or from later NEON-related commits.

Observed boundary:

- no commit for this path exists before `2026-02-21T18:51:39Z` in the queried
  repository history;
- commit `c3db10e8a556e35390b0eaac616194cb20f4ecc3`, dated
  `2026-02-21T18:51:39Z`, lists `engine/rmr/src/rmr_neon_simd.c` with
  `status=added`, `248` additions and `0` deletions;
- the commit author is `reismelorafael <reismelorafael@gmail.com>` and the
  commit message is `Add files via upload`;
- the initial patch already names the file `RAFAELIA NEON/SIMD Baremetal
  Acceleration` and contains the ARM64 NEON/x86/scalar structure.

Therefore:

```text
RMR_NEON_SIMD_FIRST_TRACEABLE_REPOSITORY_EXPRESSION =
  c3db10e8a556e35390b0eaac616194cb20f4ecc3
RMR_NEON_SIMD_PATH_STATE_AT_COMMIT = ADDED
RMR_NEON_SIMD_SOURCE_COMMIT_AUTHOR = reismelorafael
RMR_NEON_SIMD_GLOBAL_IDEA_PRIORITY = TOKEN_VAZIO
RMR_NEON_SIMD_AI_TOOL_AT_CREATION = TOKEN_VAZIO_NO_EVIDENCE_IN_CREATION_COMMIT
```

The last line is deliberate: later repository history contains AI-assisted
changes, but the creation commit itself does not prove an AI tool. Absence of
evidence is not converted into either `AI_CREATED` or `HUMAN_ONLY`.

The current SPDX/copyright header was added later in repository history and is
preserved. It is licensing/current attribution evidence, not retroactive proof
of the first file creation event.

## 2. `demo_cli/src/rmr_hw_detect_selftest.c`

The same path-history method resolves the whole-file predecessor that was
previously `TOKEN_VAZIO_ORIGIN`.

Observed boundary:

- no commit for this path exists before `2026-03-06T05:07:59Z` in the queried
  repository history;
- commit `bccefdb64051eb817b372b876acd308411bb8184`, dated
  `2026-03-06T05:07:59Z`, lists `demo_cli/src/rmr_hw_detect_selftest.c` with
  `status=added`, `40` additions and `0` deletions;
- the commit author/committer is `reismelorafael <reismelorafael@gmail.com>`;
- the commit message is `Add L4 cache tier spillover, tuning, and autodetect selftest`.

Therefore:

```text
RMR_HW_DETECT_SELFTEST_FIRST_TRACEABLE_REPOSITORY_EXPRESSION =
  bccefdb64051eb817b372b876acd308411bb8184
RMR_HW_DETECT_SELFTEST_PATH_STATE_AT_COMMIT = ADDED
RMR_HW_DETECT_SELFTEST_SOURCE_COMMIT_AUTHOR = reismelorafael
RMR_HW_DETECT_SELFTEST_GLOBAL_IDEA_PRIORITY = TOKEN_VAZIO
```

The current selftest still lacks a file-level SPDX header; this origin closure
does not silently solve that separate licensing metadata gap.

## 3. Provenance semantics

A commit that first adds a path proves a bounded proposition:

```text
first traceable expression of this path in this repository history
```

It does **not** prove:

- global historical priority of an algorithm or mathematical idea;
- legal ownership of every concept expressed in the file;
- identity equivalence between different GitHub accounts unless separately
  evidenced;
- absence or presence of external/off-Git predecessors;
- whether a tool assisted the author unless the evidence says so.

For those stronger claims, `TOKEN_VAZIO` remains valid.

## 4. Append-only sharding rule

The provenance chain is now allowed to grow through ordered JSONL shards.
A new shard starts by pointing to the last `record_id` and `record_sha256` of
the previous shard. Validators concatenate the declared shard order before
checking immediate predecessor continuity.

This avoids rewriting the historical register merely to append discoveries and
keeps the custody topology scalable.

## R3

`F_ok`: exact first traceable repository additions for NEON SIMD and the hardware
selftest are now resolved with path-status evidence.

`F_gap`: global idea priority, off-Git predecessors, creation-time AI assistance
for the NEON upload, and selftest file-level SPDX remain unproven.

`F_next`: bind these origin-resolution successors into the CI-verified
append-only provenance shard chain without changing the earlier records.
