# VECTRA Ω — x86-64 standalone CRC32 benchmark

**Status:** `IMPLEMENTED_UNTESTED` (at source creation; update only from exact CI execution receipt).  
**Source:** the user's NASM snippet dated 2026-11-03; this file documents a **revision** of that source, not an independently authored performance finding.  
**Ownership:** `rafaelmeloreisnovo/Vectras-VM-Android`, standalone host experiment only.  
**Entry:** [`../benchmark_x86_64_fixed.asm`](../benchmark_x86_64_fixed.asm).  
**Gate:** [`.github/workflows/vectra-host-asm-crc32.yml`](../.github/workflows/vectra-host-asm-crc32.yml).  
**Parent HEAD read:** `9468ace1ca7a851377ba71565f2fd6f05c47c01c` (`master`, observed 2026-11-03).  
**Federated Mapa route:** `TOKEN_VAZIO` (not required to execute this local host-only diagnostic).  
**Android VM/runtime/guest boot:** `NOT_RUN`; no performance or compatibility claim for ARM/Termux/Android.

## Compile and run on Linux x86-64

Requires NASM and GNU `ld` only at build time; the executable uses Linux syscalls directly and no libc/malloc.

```sh
nasm -f elf64 benchmark_x86_64_fixed.asm -o benchmark_x86_64_fixed.o
ld -e main -z noexecstack benchmark_x86_64_fixed.o -o benchmark_x86_64_fixed
./benchmark_x86_64_fixed
```

`Samples: 56` is the expected count. Numerical results are **not** fixed: they depend on processor, hypervisor, scheduling, clock behavior, kernel and host load.

## Measurement contract and limitations

- The benchmark runs a software CRC32/IEEE over 4096 bytes per sample. Before collection, it checks `CRC32("123456789") = 0xCBF43926` and fails with exit code 2 if wrong.
- The program records elapsed TSC ticks (`LFENCE; RDTSC` at start/end) **for each CRC32 call**, not a list of checksum integers. Mixing follows each sample *outside* the timed interval.
- An elapsed value greater than `0xFFFFFFFF` is rejected with exit code 3 rather than silently truncated.
- Reported min/max/mean are unsigned 32-bit samples and a 64-bit sum. Population variance is evaluated from a 128-bit numerator `N*sum(x²)−sum(x)²`; stddev is integer `floor(sqrt(floor(variance)))`.
- Checksum XOR is a lightweight diagnostic, **not** an authentication signature. The CRC test vector verifies the algorithm; the XOR is not an independent reference for every mutated buffer.
- CPUID AVX2 is printed only if the CPU flags and OS XCR0 show an enabled YMM state; **AVX2 is not executed by the CRC32 hot path**. SSE4.2 and POPCNT are reported only; no SIMD speedup is claimed.
- The measurement includes procedure-call and barrier overhead, does not calibrate empty-loop overhead, pin CPU affinity, control frequency or subtract interrupts, and does not report cycles per byte normalized to a physical host. A pass is a diagnostic sanity check, **not an industrial-grade hardware benchmark result**.
- This ELF64 Linux experiment is separate from Vectras Android/NDK/JNI and cannot be interpreted as an Android/ARM performance or VM execution test.

## Static gates and evidence boundary

1. NASM assembly/link: `TOKEN_VAZIO` until exact CI run or local host build.
2. IEEE CRC32 vector at runtime: code gate added; `NOT_RUN` until executable receipt.
3. 56 measured cycles and integer summary: code gate added; `NOT_RUN` until executable receipt.
4. 128-bit variance identity: `PASS` in a Python arithmetic model over 3,500 deterministic vectors on 2026-11-03; this does **not** validate the emitted machine code.
5. Integer square-root recurrence: `PASS` in a Python model over 20,000 integer vectors on 2026-11-03; this does **not** validate NASM execution.
6. Full measured cycles, target CPU and OS configuration: `TOKEN_VAZIO` before CI execution.

## μWRITE append-only provenance receipt

`VECTRA-CRC32-ASM-20261103-01 | 2026-11-03 | user-provided code (conversation) | parent:9468ace1ca7a851377ba71565f2fd6f05c47c01c | kind:host-benchmark-revision | Δ:repair entry/syscalls, CPUID, 56 cycle samples, unsigned stats, exact 128-bit variance, IEEE test gate | routes:P/E/I (local), others TOKEN_VAZIO | evidence:model 3500+20000 PASS; executable NOT_RUN | gap:NASM/CI/device receipts | next:run isolated host CI and bind head SHA; no release gate promotion`.

Corrections supersede the user-supplied example as an *implementation proposal*, without overwriting existing application/kernel contracts. Source, artifact, execution, evidence and claims remain distinct. Rollback: close the PR or revert its isolated file additions. Do not change `PROJECT_STATE.md` or claim a full Vectras release gate from this standalone host check.
