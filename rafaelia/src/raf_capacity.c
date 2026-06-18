#include "../include/raf_capacity.h"

#include <limits.h>

static uint64_t raf_mul_u32_saturating(uint32_t a, uint32_t b, uint32_t c) {
    uint64_t x = (uint64_t)a;
    if (b != 0 && x > UINT64_MAX / (uint64_t)b) {
        return UINT64_MAX;
    }
    x *= (uint64_t)b;
    if (c != 0 && x > UINT64_MAX / (uint64_t)c) {
        return UINT64_MAX;
    }
    x *= (uint64_t)c;
    return x;
}

uint64_t raf_bytes_per_second(uint32_t sample_rate_hz,
                              uint32_t bytes_per_sample,
                              uint32_t channels) {
    return raf_mul_u32_saturating(sample_rate_hz, bytes_per_sample, channels);
}

double raf_mb_per_second(uint64_t bytes_per_second) {
    return (double)bytes_per_second / 1000000.0;
}

double raf_hz_per_bin(uint32_t sample_rate_hz, uint32_t fft_size) {
    if (fft_size == 0) {
        return 0.0;
    }
    return (double)sample_rate_hz / (double)fft_size;
}

uint64_t raf_zrf_index_bytes(uint64_t records, uint32_t bytes_per_record) {
    if (bytes_per_record != 0 && records > UINT64_MAX / (uint64_t)bytes_per_record) {
        return UINT64_MAX;
    }
    return records * (uint64_t)bytes_per_record;
}

raf_capacity_result_t raf_capacity_estimate(raf_spectral_plan_t plan) {
    raf_capacity_result_t out;
    out.bytes_per_second_total = 0;
    out.mb_per_second_total = 0.0;
    out.mb_per_second_per_channel = 0.0;
    out.hz_per_bin = 0.0;
    out.state = RAF_STATE_TOKEN_VAZIO;

    if (plan.sample_rate_hz == 0 || plan.bytes_per_sample == 0 || plan.channels == 0 || plan.fft_size == 0) {
        return out;
    }

    out.bytes_per_second_total = raf_bytes_per_second(plan.sample_rate_hz,
                                                       plan.bytes_per_sample,
                                                       plan.channels);
    out.mb_per_second_total = raf_mb_per_second(out.bytes_per_second_total);
    out.mb_per_second_per_channel = raf_mb_per_second(
        raf_bytes_per_second(plan.sample_rate_hz, plan.bytes_per_sample, 1));
    out.hz_per_bin = raf_hz_per_bin(plan.sample_rate_hz, plan.fft_size);
    out.state = RAF_STATE_REFERENCE;
    return out;
}

const char *raf_capacity_state_name(raf_capacity_state_t state) {
    switch (state) {
        case RAF_STATE_PASS: return "PASS";
        case RAF_STATE_FAIL: return "FAIL";
        case RAF_STATE_NOT_RUN: return "NOT_RUN";
        case RAF_STATE_PENDING: return "PENDING";
        case RAF_STATE_AUDIT: return "AUDIT";
        case RAF_STATE_RUNTIME: return "RUNTIME";
        case RAF_STATE_REFERENCE: return "REFERENCE";
        case RAF_STATE_TOKEN_VAZIO: return "TOKEN_VAZIO";
        default: return "TOKEN_VAZIO";
    }
}
