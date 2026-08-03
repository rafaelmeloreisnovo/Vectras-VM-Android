// SPDX-License-Identifier: GPL-2.0-or-later
#ifndef RAF_B7_ORCHESTRATOR_H
#define RAF_B7_ORCHESTRATOR_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RAF_B7_BANK_COUNT       3u
#define RAF_B7_LANE_COUNT       16u
#define RAF_B7_ALIGNMENT        64u
#define RAF_B7_RECEIPT_CAPACITY 64u
#define RAF_B7_MIN_CACHE_BYTES  256u

#define RAF_B7_CAP_SCALAR       (1u << 0)
#define RAF_B7_CAP_NEON         (1u << 1)
#define RAF_B7_CAP_CRC32C_HW    (1u << 2)
#define RAF_B7_CAP_DISK         (1u << 3)
#define RAF_B7_CAP_GPU_VULKAN   (1u << 4)
#define RAF_B7_CAP_GPU_OPENCL   (1u << 5)

#define RAF_B7_FLAG_ALLOW_GPU   (1u << 0)
#define RAF_B7_FLAG_REQUIRE_CRC (1u << 1)
#define RAF_B7_FLAG_MATRIX16    (1u << 2)

#define RAF_B7_STAGE_INGEST     1u
#define RAF_B7_STAGE_COMPUTE    2u
#define RAF_B7_STAGE_EGRESS     3u
#define RAF_B7_STAGE_ATTEST     4u

#define RAF_B7_BACKEND_SCALAR   1u
#define RAF_B7_BACKEND_NEON     2u
#define RAF_B7_BACKEND_VULKAN   3u
#define RAF_B7_BACKEND_OPENCL   4u

#define RAF_B7_BANK_EMPTY       0u
#define RAF_B7_BANK_COMPUTE     1u
#define RAF_B7_BANK_WRITE       2u

#define RAF_B7_OK                0
#define RAF_B7_EINVAL           -1
#define RAF_B7_ENOSPC           -2
#define RAF_B7_EIO              -3
#define RAF_B7_EGPU             -4
#define RAF_B7_ESTATE           -5
#define RAF_B7_EVERIFY          -6

typedef struct RafB7Bank {
    uint8_t *base;
    uint32_t capacity;
    uint32_t used;
    uint32_t crc32c;
    uint32_t state;
    uintptr_t logical_address;
} RafB7Bank;

typedef struct RafB7Lane {
    uint32_t begin;
    uint32_t end;
    uint32_t crc32c;
    uint32_t id;
} RafB7Lane;

typedef struct RafB7Receipt {
    uint64_t epoch;
    uint64_t offset;
    uintptr_t address;
    uint32_t bytes;
    uint32_t input_crc32c;
    uint32_t output_crc32c;
    uint16_t stage;
    uint16_t backend;
    int32_t status;
} RafB7Receipt;

typedef int64_t (*RafB7ReadAtFn)(void *ctx, uint64_t offset,
                                  void *dst, uint32_t capacity);
typedef int64_t (*RafB7WriteAtFn)(void *ctx, uint64_t offset,
                                   const void *src, uint32_t bytes);

typedef struct RafB7DiskOps {
    void *ctx;
    RafB7ReadAtFn read_at;
    RafB7WriteAtFn write_at;
} RafB7DiskOps;

typedef int (*RafB7GpuAvailableFn)(void *ctx, uint32_t backend);
typedef int (*RafB7GpuDispatchFn)(void *ctx, uint32_t backend,
                                  const void *src, void *dst,
                                  uint32_t bytes, uint32_t lanes,
                                  void *cache, uint32_t cache_bytes);
typedef int (*RafB7GpuWaitFn)(void *ctx, uint32_t backend);

typedef struct RafB7GpuOps {
    void *ctx;
    RafB7GpuAvailableFn available;
    RafB7GpuDispatchFn dispatch;
    RafB7GpuWaitFn wait;
} RafB7GpuOps;

typedef struct RafB7Plan {
    uint8_t *region_base;
    uint32_t region_bytes;
    RafB7Bank bank[RAF_B7_BANK_COUNT];
    uint8_t *cache_base;
    uint32_t cache_bytes;
    RafB7Lane lane[RAF_B7_LANE_COUNT];
    RafB7Receipt receipt[RAF_B7_RECEIPT_CAPACITY];
    RafB7DiskOps disk;
    RafB7GpuOps gpu;
    uint64_t epoch;
    uint64_t next_read_offset;
    uint64_t next_write_offset;
    uint32_t receipt_count;
    uint32_t receipt_head;
    uint32_t capabilities;
    uint32_t flags;
    uint32_t gpu_threshold;
    uint32_t attestation_crc32c;
    uint8_t read_index;
    uint8_t compute_index;
    uint8_t write_index;
    uint8_t input_eof;
    uint8_t claim_allowed;
    uint8_t reserved[3];
} RafB7Plan;

uint32_t raf_b7_compile_capabilities(void);
uint32_t raf_b7_crc32c(uint32_t seed, const void *data, uint32_t bytes);
void raf_b7_matrix16_mix(void *dst, const void *src, uint32_t bytes);
void raf_b7_partition16(RafB7Lane lane[RAF_B7_LANE_COUNT], uint32_t bytes);

int raf_b7_init(RafB7Plan *plan, void *region, uint32_t region_bytes,
                uint32_t cache_bytes, uint32_t flags,
                const RafB7DiskOps *disk, const RafB7GpuOps *gpu);
int raf_b7_verify_layout(const RafB7Plan *plan);
int raf_b7_pipeline_step(RafB7Plan *plan);
int raf_b7_pipeline_done(const RafB7Plan *plan);
int raf_b7_attest(RafB7Plan *plan, uint32_t witness_crc32c,
                  int external_verification_ok);
const RafB7Receipt *raf_b7_last_receipt(const RafB7Plan *plan);

#ifdef __cplusplus
}
#endif
#endif
