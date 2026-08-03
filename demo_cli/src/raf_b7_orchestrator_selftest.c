// SPDX-License-Identifier: GPL-2.0-or-later
#include "raf_b7_orchestrator.h"
#include <stdio.h>

#define TEST_BYTES 8192u
#define REGION_BYTES (256u * 1024u)

static unsigned char region[REGION_BYTES + RAF_B7_ALIGNMENT];
static unsigned char input[TEST_BYTES];
static unsigned char output[TEST_BYTES];

typedef struct TestIo {
    unsigned int wrote;
} TestIo;

static int64_t test_read(void *ctx, uint64_t offset, void *dst, uint32_t cap) {
    uint32_t i;
    (void)ctx;
    if (offset >= TEST_BYTES) return 0;
    if (cap > TEST_BYTES - (uint32_t)offset) cap = TEST_BYTES - (uint32_t)offset;
    for (i = 0u; i < cap; ++i) ((unsigned char *)dst)[i] = input[(uint32_t)offset + i];
    return (int64_t)cap;
}

static int64_t test_write(void *ctx, uint64_t offset, const void *src, uint32_t bytes) {
    TestIo *io = (TestIo *)ctx;
    uint32_t i;
    if (offset + bytes > TEST_BYTES) return -1;
    for (i = 0u; i < bytes; ++i) output[(uint32_t)offset + i] = ((const unsigned char *)src)[i];
    io->wrote += bytes;
    return (int64_t)bytes;
}

int main(void) {
    RafB7Plan plan;
    RafB7DiskOps disk;
    TestIo io = {0u};
    uint32_t i;
    uint32_t expected_crc;
    uint32_t actual_crc;
    int guard = 0;

    for (i = 0u; i < TEST_BYTES; ++i) input[i] = (unsigned char)((i * 29u + 7u) & 0xffu);
    disk.ctx = &io;
    disk.read_at = test_read;
    disk.write_at = test_write;

    if (raf_b7_crc32c(0u, "123456789", 9u) != 0xE3069283u) return 1;
    if (raf_b7_init(&plan, region, sizeof(region), 4096u,
                    RAF_B7_FLAG_REQUIRE_CRC | RAF_B7_FLAG_MATRIX16,
                    &disk, 0) != RAF_B7_OK) return 2;
    while (!raf_b7_pipeline_done(&plan) && guard++ < 16)
        if (raf_b7_pipeline_step(&plan) != RAF_B7_OK) return 3;
    if (io.wrote != TEST_BYTES || !raf_b7_pipeline_done(&plan)) return 4;

    raf_b7_matrix16_mix(region, input, TEST_BYTES);
    expected_crc = raf_b7_crc32c(0u, region, TEST_BYTES);
    actual_crc = raf_b7_crc32c(0u, output, TEST_BYTES);
    if (expected_crc != actual_crc) return 5;
    if (plan.claim_allowed != 0u) return 6;
    if (raf_b7_attest(&plan, actual_crc, 1) != RAF_B7_OK || plan.claim_allowed != 1u) return 7;

    printf("PASS bytes=%u crc32c=%08x caps=%08x receipts=%u\n",
           io.wrote, actual_crc, plan.capabilities, plan.receipt_count);
    return 0;
}
