// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_TORUS_FLOW_H
#define RMR_TORUS_FLOW_H

#include "rmr_types.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_TORUS_FLOW_LEN 128u

typedef struct {
  u32 prev[RMR_TORUS_FLOW_LEN];
  u32 input[RMR_TORUS_FLOW_LEN];
  u32 output[RMR_TORUS_FLOW_LEN];
  u32 seed;
  u32 steps_done;
} RmR_TorusFlowState;

void RmR_TorusFlow_Init(RmR_TorusFlowState *state, u32 seed);
void RmR_TorusFlow_InjectGrammar(RmR_TorusFlowState *state, u32 salt);
void RmR_TorusFlow_Step(RmR_TorusFlowState *state);
u32 RmR_TorusFlow_Checksum(const RmR_TorusFlowState *state);
u32 RmR_TorusFlow_RunDeterministic(u32 seed, u32 steps);

#ifdef __cplusplus
}
#endif

#endif
