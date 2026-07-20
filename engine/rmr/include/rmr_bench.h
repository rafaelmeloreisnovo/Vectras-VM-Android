// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* rmr_bench.h - microbenchmarks determinísticos low-level */
#ifndef RMR_BENCH_H
#define RMR_BENCH_H

#include "rmr_types.h"

typedef struct {
  u32 alu;
  u32 mem;
  u32 branch;
  u32 matrix;
} RmR_Bench_Result;

void RmR_Bench_Run(u8 size, u8 shift, RmR_Bench_Result *out);

#endif
