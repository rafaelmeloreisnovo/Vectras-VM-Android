// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_stability.h"

#include <stdio.h>
#include <string.h>

#define CHECK(expr) do { if (!(expr)) { \
    fprintf(stderr, "FAIL:%d: %s\n", __LINE__, #expr); return 1; \
} } while (0)

int main(void) {
    static const uint8_t events[] = {0u,1u,1u,2u,3u,8u,4u,3u};
    RmR_StabilityState portable, dispatched;
    RmR_StabilityState_Init(&portable, 0x963u);
    RmR_StabilityState_Init(&dispatched, 0x963u);
    const uint32_t a = RmR_StabilityStepPortable(&portable, events, sizeof(events));
    const uint32_t b = RmR_StabilityStep(&dispatched, events, sizeof(events));
    CHECK(a == b);
    CHECK(memcmp(&portable, &dispatched, sizeof(portable)) == 0);
    CHECK(a == 1135092322u);

    RmR_StabilityTrace trace;
    RmR_StabilityTrace_Init(&trace);
    for (unsigned i = 0; i < 10; ++i) RmR_StabilityTrace_Add(&trace, 3u, (uint8_t)(i < 8u), 1u);
    for (unsigned i = 0; i < 10; ++i) RmR_StabilityTrace_Add(&trace, 2u, (uint8_t)(i < 2u), 0u);
    CHECK(RmR_StabilityTrace_Finalize(&trace) == RMR_STABILITY_STATUS_OK);
    CHECK(trace.delta_p_q30 == 644245095);

    const uint8_t gray[16] = {
        0,0,0,0, 0,0,0,0,
        255,255,255,255, 255,255,255,255
    };
    const int16_t uniform_angles[8] = {0,45,90,135,180,225,270,315};
    const int16_t concentrated_angles[4] = {45,45,45,45};
    RmR_VisionDescriptor uniform, concentrated;
    CHECK(RmR_Vision_BuildDescriptor(gray, 4, 4, 4, uniform_angles, 8, &uniform) == 0u);
    CHECK(RmR_Vision_BuildDescriptor(gray, 4, 4, 4, concentrated_angles, 4, &concentrated) == 0u);
    CHECK(uniform.angular_chi2_q16 == 0u);
    CHECK(concentrated.angular_chi2_q16 > 0u);
    CHECK(concentrated.angular_concentration_q16 == RMR_STABILITY_Q16_ONE);
    CHECK(RmR_Vision_DifferenceQ16(&uniform, &concentrated) > 0u);

    puts("rmr_stability_selftest: OK");
    return 0;
}
