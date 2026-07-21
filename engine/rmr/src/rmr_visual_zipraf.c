// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_visual_zipraf.h"

static void rmr_visual_zipraf_zero(RmR_VisualZiprafResult *out) {
    uint8_t *p = (uint8_t *)out;
    size_t n = sizeof(*out);
    while (n--) *p++ = 0u;
}

int RmR_VisualZipraf_Seal(const RmR_VisualPrototype *prototype,
                          uint8_t *capsule_buffer,
                          size_t capsule_capacity,
                          uint32_t seed,
                          uint32_t trajectory_id,
                          uint32_t invariant_mask,
                          RmR_VisualZiprafResult *out) {
    size_t written = 0u;
    RmR_ZiprafInput input;
    if (!out) return -1;
    rmr_visual_zipraf_zero(out);
    out->status = RmR_VisualPrototype_Serialize(prototype,
                                                capsule_buffer,
                                                capsule_capacity,
                                                &written);
    if (out->status != RMR_VISUAL_STATUS_OK) return -1;
    input.seed = seed;
    input.trajectory_id = trajectory_id;
    input.invariant_mask = invariant_mask;
    input.payload_ptr = capsule_buffer;
    input.payload_len = written;
    if (RmR_Zipraf_Execute(&input, &out->custody) != 0) {
        out->status |= RMR_VISUAL_STATUS_BAD_ARGUMENT;
        return -1;
    }
    out->capsule_size = written;
    out->capsule_crc32c = RmR_Stability_CRC32C(capsule_buffer, written);
    return 0;
}
