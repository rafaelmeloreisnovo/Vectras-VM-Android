// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "topological_guard.h"

#define RMR_TOPO_GOLD64 0x9e3779b97f4a7c15ULL
#define RMR_TOPO_ENTROPY_LIMIT (1ULL << 52)

static u64 rmr_topo_mix_hash(u64 h, u64 x) {
  h ^= x;
  h = (h << 13) | (h >> 51);
  h += RMR_TOPO_GOLD64;
  return h;
}

/* Single forward pass: transition count and 8-lane byte fold share one load. */
static void rmr_topo_scan(const u8 *bytes, u32 len, u64 *transitions_out, u64 *fold_out) {
  u64 transitions = 0u;
  u64 fold = 0u;

  if (bytes && len != 0u) {
    u8 prev = bytes[0];
    fold ^= (u64)prev;
    for (u32 i = 1u; i < len; ++i) {
      const u8 cur = bytes[i];
      transitions += (u64)(cur != prev);
      fold ^= (u64)cur << ((i & 7u) * 8u);
      prev = cur;
    }
  }

  *transitions_out = transitions;
  *fold_out = fold;
}

static u32 rmr_topo_watchdogs_coherent(const rmr_topo_guard_t *guard) {
  return (u32)(guard->watchdog_peer == ~guard->watchdog_count);
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

void rmr_topo_guard_init(rmr_topo_guard_t *guard, u32 watchdog_limit) {
  if (!guard) return;

  guard->current.cycles = 0u;
  guard->current.connectivity = 0u;
  guard->current.entropy = 0u;
  guard->current.topo_hash = 0u;
  guard->checkpoint = guard->current;
  guard->watchdog_limit = watchdog_limit ? watchdog_limit : 32u;
  guard->watchdog_count = 0u;
  guard->watchdog_peer = ~0u;
  guard->rollback_count = 0u;
  guard->failsafe_triggered = 0u;
  guard->arch = (u8)rmr_detect_arch();
}

void rmr_topo_guard_checkpoint(rmr_topo_guard_t *guard) {
  if (!guard) return;
  guard->checkpoint = guard->current;
}

void rmr_topo_guard_rollback(rmr_topo_guard_t *guard) {
  if (!guard) return;
  guard->current = guard->checkpoint;
  guard->rollback_count += 1u;
  guard->failsafe_triggered = 1u;
  guard->watchdog_count = 0u;
  guard->watchdog_peer = ~0u;
}

int rmr_topo_guard_step(rmr_topo_guard_t *guard, const u8 *bytes, u32 len) {
  if (!guard || (!bytes && len > 0u)) return -1;

  /* Watchdog A and its complement-coded peer must agree before any mutation. */
  if (!rmr_topo_watchdogs_coherent(guard)) {
    rmr_topo_guard_rollback(guard);
    return 3;
  }

  u64 transitions = 0u;
  u64 unique_acc = 0u;
  rmr_topo_scan(bytes, len, &transitions, &unique_acc);

  guard->current.cycles += (transitions & 0x3fu);
  guard->current.connectivity = (guard->current.connectivity * 3u + transitions + 1u) >> 1;
  guard->current.entropy += ((unique_acc & 0xffu) + transitions);
  guard->current.topo_hash = rmr_topo_mix_hash(guard->current.topo_hash, unique_acc ^ transitions);

  if (guard->current.connectivity == 0u || guard->current.entropy > RMR_TOPO_ENTROPY_LIMIT) {
    rmr_topo_guard_rollback(guard);
    return 1;
  }

  guard->watchdog_count += 1u;
  guard->watchdog_peer = ~guard->watchdog_count;
  if (!rmr_topo_watchdogs_coherent(guard)) {
    rmr_topo_guard_rollback(guard);
    return 3;
  }
  if (guard->watchdog_count >= guard->watchdog_limit) {
    rmr_topo_guard_rollback(guard);
    return 2;
  }
  return 0;
}
