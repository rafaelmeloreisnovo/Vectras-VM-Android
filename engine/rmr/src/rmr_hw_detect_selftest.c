// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_hw_detect.h"
#include "rmr_unified_kernel.h"

#define STATIC_ASSERT(COND, NAME) typedef char static_assert_##NAME[(COND) ? 1 : -1]

STATIC_ASSERT(sizeof(((RmR_HW_Info *)0)->cache_hint_l4) == sizeof(u32), hw_l4_hint_width_must_match_u32);
STATIC_ASSERT(sizeof(((rmr_legacy_capabilities_t *)0)->cache_hint_l4) == sizeof(uint32_t), legacy_l4_hint_present);
STATIC_ASSERT(sizeof(((rmr_jni_capabilities_t *)0)->cache_hint_l4) == sizeof(uint32_t), jni_l4_hint_present);

int main(void) {
  RmR_HW_Info hw;

  hw.cache_hint_l4 = 0u;
  RmR_HW_Detect(&hw);

  if (hw.cache_hint_l1 == 0u) return 11;
  if (hw.cache_hint_l2 == 0u) return 12;
  if (hw.cache_hint_l3 == 0u) return 13;

  if (hw.arch == 0u) {
    if (hw.cache_hint_l4 != 0u) return 14;
  } else {
    if (hw.cache_hint_l4 > (128u * 1024u * 1024u)) return 15;
  }

  return 0;
}
