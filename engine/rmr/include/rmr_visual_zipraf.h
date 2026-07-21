// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_VISUAL_ZIPRAF_H
#define RMR_VISUAL_ZIPRAF_H

#include <stddef.h>
#include <stdint.h>

#include "rmr_visual_prototype.h"
#include "rmr_zipraf_core.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t status;
    uint32_t capsule_crc32c;
    size_t capsule_size;
    RmR_ZiprafOutput custody;
} RmR_VisualZiprafResult;

int RmR_VisualZipraf_Seal(const RmR_VisualPrototype *prototype,
                          uint8_t *capsule_buffer,
                          size_t capsule_capacity,
                          uint32_t seed,
                          uint32_t trajectory_id,
                          uint32_t invariant_mask,
                          RmR_VisualZiprafResult *out);

#ifdef __cplusplus
}
#endif

#endif
