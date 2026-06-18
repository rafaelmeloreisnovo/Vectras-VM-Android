# VECTRA_OS / PR1005 Boundary Ledger — 2026-06-12

Status: boundary ledger, continuation PR seed
Repository: `rafaelmeloreisnovo/Vectras-VM-Android`
Base commit used for this branch: `6b54df0a021bccd95a51dd5dd154a9099b144cd0`
Related PR: `#1005`

## 0. Purpose

This file prevents the PR #1005 description from becoming a false proof of work not present in its diff.

PR #1005 is valid and useful, but its diff proves the **TCG cache delta-XOR layer**, not the entire VECTRA_OS G1-G8 chain.

Principle:

```text
Ruido entendido vira sinal.
Erro medido vira engenharia.
Lacuna marcada vira ciencia.
TOKEN_VAZIO protegido vira verdade futura.
```

## 1. What PR #1005 actually merged

Verified scope:

- `engine/rmr/src/rmr_tcg_cache.c`
  - resident byte read;
  - `delta = current ^ value`;
  - only divergent bits are written;
  - `delta_bits_flipped` and `delta_bits_preserved` are accumulated.

- `engine/rmr/include/rmr_tcg_cache.h`
  - cache struct gains `delta_bits_flipped` and `delta_bits_preserved`;
  - public accessors expose flipped bits and preserved percentage.

- `demo_cli/src/rmr_tcg_cache_selftest.c`
  - miss is an explicit state;
  - first insert matches payload popcount;
  - identical reinsertion flips zero bits;
  - one-bit mutation flips exactly one bit;
  - collapsed block returns MISS by policy;
  - deterministic replay preserves ISOraf identity.

- `Makefile` and `CMakeLists.txt`
  - integrate `rmr_tcg_cache_selftest` into host selftest paths.

- `docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`
  - documents cache miss as next instruction and delta-XOR as bit-level preservation metric.

## 2. What remains TOKEN_VAZIO / continuation work

The following items were described in the earlier PR body, but were not fully present in the PR #1005 diff.
They must remain protected until a dedicated PR provides code, test, and/or generated evidence.

| Gap | Object | Status | Required proof |
|---|---|---|---|
| G2-min | `VOS_CSEL` truth table | `TOKEN_VAZIO` / bug identified | change in `engine/rmr/include/rmr_vectra_os.h` plus selftest |
| G2-FRAF | `VOS_FRAF_STAR_Q16`, `VOS_FRAF_ITERS` | `TOKEN_VAZIO` | derivation note plus convergence selftest |
| G3 | `rmr_vectra_flags.def`, generated flag names | `TOKEN_VAZIO` | X-macro source and compile check |
| G4 | flag rollback / `RAF_TRY_FLAG` | `TOKEN_VAZIO` | rollback selftest |
| G5 | CAS layer / pointer hotswap | `TOKEN_VAZIO` | atomic API and negative-path test |
| G6 | trampoline hotswap opt-in | `TOKEN_VAZIO` | encoder selftest and W^X guard |
| G7 | Machine Codex enforcement | `TOKEN_VAZIO` | `VOS_MC_ASSERT` family and negative proofs |
| G8 | MVP benchmark proof layer | `TOKEN_VAZIO` | result artifact with raw samples, p5/median/p95, compiler, flags, commit and binary hash |

## 3. First concrete bug to fix next: `VOS_CSEL`

Current inspected contract says:

```c
#define VOS_CSEL(cond, a, b) \
    ((__typeof__(a))(((u32)(-(s32)((u32)(cond) != 0u)) & (u32)(a)) | \
                     ((u32)( (s32)((u32)(cond) != 0u)) & (u32)(b))))
```

The documented identity is:

```c
mask = -(cond != 0);
result = (a & mask) | (b & ~mask);
```

Required minimal correction:

```c
#define VOS_CSEL(cond, a, b) \
    ((__typeof__(a))(((u32)(-(s32)((u32)(cond) != 0u)) & (u32)(a)) | \
                     (~(u32)(-(s32)((u32)(cond) != 0u)) & (u32)(b))))
```

Required truth table:

```c
VOS_CSEL(0, 10u, 20u) == 20u
VOS_CSEL(1, 10u, 20u) == 10u
VOS_CSEL(0, 0xAAAAAAAAu, 0x55555555u) == 0x55555555u
VOS_CSEL(1, 0xAAAAAAAAu, 0x55555555u) == 0xAAAAAAAAu
```

## 4. FRAF boundary

The PR #1005 body previously mentioned a different FRAF contract (`F* = 0x17277A`, `ITERS = 96`).
The inspected header still declares:

```c
#define VOS_FRAF_STAR_Q16   ((vos_q16_t)0x00172CE4u)
#define VOS_FRAF_ITERS      48u
```

No constant must be changed until a derivation and executable convergence selftest are added.
Until then:

```text
FRAF realignment = TOKEN_VAZIO protected
```

## 5. Next PR acceptance criteria

A continuation PR can be considered evidence-bearing only if it includes at least:

1. code change for `VOS_CSEL`;
2. a selftest or executable contract proof;
3. no claim that G1-G8 is complete unless each gap maps to a changed file and proof node;
4. explicit CI/build status, or a clear statement that CI is unavailable / startup_failure.

## 6. F_ok / F_gap / F_next

```text
F_ok   = PR1005 proves TCG cache delta-XOR with selftest and build integration.
F_gap  = VECTRA_OS G1-G8 was described beyond the PR1005 diff; CI startup_failure blocks build inference.
F_next = fix VOS_CSEL, add selftest, keep FRAF as TOKEN_VAZIO until derivation + proof.
```
