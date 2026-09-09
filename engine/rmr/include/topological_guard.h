// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_TOPOLOGICAL_GUARD_H
#define RMR_TOPOLOGICAL_GUARD_H

#include "rmr_types.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_TOPO_ARCH_UNKNOWN = 0,
  RMR_TOPO_ARCH_ARM32 = 32,
  RMR_TOPO_ARCH_ARM64 = 64
} rmr_arch_t;

typedef struct {
  u64 cycles;
  u64 connectivity;
  u64 entropy;
  u64 topo_hash;
} rmr_topo_state_t;

typedef struct {
  rmr_topo_state_t current;
  rmr_topo_state_t checkpoint;
  u32 watchdog_limit;
  u32 watchdog_count;
  /* Complement-coded peer: watchdog_peer == ~watchdog_count.
   * A single-counter corruption therefore fails closed before state advances. */
  u32 watchdog_peer;
  u32 rollback_count;
  u8 failsafe_triggered;
  u8 arch;
} rmr_topo_guard_t;

void rmr_topo_guard_init(rmr_topo_guard_t *guard, u32 watchdog_limit);
void rmr_topo_guard_checkpoint(rmr_topo_guard_t *guard);
int rmr_topo_guard_step(rmr_topo_guard_t *guard, const u8 *bytes, u32 len);
void rmr_topo_guard_rollback(rmr_topo_guard_t *guard);
rmr_arch_t rmr_detect_arch(void);

#ifdef __cplusplus
}
#endif

#endif
