# VECTRA — Freestanding / Dual Watchdog — Receipt — 2026-09-09

Mode: `SOURCE_FIRST / FAIL_CLOSED / APPEND_ONLY_RECEIPT / NO_AUTO_MERGE`

## Custody

- repository: `rafaelmeloreisnovo/Vectras-VM-Android`
- source branch: `master`
- source head: `1330d75ec9c5a4b8c5a76933db1ac26bfd579b90`
- work branch: `beta/freestanding-dual-watchdog-v1-20260909`
- role: bounded beta-test engineering pass
- invariant: `SOURCE != ARTIFACT != EXECUTION != EVIDENCE != CLAIM`

## Audited source surfaces

- `START_HERE.md`
- `app/src/main/cpp/CMakeLists.txt`
- `engine/rmr/include/rmr_types.h`
- `engine/rmr/include/topological_guard.h`
- `engine/rmr/src/topological_guard.c`
- `engine/rmr/include/rmr_vector_field.h`
- `engine/rmr/src/rmr_vector_field.c`
- `engine/rmr/src/rmr_neon_simd.c`
- `demo_cli/src/rmr_hw_detect_selftest.c`
- `demo_cli/src/rmr_vector_field_selftest.c`
- Mapa federated control-plane references were used as governance context only; federation state is not runtime proof.

## Bounded structural changes

1. Topological guard no longer depends on `<stdint.h>`, `<string.h>` or `memset`; it uses canonical `rmr_types.h` and explicit initialization.
2. Topological byte analysis changed from two source traversals to one monotonically-forward pass that shares the loaded byte between transition counting and 8-lane folding.
3. A complement-coded peer watchdog was added: invariant `watchdog_peer == ~watchdog_count`. Divergence fails closed before state mutation and restores the checkpoint.
4. Vector bytecode correction budget is cumulative. `CORRECT` consumes only remaining budget; overflow restores the entry snapshot and returns `WATCHDOG|FAILSAFE|ROLLBACK`.
5. Vector bytecode traversal is bounded by `len` and moves `pc += 2`; unknown opcodes and trailing half-instructions fail closed.
6. SIMD source now uses canonical project types instead of duplicate local typedefs.
7. ARM CRC intrinsics are optional at source level. If the architecture advertises CRC but `<arm_acle.h>` is unavailable, compilation falls back to the software CRC32C path instead of emitting `#error`.
8. Unmeasured performance language was removed from the SIMD source comment. Performance remains evidence-gated.

## Build boundary preserved

Android JNI remains a hosted adapter. The existing `abi_core_freestanding` target keeps the strict compile contract (`-ffreestanding`, `-fno-builtin`, no stack protector/unwind, function/data sections), while `vectra_freestanding_link_probe` owns final-link flags (`-nostdlib`, `--gc-sections`, `--no-undefined`). No attempt was made to force JNI/Bionic into the freestanding contract.

## Tests materialized

- vector KAT: cumulative watchdog overflow must rollback atomically.
- vector KAT: unknown opcode must fail closed and rollback.
- topological KAT: peer watchdog encoding remains coherent through normal steps and rollback.
- topological negative KAT: deliberate peer corruption must be detected before state advancement.

## TOKEN_VAZIO gates

- `CI_EXECUTION = TOKEN_VAZIO_PROVIDER` until workflow evidence is observed for this branch/PR.
- `PHYSICAL_ARM32_EXECUTION = TOKEN_VAZIO_DEVICE`.
- `PHYSICAL_ARM64_EXECUTION = TOKEN_VAZIO_DEVICE`.
- `ARM32_NEON_RMR_SIMD_ROUTE = TOKEN_VAZIO_INTEGRATION`: `armeabi-v7a` compiler flags enable NEON, but the current RMR SIMD manifest group remains ARM64-scoped.
- `GPIO_PHYSICAL_PIN_ACCESS = TOKEN_VAZIO_DEVICE_AUTHORITY`: current hardware detection exposes word/stride hints; no claim is made that Android/no-root grants direct SoC GPIO register access.
- `CRC_SW_EQ_HW = TOKEN_VAZIO_EXECUTION` for this delta until same-parameter KAT runs on a CRC-capable target.
- `4096_STATE_PARALLEL_THROUGHPUT = TOKEN_VAZIO_MEASUREMENT`: bit-slicing/layout concepts do not themselves prove physical parallel throughput.

## F_ok / F_gap / F_next

`F_ok`: source-level surgical delta is isolated and reversible on a dedicated branch; no main rewrite and no auto-merge.

`F_gap`: CI, final-link output inspection, physical ARM receipts, ARM32 NEON route, GPIO authority and throughput remain evidence-gated.

`F_next_single`: open a draft PR and observe canonical CI/freestanding-link evidence for this exact head before any merge or runtime claim.

---

## APPEND — Second-pass symbol audit — 2026-09-09

This section is append-only and supersedes no earlier observation; it records findings discovered while reviewing the delta itself.

### Hidden freestanding symbol risks removed

1. C aggregate assignment in checkpoint/rollback paths could legally be lowered by the compiler to an external `memcpy`, even when source contains no libc call. Topological and vector state snapshots are now copied field-by-field.
2. Fixed-width CRC word loads no longer use `__builtin_memcpy`; ARM/x86 hardware paths assemble little-endian 32/64-bit words explicitly from bytes.
3. An isolated ARMv7 freestanding object check exposed `__aeabi_uldivmod` in the vector kernel from 64-bit division/modulo. The topological guard already had zero undefined symbols.
4. The vector geometry was algebraically reduced without changing its residue class: chord multiplication is bounded in `u32`; toroid node is evaluated term-by-term in `Z/1000Z`. The resulting ARMv7 vector object check had zero undefined symbols.

### Stale KAT provenance resolved

The historical smoke signature `0xe30aefc6` represents the pre-hotfix behavior where repeated contraction was allowed to reach `gap_q16=spiral_q16=0`. Commit `8f7ababa4174289a8b22e554223cd87c8372e977` replaced the mathematically dead modulo guard with rollback on zero convergence, but did not update `demo_cli/src/rmr_vector_field_selftest.c`.

Reproduction of both semantics shows:

- pre-hotfix terminal degeneration => `0xe30aefc6`;
- post-hotfix fail-closed rollback => `0xb1a90198`.

The selftest KAT is therefore updated to `0xb1a90198`, with the causal commit recorded beside the assertion. Base geometry remains unchanged for index 0: `n=56`, `mod42=14`, `arc=56`, `chord_q16=20388`, `h_q16=17656`, `toroid_node=983`, `gap_q16=spiral_q16=23943` after seven correction steps.

### Evidence boundary for local checks

The ARMv7 symbol result is an isolated compiler/object-level reproduction of the materialized source logic under strict freestanding ARMv7 flags. It is useful engineering evidence but is not substituted for canonical repository CI, the repository's dedicated final-link probe, APK/NDK matrix evidence, or physical-device execution.

### Updated gaps

- `ARMV7_OBJECT_UNDEFINED_SYMBOLS = 0_OBSERVED_ISOLATED_REPRODUCTION`.
- `CANONICAL_FREESTANDING_FINAL_LINK = TOKEN_VAZIO_PROVIDER` until exact-head CI/link-probe evidence is retrieved.
- `APK_NDK_GRADLE_MATRIX = TOKEN_VAZIO_PROVIDER` for this exact head.
- all physical-device, GPIO authority, ARM32 RMR-NEON routing, CRC HW/SW on-device equivalence and throughput gates remain unchanged.

### Updated F_next_single

Observe the exact PR head through the canonical CI + dedicated `vectra_freestanding_link_probe`; merge remains forbidden until that evidence exists.
