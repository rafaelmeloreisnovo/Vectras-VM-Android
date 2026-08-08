# Vectras / RAFCODEΦ Industrial Benchmark Methods V1

Status: **methodology contract — not certification**  
Claim gate: **fail closed**  
Primary rule: **never promote an observation beyond the evidence actually captured**.

## 1. Invariant of support

A performance claim is admissible only when the workload, input, metric definition, unit, execution route, artifact identity, environment and acceptance predicate refer to the same observation.

A collection of different benchmarks is not a repeated series. Even when every `rawValue` is elapsed nanoseconds, CPU add, memory copy, storage fsync and emulator event dispatch are different data-generating processes. Their values must not be pooled into one mean, standard deviation, confidence interval or reproducibility score.

User-space benchmarks observe the combined stack: silicon + firmware + kernel + scheduler + Android runtime + compiler output + linker + filesystem + thermal/power policy. They do **not** isolate silicon performance unless required counters and controls are directly available and recorded.

## 2. Evidence states

| State | Meaning |
|---|---|
| PASS | Required observation exists and satisfies its declared predicate. |
| FAIL | Observation exists and violates its predicate. |
| NOT_MEASURED | The procedure has not produced a measurement series or receipt. |
| UNAVAILABLE | The platform does not expose the required capability. |
| BLOCKED | A prerequisite prevents execution. |
| INVALIDATED | Execution happened, but provenance or interference makes interpretation unsafe. |
| OBSERVED_LIMITED | Direct evidence exists, but supports only a narrower claim. |

`TOKEN_VAZIO` may remain as a custody marker for truly absent evidence, but user-facing benchmark state should name the operational reason when it is known.

## 3. Seven production domains × seven controls

### A. CPU / instruction execution
1. Integer/bitwise throughput with declared operation count.
2. FP32/FP64 workload with exact operation definition and compiler flags.
3. NEON/SIMD versus scalar path on identical data.
4. Branch/control-flow workload with fixed distribution.
5. Single-thread versus multi-thread scaling.
6. Syscall transition cost measured separately from user-space compute.
7. ABI, binary hash and executed code path bound to the result.

### B. Memory hierarchy
1. Sequential read/write bandwidth by declared bytes touched.
2. Random access with fixed index sequence/seed.
3. Copy/fill route identity: native arena, JNI, NEON or Java fallback.
4. Working-set sweep across multiple footprints.
5. Stride sweep for locality sensitivity.
6. Warm/first-touch state reported separately.
7. Cache-line/cache-level values reported only when positively detected; `0` means unavailable, not a physical zero-sized cache line.

### C. Storage / durability
1. Exact fixture path and size.
2. Sequential read/write with bytes transferred.
3. Random 4 KiB I/O with operation count and deterministic seed.
4. Buffered and synchronized writes separated.
5. `fsync`/durability latency isolated.
6. Warm-cache/cache-unknown explicitly labelled; no invented cold-cache state.
7. Free-space, fixture integrity and cleanup outcomes preserved.

### D. Kernel / scheduler / concurrency
1. Clock source and timer-resolution/overhead check.
2. Scheduler/background interference snapshot.
3. Thread count, affinity availability and priority recorded.
4. Context-switch and synchronization workloads isolated.
5. GC/runtime interference recorded for Java-mediated paths.
6. Severe interference invalidates comparison instead of being averaged away.
7. Execution-governance limits, queue depth and rejection behavior retained.

### E. Virtualization / emulation
1. Native-host baseline separated from guest/emulated execution.
2. Guest architecture and acceleration mode recorded.
3. Emulated syscall transition isolated.
4. Memory-map and buffer-copy overhead isolated.
5. Timer/event-dispatch behavior measured separately.
6. State serialization workload defined by bytes/operations.
7. Host/guest/configuration hashes bound to the run.

### F. Sensors / edge timing
1. Sensor inventory separated from acquisition proof.
2. Requested sample period separated from observed timestamp interval.
3. Callback latency represented as a distribution, not a preset label.
4. Cancellation/timeout/error paths exercised.
5. Missing hardware is `UNAVAILABLE`, never numeric zero.
6. Framework-reported power is metadata, not measured energy.
7. Timestamp clock domain/provenance recorded.

### G. Integrity / build / provenance
1. APK/ELF/source commit hashes captured.
2. Compiler version and optimization flags captured.
3. CRC/hash work defined by byte count.
4. Expected checksum/sink protects against dead-code elimination.
5. Linker route and dependency contract inspected.
6. Receipt publication is atomic/fail-closed.
7. Claim scope never exceeds the strongest direct evidence.

## 4. Experimental workflow

1. Define one falsifiable metric claim and its physical/unit meaning.
2. Freeze source commit, build flags, workload version and artifact hashes.
3. Capture preflight: ABI, page size, CPU topology exposed to app, available RAM, free storage, battery/thermal state where available, clock route and execution policy.
4. Execute declared warm-up. Warm-up samples are not silently mixed into measured trials.
5. Repeat the **same** workload/input. Preserve every raw sample before summarization.
6. Capture post-run environment and interference diagnostics.
7. Publish a receipt binding raw samples, hashes, environment, exit state and interpretation boundary.

## 5. Statistical contract

For one homogeneous repeated series:

- report `n`;
- median and mean;
- sample standard deviation using `N-1` for `n > 1`;
- MAD and IQR when useful;
- 95% Student-t interval for a small approximately normal repeated series, or a declared bootstrap procedure when distributional assumptions are not justified;
- CV only for the same positive ratio-scale metric;
- outlier policy declared before interpretation; robust summaries preferred to arbitrary deletion.

Forbidden without a separately versioned normalization model:

- averaging different metric IDs/workloads to claim reproducibility;
- treating one sample as reproducible;
- returning “100% reproducible” because the mean is zero;
- combining MB/s, IOPS, ns/op, MFLOPS or unrelated elapsed times into a composite statistic;
- declaring cross-device comparability when workload/software route differs.

## 6. Current code audit invariant

The current `VectraBenchmark` correctly distinguishes many categories and formats engineering metrics from elapsed time; however the existing Professional Tools aggregate-analysis path historically builds one vector from every metric `rawValue` and derives a global mean/stddev/CI/CV-like reproducibility score. That aggregate is **not** a repeated-measurement reproducibility experiment.

This contract introduces `IndustrialStatistics`, whose API accepts one homogeneous sample series and deliberately provides no cross-metric reproducibility aggregator. Integration into any grade/certification-like UI must require repeated same-metric observations first.

## 7. Industrial report gate

A result can be described as industrial-quality evidence only when:

- provenance is complete;
- metric dimensionality is coherent;
- execution route is observed;
- repeated samples represent the same workload;
- interference is bounded or explicitly disclosed;
- statistical estimator matches the data-generating process;
- raw evidence is retained;
- invalidated/blocked states cannot be promoted;
- external standards are referenced as methodology guidance only unless an actual conformance assessment exists.

Relevant methodology references may include ISO/IEC 25010 quality characteristics, IEEE-style verification/test documentation, SPEC-style workload disclosure, NIST measurement principles and MLPerf-style reproducibility practices. This document claims no external certification.
