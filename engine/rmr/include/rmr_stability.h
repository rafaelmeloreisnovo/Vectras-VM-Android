// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_STABILITY_H
#define RMR_STABILITY_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_STABILITY_API_VERSION 1u
#define RMR_STABILITY_ANGLE_BINS 8u
#define RMR_STABILITY_Q16_ONE 65536u
#define RMR_STABILITY_Q30_ONE 1073741824u

enum {
    RMR_STABILITY_STATUS_OK                 = 0u,
    RMR_STABILITY_STATUS_NO_SAMPLES         = 1u << 0,
    RMR_STABILITY_STATUS_NO_PEAK_SAMPLES    = 1u << 1,
    RMR_STABILITY_STATUS_NO_NONPEAK_SAMPLES = 1u << 2,
    RMR_STABILITY_STATUS_BAD_ARGUMENT       = 1u << 3,
    RMR_STABILITY_STATUS_COUNTER_SATURATED  = 1u << 4,
    RMR_STABILITY_STATUS_ANGLE_SATURATED    = 1u << 5
};

typedef struct {
    uint32_t s0;
    uint32_t s1;
    uint32_t s2;
    uint32_t s3;
} RmR_StabilityState;

typedef struct {
    uint32_t rows;
    uint32_t peak_total;
    uint32_t peak_stable;
    uint32_t nonpeak_total;
    uint32_t nonpeak_stable;
    uint32_t peak_rate_q30;
    uint32_t nonpeak_rate_q30;
    int32_t delta_p_q30;
    uint32_t status;
} RmR_StabilityTrace;

typedef struct {
    uint32_t width;
    uint32_t height;
    uint32_t pixel_count;
    uint32_t foreground_count;
    uint32_t foreground_q16;
    uint32_t angle_count;
    uint32_t angle_hist[RMR_STABILITY_ANGLE_BINS];
    uint32_t angular_chi2_q16;
    uint32_t angular_concentration_q16;
    uint32_t gray_crc32c;
    uint32_t descriptor_crc32c;
    uint32_t status;
    uint8_t otsu_threshold;
    uint8_t reserved[3];
} RmR_VisionDescriptor;

void RmR_StabilityState_Init(RmR_StabilityState *state, uint32_t seed);
uint32_t RmR_StabilityStepPortable(RmR_StabilityState *state,
                                   const uint8_t *events,
                                   size_t count);
uint32_t RmR_StabilityStep(RmR_StabilityState *state,
                           const uint8_t *events,
                           size_t count);
void RmR_StabilityTrace_Init(RmR_StabilityTrace *trace);
void RmR_StabilityTrace_Add(RmR_StabilityTrace *trace,
                            uint32_t gate,
                            uint8_t stable_any,
                            uint8_t gate_in_peaks);
uint32_t RmR_StabilityTrace_Finalize(RmR_StabilityTrace *trace);
uint8_t RmR_Vision_OtsuThreshold(const uint8_t *gray,
                                 uint32_t width,
                                 uint32_t height,
                                 uint32_t stride,
                                 uint32_t *status);
uint32_t RmR_Vision_BuildDescriptor(const uint8_t *gray,
                                    uint32_t width,
                                    uint32_t height,
                                    uint32_t stride,
                                    const int16_t *angles_deg,
                                    uint32_t angle_count,
                                    RmR_VisionDescriptor *out);
uint32_t RmR_Vision_DifferenceQ16(const RmR_VisionDescriptor *a,
                                  const RmR_VisionDescriptor *b);
uint32_t RmR_Stability_DifferenceHash(const RmR_VisionDescriptor *a,
                                      const RmR_VisionDescriptor *b);
uint32_t RmR_Stability_CRC32C(const void *data, size_t size);

#ifdef __cplusplus
}
#endif

#endif
