// SPDX-License-Identifier: GPL-2.0-or-later
#include "raf_b7_orchestrator.h"
#include <stdio.h>

#define TEST_BYTES (64u * 1024u)
#define REGION_BYTES (512u * 1024u)

static unsigned char region[REGION_BYTES + RAF_B7_ALIGNMENT];
static unsigned char input[TEST_BYTES];
static unsigned char output[TEST_BYTES];

static int g_qualified;
static unsigned int g_dispatch_count;

typedef struct TestIo {
    unsigned int wrote;
} TestIo;

static int64_t test_read(void *ctx, uint64_t offset, void *dst, uint32_t cap) {
    uint32_t i;
    (void)ctx;
    if (offset >= TEST_BYTES) return 0;
    if (cap > TEST_BYTES - (uint32_t)offset) cap = TEST_BYTES - (uint32_t)offset;
    for (i = 0u; i < cap; ++i)
        ((unsigned char *)dst)[i] = input[(uint32_t)offset + i];
    return (int64_t)cap;
}

static int64_t test_write(void *ctx, uint64_t offset, const void *src, uint32_t bytes) {
    TestIo *io = (TestIo *)ctx;
    uint32_t i;
    if (offset + bytes > TEST_BYTES) return -1;
    for (i = 0u; i < bytes; ++i)
        output[(uint32_t)offset + i] = ((const unsigned char *)src)[i];
    io->wrote += bytes;
    return (int64_t)bytes;
}

static int gpu_available(void *ctx, uint32_t backend) {
    (void)ctx;
    return backend == RAF_B7_BACKEND_VULKAN ? 1 : 0;
}

static int gpu_qualified(void *ctx, uint32_t backend, uint32_t bytes) {
    (void)ctx;
    return backend == RAF_B7_BACKEND_VULKAN && bytes >= TEST_BYTES && g_qualified;
}

static int gpu_dispatch(void *ctx, uint32_t backend,
                        const void *src, void *dst,
                        uint32_t bytes, uint32_t lanes,
                        void *cache, uint32_t cache_bytes) {
    (void)ctx;
    (void)lanes;
    (void)cache;
    (void)cache_bytes;
    if (backend != RAF_B7_BACKEND_VULKAN) return -1;
    ++g_dispatch_count;
    raf_b7_matrix16_mix(dst, src, bytes);
    return 0;
}

static int gpu_wait(void *ctx, uint32_t backend) {
    (void)ctx;
    return backend == RAF_B7_BACKEND_VULKAN ? 0 : -1;
}

static int run_case(int qualified, unsigned int expected_dispatches) {
    RafB7Plan plan;
    RafB7DiskOps disk;
    RafB7GpuOps gpu;
    TestIo io = {0u};
    uint32_t i;
    int guard = 0;

    for (i = 0u; i < TEST_BYTES; ++i)
        input[i] = (unsigned char)((i * 17u + 3u) & 0xffu);
    for (i = 0u; i < TEST_BYTES; ++i) output[i] = 0u;

    disk.ctx = &io;
    disk.read_at = test_read;
    disk.write_at = test_write;

    gpu.ctx = 0;
    gpu.available = gpu_available;
    gpu.qualified = gpu_qualified;
    gpu.dispatch = gpu_dispatch;
    gpu.wait = gpu_wait;

    g_qualified = qualified;
    g_dispatch_count = 0u;

    if (raf_b7_init(&plan, region, sizeof(region), 4096u,
                    RAF_B7_FLAG_ALLOW_GPU | RAF_B7_FLAG_REQUIRE_CRC,
                    &disk, &gpu) != RAF_B7_OK) return 10;

    while (!raf_b7_pipeline_done(&plan) && guard++ < 16)
        if (raf_b7_pipeline_step(&plan) != RAF_B7_OK) return 11;

    if (io.wrote != TEST_BYTES || !raf_b7_pipeline_done(&plan)) return 12;
    if (g_dispatch_count != expected_dispatches) return 13;
    return 0;
}

int main(void) {
    int rc;
    rc = run_case(0, 0u);
    if (rc != 0) return rc;
    rc = run_case(1, 1u);
    if (rc != 0) return rc;
    printf("PASS gpu capability!=qualification dispatch_gate=closed_then_open\n");
    return 0;
}
