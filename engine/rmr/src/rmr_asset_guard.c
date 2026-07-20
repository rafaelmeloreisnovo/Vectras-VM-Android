// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_asset_guard.h"

static uint32_t rmr_asset_mask(uint32_t predicate) {
  return 0u - (uint32_t)(predicate != 0u);
}

static uint32_t rmr_asset_select(uint32_t mask, uint32_t yes_value, uint32_t no_value) {
  return (yes_value & mask) | (no_value & ~mask);
}

static uint32_t rmr_asset_rotl32(uint32_t v, uint32_t n) {
  n &= 31u;
  return (v << n) | (v >> ((32u - n) & 31u));
}

static uint32_t rmr_asset_popcount32(uint32_t v) {
  uint32_t c = 0u;
  while (v != 0u) {
    c += v & 1u;
    v >>= 1u;
  }
  return c;
}

static uint32_t rmr_asset_arch_caps(void) {
  uint32_t caps = RMR_ASSET_CAP_GENERIC_U32;
#if defined(__aarch64__)
  caps |= RMR_ASSET_CAP_ARM64_U32;
#elif defined(__arm__)
  caps |= RMR_ASSET_CAP_ARM32_U32;
#elif defined(__x86_64__)
  caps |= RMR_ASSET_CAP_X86_64_U32;
#elif defined(__riscv) && (__riscv_xlen == 64)
  caps |= RMR_ASSET_CAP_RISCV64_U32;
#endif
  return caps;
}

static uint32_t rmr_asset_effective_probe(uint32_t *base_out, uint32_t *span_out) {
  uint32_t base = 0u;
  uint32_t span = 0u;
  uint32_t caps = RMR_ASSET_CAP_GENERIC_U32;

#if defined(__aarch64__)
  {
    register uint64_t r5 __asm__("x5") = 5u;
    register uint64_t r6 __asm__("x6") = 6u;
    register uint64_t r7 __asm__("x7") = 7u;
    __asm__ __volatile__(
      "add x5, x5, #0\n"
      "add x6, x6, #0\n"
      "add x7, x7, #0\n"
      : "+r"(r5), "+r"(r6), "+r"(r7)
      :
      : "memory");
    base = (uint32_t)r5;
    span = (uint32_t)((r6 == (r5 + 1u)) & (r7 == (r6 + 1u))) * 3u;
    caps |= RMR_ASSET_CAP_INLINE_ASM_U32 | RMR_ASSET_CAP_ARM64_U32;
  }
#elif defined(__arm__)
  {
    uint32_t r5 = 5u;
    uint32_t r6 = 6u;
    uint32_t r7 = 7u;
    __asm__ __volatile__(
      "add %0, %0, #0\n"
      "add %1, %1, #0\n"
      "add %2, %2, #0\n"
      : "+r"(r5), "+r"(r6), "+r"(r7)
      :
      : "memory");
    base = r5;
    span = (uint32_t)((r6 == (r5 + 1u)) & (r7 == (r6 + 1u))) * 3u;
    caps |= RMR_ASSET_CAP_INLINE_ASM_U32 | RMR_ASSET_CAP_ARM32_U32;
  }
#elif defined(__x86_64__)
  {
    uint64_t r8v = 5u;
    uint64_t r9v = 6u;
    uint64_t r10v = 7u;
    __asm__ __volatile__(
      "addq $0, %0\n"
      "addq $0, %1\n"
      "addq $0, %2\n"
      : "+r"(r8v), "+r"(r9v), "+r"(r10v)
      :
      : "memory");
    base = (uint32_t)r8v;
    span = (uint32_t)((r9v == (r8v + 1u)) & (r10v == (r9v + 1u))) * 3u;
    caps |= RMR_ASSET_CAP_INLINE_ASM_U32 | RMR_ASSET_CAP_X86_64_U32;
  }
#elif defined(__riscv) && (__riscv_xlen == 64)
  {
    uintptr_t t0 = 5u;
    uintptr_t t1 = 6u;
    uintptr_t t2 = 7u;
    __asm__ __volatile__(
      "addi %0, %0, 0\n"
      "addi %1, %1, 0\n"
      "addi %2, %2, 0\n"
      : "+r"(t0), "+r"(t1), "+r"(t2)
      :
      : "memory");
    base = (uint32_t)t0;
    span = (uint32_t)((t1 == (t0 + 1u)) & (t2 == (t1 + 1u))) * 3u;
    caps |= RMR_ASSET_CAP_INLINE_ASM_U32 | RMR_ASSET_CAP_RISCV64_U32;
  }
#endif

  caps |= rmr_asset_select(rmr_asset_mask(span == 3u), RMR_ASSET_CAP_LINEAR_REG_U32, 0u);
  if (base_out) *base_out = base;
  if (span_out) *span_out = span;
  return caps;
}

static RmR_AssetGuardState rmr_asset_mix_state(RmR_AssetGuardState state, const uint8_t *data, size_t len) {
  size_t i = 0u;
  uint32_t transitions = 0u;
  uint32_t unique = 0u;
  uint32_t prev = 0u;

  while (i < len) {
    uint32_t b = (uint32_t)data[i];
    uint32_t lane = b << ((uint32_t)(i & 3u) * 8u);
    uint32_t not_first = rmr_asset_mask(i != 0u);
    transitions += (uint32_t)(((prev ^ b) != 0u) & (not_first & 1u));
    unique ^= (1u << (b & 31u));
    state.acc ^= lane;
    state.acc = rmr_asset_rotl32(state.acc + 0x9E3779B9u + (uint32_t)i, 5u);
    prev = b;
    ++i;
  }

  state.len_seen += (uint32_t)(len & 0xFFFFFFFFu);
  state.entropy_milli = (rmr_asset_popcount32(unique) * 6000u) / 32u;
  if (len > 1u) state.entropy_milli += (transitions * 2000u) / (uint32_t)(len - 1u);
  state.phase = (state.phase + 1u) % 42u;
  return state;
}

void RmR_AssetGuard_Init(RmR_AssetGuard *guard, uint32_t required_rights, uint32_t watchdog_limit) {
  if (!guard) return;
  guard->current.acc = 0u;
  guard->current.len_seen = 0u;
  guard->current.entropy_milli = 0u;
  guard->current.phase = 0u;
  guard->checkpoint = guard->current;
  guard->required_rights = required_rights ? required_rights : RMR_ASSET_RIGHT_READ_U32;
  guard->denied_rights = RMR_ASSET_RIGHT_ZERO_THRUST_U32;
  guard->capability_bits = rmr_asset_arch_caps();
  guard->effective_bits = 0u;
  guard->reg_linear_base = 0u;
  guard->reg_linear_span = 0u;
  guard->watchdog_limit = watchdog_limit ? watchdog_limit : 42u;
  guard->watchdog_count = 0u;
  guard->rollback_count = 0u;
  guard->failsafe_count = 0u;
  guard->status = RMR_ASSET_STATUS_OK_U32;
}

void RmR_AssetGuard_Checkpoint(RmR_AssetGuard *guard) {
  if (!guard) return;
  guard->checkpoint = guard->current;
}

void RmR_AssetGuard_Rollback(RmR_AssetGuard *guard, uint32_t reason_bits) {
  if (!guard) return;
  guard->current = guard->checkpoint;
  guard->watchdog_count = 0u;
  guard->rollback_count += 1u;
  guard->failsafe_count += 1u;
  guard->status = RMR_ASSET_STATUS_FAILSAFE_U32 | RMR_ASSET_STATUS_ROLLBACK_U32 | reason_bits;
}

uint32_t RmR_AssetGuard_ProbeInlineAsm(RmR_AssetGuard *guard) {
  uint32_t base = 0u;
  uint32_t span = 0u;
  uint32_t caps = rmr_asset_effective_probe(&base, &span);
  if (guard) {
    guard->effective_bits = caps;
    guard->capability_bits |= caps;
    guard->reg_linear_base = base;
    guard->reg_linear_span = span;
  }
  return caps;
}

uint32_t RmR_AssetGuard_Enter(RmR_AssetGuard *guard, const uint8_t *data, size_t len, uint32_t granted_rights) {
  if (!guard) return RMR_ASSET_STATUS_ARG_U32;
  if (!data && len != 0u) {
    RmR_AssetGuard_Rollback(guard, RMR_ASSET_STATUS_ARG_U32);
    return guard->status;
  }

  guard->status = RMR_ASSET_STATUS_OK_U32;
  guard->capability_bits |= rmr_asset_arch_caps();
  RmR_AssetGuard_ProbeInlineAsm(guard);

  uint32_t missing = guard->required_rights & ~granted_rights;
  uint32_t denied = guard->denied_rights & granted_rights;
  if (missing != 0u) {
    RmR_AssetGuard_Rollback(guard, RMR_ASSET_STATUS_RIGHTS_U32);
    return guard->status;
  }
  if (denied != 0u) {
    RmR_AssetGuard_Rollback(guard, RMR_ASSET_STATUS_ZERO_THRUST_U32);
    return guard->status;
  }

  guard->current = rmr_asset_mix_state(guard->current, data, len);
  guard->watchdog_count += 1u;
  if (guard->watchdog_count >= guard->watchdog_limit) {
    RmR_AssetGuard_Rollback(guard, RMR_ASSET_STATUS_WATCHDOG_U32);
    return guard->status;
  }

  guard->checkpoint = guard->current;
  return guard->status;
}
