# Professional Tools Statistics Blocker V1

Status: **P0 / fail closed / claim_allowed=false**

## Conflict

`docs/benchmark/INDUSTRIAL_BENCHMARK_METHODS_V1.md` and `IndustrialStatistics` require one homogeneous repeated measurement series: same metric, workload, input and unit.

The current `ProfessionalToolsActivity` still contains a legacy analysis path that:

1. collects `rawValue` from all non-null `BenchmarkResult` objects into one array;
2. computes global mean/median/stddev/confidence interval over that heterogeneous vector;
3. computes a CV-derived `reproducibilityScore` from the same mixed vector;
4. returns 100% reproducibility when the mixed mean is zero;
5. uses thresholds 70/85/95 to help promote Industry/Academic/Scientific grade.

Those operations are incompatible with the merged industrial methodology contract because different benchmark metric IDs/workloads are different data-generating processes even when their backing `rawValue` happens to use elapsed nanoseconds.

## Required transition

The Professional Tools grade path must not produce reproducibility or scientific/academic/industry grade from a single heterogeneous benchmark sweep.

A future valid path may:

- collect repeated samples for **one metric/workload/input/unit**;
- summarize that series through `IndustrialStatistics`;
- keep each metric series separate;
- require provenance/environment/run identity before grade promotion;
- expose `NOT_MEASURED`, `BLOCKED`, `INVALIDATED` or equivalent when the repeated series does not exist.

## Automated guard

`tools/ci/validate_professional_tools_statistics_contract.py` fails if it sees known prohibited patterns:

- `Arrays.stream(results) -> rawValue -> toArray()` pooling;
- zero mean promoted to 100% reproducibility;
- legacy `reproducibilityScore` directly driving 70/85/95 grade thresholds;
- no `IndustrialStatistics` reference from `ProfessionalToolsActivity`.

The initial expected state of this branch is **FAIL**, because the blocker is intentionally exposed before any risky full-file rewrite.

A green result is only `PASS_LIMITED`: absence of those anti-patterns is necessary but is not itself proof of benchmark validity, reproducibility, certification or scientific grade.

## Closure contract

Close this blocker only when all are true:

- mixed-metric pooling is removed or quarantined from grade/reproducibility;
- one-sample input cannot create a reproducibility score;
- different metric IDs cannot enter the same statistical series;
- Professional Tools consumes homogeneous repeated-series data or refuses promotion;
- the guard executes and passes;
- the relevant Java/unit tests execute and pass on a real runner/runtime;
- receipts preserve source SHA, environment, commands, stdout/stderr and exit status.

Historical code/results remain append-only evidence and are not deleted.
