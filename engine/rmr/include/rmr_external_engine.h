// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_EXTERNAL_ENGINE_H
#define RMR_EXTERNAL_ENGINE_H

#include <stddef.h>
#include <stdint.h>

#include "bitomega.h"
#include "bitraf.h"
#include "rmr_hw_detect.h"
#include "rmr_ll_tuning.h"
#include "rmr_policy_kernel.h"
#include "rmr_unified_kernel.h"
#include "rmr_zipraf_core.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  const char *event;
  uint64_t offset;
  uint32_t size;
  uint32_t mem_tier;
  uint32_t route_id;
  uint32_t crc32c;
  uint64_t hash64;
  uint32_t entropy_milli;
  uint32_t math_signature;
  uint64_t stage_signature;
  uint32_t decision_mode;
  uint32_t recompute_skipped;
  uint32_t verify_ok;
} RmR_ExternalStatePromotionEvent;

int RmR_External_DetectHardware(RmR_HW_Info *out_hw);
int RmR_External_BuildTunePlan(const RmR_HW_Info *hw, RmR_LL_TunePlan *out_plan);
int RmR_External_RunPolicyPipeline(const char *input_path,
                                   const char *output_path,
                                   const char *audit_log_path,
                                   const RmR_PipelineConfig *config,
                                   RmR_AuditSummary *out_summary);
int RmR_External_RunZipRaf(const RmR_ZiprafInput *in, RmR_ZiprafOutput *out);
int RmR_External_RunBitRafVerify(const uint8_t *data,
                                 size_t len,
                                 uint64_t expected_hash,
                                 uint64_t seed,
                                 int *out_verify_ok);
int RmR_External_RunBitOmegaStep(bitomega_node_t *node, const bitomega_ctx_t *ctx);
int RmR_External_WriteStatePromotionReport(const char *report_path,
                                           const RmR_ExternalStatePromotionEvent *event);

#ifdef __cplusplus
}
#endif

#endif
