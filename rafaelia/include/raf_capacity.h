#ifndef RAFAELIA_RAF_CAPACITY_H
#define RAFAELIA_RAF_CAPACITY_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * RAFAELIA capacity model
 *
 * Pure deterministic helpers for estimating effective throughput,
 * spectral resolution and ZRF index size. These functions do not touch
 * files, devices, sensors, network, kernel state or private data.
 */

typedef enum raf_capacity_state {
    RAF_STATE_PASS = 0,
    RAF_STATE_FAIL = 1,
    RAF_STATE_NOT_RUN = 2,
    RAF_STATE_PENDING = 3,
    RAF_STATE_AUDIT = 4,
    RAF_STATE_RUNTIME = 5,
    RAF_STATE_REFERENCE = 6,
    RAF_STATE_TOKEN_VAZIO = 7
} raf_capacity_state_t;

typedef struct raf_spectral_plan {
    uint32_t sample_rate_hz;
    uint32_t fft_size;
    uint32_t channels;
    uint32_t bytes_per_sample;
} raf_spectral_plan_t;

typedef struct raf_capacity_result {
    uint64_t bytes_per_second_total;
    double mb_per_second_total;
    double mb_per_second_per_channel;
    double hz_per_bin;
    raf_capacity_state_t state;
} raf_capacity_result_t;

uint64_t raf_bytes_per_second(uint32_t sample_rate_hz,
                              uint32_t bytes_per_sample,
                              uint32_t channels);

double raf_mb_per_second(uint64_t bytes_per_second);

double raf_hz_per_bin(uint32_t sample_rate_hz, uint32_t fft_size);

uint64_t raf_zrf_index_bytes(uint64_t records, uint32_t bytes_per_record);

raf_capacity_result_t raf_capacity_estimate(raf_spectral_plan_t plan);

const char *raf_capacity_state_name(raf_capacity_state_t state);

#ifdef __cplusplus
}
#endif

#endif /* RAFAELIA_RAF_CAPACITY_H */
