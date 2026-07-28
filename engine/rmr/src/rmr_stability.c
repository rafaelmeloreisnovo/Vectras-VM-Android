// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_stability.h"
#include "rmr_crc_internal.h"

#include <limits.h>

#if defined(__arm__) && !defined(__aarch64__) && (defined(__GNUC__) || defined(__clang__))
extern uint32_t rmr_stability_step_armv7(uint32_t state_words[4],
                                         const uint8_t *events,
                                         uint32_t count) __attribute__((weak));
#define RMR_STABILITY_CAN_DISPATCH_ARMV7 1
#else
#define RMR_STABILITY_CAN_DISPATCH_ARMV7 0
#endif

static uint32_t rmr_rotl32(uint32_t x, uint32_t n) {
    return (x << n) | (x >> (32u - n));
}

static uint32_t rmr_rotr32(uint32_t x, uint32_t n) {
    return (x >> n) | (x << (32u - n));
}

static uint32_t rmr_sat_add_u32(uint32_t a, uint32_t b, uint32_t *status) {
    if (UINT32_MAX - a < b) {
        if (status) *status |= RMR_STABILITY_STATUS_COUNTER_SATURATED;
        return UINT32_MAX;
    }
    return a + b;
}

static uint32_t rmr_ratio_q30(uint32_t numerator, uint32_t denominator) {
    if (denominator == 0u) return 0u;
    return (uint32_t)(((uint64_t)numerator << 30u) / denominator);
}

static uint32_t rmr_ratio_q16(uint32_t numerator, uint32_t denominator) {
    if (denominator == 0u) return 0u;
    return (uint32_t)(((uint64_t)numerator << 16u) / denominator);
}

static uint32_t rmr_absdiff_u32(uint32_t a, uint32_t b) {
    return (a >= b) ? (a - b) : (b - a);
}

uint32_t RmR_Stability_CRC32C(const void *data, size_t size) {
    if (!data && size != 0u) return 0u;
    uint32_t crc = 0xFFFFFFFFu;
    crc = rmr_crc32c_update(crc, (const uint8_t *)data, size);
    return crc ^ 0xFFFFFFFFu;
}

void RmR_StabilityState_Init(RmR_StabilityState *state, uint32_t seed) {
    if (!state) return;
    const uint32_t x = seed ? seed : 0x00000963u;
    state->s0 = x ^ 0x243F6A88u;
    state->s1 = rmr_rotl32(x, 7u) ^ 0x85A308D3u;
    state->s2 = rmr_rotl32(x, 13u) ^ 0x13198A2Eu;
    state->s3 = rmr_rotl32(x, 21u) ^ 0x03707344u;
}

uint32_t RmR_StabilityStepPortable(RmR_StabilityState *state,
                                   const uint8_t *events,
                                   size_t count) {
    if (!state || (!events && count != 0u)) return 0u;

    uint32_t s0 = state->s0;
    uint32_t s1 = state->s1;
    uint32_t s2 = state->s2;
    uint32_t s3 = state->s3;

    for (size_t i = 0; i < count; ++i) {
        s0 ^= s1;
        s1 += events[i];
        s2 = rmr_rotr32(s2, 5u);
        s2 += s0;
        s3 ^= s2;
        s0 += s3;
    }

    state->s0 = s0;
    state->s1 = s1;
    state->s2 = s2;
    state->s3 = s3;

    return ((((s0 ^ s1) + s2) ^ s3) & 0x7FFFFFFFu);
}

uint32_t RmR_StabilityStep(RmR_StabilityState *state,
                           const uint8_t *events,
                           size_t count) {
    if (!state || (!events && count != 0u)) return 0u;
#if RMR_STABILITY_CAN_DISPATCH_ARMV7
    if (rmr_stability_step_armv7 != 0 && count <= UINT32_MAX) {
        return rmr_stability_step_armv7(&state->s0, events, (uint32_t)count);
    }
#endif
    return RmR_StabilityStepPortable(state, events, count);
}

void RmR_StabilityTrace_Init(RmR_StabilityTrace *trace) {
    if (!trace) return;
    trace->rows = 0u;
    trace->peak_total = 0u;
    trace->peak_stable = 0u;
    trace->nonpeak_total = 0u;
    trace->nonpeak_stable = 0u;
    trace->peak_rate_q30 = 0u;
    trace->nonpeak_rate_q30 = 0u;
    trace->delta_p_q30 = 0;
    trace->status = RMR_STABILITY_STATUS_NO_SAMPLES |
                    RMR_STABILITY_STATUS_NO_PEAK_SAMPLES |
                    RMR_STABILITY_STATUS_NO_NONPEAK_SAMPLES;
}

void RmR_StabilityTrace_Add(RmR_StabilityTrace *trace,
                            uint32_t gate,
                            uint8_t stable_any,
                            uint8_t gate_in_peaks) {
    if (!trace) return;
    const uint32_t is_peak = (gate_in_peaks != 0u) || gate == 3u || gate == 4u || gate == 8u;
    trace->rows = rmr_sat_add_u32(trace->rows, 1u, &trace->status);
    if (is_peak) {
        trace->peak_total = rmr_sat_add_u32(trace->peak_total, 1u, &trace->status);
        if (stable_any) trace->peak_stable = rmr_sat_add_u32(trace->peak_stable, 1u, &trace->status);
    } else {
        trace->nonpeak_total = rmr_sat_add_u32(trace->nonpeak_total, 1u, &trace->status);
        if (stable_any) trace->nonpeak_stable = rmr_sat_add_u32(trace->nonpeak_stable, 1u, &trace->status);
    }
}

uint32_t RmR_StabilityTrace_Finalize(RmR_StabilityTrace *trace) {
    if (!trace) return RMR_STABILITY_STATUS_BAD_ARGUMENT;

    trace->status &= RMR_STABILITY_STATUS_COUNTER_SATURATED;
    if (trace->rows == 0u) trace->status |= RMR_STABILITY_STATUS_NO_SAMPLES;
    if (trace->peak_total == 0u) trace->status |= RMR_STABILITY_STATUS_NO_PEAK_SAMPLES;
    if (trace->nonpeak_total == 0u) trace->status |= RMR_STABILITY_STATUS_NO_NONPEAK_SAMPLES;

    trace->peak_rate_q30 = rmr_ratio_q30(trace->peak_stable, trace->peak_total);
    trace->nonpeak_rate_q30 = rmr_ratio_q30(trace->nonpeak_stable, trace->nonpeak_total);
    trace->delta_p_q30 = (int32_t)trace->peak_rate_q30 - (int32_t)trace->nonpeak_rate_q30;
    return trace->status;
}

uint8_t RmR_Vision_OtsuThreshold(const uint8_t *gray,
                                 uint32_t width,
                                 uint32_t height,
                                 uint32_t stride,
                                 uint32_t *status) {
    if (status) *status = RMR_STABILITY_STATUS_OK;
    if (!gray || width == 0u || height == 0u || stride < width) {
        if (status) *status |= RMR_STABILITY_STATUS_BAD_ARGUMENT;
        return 0u;
    }

    uint32_t hist[256] = {0u};
    uint64_t total = (uint64_t)width * height;
    if (total > UINT32_MAX) {
        if (status) *status |= RMR_STABILITY_STATUS_COUNTER_SATURATED;
        return 0u;
    }

    uint64_t sum = 0u;
    for (uint32_t y = 0u; y < height; ++y) {
        const uint8_t *row = gray + (size_t)y * stride;
        for (uint32_t x = 0u; x < width; ++x) {
            const uint8_t v = row[x];
            hist[v]++;
            sum += v;
        }
    }

    uint64_t background_weight = 0u;
    uint64_t background_sum = 0u;
    uint64_t best_score = 0u;
    uint8_t best_threshold = 0u;
    uint32_t weight_shift = 0u;
    while ((total >> weight_shift) > 65535u) ++weight_shift;

    for (uint32_t i = 0u; i < 256u; ++i) {
        background_weight += hist[i];
        if (background_weight == 0u) continue;
        const uint64_t foreground_weight = total - background_weight;
        if (foreground_weight == 0u) break;
        background_sum += (uint64_t)i * hist[i];

        const uint64_t mean_b_q16 = (background_sum << 16u) / background_weight;
        const uint64_t mean_f_q16 = ((sum - background_sum) << 16u) / foreground_weight;
        const uint64_t diff = (mean_b_q16 >= mean_f_q16)
                            ? mean_b_q16 - mean_f_q16
                            : mean_f_q16 - mean_b_q16;
        const uint64_t d = diff >> 8u;
        uint64_t wb = background_weight >> weight_shift;
        uint64_t wf = foreground_weight >> weight_shift;
        if (wb == 0u) wb = 1u;
        if (wf == 0u) wf = 1u;
        const uint64_t score = (d * d) * wb * wf;
        if (score > best_score) {
            best_score = score;
            best_threshold = (uint8_t)i;
        }
    }
    return best_threshold;
}

static uint32_t rmr_normalize_angle_bin(int16_t angle_deg) {
    int32_t a = angle_deg;
    while (a < 0) a += 360;
    while (a >= 360) a -= 360;
    return (uint32_t)((a + 22) / 45) & 7u;
}

static uint32_t rmr_angular_chi2_q16(const uint32_t hist[8], uint32_t count, uint32_t *status) {
    if (count == 0u) return 0u;
    if (count > 1000000u) {
        if (status) *status |= RMR_STABILITY_STATUS_ANGLE_SATURATED;
        count = 1000000u;
    }
    uint64_t numerator_sum = 0u;
    for (uint32_t i = 0u; i < 8u; ++i) {
        const int64_t d = (int64_t)(8u * hist[i]) - (int64_t)count;
        numerator_sum += (uint64_t)(d * d);
    }
    const uint64_t denominator = (uint64_t)8u * count;
    const uint64_t whole = numerator_sum / denominator;
    const uint64_t remainder = numerator_sum % denominator;
    if (whole > (UINT32_MAX >> 16u)) return UINT32_MAX;
    const uint64_t q = (whole << 16u) + ((remainder << 16u) / denominator);
    return (uint32_t)(q > UINT32_MAX ? UINT32_MAX : q);
}

static uint32_t rmr_angular_concentration_q16(const uint32_t hist[8], uint32_t count) {
    if (count == 0u) return 0u;
    uint32_t max_bin = hist[0];
    for (uint32_t i = 1u; i < 8u; ++i) if (hist[i] > max_bin) max_bin = hist[i];
    const uint64_t scaled = (uint64_t)max_bin * 8u;
    if (scaled <= count) return 0u;
    return (uint32_t)(((scaled - count) << 16u) / ((uint64_t)7u * count));
}

static void rmr_crc_u32(uint32_t *crc, uint32_t value) {
    uint8_t bytes[4];
    bytes[0] = (uint8_t)value;
    bytes[1] = (uint8_t)(value >> 8u);
    bytes[2] = (uint8_t)(value >> 16u);
    bytes[3] = (uint8_t)(value >> 24u);
    *crc = rmr_crc32c_update(*crc, bytes, sizeof(bytes));
}

uint32_t RmR_Vision_BuildDescriptor(const uint8_t *gray,
                                    uint32_t width,
                                    uint32_t height,
                                    uint32_t stride,
                                    const int16_t *angles_deg,
                                    uint32_t angle_count,
                                    RmR_VisionDescriptor *out) {
    if (!out) return RMR_STABILITY_STATUS_BAD_ARGUMENT;

    *out = (RmR_VisionDescriptor){0};
    out->width = width;
    out->height = height;
    out->status = RMR_STABILITY_STATUS_OK;

    if (!gray || width == 0u || height == 0u || stride < width || (!angles_deg && angle_count != 0u)) {
        out->status |= RMR_STABILITY_STATUS_BAD_ARGUMENT;
        return out->status;
    }

    const uint64_t pixel_count64 = (uint64_t)width * height;
    if (pixel_count64 > UINT32_MAX) {
        out->status |= RMR_STABILITY_STATUS_COUNTER_SATURATED;
        return out->status;
    }
    out->pixel_count = (uint32_t)pixel_count64;
    out->otsu_threshold = RmR_Vision_OtsuThreshold(gray, width, height, stride, &out->status);

    uint32_t gray_crc = 0xFFFFFFFFu;
    for (uint32_t y = 0u; y < height; ++y) {
        const uint8_t *row = gray + (size_t)y * stride;
        gray_crc = rmr_crc32c_update(gray_crc, row, width);
        for (uint32_t x = 0u; x < width; ++x) {
            if (row[x] <= out->otsu_threshold) out->foreground_count++;
        }
    }
    out->gray_crc32c = gray_crc ^ 0xFFFFFFFFu;
    out->foreground_q16 = rmr_ratio_q16(out->foreground_count, out->pixel_count);

    const uint32_t processed_angles = angle_count > 1000000u ? 1000000u : angle_count;
    if (processed_angles != angle_count) out->status |= RMR_STABILITY_STATUS_ANGLE_SATURATED;
    out->angle_count = processed_angles;
    for (uint32_t i = 0u; i < processed_angles; ++i) {
        const uint32_t bin = rmr_normalize_angle_bin(angles_deg[i]);
        out->angle_hist[bin] = rmr_sat_add_u32(out->angle_hist[bin], 1u, &out->status);
    }
    out->angular_chi2_q16 = rmr_angular_chi2_q16(out->angle_hist, processed_angles, &out->status);
    out->angular_concentration_q16 = rmr_angular_concentration_q16(out->angle_hist, processed_angles);

    uint32_t crc = 0xFFFFFFFFu;
    rmr_crc_u32(&crc, out->width);
    rmr_crc_u32(&crc, out->height);
    rmr_crc_u32(&crc, out->pixel_count);
    rmr_crc_u32(&crc, out->foreground_count);
    rmr_crc_u32(&crc, out->foreground_q16);
    rmr_crc_u32(&crc, out->angle_count);
    for (uint32_t i = 0u; i < 8u; ++i) rmr_crc_u32(&crc, out->angle_hist[i]);
    rmr_crc_u32(&crc, out->angular_chi2_q16);
    rmr_crc_u32(&crc, out->angular_concentration_q16);
    rmr_crc_u32(&crc, out->gray_crc32c);
    crc = rmr_crc32c_update(crc, &out->otsu_threshold, 1u);
    out->descriptor_crc32c = crc ^ 0xFFFFFFFFu;
    return out->status;
}

uint32_t RmR_Vision_DifferenceQ16(const RmR_VisionDescriptor *a,
                                  const RmR_VisionDescriptor *b) {
    if (!a || !b) return UINT32_MAX;
    const uint32_t threshold_q16 = (rmr_absdiff_u32(a->otsu_threshold, b->otsu_threshold) << 16u) / 255u;
    const uint32_t foreground_q16 = rmr_absdiff_u32(a->foreground_q16, b->foreground_q16);

    uint64_t hist_l1_q16 = 0u;
    if (a->angle_count != 0u || b->angle_count != 0u) {
        for (uint32_t i = 0u; i < 8u; ++i) {
            const uint32_t pa = rmr_ratio_q16(a->angle_hist[i], a->angle_count);
            const uint32_t pb = rmr_ratio_q16(b->angle_hist[i], b->angle_count);
            hist_l1_q16 += rmr_absdiff_u32(pa, pb);
        }
        hist_l1_q16 >>= 1u;
        if (hist_l1_q16 > RMR_STABILITY_Q16_ONE) hist_l1_q16 = RMR_STABILITY_Q16_ONE;
    }

    const uint64_t mean = ((uint64_t)threshold_q16 + foreground_q16 + hist_l1_q16) / 3u;
    return (uint32_t)(mean > UINT32_MAX ? UINT32_MAX : mean);
}

uint32_t RmR_Stability_DifferenceHash(const RmR_VisionDescriptor *a,
                                      const RmR_VisionDescriptor *b) {
    if (!a || !b) return 0u;
    uint32_t crc = 0xFFFFFFFFu;
    const uint32_t values[] = {
        a->descriptor_crc32c ^ b->descriptor_crc32c,
        a->gray_crc32c ^ b->gray_crc32c,
        rmr_absdiff_u32(a->foreground_q16, b->foreground_q16),
        rmr_absdiff_u32(a->angular_chi2_q16, b->angular_chi2_q16),
        rmr_absdiff_u32(a->angular_concentration_q16, b->angular_concentration_q16),
        RmR_Vision_DifferenceQ16(a, b)
    };
    for (size_t i = 0u; i < sizeof(values) / sizeof(values[0]); ++i) rmr_crc_u32(&crc, values[i]);
    for (uint32_t i = 0u; i < 8u; ++i) rmr_crc_u32(&crc, a->angle_hist[i] ^ b->angle_hist[i]);
    return crc ^ 0xFFFFFFFFu;
}
