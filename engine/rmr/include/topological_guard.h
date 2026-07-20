// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_TOPOLOGICAL_GUARD_H
#define RMR_TOPOLOGICAL_GUARD_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
  RMR_TOPO_ARCH_UNKNOWN = 0,
  RMR_TOPO_ARCH_ARM32 = 32,
  RMR_TOPO_ARCH_ARM64 = 64
} rmr_arch_t;

typedef struct {
  uint64_t cycles;
  uint64_t connectivity;
  uint64_t entropy;
  uint64_t topo_hash;
} rmr_topo_state_t;

typedef struct {
  rmr_topo_state_t current;
  rmr_topo_state_t checkpoint;
  uint32_t watchdog_limit;
  uint32_t watchdog_count;
  uint32_t rollback_count;
  uint8_t failsafe_triggered;
  uint8_t arch;
} rmr_topo_guard_t;

void rmr_topo_guard_init(rmr_topo_guard_t *guard, uint32_t watchdog_limit);
void rmr_topo_guard_checkpoint(rmr_topo_guard_t *guard);
int rmr_topo_guard_step(rmr_topo_guard_t *guard, const uint8_t *bytes, uint32_t len);
void rmr_topo_guard_rollback(rmr_topo_guard_t *guard);
rmr_arch_t rmr_detect_arch(void);

#ifdef __cplusplus
}
#endif

#endif
