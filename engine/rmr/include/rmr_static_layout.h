// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_STATIC_LAYOUT_H
#define RMR_STATIC_LAYOUT_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_STATIC_LAYOUT_ABI_VERSION 1u

typedef enum {
  RMR_LAYOUT_OK = 0,
  RMR_LAYOUT_ERR_ARG = -1,
  RMR_LAYOUT_ERR_VERSION = -2,
  RMR_LAYOUT_ERR_BOUNDS = -3,
  RMR_LAYOUT_ERR_ALIGN = -4,
  RMR_LAYOUT_ERR_OVERLAP = -5,
  RMR_LAYOUT_ERR_DUPLICATE = -6,
  RMR_LAYOUT_ERR_POLICY = -7,
  RMR_LAYOUT_ERR_STATE = -8,
  RMR_LAYOUT_ERR_STALE = -9
} rmr_layout_status_t;

typedef enum {
  RMR_LAYOUT_BASE_RELATIVE = 0,
  RMR_LAYOUT_BASE_FIXED_VIRTUAL = 1,
  RMR_LAYOUT_BASE_FIXED_PHYSICAL = 2,
  RMR_LAYOUT_BASE_TOKEN_VAZIO = 255
} rmr_layout_base_policy_t;

typedef enum {
  RMR_LAYOUT_MOVABLE_BASE = 0,
  RMR_LAYOUT_FIXED_OFFSET = 1,
  RMR_LAYOUT_PINNED_RUNTIME = 2,
  RMR_LAYOUT_REMAP_ONLY = 3,
  RMR_LAYOUT_PHYSICAL_FIXED = 4,
  RMR_LAYOUT_MOBILITY_TOKEN_VAZIO = 255
} rmr_layout_mobility_t;

typedef enum {
  RMR_LAYOUT_REGION_ABSENT = 0,
  RMR_LAYOUT_REGION_EMPTY = 1,
  RMR_LAYOUT_REGION_PRESENT = 2,
  RMR_LAYOUT_REGION_FAULT = 3,
  RMR_LAYOUT_REGION_TOKEN_VAZIO = 255
} rmr_layout_region_state_t;

enum {
  RMR_LAYOUT_FLAG_READ_ONLY = 1u << 0,
  RMR_LAYOUT_FLAG_EXECUTABLE = 1u << 1,
  RMR_LAYOUT_FLAG_ZERO_FILL = 1u << 2,
  RMR_LAYOUT_FLAG_NO_RELOCATION_TABLE = 1u << 3,
  RMR_LAYOUT_FLAG_FIXED_OFFSET_BITS = 1u << 4
};

typedef struct {
  uint32_t region_id;
  uint32_t offset;
  uint32_t size;
  uint32_t alignment;
  uint64_t fixed_offset_mask;
  uint64_t fixed_offset_value;
  uint8_t mobility;
  uint8_t semantic_state;
  uint16_t flags;
} rmr_static_region_t;

typedef struct {
  uint32_t abi_version;
  uint32_t layout_epoch;
  uint32_t total_size;
  uint32_t base_alignment;
  uint32_t region_count;
  uint8_t base_policy;
  uint8_t reserved0;
  uint16_t reserved1;
  const rmr_static_region_t *regions;
} rmr_static_layout_manifest_t;

typedef struct {
  uint8_t *base;
  uint32_t capacity;
  uint32_t mapping_epoch;
  uint64_t manifest_signature;
  const rmr_static_layout_manifest_t *manifest;
} rmr_static_layout_binding_t;

typedef struct {
  uint32_t region_id;
  uint32_t local_offset;
  uint32_t length;
} rmr_relative_span_t;

/* Validate an immutable offset graph. No allocation and no global state. */
int RmR_StaticLayout_Validate(const rmr_static_layout_manifest_t *manifest);

/* Stable, non-cryptographic identity over manifest fields and regions. */
uint64_t RmR_StaticLayout_ManifestSignature(const rmr_static_layout_manifest_t *manifest);

/* Bind the immutable offset graph to one runtime base address. */
int RmR_StaticLayout_Bind(const rmr_static_layout_manifest_t *manifest,
                          void *base,
                          uint32_t capacity,
                          uint32_t mapping_epoch,
                          rmr_static_layout_binding_t *out_binding);

/*
 * Rebind only the base. Relative offsets remain reusable when the manifest
 * signature is unchanged. PINNED_RUNTIME/PHYSICAL_FIXED regions reject a base
 * change; FIXED_VIRTUAL/FIXED_PHYSICAL policies reject it globally.
 */
int RmR_StaticLayout_Rebind(rmr_static_layout_binding_t *binding,
                            void *new_base,
                            uint32_t new_capacity,
                            uint32_t new_mapping_epoch);

/* Resolve region + local offset without a relocation table per object. */
int RmR_StaticLayout_Resolve(const rmr_static_layout_binding_t *binding,
                             uint32_t region_id,
                             uint32_t local_offset,
                             uint32_t length,
                             void **out_ptr);

int RmR_StaticLayout_ResolveSpan(const rmr_static_layout_binding_t *binding,
                                 const rmr_relative_span_t *span,
                                 void **out_ptr);

/* Offset plans can be reused across mappings; absolute pointers cannot. */
int RmR_StaticLayout_CanReuseOffsets(const rmr_static_layout_manifest_t *before,
                                     const rmr_static_layout_manifest_t *after);

int RmR_StaticLayout_CanReuseAbsolutePointers(const rmr_static_layout_binding_t *binding,
                                               const void *candidate_base,
                                               uint32_t candidate_mapping_epoch);

/* Six-bit/bit-matrix helper already used by ZIPRAF tests, now domain-qualified. */
uint64_t RmR_StaticLayout_PreserveFixedBits(uint64_t candidate,
                                           uint64_t fixed_mask,
                                           uint64_t fixed_value);

#ifdef __cplusplus
}
#endif

#endif /* RMR_STATIC_LAYOUT_H */
