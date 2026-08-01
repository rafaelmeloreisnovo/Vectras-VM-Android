// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_static_layout.h"

#include <stdint.h>

#define RMR_FNV1A64_OFFSET 14695981039346656037ULL
#define RMR_FNV1A64_PRIME 1099511628211ULL

static int rmr_is_power_of_two_u32(uint32_t value) {
  return value != 0u && (value & (value - 1u)) == 0u;
}

static int rmr_base_policy_valid(uint8_t value) {
  return value == RMR_LAYOUT_BASE_RELATIVE ||
         value == RMR_LAYOUT_BASE_FIXED_VIRTUAL ||
         value == RMR_LAYOUT_BASE_FIXED_PHYSICAL ||
         value == RMR_LAYOUT_BASE_TOKEN_VAZIO;
}

static int rmr_mobility_valid(uint8_t value) {
  return value == RMR_LAYOUT_MOVABLE_BASE ||
         value == RMR_LAYOUT_FIXED_OFFSET ||
         value == RMR_LAYOUT_PINNED_RUNTIME ||
         value == RMR_LAYOUT_REMAP_ONLY ||
         value == RMR_LAYOUT_PHYSICAL_FIXED ||
         value == RMR_LAYOUT_MOBILITY_TOKEN_VAZIO;
}

static int rmr_region_state_valid(uint8_t value) {
  return value == RMR_LAYOUT_REGION_ABSENT ||
         value == RMR_LAYOUT_REGION_EMPTY ||
         value == RMR_LAYOUT_REGION_PRESENT ||
         value == RMR_LAYOUT_REGION_FAULT ||
         value == RMR_LAYOUT_REGION_TOKEN_VAZIO;
}

static int rmr_ranges_overlap(const rmr_static_region_t *a,
                              const rmr_static_region_t *b) {
  uint64_t a_begin;
  uint64_t a_end;
  uint64_t b_begin;
  uint64_t b_end;

  if (a->size == 0u || b->size == 0u) return 0;
  a_begin = (uint64_t)a->offset;
  a_end = a_begin + (uint64_t)a->size;
  b_begin = (uint64_t)b->offset;
  b_end = b_begin + (uint64_t)b->size;
  return a_begin < b_end && b_begin < a_end;
}

uint64_t RmR_StaticLayout_PreserveFixedBits(uint64_t candidate,
                                           uint64_t fixed_mask,
                                           uint64_t fixed_value) {
  return (candidate & ~fixed_mask) | (fixed_value & fixed_mask);
}

int RmR_StaticLayout_Validate(const rmr_static_layout_manifest_t *manifest) {
  uint32_t i;
  uint32_t j;

  if (!manifest) return RMR_LAYOUT_ERR_ARG;
  if (manifest->abi_version != RMR_STATIC_LAYOUT_ABI_VERSION) {
    return RMR_LAYOUT_ERR_VERSION;
  }
  if (!rmr_is_power_of_two_u32(manifest->base_alignment)) {
    return RMR_LAYOUT_ERR_ALIGN;
  }
  if (!rmr_base_policy_valid(manifest->base_policy)) {
    return RMR_LAYOUT_ERR_POLICY;
  }
  if (manifest->region_count != 0u && !manifest->regions) {
    return RMR_LAYOUT_ERR_ARG;
  }

  for (i = 0u; i < manifest->region_count; ++i) {
    const rmr_static_region_t *region = &manifest->regions[i];
    uint64_t region_end = (uint64_t)region->offset + (uint64_t)region->size;

    if (!rmr_is_power_of_two_u32(region->alignment)) {
      return RMR_LAYOUT_ERR_ALIGN;
    }
    if ((region->offset & (region->alignment - 1u)) != 0u) {
      return RMR_LAYOUT_ERR_ALIGN;
    }
    if (region_end > (uint64_t)manifest->total_size) {
      return RMR_LAYOUT_ERR_BOUNDS;
    }
    if (!rmr_mobility_valid(region->mobility) ||
        !rmr_region_state_valid(region->semantic_state)) {
      return RMR_LAYOUT_ERR_STATE;
    }
    if ((region->semantic_state == RMR_LAYOUT_REGION_ABSENT ||
         region->semantic_state == RMR_LAYOUT_REGION_EMPTY) &&
        region->size != 0u) {
      return RMR_LAYOUT_ERR_STATE;
    }
    if (region->semantic_state == RMR_LAYOUT_REGION_PRESENT &&
        region->size == 0u) {
      return RMR_LAYOUT_ERR_STATE;
    }
    if ((region->flags & RMR_LAYOUT_FLAG_FIXED_OFFSET_BITS) != 0u &&
        RmR_StaticLayout_PreserveFixedBits((uint64_t)region->offset,
                                           region->fixed_offset_mask,
                                           region->fixed_offset_value) !=
            (uint64_t)region->offset) {
      return RMR_LAYOUT_ERR_POLICY;
    }

    for (j = i + 1u; j < manifest->region_count; ++j) {
      const rmr_static_region_t *other = &manifest->regions[j];
      if (region->region_id == other->region_id) {
        return RMR_LAYOUT_ERR_DUPLICATE;
      }
      if (rmr_ranges_overlap(region, other)) {
        return RMR_LAYOUT_ERR_OVERLAP;
      }
    }
  }

  return RMR_LAYOUT_OK;
}

static uint64_t rmr_fnv_feed_u64(uint64_t hash, uint64_t value) {
  uint32_t i;
  for (i = 0u; i < 8u; ++i) {
    hash ^= (uint8_t)(value & 0xffu);
    hash *= RMR_FNV1A64_PRIME;
    value >>= 8u;
  }
  return hash;
}

uint64_t RmR_StaticLayout_ManifestSignature(const rmr_static_layout_manifest_t *manifest) {
  uint64_t hash = RMR_FNV1A64_OFFSET;
  uint32_t i;

  if (RmR_StaticLayout_Validate(manifest) != RMR_LAYOUT_OK) return 0u;

  hash = rmr_fnv_feed_u64(hash, manifest->abi_version);
  hash = rmr_fnv_feed_u64(hash, manifest->layout_epoch);
  hash = rmr_fnv_feed_u64(hash, manifest->total_size);
  hash = rmr_fnv_feed_u64(hash, manifest->base_alignment);
  hash = rmr_fnv_feed_u64(hash, manifest->region_count);
  hash = rmr_fnv_feed_u64(hash, manifest->base_policy);

  for (i = 0u; i < manifest->region_count; ++i) {
    const rmr_static_region_t *region = &manifest->regions[i];
    hash = rmr_fnv_feed_u64(hash, region->region_id);
    hash = rmr_fnv_feed_u64(hash, region->offset);
    hash = rmr_fnv_feed_u64(hash, region->size);
    hash = rmr_fnv_feed_u64(hash, region->alignment);
    hash = rmr_fnv_feed_u64(hash, region->fixed_offset_mask);
    hash = rmr_fnv_feed_u64(hash, region->fixed_offset_value);
    hash = rmr_fnv_feed_u64(hash, region->mobility);
    hash = rmr_fnv_feed_u64(hash, region->semantic_state);
    hash = rmr_fnv_feed_u64(hash, region->flags);
  }

  return hash;
}

static int rmr_binding_base_valid(const rmr_static_layout_manifest_t *manifest,
                                  const void *base,
                                  uint32_t capacity) {
  uintptr_t address;
  if (!manifest) return RMR_LAYOUT_ERR_ARG;
  if (capacity < manifest->total_size) return RMR_LAYOUT_ERR_BOUNDS;
  if (manifest->total_size != 0u && !base) return RMR_LAYOUT_ERR_ARG;
  if (!base) return RMR_LAYOUT_OK;
  address = (uintptr_t)base;
  if ((address & (uintptr_t)(manifest->base_alignment - 1u)) != 0u) {
    return RMR_LAYOUT_ERR_ALIGN;
  }
  return RMR_LAYOUT_OK;
}

int RmR_StaticLayout_Bind(const rmr_static_layout_manifest_t *manifest,
                          void *base,
                          uint32_t capacity,
                          uint32_t mapping_epoch,
                          rmr_static_layout_binding_t *out_binding) {
  uint64_t signature;
  int rc;

  if (!out_binding) return RMR_LAYOUT_ERR_ARG;
  rc = RmR_StaticLayout_Validate(manifest);
  if (rc != RMR_LAYOUT_OK) return rc;
  rc = rmr_binding_base_valid(manifest, base, capacity);
  if (rc != RMR_LAYOUT_OK) return rc;

  signature = RmR_StaticLayout_ManifestSignature(manifest);
  if (signature == 0u) return RMR_LAYOUT_ERR_STATE;

  out_binding->base = (uint8_t *)base;
  out_binding->capacity = capacity;
  out_binding->mapping_epoch = mapping_epoch;
  out_binding->manifest_signature = signature;
  out_binding->manifest = manifest;
  return RMR_LAYOUT_OK;
}

static int rmr_layout_blocks_rebind(const rmr_static_layout_manifest_t *manifest) {
  uint32_t i;
  if (manifest->base_policy == RMR_LAYOUT_BASE_FIXED_VIRTUAL ||
      manifest->base_policy == RMR_LAYOUT_BASE_FIXED_PHYSICAL) {
    return 1;
  }
  for (i = 0u; i < manifest->region_count; ++i) {
    uint8_t mobility = manifest->regions[i].mobility;
    if (mobility == RMR_LAYOUT_PINNED_RUNTIME ||
        mobility == RMR_LAYOUT_PHYSICAL_FIXED) {
      return 1;
    }
  }
  return 0;
}

int RmR_StaticLayout_Rebind(rmr_static_layout_binding_t *binding,
                            void *new_base,
                            uint32_t new_capacity,
                            uint32_t new_mapping_epoch) {
  int rc;
  if (!binding || !binding->manifest) return RMR_LAYOUT_ERR_ARG;
  if (new_mapping_epoch < binding->mapping_epoch) return RMR_LAYOUT_ERR_STALE;
  rc = rmr_binding_base_valid(binding->manifest, new_base, new_capacity);
  if (rc != RMR_LAYOUT_OK) return rc;

  if ((uint8_t *)new_base != binding->base &&
      rmr_layout_blocks_rebind(binding->manifest)) {
    return RMR_LAYOUT_ERR_POLICY;
  }

  binding->base = (uint8_t *)new_base;
  binding->capacity = new_capacity;
  binding->mapping_epoch = new_mapping_epoch;
  return RMR_LAYOUT_OK;
}

static const rmr_static_region_t *rmr_find_region(
    const rmr_static_layout_manifest_t *manifest,
    uint32_t region_id) {
  uint32_t i;
  for (i = 0u; i < manifest->region_count; ++i) {
    if (manifest->regions[i].region_id == region_id) {
      return &manifest->regions[i];
    }
  }
  return (const rmr_static_region_t *)0;
}

int RmR_StaticLayout_Resolve(const rmr_static_layout_binding_t *binding,
                             uint32_t region_id,
                             uint32_t local_offset,
                             uint32_t length,
                             void **out_ptr) {
  const rmr_static_region_t *region;
  uint64_t absolute_offset;

  if (!binding || !binding->manifest || !out_ptr) return RMR_LAYOUT_ERR_ARG;
  if (binding->manifest_signature !=
      RmR_StaticLayout_ManifestSignature(binding->manifest)) {
    return RMR_LAYOUT_ERR_STALE;
  }

  region = rmr_find_region(binding->manifest, region_id);
  if (!region) return RMR_LAYOUT_ERR_ARG;
  if (region->semantic_state == RMR_LAYOUT_REGION_ABSENT ||
      region->semantic_state == RMR_LAYOUT_REGION_TOKEN_VAZIO ||
      region->semantic_state == RMR_LAYOUT_REGION_FAULT) {
    return RMR_LAYOUT_ERR_STATE;
  }
  if (local_offset > region->size || length > region->size - local_offset) {
    return RMR_LAYOUT_ERR_BOUNDS;
  }

  absolute_offset = (uint64_t)region->offset + (uint64_t)local_offset;
  if (absolute_offset + (uint64_t)length > (uint64_t)binding->capacity) {
    return RMR_LAYOUT_ERR_BOUNDS;
  }
  if (!binding->base && (absolute_offset != 0u || length != 0u)) {
    return RMR_LAYOUT_ERR_STATE;
  }

  *out_ptr = binding->base ? (void *)(binding->base + absolute_offset) : (void *)0;
  return RMR_LAYOUT_OK;
}

int RmR_StaticLayout_ResolveSpan(const rmr_static_layout_binding_t *binding,
                                 const rmr_relative_span_t *span,
                                 void **out_ptr) {
  if (!span) return RMR_LAYOUT_ERR_ARG;
  return RmR_StaticLayout_Resolve(binding,
                                  span->region_id,
                                  span->local_offset,
                                  span->length,
                                  out_ptr);
}

int RmR_StaticLayout_CanReuseOffsets(const rmr_static_layout_manifest_t *before,
                                     const rmr_static_layout_manifest_t *after) {
  uint64_t before_signature;
  uint64_t after_signature;
  if (!before || !after) return 0;
  before_signature = RmR_StaticLayout_ManifestSignature(before);
  after_signature = RmR_StaticLayout_ManifestSignature(after);
  return before_signature != 0u && before_signature == after_signature;
}

int RmR_StaticLayout_CanReuseAbsolutePointers(const rmr_static_layout_binding_t *binding,
                                               const void *candidate_base,
                                               uint32_t candidate_mapping_epoch) {
  if (!binding || !binding->manifest) return 0;
  if (binding->manifest_signature !=
      RmR_StaticLayout_ManifestSignature(binding->manifest)) {
    return 0;
  }
  return binding->base == (const uint8_t *)candidate_base &&
         binding->mapping_epoch == candidate_mapping_epoch;
}
