// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_external_engine.h"

#include <stdio.h>

int RmR_External_DetectHardware(RmR_HW_Info *out_hw) {
  if (!out_hw) return -1;
  RmR_HW_Detect(out_hw);
  return 0;
}

int RmR_External_BuildTunePlan(const RmR_HW_Info *hw, RmR_LL_TunePlan *out_plan) {
  if (!out_plan) return -1;
  RmR_LL_ApplyTuneDefaults(hw, out_plan);
  return 0;
}

int RmR_External_RunPolicyPipeline(const char *input_path,
                                   const char *output_path,
                                   const char *audit_log_path,
                                   const RmR_PipelineConfig *config,
                                   RmR_AuditSummary *out_summary) {
  return RmR_RunPolicyPipeline(input_path, output_path, audit_log_path, config, out_summary);
}

int RmR_External_RunZipRaf(const RmR_ZiprafInput *in, RmR_ZiprafOutput *out) { return RmR_Zipraf_Execute(in, out); }

int RmR_External_RunBitRafVerify(const uint8_t *data,
                                 size_t len,
                                 uint64_t expected_hash,
                                 uint64_t seed,
                                 int *out_verify_ok) {
  int verify_ok;
  if (!out_verify_ok) return -1;
  verify_ok = bitraf_verify(data, len, expected_hash, seed);
  *out_verify_ok = verify_ok ? 1 : 0;
  return 0;
}

int RmR_External_RunBitOmegaStep(bitomega_node_t *node, const bitomega_ctx_t *ctx) {
  return (int)bitomega_transition(node, ctx);
}

int RmR_External_WriteStatePromotionReport(const char *report_path,
                                           const RmR_ExternalStatePromotionEvent *event) {
  FILE *f;
  if (!report_path || !event) return -1;
  f = fopen(report_path, "ab");
  if (!f) return -2;
  fprintf(f,
          "{\"event\":\"%s\",\"offset\":%llu,\"size\":%u,\"mem_tier\":%u,\"route_id\":%u,"
          "\"crc32c\":%u,\"hash64\":%llu,\"entropy_milli\":%u,\"math_signature\":%u,"
          "\"stage_signature\":%llu,\"decision_mode\":%u,\"recompute_skipped\":%u,\"verify_ok\":%u}\n",
          event->event ? event->event : "unknown",
          (unsigned long long)event->offset,
          event->size,
          event->mem_tier,
          event->route_id,
          event->crc32c,
          (unsigned long long)event->hash64,
          event->entropy_milli,
          event->math_signature,
          (unsigned long long)event->stage_signature,
          event->decision_mode,
          event->recompute_skipped,
          event->verify_ok);
  fclose(f);
  return 0;
}
