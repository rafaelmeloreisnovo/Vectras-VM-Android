// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_torus_flow.h"

enum {
  RMR_TORUS_Q16_ONE = 65536u,
  RMR_TORUS_ALPHA_Q16 = 16384u,       /* 0.25 */
  RMR_TORUS_INV_ALPHA_Q16 = 49152u,   /* 0.75 */
  RMR_TORUS_PHI_SIGMA_Q16 = 91830u    /* 1.401222 */
};

static u32 rmr_torus_q16_mul(u32 a, u32 b) {
  return (u32)(((u64)a * (u64)b) >> 16);
}

void RmR_TorusFlow_Init(RmR_TorusFlowState *state, u32 seed) {
  if (!state) return;
  state->seed = (seed == 0u) ? 0x963u : seed;
  state->steps_done = 0u;
  for (u32 i = 0; i < RMR_TORUS_FLOW_LEN; ++i) {
    state->prev[i] = RMR_TORUS_Q16_ONE;
    state->input[i] = (u32)((((u64)i) << 16) / RMR_TORUS_FLOW_LEN);
    state->output[i] = 0u;
  }
}

void RmR_TorusFlow_InjectGrammar(RmR_TorusFlowState *state, u32 salt) {
  if (!state) return;
  u32 seed = state->seed ^ (salt * 0x9E3779B9u);
  for (u32 i = 0; i < RMR_TORUS_FLOW_LEN; ++i) {
    seed = (seed * 1103515245u) + 12345u;
    state->input[i] = (seed & 0xFFFFu);
  }
  state->seed = seed;
}

void RmR_TorusFlow_Step(RmR_TorusFlowState *state) {
  if (!state) return;
  const u32 gain_q16 = rmr_torus_q16_mul(RMR_TORUS_PHI_SIGMA_Q16, RMR_TORUS_ALPHA_Q16);
  u32 seed = state->seed;
  for (u32 i = 0; i < RMR_TORUS_FLOW_LEN; ++i) {
    u32 jitter = seed ^ (i * 0x9E3779B9u);
    u32 in_dyn = state->input[i] ^ (jitter & 0x0FFFu);
    u32 prev_mix = rmr_torus_q16_mul(state->prev[i], RMR_TORUS_INV_ALPHA_Q16);
    u32 in_mix = rmr_torus_q16_mul(in_dyn, gain_q16);
    state->output[i] = prev_mix + in_mix;
    state->prev[i] = state->output[i];
    seed = (seed * 1664525u) + 1013904223u;
  }
  state->seed = seed;
  state->steps_done += 1u;
}

u32 RmR_TorusFlow_Checksum(const RmR_TorusFlowState *state) {
  if (!state) return 0u;
  u32 chk = state->steps_done * 0xA5A5A5A5u;
  for (u32 i = 0; i < RMR_TORUS_FLOW_LEN; ++i) {
    chk ^= (state->output[i] + (i * 17u));
    chk = (chk << 3) | (chk >> 29);
  }
  return chk;
}

u32 RmR_TorusFlow_RunDeterministic(u32 seed, u32 steps) {
  RmR_TorusFlowState state;
  RmR_TorusFlow_Init(&state, seed);
  for (u32 step = 0; step < steps; ++step) {
    RmR_TorusFlow_InjectGrammar(&state, step + 1u);
    RmR_TorusFlow_Step(&state);
  }
  return RmR_TorusFlow_Checksum(&state);
}
