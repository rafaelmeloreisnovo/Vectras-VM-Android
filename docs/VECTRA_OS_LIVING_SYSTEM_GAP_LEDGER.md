# VECTRA_OS Living System Gap Ledger

Date: 2026-06-09
Scope: Vectras-VM-Android / VECTRA_OS / RAFAELIA freestanding-bare-metal corpus
Status: first verification ledger, not a final implementation patch

## 0. Operating principle

VECTRA_OS is treated here as a living system, not as a conventional C module.
A gap is not merely a compiler warning. A gap is any mismatch between:

1. the documented operational contract;
2. the low-level implementation;
3. the build/linker policy;
4. the runtime behavior;
5. the selftest / benchmark evidence.

In this model, comments are not compiled by the C compiler, but they are part of the engineering contract. They must become enforceable through macros, CMake flags, linker checks, selftests, benchmark reports, and rollback rules.

## 1. Source corpus used for this ledger

Uploaded/session documents:

- `RAFAELIA_CONCEPTS_MVP.txt` — MVP benchmark, 39 Trickstopathcutter techniques, HOTSWAP, Machine Codex.
- `RAFAELIA_CODEX_TTL8_SUMMARY.txt` — TTL8 state machine, bit-paths, flags HEX U64, hotswap atomicity.
- `RAFAELIA_BARE_HARDWARE.txt` — direct-register bare-metal philosophy and hardware register mapping.
- `RAFAELIA_NDK.txt` — Android NDK, JNI, ARM32/ARM64, no STL, atomic ARM32, mmap2 arena.
- `METHODOLOGY.md` — experimental protocol and validation criteria.
- `vm_runtime.txt` — dual libc/freestanding VM runtime and ABI/opcodes.
- `compiladorlowFala.txt` — speech/text to tokens/AST/bytecode/ASM/VM pipeline.
- `RAFAELIA_SEMENTES.txt` — Q16 sqrt(3)/2, FRAF fixed point, Lyapunov stability seeds.
- `todos_scripts.zip` and `RAFAELIA_ΣΩΔΦBITRAF_MASTER.zip` — broader script corpus, not fully reduced in this first ledger.

Repository files inspected:

- `engine/rmr/include/rmr_vectra_os.h`
- `engine/rmr/src/rmr_vectra_os.c`
- `engine/rmr/interop/rmr_vectra_os_arm64.S`
- `CMakeLists.txt`
- PR #1001 metadata and changed file list

## 2. Existing implementation — confirmed base

### 2.1 Freestanding-oriented compilation contract

Confirmed in `rmr_vectra_os.h`:

- hot path should be macro-only;
- arena allocation only;
- no malloc / brk / mmap in hot path;
- branchless selection via `VOS_CSEL`;
- bounded or unrolled loops;
- hidden symbols by default;
- explicit public API.

Confirmed in `CMakeLists.txt`:

- `-ffunction-sections`;
- `-fdata-sections`;
- `-fvisibility=hidden`;
- `-Wl,--gc-sections`;
- `-Wl,--exclude-libs,ALL`.

### 2.2 Current runtime primitives

Confirmed:

- BSS arena: `vos_g_arena`, `vos_g_arena_top`, `vos_g_arena_mark`;
- arena rollback: `VOS_MARK`, `VOS_RESTORE`, `VOS_ARENA_RESET`;
- capability flags: `VOS_CAP_*`;
- dispatch table: `vos_g_crc`, `vos_g_tick`;
- hotswap macros: `VOS_HOTSWAP_CRC`, `VOS_HOTSWAP_TICK`;
- Q16/FRAF constants and convergence macro;
- CRC32C software fallback;
- ARM64/x86/ARMv7/RISC-V ASM primitives;
- `vos_init`, `vos_selftest`, `vos_caps_report`.

## 3. First critical mismatch — VOS_CSEL contract break

### Contract

`VOS_CSEL(cond, a, b)` must return `a` when `cond != 0`, otherwise `b`.
The documented identity is:

```c
mask = -(cond != 0);
result = (a & mask) | (b & ~mask);
```

### Current failure mode

The second half currently uses `(cond != 0)` as a 0/1 value instead of `~mask`.
This is a logical contract error, not a compiler warning.

### Required fix

```c
#define VOS_CSEL(cond, a, b) \
    ((__typeof__(a))(((u32)(-(s32)((u32)(cond) != 0u)) & (u32)(a)) | \
                     (~(u32)(-(s32)((u32)(cond) != 0u)) & (u32)(b))))
```

### Required selftest

```c
if (VOS_CSEL(0, 10u, 20u) != 20u) return 0u;
if (VOS_CSEL(1, 10u, 20u) != 10u) return 0u;
if (VOS_CSEL(0, 0xAAAAAAAAu, 0x55555555u) != 0x55555555u) return 0u;
if (VOS_CSEL(1, 0xAAAAAAAAu, 0x55555555u) != 0xAAAAAAAAu) return 0u;
```

## 4. Gap families to implement in stages

### G1 — Contract audit report

Create an automated report that checks whether the engineering contract is true after build.

Target files:

- `tools/verify_vectra_os_contract.sh`
- `reports/vectra_os_contract_report.md`

Checks:

- exported symbol list;
- forbidden symbol scan: `malloc`, `calloc`, `realloc`, `free`, `mmap`, `brk`, `printf`, `clock_gettime` in hot path;
- ELF section visibility;
- `--gc-sections` effect;
- public API only exports intended symbols.

### G2 — VECTRA_OS contract selftest

Create:

- `demo_cli/src/rmr_vectra_os_contract_selftest.c`

Tests:

- `VOS_CSEL` truth table;
- arena mark/restore;
- capability flag mark/restore;
- dispatch swap;
- `vos_caps_report` contents;
- X-macro flag name lookup after G3;
- CAS after G4;
- trampoline only if explicitly enabled after G6.

### G3 — X-macro flag source of truth

Create:

- `engine/rmr/include/rmr_vectra_flags.def`

Goal:

- generate `VOS_CAP_*` values;
- generate `vos_flag_name`;
- avoid duplicated bit definitions;
- align with TTL8/HEX U64 layered flags later.

Initial entries:

```c
VOS_CAP_DEF(CRC32C_HW, 0, "crc32c_hw")
VOS_CAP_DEF(NEON_128,  1, "neon_128")
VOS_CAP_DEF(SVE,       2, "sve")
VOS_CAP_DEF(FMA,       3, "fma")
VOS_CAP_DEF(CNTVCT,    4, "cntvct")
VOS_CAP_DEF(RDTSC,     5, "rdtsc")
VOS_CAP_DEF(SSE42,     6, "sse42")
VOS_CAP_DEF(MOCK,     31, "mock")
```

### G4 — Flag rollback and transactional hotswap

Add:

- `volatile vos_cap_t vos_g_caps_prev;`
- `VOS_FLAGS_MARK()`;
- `VOS_FLAGS_RESTORE()`;
- `RAF_TRY_FLAG(mask, body)`;
- return-code propagation through TTL8 states.

This links the current VOS capability model to the TTL8 failure model.

### G5 — Atomic CAS layer

Add atomic macro layer:

- `VOS_CAS32`;
- `VOS_ATOMIC_LOAD32`;
- `VOS_ATOMIC_STORE32`;
- pointer CAS for dispatch hotswap.

Rules:

- ARM32 should use LDREX/STREX or compiler builtins behind a contract layer;
- ARM64 should use compiler atomics or exclusive ops where needed;
- hosted/JNI and freestanding strict must be separated.

### G6 — Trampoline hotswap, opt-in only

Add only after G1-G5 pass.

Guards:

```c
#ifndef VOS_ENABLE_TRAMPOLINE
#define VOS_ENABLE_TRAMPOLINE 0
#endif
```

ARM64 path:

- verify 4-byte alignment;
- verify branch range for `B imm26`;
- patch instruction;
- flush/invalidate instruction cache with `ic ivau`, `dsb ish`, `isb`;
- document Android W^X limitation.

x86_64 path:

- relative `JMP rel32` with range check;
- warn that 5-byte patch is not naturally atomic;
- require external synchronization or stop-the-world gate.

### G7 — Machine Codex enforcement

Convert Machine Codex constants into checking macros:

- `VOS_MC_ASSERT`;
- `VOS_MC_REQUIRE_ALIGNED`;
- `VOS_MC_REQUIRE_POW2`;
- `VOS_MC_RECIP_U32` plus reciprocal verification;
- `VOS_MC_LOOP_BOUND`.

### G8 — MVP benchmark proof layer

Implement benchmark harness around the 5 MVP kernels:

1. FRAF Q16 48 iterations;
2. CRC32C 4KB throughput;
3. arena alloc 64B;
4. T7 100 steps;
5. FSM step + Lyapunov classification.

Output:

- median;
- p5;
- p95;
- raw samples;
- platform tag;
- compiler flags;
- commit hash;
- binary hash.

### G9 — VM/compiler integration boundary

Keep VECTRA_OS separate from the larger speech/text-to-bytecode VM until the OS contract is stable.

Boundary rule:

- VECTRA_OS proves low-level runtime primitives;
- RAFAELIA-VM proves bytecode semantics;
- compilerlowFala proves language-to-bytecode front end;
- integration happens after each layer has independent selftest.

## 5. Implementation order

1. Fix `VOS_CSEL` and add truth-table selftest.
2. Add `docs/VECTRA_OS_LIVING_SYSTEM_GAP_LEDGER.md`.
3. Add `tools/verify_vectra_os_contract.sh`.
4. Add `demo_cli/src/rmr_vectra_os_contract_selftest.c` and register in CMake.
5. Add `rmr_vectra_flags.def` and `vos_flag_name`.
6. Add flag rollback and TTL8 state mapping.
7. Add CAS macros and dispatch CAS.
8. Add benchmark harness for MVP kernels.
9. Add trampoline hotswap only behind `VOS_ENABLE_TRAMPOLINE`.

## 6. Falsifiers

The system contract is falsified if any of these happen:

- `VOS_CSEL(0, a, b)` does not return `b`;
- hot path calls forbidden hosted functions;
- hidden/internal symbols leak into public export table;
- rollback does not restore prior state;
- dispatch hotswap races under concurrent mutation;
- benchmark report lacks raw samples or platform metadata;
- trampoline patching is enabled by default on Android without W^X guard;
- comments describe obligations that no script, macro, test, or build rule checks.

## 7. Current status summary

```text
VECTRA_OS_LIVING_STATUS:
  contract_comment_layer: PRESENT
  cmake_contract_layer: PRESENT
  makefile_manifest_alignment: PRESENT (sources_rmr_core.mk regenerado; vectra_os na trilha host)
  no_malloc_arena_layer: PRESENT_PARTIAL
  dispatch_hotswap_layer: PRESENT_PARTIAL
  asm_primitive_layer: PRESENT
  selftest_layer: PRESENT_PARTIAL
  contract_selftest: PRESENT (demo_cli/src/rmr_vectra_os_contract_selftest.c — G2 parcial: CSEL, arena, hotswap, caps)
  contract_audit_report: PRESENT (tools/verify_vectra_os_contract.sh — G1; evidência em reports/vectra_os_contract_report.md)
  csel_contract: FIXED_WITH_PROOF (máscara ~mask restaurada; tabela-verdade no selftest)
  fraf_attractor_constant: FIXED_QUANTIZED (F* = 0x17277A, ponto fixo do sistema Q16 implementado; ITERS 48→96 para honrar ε=0.001 — o contrato anterior era matematicamente insatisfazível)
  xmacro_flags: PRESENT (engine/rmr/include/rmr_vectra_flags.def — fonte única de bits; enum + máscaras + vos_flag_name gerados; prova no contract selftest)
  flag_rollback: PRESENT_PARTIAL (núcleo G4: vos_g_caps_prev + VOS_FLAGS_MARK/RESTORE + RAF_TRY_FLAG, provado no contract selftest; mapeamento TTL8 PENDENTE — RAFAELIA_CODEX_TTL8_SUMMARY.txt citado em §1 não está na árvore)
  cas_layer: PRESENT (VOS_CAS32, VOS_ATOMIC_LOAD32/STORE32, VOS_CAS_PTR sobre builtins GCC/Clang — LDREX/STREX em ARM32 via compilador; toolchain sem builtins = erro de compilação explícito; prova no contract selftest incluindo hotswap de dispatch por CAS)
  machine_codex_enforcement: MISSING
  mvp_benchmark_proof: MISSING
  trampoline_runtime_patch: MISSING_OPT_IN_REQUIRED
  vm_compiler_integration: SEPARATE_LAYER_PENDING
```

## 8. Next patch target

Done (proof nodes left behind):

1. ~~fix `VOS_CSEL`~~ — fixed; truth table in `rmr_vectra_os_contract_selftest.c`;
2. ~~add `rmr_vectra_os_contract_selftest.c`~~ — added;
3. ~~register it in CMake `run_selftest`~~ — registered in CMake and Makefile;
4. trampoline untouched, as required.

Additional proof nodes from the same patch:

- `tools/verify_vectra_os_contract.sh` (G1) — audits the warning→gc-sections
  pipeline: captures `-Wunused-*` as the elimination signal, asserts the
  3-symbol public export set, confirms `vos_tick_sw` is absent from the final
  binary, scans forbidden hot-path symbols. Evidence:
  `reports/vectra_os_contract_report.md`. Run: `make verify-vectra-os-contract`.
- FRAF attractor constant corrected to the quantized fixed point (see §7) —
  exposed by the new selftest; the old constant satisfied no build, test or
  script (falsifier family: "comments describe obligations nothing checks").

~~G3 done~~ — `rmr_vectra_flags.def` added as the single source of bit
numbers; `VOS_CAP_BIT_*`, `VOS_CAP_COUNT`, derived masks and
`vos_flag_name` generated from it; contract selftest proves bit
uniqueness, mask consistency and name lookup (including the "unknown"
default). `vos_flag_name` is `static inline`: zero exported symbols —
contract audit still reports exactly 3.

~~G4 core done~~ — `vos_g_caps_prev`, `VOS_FLAGS_MARK()`,
`VOS_FLAGS_RESTORE()` and `RAF_TRY_FLAG(mask, body)` added with the same
mark/restore geometry as the arena; contract selftest proves restore
exactness and that a false body leaves zero residue. The TTL8
return-code mapping remains PENDING: the reference
`RAFAELIA_CODEX_TTL8_SUMMARY.txt` cited in §1 is not in the tree — do
not invent the state model; ingest the codex first.

~~G5 done~~ — CAS layer over compiler builtins with explicit
compile-error contract for toolchains without atomics; selftest proves
success/failure semantics (failed CAS preserves the target and reports
the observed value — miss as information) and pointer-CAS dispatch
hotswap with rollback.

**Checkpoint: G1–G5 now pass.** G6 (trampoline) is unlocked by the
ledger rule but is opt-in by design and architecturally significant
(W^X on Android, non-atomic 5-byte patch on x86_64) — requires an
explicit owner decision before any code. Remaining after that decision:
G4-TTL8 (blocked on codex ingestion), G7 (Machine Codex macros),
G8 (MVP benchmark proof layer), G9 (boundary rule, documentation-only).

This preserves the living-system logic: every correction must leave behind a proof node.
