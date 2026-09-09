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
