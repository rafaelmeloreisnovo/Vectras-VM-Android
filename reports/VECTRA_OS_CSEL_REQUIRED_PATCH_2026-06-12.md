# Required CSEL Patch — 2026-06-12

Target file: `engine/rmr/include/rmr_vectra_os.h`
Target macro: `VOS_CSEL`

## Problem

The inspected macro does not use the complement of the full-width mask on the fallback side.

The intended identity is:

```text
mask = minus one when condition is true, otherwise zero
result = left value masked by mask OR right value masked by complement(mask)
```

## Required behavior

```text
condition false, left 10, right 20 -> 20
condition true, left 10, right 20 -> 10
condition false, left 0xAAAAAAAA, right 0x55555555 -> 0x55555555
condition true, left 0xAAAAAAAA, right 0x55555555 -> 0xAAAAAAAA
```

## Acceptance requirement

Do not mark VECTRA_OS G2 complete until this behavior is proven by an executable selftest wired into the host selftest path.

## TOKEN_VAZIO

```text
state = TOKEN_VAZIO protected
object = corrected CSEL implementation and executable proof
risk_if_invented = false VECTRA_OS G2 completion claim
next_measure = commit code change plus host selftest
```
