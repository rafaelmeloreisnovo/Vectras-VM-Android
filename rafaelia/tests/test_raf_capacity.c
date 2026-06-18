#include "../include/raf_capacity.h"

#include <stdio.h>
#include <stdlib.h>

static void expect_u64(const char *name, uint64_t got, uint64_t want) {
    if (got != want) {
        fprintf(stderr, "FAIL %s: got=%llu want=%llu\n",
                name,
                (unsigned long long)got,
                (unsigned long long)want);
        exit(1);
    }
}

static void expect_double_range(const char *name, double got, double lo, double hi) {
    if (got < lo || got > hi) {
        fprintf(stderr, "FAIL %s: got=%f want=[%f,%f]\n", name, got, lo, hi);
        exit(1);
    }
}

int main(void) {
    expect_u64("48k_float32_1ch_bytes",
               raf_bytes_per_second(48000, 4, 1),
               192000ULL);

    expect_u64("48k_float32_1024ch_bytes",
               raf_bytes_per_second(48000, 4, 1024),
               196608000ULL);

    expect_double_range("mbps_192000",
                        raf_mb_per_second(192000ULL),
                        0.1919,
                        0.1921);

    expect_double_range("hz_per_bin_4096",
                        raf_hz_per_bin(48000, 4096),
                        11.718,
                        11.720);

    expect_u64("zrf_index_1m_256b",
               raf_zrf_index_bytes(1000000ULL, 256),
               256000000ULL);

    raf_spectral_plan_t plan;
    plan.sample_rate_hz = 48000;
    plan.fft_size = 2048;
    plan.channels = 64;
    plan.bytes_per_sample = 4;

    raf_capacity_result_t result = raf_capacity_estimate(plan);
    expect_u64("estimate_total_bytes", result.bytes_per_second_total, 12288000ULL);
    expect_double_range("estimate_hz_bin", result.hz_per_bin, 23.437, 23.438);

    printf("PASS raf_capacity checks\n");
    return 0;
}
