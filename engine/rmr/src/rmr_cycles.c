// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* rmr_cycles.c - leitura de ciclos low-level (baremetal) */
#include "rmr_cycles.h"
#include "rmr_ll_ops.h"

u64 RmR_ReadCycles(void){
  return (u64)RmR_LL_ReadCycles();
}
