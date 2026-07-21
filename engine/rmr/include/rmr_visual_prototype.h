// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_VISUAL_PROTOTYPE_H
#define RMR_VISUAL_PROTOTYPE_H

#include <stddef.h>
#include <stdint.h>

#include "rmr_stability.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_VISUAL_API_VERSION 1u
#define RMR_VISUAL_PROTOTYPE_MAGIC 0x52565031u /* RVP1 */
#define RMR_VISUAL_CAPSULE_MAGIC 0x52564331u   /* RVC1 */
#define RMR_VISUAL_MAX_LABEL 32u
#define RMR_VISUAL_MAX_VIEWS 16u
#define RMR_VISUAL_VIEW_ANY 0xffu

enum {
    RMR_VISUAL_VIEW_FRONT = 0u,
    RMR_VISUAL_VIEW_REAR = 1u,
    RMR_VISUAL_VIEW_LEFT = 2u,
    RMR_VISUAL_VIEW_RIGHT = 3u,
    RMR_VISUAL_VIEW_TOP = 4u,
    RMR_VISUAL_VIEW_BOTTOM = 5u,
    RMR_VISUAL_VIEW_FRONT_LEFT = 6u,
    RMR_VISUAL_VIEW_FRONT_RIGHT = 7u,
    RMR_VISUAL_VIEW_REAR_LEFT = 8u,
    RMR_VISUAL_VIEW_REAR_RIGHT = 9u,
    RMR_VISUAL_VIEW_TOP_FRONT = 10u,
    RMR_VISUAL_VIEW_TOP_REAR = 11u,
    RMR_VISUAL_VIEW_BOTTOM_FRONT = 12u,
    RMR_VISUAL_VIEW_BOTTOM_REAR = 13u,
    RMR_VISUAL_VIEW_AUX_0 = 14u,
    RMR_VISUAL_VIEW_AUX_1 = 15u
};

enum {
    RMR_VISUAL_STATUS_OK = 0u,
    RMR_VISUAL_STATUS_BAD_ARGUMENT = 1u << 0,
    RMR_VISUAL_STATUS_EMPTY = 1u << 1,
    RMR_VISUAL_STATUS_FULL = 1u << 2,
    RMR_VISUAL_STATUS_DUPLICATE_VIEW = 1u << 3,
    RMR_VISUAL_STATUS_NO_MATCH = 1u << 4,
    RMR_VISUAL_STATUS_AMBIGUOUS = 1u << 5,
    RMR_VISUAL_STATUS_BAD_MAGIC = 1u << 6,
    RMR_VISUAL_STATUS_BAD_VERSION = 1u << 7,
    RMR_VISUAL_STATUS_TRUNCATED = 1u << 8,
    RMR_VISUAL_STATUS_CRC_MISMATCH = 1u << 9,
    RMR_VISUAL_STATUS_UNSEALED = 1u << 10
};

enum {
    RMR_VISUAL_ADD_REPLACE = 1u << 0,
    RMR_VISUAL_SAMPLE_CONFIRMED = 1u << 0,
    RMR_VISUAL_SAMPLE_DERIVED = 1u << 1
};

typedef struct {
    uint8_t view_id;
    uint8_t flags;
    uint16_t reserved;
    uint32_t sample_id;
    uint32_t source_crc32c;
    RmR_VisionDescriptor descriptor;
} RmR_VisualView;

typedef struct {
    uint32_t magic;
    uint32_t version;
    uint32_t class_id;
    uint32_t flags;
    uint32_t view_count;
    uint32_t view_mask;
    uint32_t prototype_crc32c;
    char label[RMR_VISUAL_MAX_LABEL];
    RmR_VisualView views[RMR_VISUAL_MAX_VIEWS];
} RmR_VisualPrototype;

typedef struct {
    uint32_t status;
    uint32_t distance_q16;
    uint32_t score_q16;
    uint32_t best_view_id;
    uint32_t compared_views;
    uint32_t prototype_crc32c;
} RmR_VisualMatch;

typedef struct {
    RmR_VisualPrototype *slots;
    uint32_t capacity;
    uint32_t count;
} RmR_VisualStore;

typedef struct {
    uint32_t status;
    uint32_t slot_index;
    uint32_t class_id;
    uint32_t score_q16;
    uint32_t runner_up_q16;
    uint32_t margin_q16;
    uint32_t best_view_id;
} RmR_VisualClassification;

uint32_t RmR_Visual_ClassId(const char *label);
uint32_t RmR_VisualPrototype_Init(RmR_VisualPrototype *prototype,
                                  const char *label,
                                  uint32_t flags);
uint32_t RmR_VisualPrototype_AddView(RmR_VisualPrototype *prototype,
                                     uint8_t view_id,
                                     uint8_t sample_flags,
                                     uint32_t sample_id,
                                     uint32_t source_crc32c,
                                     const RmR_VisionDescriptor *descriptor,
                                     uint32_t add_flags);
uint32_t RmR_VisualPrototype_Seal(RmR_VisualPrototype *prototype);
uint32_t RmR_VisualPrototype_Verify(const RmR_VisualPrototype *prototype);
uint32_t RmR_VisualPrototype_Compare(const RmR_VisualPrototype *prototype,
                                     const RmR_VisionDescriptor *query,
                                     uint8_t preferred_view,
                                     RmR_VisualMatch *out);
size_t RmR_VisualPrototype_SerializedSize(const RmR_VisualPrototype *prototype);
uint32_t RmR_VisualPrototype_Serialize(const RmR_VisualPrototype *prototype,
                                       uint8_t *out,
                                       size_t out_capacity,
                                       size_t *out_written);
uint32_t RmR_VisualPrototype_Deserialize(const uint8_t *data,
                                         size_t data_size,
                                         RmR_VisualPrototype *out,
                                         size_t *out_consumed);
uint32_t RmR_VisualStore_Init(RmR_VisualStore *store,
                              RmR_VisualPrototype *slots,
                              uint32_t capacity);
uint32_t RmR_VisualStore_Upsert(RmR_VisualStore *store,
                                const RmR_VisualPrototype *prototype,
                                uint32_t *out_slot);
const RmR_VisualPrototype *RmR_VisualStore_Find(const RmR_VisualStore *store,
                                                uint32_t class_id);
uint32_t RmR_VisualStore_Classify(const RmR_VisualStore *store,
                                  const RmR_VisionDescriptor *query,
                                  uint8_t preferred_view,
                                  uint32_t minimum_score_q16,
                                  uint32_t minimum_margin_q16,
                                  RmR_VisualClassification *out);

#ifdef __cplusplus
}
#endif

#endif
