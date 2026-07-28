// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_visual_prototype.h"
#include "rmr_crc_internal.h"

#include <limits.h>

#define RMR_VISUAL_CAPSULE_HEADER_SIZE 36u
#define RMR_VISUAL_CAPSULE_VIEW_SIZE 92u
#define RMR_VISUAL_CAPSULE_TRAILER_SIZE 4u

static size_t rmr_visual_strnlen(const char *s, size_t cap) {
    size_t n = 0u;
    if (!s) return 0u;
    while (n < cap && s[n] != '\0') ++n;
    return n;
}

static void rmr_visual_zero(void *ptr, size_t n) {
    uint8_t *p = (uint8_t *)ptr;
    while (n--) *p++ = 0u;
}

static void rmr_visual_copy(void *dst, const void *src, size_t n) {
    uint8_t *d = (uint8_t *)dst;
    const uint8_t *s = (const uint8_t *)src;
    while (n--) *d++ = *s++;
}

static uint32_t rmr_crc_u8(uint32_t crc, uint8_t value) {
    return rmr_crc32c_update(crc, &value, 1u);
}

static uint32_t rmr_crc_u16(uint32_t crc, uint16_t value) {
    const uint8_t b[2] = {(uint8_t)value, (uint8_t)(value >> 8u)};
    return rmr_crc32c_update(crc, b, sizeof(b));
}

static uint32_t rmr_crc_u32(uint32_t crc, uint32_t value) {
    const uint8_t b[4] = {
        (uint8_t)value,
        (uint8_t)(value >> 8u),
        (uint8_t)(value >> 16u),
        (uint8_t)(value >> 24u)
    };
    return rmr_crc32c_update(crc, b, sizeof(b));
}

static void rmr_put_u16(uint8_t **p, uint16_t v) {
    (*p)[0] = (uint8_t)v;
    (*p)[1] = (uint8_t)(v >> 8u);
    *p += 2u;
}

static void rmr_put_u32(uint8_t **p, uint32_t v) {
    (*p)[0] = (uint8_t)v;
    (*p)[1] = (uint8_t)(v >> 8u);
    (*p)[2] = (uint8_t)(v >> 16u);
    (*p)[3] = (uint8_t)(v >> 24u);
    *p += 4u;
}

static uint16_t rmr_get_u16(const uint8_t **p) {
    const uint16_t v = (uint16_t)((uint16_t)(*p)[0] | ((uint16_t)(*p)[1] << 8u));
    *p += 2u;
    return v;
}

static uint32_t rmr_get_u32(const uint8_t **p) {
    const uint32_t v = (uint32_t)(*p)[0] |
                       ((uint32_t)(*p)[1] << 8u) |
                       ((uint32_t)(*p)[2] << 16u) |
                       ((uint32_t)(*p)[3] << 24u);
    *p += 4u;
    return v;
}

static uint32_t rmr_descriptor_crc(uint32_t crc, const RmR_VisionDescriptor *d) {
    crc = rmr_crc_u32(crc, d->width);
    crc = rmr_crc_u32(crc, d->height);
    crc = rmr_crc_u32(crc, d->pixel_count);
    crc = rmr_crc_u32(crc, d->foreground_count);
    crc = rmr_crc_u32(crc, d->foreground_q16);
    crc = rmr_crc_u32(crc, d->angle_count);
    for (uint32_t i = 0u; i < RMR_STABILITY_ANGLE_BINS; ++i) crc = rmr_crc_u32(crc, d->angle_hist[i]);
    crc = rmr_crc_u32(crc, d->angular_chi2_q16);
    crc = rmr_crc_u32(crc, d->angular_concentration_q16);
    crc = rmr_crc_u32(crc, d->gray_crc32c);
    crc = rmr_crc_u32(crc, d->descriptor_crc32c);
    crc = rmr_crc_u32(crc, d->status);
    crc = rmr_crc_u8(crc, d->otsu_threshold);
    return crc;
}

static uint32_t rmr_prototype_crc(const RmR_VisualPrototype *p) {
    uint32_t crc = 0xFFFFFFFFu;
    const size_t label_len = rmr_visual_strnlen(p->label, RMR_VISUAL_MAX_LABEL);
    crc = rmr_crc_u32(crc, p->magic);
    crc = rmr_crc_u32(crc, p->version);
    crc = rmr_crc_u32(crc, p->class_id);
    crc = rmr_crc_u32(crc, p->flags);
    crc = rmr_crc_u32(crc, p->view_count);
    crc = rmr_crc_u32(crc, p->view_mask);
    crc = rmr_crc_u32(crc, (uint32_t)label_len);
    crc = rmr_crc32c_update(crc, (const uint8_t *)p->label, label_len);
    for (uint32_t i = 0u; i < p->view_count; ++i) {
        const RmR_VisualView *v = &p->views[i];
        crc = rmr_crc_u8(crc, v->view_id);
        crc = rmr_crc_u8(crc, v->flags);
        crc = rmr_crc_u16(crc, v->reserved);
        crc = rmr_crc_u32(crc, v->sample_id);
        crc = rmr_crc_u32(crc, v->source_crc32c);
        crc = rmr_descriptor_crc(crc, &v->descriptor);
    }
    return crc ^ 0xFFFFFFFFu;
}

uint32_t RmR_Visual_ClassId(const char *label) {
    const size_t n = rmr_visual_strnlen(label, RMR_VISUAL_MAX_LABEL);
    if (n == 0u || n >= RMR_VISUAL_MAX_LABEL) return 0u;
    return RmR_Stability_CRC32C(label, n);
}

uint32_t RmR_VisualPrototype_Init(RmR_VisualPrototype *p, const char *label, uint32_t flags) {
    const size_t n = rmr_visual_strnlen(label, RMR_VISUAL_MAX_LABEL);
    if (!p || !label || n == 0u || n >= RMR_VISUAL_MAX_LABEL) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    rmr_visual_zero(p, sizeof(*p));
    p->magic = RMR_VISUAL_PROTOTYPE_MAGIC;
    p->version = RMR_VISUAL_API_VERSION;
    p->flags = flags;
    rmr_visual_copy(p->label, label, n);
    p->label[n] = '\0';
    p->class_id = RmR_Visual_ClassId(label);
    return p->class_id ? RMR_VISUAL_STATUS_OK : RMR_VISUAL_STATUS_BAD_ARGUMENT;
}

uint32_t RmR_VisualPrototype_AddView(RmR_VisualPrototype *p,
                                     uint8_t view_id,
                                     uint8_t sample_flags,
                                     uint32_t sample_id,
                                     uint32_t source_crc32c,
                                     const RmR_VisionDescriptor *descriptor,
                                     uint32_t add_flags) {
    if (!p || !descriptor || p->magic != RMR_VISUAL_PROTOTYPE_MAGIC || p->version != RMR_VISUAL_API_VERSION ||
        view_id >= RMR_VISUAL_MAX_VIEWS) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    uint32_t slot = p->view_count;
    for (uint32_t i = 0u; i < p->view_count; ++i) {
        if (p->views[i].view_id == view_id) {
            if ((add_flags & RMR_VISUAL_ADD_REPLACE) == 0u) return RMR_VISUAL_STATUS_DUPLICATE_VIEW;
            slot = i;
            break;
        }
    }
    if (slot == p->view_count) {
        if (p->view_count >= RMR_VISUAL_MAX_VIEWS) return RMR_VISUAL_STATUS_FULL;
        p->view_count++;
    }
    RmR_VisualView *v = &p->views[slot];
    rmr_visual_zero(v, sizeof(*v));
    v->view_id = view_id;
    v->flags = sample_flags;
    v->sample_id = sample_id;
    v->source_crc32c = source_crc32c;
    v->descriptor = *descriptor;
    p->view_mask |= (1u << view_id);
    p->prototype_crc32c = 0u;
    return RMR_VISUAL_STATUS_OK;
}

static uint32_t rmr_visual_structure_status(const RmR_VisualPrototype *p) {
    if (!p) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    if (p->magic != RMR_VISUAL_PROTOTYPE_MAGIC) return RMR_VISUAL_STATUS_BAD_MAGIC;
    if (p->version != RMR_VISUAL_API_VERSION) return RMR_VISUAL_STATUS_BAD_VERSION;
    const size_t label_len = rmr_visual_strnlen(p->label, RMR_VISUAL_MAX_LABEL);
    if (label_len == 0u || label_len >= RMR_VISUAL_MAX_LABEL) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    if (p->class_id == 0u || p->class_id != RmR_Visual_ClassId(p->label)) return RMR_VISUAL_STATUS_CRC_MISMATCH;
    if (p->view_count == 0u) return RMR_VISUAL_STATUS_EMPTY;
    if (p->view_count > RMR_VISUAL_MAX_VIEWS) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    uint32_t mask = 0u;
    for (uint32_t i = 0u; i < p->view_count; ++i) {
        const uint32_t view_id = p->views[i].view_id;
        if (view_id >= RMR_VISUAL_MAX_VIEWS) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
        const uint32_t bit = 1u << view_id;
        if ((mask & bit) != 0u) return RMR_VISUAL_STATUS_DUPLICATE_VIEW;
        mask |= bit;
    }
    if (mask != p->view_mask) return RMR_VISUAL_STATUS_CRC_MISMATCH;
    return RMR_VISUAL_STATUS_OK;
}

uint32_t RmR_VisualPrototype_Seal(RmR_VisualPrototype *p) {
    const uint32_t structure = rmr_visual_structure_status(p);
    if (structure != RMR_VISUAL_STATUS_OK) return structure;
    p->prototype_crc32c = rmr_prototype_crc(p);
    return RMR_VISUAL_STATUS_OK;
}

uint32_t RmR_VisualPrototype_Verify(const RmR_VisualPrototype *p) {
    const uint32_t structure = rmr_visual_structure_status(p);
    if (structure != RMR_VISUAL_STATUS_OK) return structure;
    if (p->prototype_crc32c == 0u) return RMR_VISUAL_STATUS_UNSEALED;
    return rmr_prototype_crc(p) == p->prototype_crc32c ? RMR_VISUAL_STATUS_OK : RMR_VISUAL_STATUS_CRC_MISMATCH;
}

uint32_t RmR_VisualPrototype_Compare(const RmR_VisualPrototype *p,
                                     const RmR_VisionDescriptor *query,
                                     uint8_t preferred_view,
                                     RmR_VisualMatch *out) {
    if (!out) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    rmr_visual_zero(out, sizeof(*out));
    out->best_view_id = RMR_VISUAL_VIEW_ANY;
    if (!p || !query) return out->status = RMR_VISUAL_STATUS_BAD_ARGUMENT;
    const uint32_t verify = RmR_VisualPrototype_Verify(p);
    if (verify != RMR_VISUAL_STATUS_OK) return out->status = verify;
    uint32_t best = UINT32_MAX;
    uint32_t compared = 0u;
    for (uint32_t pass = 0u; pass < 2u; ++pass) {
        for (uint32_t i = 0u; i < p->view_count; ++i) {
            const RmR_VisualView *v = &p->views[i];
            const uint32_t exact_pass = preferred_view != RMR_VISUAL_VIEW_ANY && v->view_id == preferred_view;
            if (preferred_view != RMR_VISUAL_VIEW_ANY) {
                if (pass == 0u && !exact_pass) continue;
                if (pass == 1u && exact_pass) continue;
            } else if (pass == 1u) {
                continue;
            }
            const uint32_t d = RmR_Vision_DifferenceQ16(&v->descriptor, query);
            compared++;
            if (d < best) {
                best = d;
                out->best_view_id = v->view_id;
            }
        }
        if (best != UINT32_MAX || preferred_view == RMR_VISUAL_VIEW_ANY) break;
    }
    if (best == UINT32_MAX) return out->status = RMR_VISUAL_STATUS_NO_MATCH;
    if (best > RMR_STABILITY_Q16_ONE) best = RMR_STABILITY_Q16_ONE;
    out->distance_q16 = best;
    out->score_q16 = RMR_STABILITY_Q16_ONE - best;
    out->compared_views = compared;
    out->prototype_crc32c = p->prototype_crc32c;
    return out->status = RMR_VISUAL_STATUS_OK;
}

size_t RmR_VisualPrototype_SerializedSize(const RmR_VisualPrototype *p) {
    if (!p || p->view_count > RMR_VISUAL_MAX_VIEWS) return 0u;
    const size_t label_len = rmr_visual_strnlen(p->label, RMR_VISUAL_MAX_LABEL);
    return RMR_VISUAL_CAPSULE_HEADER_SIZE + label_len +
           (size_t)p->view_count * RMR_VISUAL_CAPSULE_VIEW_SIZE + RMR_VISUAL_CAPSULE_TRAILER_SIZE;
}

static void rmr_serialize_descriptor(uint8_t **cursor, const RmR_VisionDescriptor *d) {
    rmr_put_u32(cursor, d->width);
    rmr_put_u32(cursor, d->height);
    rmr_put_u32(cursor, d->pixel_count);
    rmr_put_u32(cursor, d->foreground_count);
    rmr_put_u32(cursor, d->foreground_q16);
    rmr_put_u32(cursor, d->angle_count);
    for (uint32_t i = 0u; i < RMR_STABILITY_ANGLE_BINS; ++i) rmr_put_u32(cursor, d->angle_hist[i]);
    rmr_put_u32(cursor, d->angular_chi2_q16);
    rmr_put_u32(cursor, d->angular_concentration_q16);
    rmr_put_u32(cursor, d->gray_crc32c);
    rmr_put_u32(cursor, d->descriptor_crc32c);
    rmr_put_u32(cursor, d->status);
    *(*cursor)++ = d->otsu_threshold;
    *(*cursor)++ = 0u;
    *(*cursor)++ = 0u;
    *(*cursor)++ = 0u;
}

static void rmr_deserialize_descriptor(const uint8_t **cursor, RmR_VisionDescriptor *d) {
    d->width = rmr_get_u32(cursor);
    d->height = rmr_get_u32(cursor);
    d->pixel_count = rmr_get_u32(cursor);
    d->foreground_count = rmr_get_u32(cursor);
    d->foreground_q16 = rmr_get_u32(cursor);
    d->angle_count = rmr_get_u32(cursor);
    for (uint32_t i = 0u; i < RMR_STABILITY_ANGLE_BINS; ++i) d->angle_hist[i] = rmr_get_u32(cursor);
    d->angular_chi2_q16 = rmr_get_u32(cursor);
    d->angular_concentration_q16 = rmr_get_u32(cursor);
    d->gray_crc32c = rmr_get_u32(cursor);
    d->descriptor_crc32c = rmr_get_u32(cursor);
    d->status = rmr_get_u32(cursor);
    d->otsu_threshold = *(*cursor)++;
    d->reserved[0] = *(*cursor)++;
    d->reserved[1] = *(*cursor)++;
    d->reserved[2] = *(*cursor)++;
}

uint32_t RmR_VisualPrototype_Serialize(const RmR_VisualPrototype *p,
                                       uint8_t *out,
                                       size_t cap,
                                       size_t *out_written) {
    if (out_written) *out_written = 0u;
    const uint32_t verify = RmR_VisualPrototype_Verify(p);
    if (verify != RMR_VISUAL_STATUS_OK || !out) return verify ? verify : RMR_VISUAL_STATUS_BAD_ARGUMENT;
    const size_t label_len = rmr_visual_strnlen(p->label, RMR_VISUAL_MAX_LABEL);
    const size_t total = RmR_VisualPrototype_SerializedSize(p);
    if (cap < total || total > UINT32_MAX) return RMR_VISUAL_STATUS_TRUNCATED;
    uint8_t *c = out;
    rmr_put_u32(&c, RMR_VISUAL_CAPSULE_MAGIC);
    rmr_put_u16(&c, (uint16_t)RMR_VISUAL_API_VERSION);
    rmr_put_u16(&c, (uint16_t)RMR_VISUAL_CAPSULE_HEADER_SIZE);
    rmr_put_u32(&c, (uint32_t)total);
    rmr_put_u32(&c, p->class_id);
    rmr_put_u32(&c, p->flags);
    rmr_put_u32(&c, p->view_count);
    rmr_put_u32(&c, p->view_mask);
    rmr_put_u32(&c, (uint32_t)label_len);
    rmr_put_u32(&c, p->prototype_crc32c);
    rmr_visual_copy(c, p->label, label_len);
    c += label_len;
    for (uint32_t i = 0u; i < p->view_count; ++i) {
        const RmR_VisualView *v = &p->views[i];
        *c++ = v->view_id;
        *c++ = v->flags;
        rmr_put_u16(&c, v->reserved);
        rmr_put_u32(&c, v->sample_id);
        rmr_put_u32(&c, v->source_crc32c);
        rmr_serialize_descriptor(&c, &v->descriptor);
    }
    const uint32_t capsule_crc = RmR_Stability_CRC32C(out, total - RMR_VISUAL_CAPSULE_TRAILER_SIZE);
    rmr_put_u32(&c, capsule_crc);
    if ((size_t)(c - out) != total) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    if (out_written) *out_written = total;
    return RMR_VISUAL_STATUS_OK;
}

uint32_t RmR_VisualPrototype_Deserialize(const uint8_t *data,
                                         size_t size,
                                         RmR_VisualPrototype *out,
                                         size_t *out_consumed) {
    if (out_consumed) *out_consumed = 0u;
    if (!data || !out || size < RMR_VISUAL_CAPSULE_HEADER_SIZE + RMR_VISUAL_CAPSULE_TRAILER_SIZE) return RMR_VISUAL_STATUS_TRUNCATED;
    const uint8_t *c = data;
    const uint32_t magic = rmr_get_u32(&c);
    const uint16_t version = rmr_get_u16(&c);
    const uint16_t header_size = rmr_get_u16(&c);
    const uint32_t total = rmr_get_u32(&c);
    const uint32_t class_id = rmr_get_u32(&c);
    const uint32_t flags = rmr_get_u32(&c);
    const uint32_t view_count = rmr_get_u32(&c);
    const uint32_t view_mask = rmr_get_u32(&c);
    const uint32_t label_len = rmr_get_u32(&c);
    const uint32_t prototype_crc = rmr_get_u32(&c);
    if (magic != RMR_VISUAL_CAPSULE_MAGIC) return RMR_VISUAL_STATUS_BAD_MAGIC;
    if (version != RMR_VISUAL_API_VERSION || header_size != RMR_VISUAL_CAPSULE_HEADER_SIZE) return RMR_VISUAL_STATUS_BAD_VERSION;
    if (total > size || total < RMR_VISUAL_CAPSULE_HEADER_SIZE + RMR_VISUAL_CAPSULE_TRAILER_SIZE) return RMR_VISUAL_STATUS_TRUNCATED;
    if (view_count == 0u || view_count > RMR_VISUAL_MAX_VIEWS || label_len == 0u || label_len >= RMR_VISUAL_MAX_LABEL) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    const size_t expected = RMR_VISUAL_CAPSULE_HEADER_SIZE + label_len +
                            (size_t)view_count * RMR_VISUAL_CAPSULE_VIEW_SIZE + RMR_VISUAL_CAPSULE_TRAILER_SIZE;
    if (expected != total) return RMR_VISUAL_STATUS_TRUNCATED;
    const uint32_t stored_crc = (uint32_t)data[total - 4u] |
                                ((uint32_t)data[total - 3u] << 8u) |
                                ((uint32_t)data[total - 2u] << 16u) |
                                ((uint32_t)data[total - 1u] << 24u);
    if (RmR_Stability_CRC32C(data, total - 4u) != stored_crc) return RMR_VISUAL_STATUS_CRC_MISMATCH;
    rmr_visual_zero(out, sizeof(*out));
    out->magic = RMR_VISUAL_PROTOTYPE_MAGIC;
    out->version = RMR_VISUAL_API_VERSION;
    out->class_id = class_id;
    out->flags = flags;
    out->view_count = view_count;
    out->view_mask = view_mask;
    out->prototype_crc32c = prototype_crc;
    rmr_visual_copy(out->label, c, label_len);
    out->label[label_len] = '\0';
    c += label_len;
    for (uint32_t i = 0u; i < view_count; ++i) {
        RmR_VisualView *v = &out->views[i];
        v->view_id = *c++;
        v->flags = *c++;
        v->reserved = rmr_get_u16(&c);
        v->sample_id = rmr_get_u32(&c);
        v->source_crc32c = rmr_get_u32(&c);
        rmr_deserialize_descriptor(&c, &v->descriptor);
    }
    c += RMR_VISUAL_CAPSULE_TRAILER_SIZE;
    if ((size_t)(c - data) != total) return RMR_VISUAL_STATUS_TRUNCATED;
    const uint32_t verify = RmR_VisualPrototype_Verify(out);
    if (verify != RMR_VISUAL_STATUS_OK || out->class_id != RmR_Visual_ClassId(out->label)) return RMR_VISUAL_STATUS_CRC_MISMATCH;
    if (out_consumed) *out_consumed = total;
    return RMR_VISUAL_STATUS_OK;
}

uint32_t RmR_VisualStore_Init(RmR_VisualStore *store, RmR_VisualPrototype *slots, uint32_t capacity) {
    if (!store || !slots || capacity == 0u) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    store->slots = slots;
    store->capacity = capacity;
    store->count = 0u;
    rmr_visual_zero(slots, (size_t)capacity * sizeof(*slots));
    return RMR_VISUAL_STATUS_OK;
}

const RmR_VisualPrototype *RmR_VisualStore_Find(const RmR_VisualStore *store, uint32_t class_id) {
    if (!store || !store->slots || class_id == 0u) return (const RmR_VisualPrototype *)0;
    for (uint32_t i = 0u; i < store->count; ++i) if (store->slots[i].class_id == class_id) return &store->slots[i];
    return (const RmR_VisualPrototype *)0;
}

uint32_t RmR_VisualStore_Upsert(RmR_VisualStore *store, const RmR_VisualPrototype *p, uint32_t *out_slot) {
    if (out_slot) *out_slot = UINT32_MAX;
    if (!store || !store->slots || !p) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    const uint32_t verify = RmR_VisualPrototype_Verify(p);
    if (verify != RMR_VISUAL_STATUS_OK) return verify;
    uint32_t slot = store->count;
    for (uint32_t i = 0u; i < store->count; ++i) if (store->slots[i].class_id == p->class_id) { slot = i; break; }
    if (slot == store->count) {
        if (store->count >= store->capacity) return RMR_VISUAL_STATUS_FULL;
        store->count++;
    }
    store->slots[slot] = *p;
    if (out_slot) *out_slot = slot;
    return RMR_VISUAL_STATUS_OK;
}

uint32_t RmR_VisualStore_Classify(const RmR_VisualStore *store,
                                  const RmR_VisionDescriptor *query,
                                  uint8_t preferred_view,
                                  uint32_t minimum_score_q16,
                                  uint32_t minimum_margin_q16,
                                  RmR_VisualClassification *out) {
    if (!out) return RMR_VISUAL_STATUS_BAD_ARGUMENT;
    rmr_visual_zero(out, sizeof(*out));
    out->slot_index = UINT32_MAX;
    out->best_view_id = RMR_VISUAL_VIEW_ANY;
    if (!store || !store->slots || !query || store->count == 0u) return out->status = RMR_VISUAL_STATUS_EMPTY;
    uint32_t best = 0u, second = 0u, best_slot = UINT32_MAX, best_view = RMR_VISUAL_VIEW_ANY;
    for (uint32_t i = 0u; i < store->count; ++i) {
        RmR_VisualMatch m;
        if (RmR_VisualPrototype_Compare(&store->slots[i], query, preferred_view, &m) != RMR_VISUAL_STATUS_OK) continue;
        if (m.score_q16 > best) {
            second = best;
            best = m.score_q16;
            best_slot = i;
            best_view = m.best_view_id;
        } else if (m.score_q16 > second) {
            second = m.score_q16;
        }
    }
    if (best_slot == UINT32_MAX || best < minimum_score_q16) return out->status = RMR_VISUAL_STATUS_NO_MATCH;
    out->slot_index = best_slot;
    out->class_id = store->slots[best_slot].class_id;
    out->score_q16 = best;
    out->runner_up_q16 = second;
    out->margin_q16 = best - second;
    out->best_view_id = best_view;
    if (out->margin_q16 < minimum_margin_q16) return out->status = RMR_VISUAL_STATUS_AMBIGUOUS;
    return out->status = RMR_VISUAL_STATUS_OK;
}
