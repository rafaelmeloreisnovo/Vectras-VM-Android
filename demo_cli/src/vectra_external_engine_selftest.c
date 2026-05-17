#include "rmr_external_engine.h"
#include "bitraf.h"

#include <stdio.h>
#include <string.h>

static int bitomega_transition_test(void) {
  bitomega_node_t node = {BITOMEGA_ZERO, BITOMEGA_DIR_NONE, BITOMEGA_Q16_HALF, BITOMEGA_Q16_HALF};
  bitomega_ctx_t ctx = bitomega_ctx_default(7u);
  ctx.coherence_in = BITOMEGA_Q16_ONE;
  return RmR_External_RunBitOmegaStep(&node, &ctx) == 0 ? 0 : 1;
}

static int bitraf_roundtrip_test(void) {
  static const uint8_t payload[] = "vectra_rmr_roundtrip";
  uint64_t seed = 42u;
  uint64_t hash = bitraf_hash(payload, sizeof(payload), seed);
  int verify_ok = 0;
  if (RmR_External_RunBitRafVerify(payload, sizeof(payload), hash, seed, &verify_ok) != 0) return 1;
  return verify_ok ? 0 : 1;
}

static int zipraf_coherence_test(void) {
  static const uint8_t payload[] = {1u,2u,3u,4u,5u,6u};
  RmR_ZiprafInput req;
  RmR_ZiprafOutput out;
  req.seed = 11u;
  req.trajectory_id = 3u;
  req.invariant_mask = 0u;
  req.payload_ptr = payload;
  req.payload_len = sizeof(payload);
  return RmR_External_RunZipRaf(&req, &out);
}

static int policy_pipeline_replay_test(void) {
  RmR_PipelineConfig config;
  RmR_AuditSummary sum;
  memset(&config, 0, sizeof(config));
  config.chunk_size = 64u;
  config.mutation_stride = 0u;
  config.mutation_xor = 0u;
  config.triad.cpu_ok = 1u;
  config.triad.ram_ok = 1u;
  config.triad.disk_ok = 1u;
  return RmR_External_RunPolicyPipeline("bench/results/policy_in.bin",
                                        "bench/results/policy_out_ext.bin",
                                        "bench/results/policy_log_ext.log",
                                        &config,
                                        &sum);
}

int main(void) {
  RmR_HW_Info hw;
  RmR_LL_TunePlan tune;
  FILE *fixture = fopen("bench/results/policy_in.bin", "wb");
  uint8_t block[512];
  size_t i;
  if (!fixture) return 10;
  for (i = 0; i < sizeof(block); ++i) block[i] = (uint8_t)(i & 0xFFu);
  fwrite(block, 1u, sizeof(block), fixture);
  fclose(fixture);

  if (bitomega_transition_test() != 0) return 1;
  if (bitraf_roundtrip_test() != 0) return 2;
  if (zipraf_coherence_test() != 0) return 3;
  if (policy_pipeline_replay_test() != 0) return 4;
  if (RmR_External_DetectHardware(&hw) != 0) return 5;
  if (RmR_External_BuildTunePlan(&hw, &tune) != 0) return 6;

  printf("OK vectra_external_engine_selftest\n");
  return 0;
}
