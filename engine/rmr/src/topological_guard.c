// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "topological_guard.h"

#include <string.h>

static uint64_t mix_hash(uint64_t h, uint64_t x) {
  h ^= x;
  h = (h << 13) | (h >> (64 - 13));
  h += 0x9e3779b97f4a7c15ULL;
  return h;
}

static uint64_t count_transitions(const uint8_t *bytes, uint32_t len) {
  if (!bytes || len <= 1u) return 0u;
  uint64_t transitions = 0u;
  for (uint32_t i = 1; i < len; ++i) transitions += (bytes[i] != bytes[i - 1]);
  return transitions;
}

rmr_arch_t rmr_detect_arch(void) {
#if defined(__aarch64__)
  return RMR_TOPO_ARCH_ARM64;
#elif defined(__arm__)
  return RMR_TOPO_ARCH_ARM32;
#else
  return RMR_TOPO_ARCH_UNKNOWN;
#endif
}

void rmr_topo_guard_init(rmr_topo_guard_t *guard, uint32_t watchdog_limit) {
  if (!guard) return;
  memset(guard, 0, sizeof(*guard));
  guard->arch = (uint8_t)rmr_detect_arch();
  guard->watchdog_limit = watchdog_limit ? watchdog_limit : 32u;
}

void rmr_topo_guard_checkpoint(rmr_topo_guard_t *guard) {
  if (!guard) return;
  guard->checkpoint = guard->current;
}

void rmr_topo_guard_rollback(rmr_topo_guard_t *guard) {
  if (!guard) return;
  guard->current = guard->checkpoint;
  guard->rollback_count++;
  guard->failsafe_triggered = 1u;
  guard->watchdog_count = 0u;
}

int rmr_topo_guard_step(rmr_topo_guard_t *guard, const uint8_t *bytes, uint32_t len) {
  if (!guard || (!bytes && len > 0u)) return -1;

  const uint64_t transitions = count_transitions(bytes, len);
  uint64_t unique_acc = 0u;
  for (uint32_t i = 0; i < len; ++i) unique_acc ^= (uint64_t)bytes[i] << ((i & 7u) * 8u);

  guard->current.cycles += (transitions & 0x3fu);
  guard->current.connectivity = (guard->current.connectivity * 3u + transitions + 1u) >> 1;
  guard->current.entropy += ((unique_acc & 0xffu) + transitions);
  guard->current.topo_hash = mix_hash(guard->current.topo_hash, unique_acc ^ transitions);

  if (guard->current.connectivity == 0u || guard->current.entropy > (1ULL << 52)) {
    rmr_topo_guard_rollback(guard);
    return 1;
  }
  guard->watchdog_count++;
  if (guard->watchdog_count >= guard->watchdog_limit) {
    rmr_topo_guard_rollback(guard);
    return 2;
  }
  return 0;
}
