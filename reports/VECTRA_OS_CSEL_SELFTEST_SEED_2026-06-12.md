# VOS_CSEL Contract Selftest Seed — 2026-06-12

Status: markdown seed because connector safety blocked creation of the `.c` selftest file in this run.
Target future file: `demo_cli/src/rmr_vectra_os_csel_contract_selftest.c`

## Purpose

Prove the minimal VECTRA_OS G2 contract before broader G1-G8 claims.

The current inspected macro form uses the wrong right-side mask for `b`:

```c
((u32)((s32)((u32)(cond) != 0u)) & (u32)(b))
```

The documented identity requires:

```c
mask = -(cond != 0);
result = (a & mask) | (b & ~mask);
```

## Required replacement

```c
#define VOS_CSEL(cond, a, b) \
    ((__typeof__(a))(((u32)(-(s32)((u32)(cond) != 0u)) & (u32)(a)) | \
                     (~(u32)(-(s32)((u32)(cond) != 0u)) & (u32)(b))))
```

## Required executable selftest

```c
#include "rmr_vectra_os.h"
#include <stdio.h>

int main(void) {
  if (VOS_CSEL(0u, 10u, 20u) != 20u) return 1;
  if (VOS_CSEL(1u, 10u, 20u) != 10u) return 1;
  if (VOS_CSEL(0u, 0xAAAAAAAAu, 0x55555555u) != 0x55555555u) return 1;
  if (VOS_CSEL(1u, 0xAAAAAAAAu, 0x55555555u) != 0xAAAAAAAAu) return 1;
  printf("OK VOS_CSEL contract\n");
  return 0;
}
```

## TOKEN_VAZIO note

Until the C selftest is committed and wired into Makefile/CMake, this remains:

```text
state = TOKEN_VAZIO protected
object = executable VOS_CSEL proof
risk_if_invented = false G2 completion claim
next_measure = commit `.c` selftest + wire into host selftest target
```
