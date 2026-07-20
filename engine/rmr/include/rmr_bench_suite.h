// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* rmr_bench_suite.h - suite industrial (50 testes) */
#ifndef RMR_BENCH_SUITE_H
#define RMR_BENCH_SUITE_H

#include "rmr_types.h"

#define RMR_BENCH_COUNT 50u

/*
 * Canonical bench score formula (v1):
 *   score = ((((u64)ops) << 8) / cycles) ^ checksum
 * Special case for cycles == 0:
 *   score = checksum ^ ops
 */
#define RMR_BENCH_SCORE_FORMULA_ID "rmr_score_v1_ops_shift8_div_cycles_xor_checksum"

typedef struct {
  u32 score;
  u32 variance;
  u32 error_margin;
  u32 stage_seed;
  u32 tune_plan;
  u32 path_id;
  u32 output_checksum;
  u64 stage_signature;
} RmR_Bench_Metric;

typedef struct {
  RmR_Bench_Metric metric[RMR_BENCH_COUNT];
  u32 total_score;
  u32 total_error;
  u64 exec_signature;
} RmR_Bench_SuiteResult;

typedef struct {
  u32 budget_cycles;
  u32 max_iters;
  u32 stride_bytes;
  u32 matrix_n;
} RmR_Bench_Config;

void RmR_BenchSuite_Run(const RmR_Bench_Config *cfg, RmR_Bench_SuiteResult *out);

#endif
