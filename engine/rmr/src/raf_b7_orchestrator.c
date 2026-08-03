// SPDX-License-Identifier: GPL-2.0-or-later
#include "raf_b7_orchestrator.h"

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#  include <arm_neon.h>
#  define RAF_B7_HAVE_NEON 1
#else
#  define RAF_B7_HAVE_NEON 0
#endif

#if defined(__ARM_FEATURE_CRC32)
#  include <arm_acle.h>
#  define RAF_B7_HAVE_ARM_CRC 1
#else
#  define RAF_B7_HAVE_ARM_CRC 0
#endif

#define RAF_B7_PHI32 0x9E3779B9u
#define RAF_B7_CRC32C_POLY 0x82F63B78u

static const uint32_t raf_b7_salt[RAF_B7_LANE_COUNT] = {
    0x243F6A88u, 0x85A308D3u, 0x13198A2Eu, 0x03707344u,
    0xA4093822u, 0x299F31D0u, 0x082EFA98u, 0xEC4E6C89u,
    0x452821E6u, 0x38D01377u, 0xBE5466CFu, 0x34E90C6Cu,
    0xC0AC29B7u, 0xC97C50DDu, 0x3F84D5B5u, 0xB5470917u
};

static uintptr_t raf_b7_align_up(uintptr_t value, uintptr_t alignment) {
    return (value + alignment - 1u) & ~(alignment - 1u);
}

static uint32_t raf_b7_align_down32(uint32_t value, uint32_t alignment) {
    return value & ~(alignment - 1u);
}

static uint32_t raf_b7_rotl32(uint32_t x, uint32_t n) {
    return (x << n) | (x >> (32u - n));
}

static uint32_t raf_b7_load32le(const uint8_t *p) {
    return ((uint32_t)p[0]) |
           ((uint32_t)p[1] << 8u) |
           ((uint32_t)p[2] << 16u) |
           ((uint32_t)p[3] << 24u);
}

#if RAF_B7_HAVE_ARM_CRC && defined(__aarch64__)
static uint64_t raf_b7_load64le(const uint8_t *p) {
    uint64_t lo = (uint64_t)raf_b7_load32le(p);
    uint64_t hi = (uint64_t)raf_b7_load32le(p + 4u);
    return lo | (hi << 32u);
}
#endif

static void raf_b7_copy_bytes(uint8_t *dst, const uint8_t *src, uint32_t bytes) {
    uint32_t i;
    if (dst == src || bytes == 0u) return;
    if (dst < src || dst >= src + bytes) {
        for (i = 0u; i < bytes; ++i) dst[i] = src[i];
    } else {
        for (i = bytes; i != 0u; --i) dst[i - 1u] = src[i - 1u];
    }
}

uint32_t raf_b7_compile_capabilities(void) {
    uint32_t caps = RAF_B7_CAP_SCALAR;
#if RAF_B7_HAVE_NEON
    caps |= RAF_B7_CAP_NEON;
#endif
#if RAF_B7_HAVE_ARM_CRC
    caps |= RAF_B7_CAP_CRC32C_HW;
#endif
    return caps;
}

uint32_t raf_b7_crc32c(uint32_t seed, const void *data, uint32_t bytes) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t crc = ~seed;
    uint32_t i = 0u;
    if (p == 0 || bytes == 0u) return ~crc;

#if RAF_B7_HAVE_ARM_CRC && defined(__aarch64__)
    for (; i + 8u <= bytes; i += 8u) crc = __crc32cd(crc, raf_b7_load64le(p + i));
    for (; i + 4u <= bytes; i += 4u) crc = __crc32cw(crc, raf_b7_load32le(p + i));
    for (; i < bytes; ++i) crc = __crc32cb(crc, p[i]);
#elif RAF_B7_HAVE_ARM_CRC
    for (; i + 4u <= bytes; i += 4u) crc = __crc32cw(crc, raf_b7_load32le(p + i));
    for (; i < bytes; ++i) crc = __crc32cb(crc, p[i]);
#else
    for (; i < bytes; ++i) {
        uint32_t bit;
        crc ^= p[i];
        for (bit = 0u; bit < 8u; ++bit) {
            uint32_t mask = (uint32_t)(0u - (crc & 1u));
            crc = (crc >> 1u) ^ (RAF_B7_CRC32C_POLY & mask);
        }
    }
#endif
    return ~crc;
}

void raf_b7_partition16(RafB7Lane lane[RAF_B7_LANE_COUNT], uint32_t bytes) {
    uint32_t base = bytes / RAF_B7_LANE_COUNT;
    uint32_t extra = bytes % RAF_B7_LANE_COUNT;
    uint32_t cursor = 0u;
    uint32_t i;
    if (lane == 0) return;
    for (i = 0u; i < RAF_B7_LANE_COUNT; ++i) {
        uint32_t span = base + (i < extra ? 1u : 0u);
        lane[i].begin = cursor;
        lane[i].end = cursor + span;
        lane[i].crc32c = 0u;
        lane[i].id = i;
        cursor += span;
    }
}

void raf_b7_matrix16_mix(void *dst_void, const void *src_void, uint32_t bytes) {
    uint8_t *dst = (uint8_t *)dst_void;
    const uint8_t *src = (const uint8_t *)src_void;
    uint32_t words = bytes / 4u;
    uint32_t i = 0u;
    if (dst == 0 || src == 0) return;

#if RAF_B7_HAVE_NEON
    for (; i + RAF_B7_LANE_COUNT <= words; i += RAF_B7_LANE_COUNT) {
        uint32x4_t x0 = vld1q_u32((const uint32_t *)(const void *)(src + (i + 0u) * 4u));
        uint32x4_t x1 = vld1q_u32((const uint32_t *)(const void *)(src + (i + 4u) * 4u));
        uint32x4_t x2 = vld1q_u32((const uint32_t *)(const void *)(src + (i + 8u) * 4u));
        uint32x4_t x3 = vld1q_u32((const uint32_t *)(const void *)(src + (i + 12u) * 4u));
        uint32x4_t s0 = vld1q_u32(raf_b7_salt + 0u);
        uint32x4_t s1 = vld1q_u32(raf_b7_salt + 4u);
        uint32x4_t s2 = vld1q_u32(raf_b7_salt + 8u);
        uint32x4_t s3 = vld1q_u32(raf_b7_salt + 12u);
        x0 = vmulq_n_u32(veorq_u32(x0, s0), RAF_B7_PHI32);
        x1 = vmulq_n_u32(veorq_u32(x1, s1), RAF_B7_PHI32);
        x2 = vmulq_n_u32(veorq_u32(x2, s2), RAF_B7_PHI32);
        x3 = vmulq_n_u32(veorq_u32(x3, s3), RAF_B7_PHI32);
#define RAF_B7_NEON_FINALIZE(v) \
        do { \
            (v) = vorrq_u32(vshlq_n_u32((v), 7), vshrq_n_u32((v), 25)); \
            (v) = veorq_u32((v), vshrq_n_u32((v), 11)); \
        } while (0)
        RAF_B7_NEON_FINALIZE(x0);
        RAF_B7_NEON_FINALIZE(x1);
        RAF_B7_NEON_FINALIZE(x2);
        RAF_B7_NEON_FINALIZE(x3);
#undef RAF_B7_NEON_FINALIZE
        vst1q_u32((uint32_t *)(void *)(dst + (i + 0u) * 4u), x0);
        vst1q_u32((uint32_t *)(void *)(dst + (i + 4u) * 4u), x1);
        vst1q_u32((uint32_t *)(void *)(dst + (i + 8u) * 4u), x2);
        vst1q_u32((uint32_t *)(void *)(dst + (i + 12u) * 4u), x3);
    }
#endif

    for (; i < words; ++i) {
        uint32_t x = raf_b7_load32le(src + i * 4u);
        uint32_t lane = i & (RAF_B7_LANE_COUNT - 1u);
        x = (x ^ raf_b7_salt[lane]) * RAF_B7_PHI32;
        x = raf_b7_rotl32(x, 7u);
        x ^= x >> 11u;
        dst[i * 4u + 0u] = (uint8_t)(x);
        dst[i * 4u + 1u] = (uint8_t)(x >> 8u);
        dst[i * 4u + 2u] = (uint8_t)(x >> 16u);
        dst[i * 4u + 3u] = (uint8_t)(x >> 24u);
    }
    raf_b7_copy_bytes(dst + words * 4u, src + words * 4u, bytes - words * 4u);
}

static void raf_b7_append_receipt(RafB7Plan *plan, uint16_t stage,
                                  uint16_t backend, int32_t status,
                                  const RafB7Bank *bank, uint64_t offset,
                                  uint32_t input_crc, uint32_t output_crc) {
    RafB7Receipt *r;
    if (plan == 0 || bank == 0) return;
    r = &plan->receipt[plan->receipt_head];
    r->epoch = plan->epoch;
    r->offset = offset;
    r->address = bank->logical_address;
    r->bytes = bank->used;
    r->input_crc32c = input_crc;
    r->output_crc32c = output_crc;
    r->stage = stage;
    r->backend = backend;
    r->status = status;
    plan->receipt_head = (plan->receipt_head + 1u) % RAF_B7_RECEIPT_CAPACITY;
    if (plan->receipt_count < RAF_B7_RECEIPT_CAPACITY) ++plan->receipt_count;
}

static uint16_t raf_b7_choose_backend(RafB7Plan *plan, uint32_t bytes) {
    if (plan != 0 && (plan->flags & RAF_B7_FLAG_ALLOW_GPU) != 0u &&
        bytes >= plan->gpu_threshold && plan->gpu.available != 0 &&
        plan->gpu.dispatch != 0) {
        if ((plan->capabilities & RAF_B7_CAP_GPU_VULKAN) != 0u &&
            plan->gpu.available(plan->gpu.ctx, RAF_B7_BACKEND_VULKAN) > 0)
            return RAF_B7_BACKEND_VULKAN;
        if ((plan->capabilities & RAF_B7_CAP_GPU_OPENCL) != 0u &&
            plan->gpu.available(plan->gpu.ctx, RAF_B7_BACKEND_OPENCL) > 0)
            return RAF_B7_BACKEND_OPENCL;
    }
    return (plan != 0 && (plan->capabilities & RAF_B7_CAP_NEON) != 0u)
        ? RAF_B7_BACKEND_NEON : RAF_B7_BACKEND_SCALAR;
}

static int raf_b7_compute(RafB7Plan *plan, RafB7Bank *bank) {
    uint16_t backend;
    uint32_t input_crc;
    uint32_t output_crc;
    uint32_t i;
    int rc = RAF_B7_OK;
    if (plan == 0 || bank == 0 || bank->state != RAF_B7_BANK_COMPUTE)
        return RAF_B7_ESTATE;

    input_crc = raf_b7_crc32c(0u, bank->base, bank->used);
    raf_b7_partition16(plan->lane, bank->used);
    for (i = 0u; i < RAF_B7_LANE_COUNT; ++i) {
        uint32_t span = plan->lane[i].end - plan->lane[i].begin;
        plan->lane[i].crc32c = raf_b7_crc32c(0u,
            bank->base + plan->lane[i].begin, span);
    }
    if (plan->cache_bytes >= RAF_B7_LANE_COUNT * (uint32_t)sizeof(uint32_t)) {
        uint32_t *lane_crc = (uint32_t *)(void *)plan->cache_base;
        for (i = 0u; i < RAF_B7_LANE_COUNT; ++i)
            lane_crc[i] = plan->lane[i].crc32c;
    }

    backend = raf_b7_choose_backend(plan, bank->used);
    if (backend == RAF_B7_BACKEND_VULKAN || backend == RAF_B7_BACKEND_OPENCL) {
        rc = plan->gpu.dispatch(plan->gpu.ctx, backend,
                                bank->base, bank->base, bank->used,
                                RAF_B7_LANE_COUNT,
                                plan->cache_base, plan->cache_bytes);
        if (rc == 0 && plan->gpu.wait != 0)
            rc = plan->gpu.wait(plan->gpu.ctx, backend);
        if (rc != 0) {
            raf_b7_append_receipt(plan, RAF_B7_STAGE_COMPUTE, backend,
                                  RAF_B7_EGPU, bank, plan->next_write_offset,
                                  input_crc, input_crc);
            return RAF_B7_EGPU;
        }
    } else {
        raf_b7_matrix16_mix(bank->base, bank->base, bank->used);
    }

    output_crc = raf_b7_crc32c(0u, bank->base, bank->used);
    bank->crc32c = output_crc;
    bank->state = RAF_B7_BANK_WRITE;
    raf_b7_append_receipt(plan, RAF_B7_STAGE_COMPUTE, backend,
                          RAF_B7_OK, bank, plan->next_write_offset,
                          input_crc, output_crc);
    return RAF_B7_OK;
}

int raf_b7_init(RafB7Plan *plan, void *region, uint32_t region_bytes,
                uint32_t cache_bytes, uint32_t flags,
                const RafB7DiskOps *disk, const RafB7GpuOps *gpu) {
    uintptr_t raw;
    uintptr_t aligned;
    uint32_t front_skip;
    uint32_t usable;
    uint32_t bank_bytes;
    uint32_t i;
    if (plan == 0 || region == 0) return RAF_B7_EINVAL;
    if (cache_bytes < RAF_B7_MIN_CACHE_BYTES) cache_bytes = RAF_B7_MIN_CACHE_BYTES;
    cache_bytes = raf_b7_align_down32(cache_bytes, RAF_B7_ALIGNMENT);
    if (cache_bytes < RAF_B7_MIN_CACHE_BYTES) cache_bytes = RAF_B7_MIN_CACHE_BYTES;

    raw = (uintptr_t)region;
    aligned = raf_b7_align_up(raw, RAF_B7_ALIGNMENT);
    front_skip = (uint32_t)(aligned - raw);
    if (region_bytes <= front_skip + cache_bytes + RAF_B7_BANK_COUNT * RAF_B7_ALIGNMENT)
        return RAF_B7_ENOSPC;
    usable = region_bytes - front_skip;
    bank_bytes = raf_b7_align_down32((usable - cache_bytes) / RAF_B7_BANK_COUNT,
                                     RAF_B7_ALIGNMENT);
    if (bank_bytes < RAF_B7_ALIGNMENT) return RAF_B7_ENOSPC;

    for (i = 0u; i < (uint32_t)sizeof(*plan); ++i)
        ((uint8_t *)(void *)plan)[i] = 0u;
    plan->region_base = (uint8_t *)aligned;
    plan->region_bytes = bank_bytes * RAF_B7_BANK_COUNT + cache_bytes;
    for (i = 0u; i < RAF_B7_BANK_COUNT; ++i) {
        plan->bank[i].base = plan->region_base + i * bank_bytes;
        plan->bank[i].capacity = bank_bytes;
        plan->bank[i].logical_address = (uintptr_t)plan->bank[i].base;
        plan->bank[i].state = RAF_B7_BANK_EMPTY;
    }
    plan->cache_base = plan->region_base + bank_bytes * RAF_B7_BANK_COUNT;
    plan->cache_bytes = cache_bytes;
    plan->flags = flags | RAF_B7_FLAG_MATRIX16;
    plan->gpu_threshold = 64u * 1024u;
    plan->capabilities = raf_b7_compile_capabilities();
    if (disk != 0) {
        plan->disk = *disk;
        if (disk->read_at != 0 || disk->write_at != 0)
            plan->capabilities |= RAF_B7_CAP_DISK;
    }
    if (gpu != 0) {
        plan->gpu = *gpu;
        if (gpu->available != 0 && gpu->dispatch != 0) {
            if (gpu->available(gpu->ctx, RAF_B7_BACKEND_VULKAN) > 0)
                plan->capabilities |= RAF_B7_CAP_GPU_VULKAN;
            if (gpu->available(gpu->ctx, RAF_B7_BACKEND_OPENCL) > 0)
                plan->capabilities |= RAF_B7_CAP_GPU_OPENCL;
        }
    }
    plan->read_index = 0u;
    plan->compute_index = 1u;
    plan->write_index = 2u;
    return raf_b7_verify_layout(plan);
}

int raf_b7_verify_layout(const RafB7Plan *plan) {
    uintptr_t begin;
    uintptr_t end;
    uint32_t i;
    if (plan == 0 || plan->region_base == 0) return RAF_B7_EINVAL;
    begin = (uintptr_t)plan->region_base;
    end = begin + plan->region_bytes;
    if ((begin & (RAF_B7_ALIGNMENT - 1u)) != 0u) return RAF_B7_EVERIFY;
    for (i = 0u; i < RAF_B7_BANK_COUNT; ++i) {
        uintptr_t b = (uintptr_t)plan->bank[i].base;
        uintptr_t e = b + plan->bank[i].capacity;
        if ((b & (RAF_B7_ALIGNMENT - 1u)) != 0u || b < begin || e > end)
            return RAF_B7_EVERIFY;
        if (i != 0u && b < (uintptr_t)plan->bank[i - 1u].base + plan->bank[i - 1u].capacity)
            return RAF_B7_EVERIFY;
    }
    if ((uintptr_t)plan->cache_base <
        (uintptr_t)plan->bank[2].base + plan->bank[2].capacity)
        return RAF_B7_EVERIFY;
    if ((uintptr_t)plan->cache_base + plan->cache_bytes > end)
        return RAF_B7_EVERIFY;
    return RAF_B7_OK;
}

int raf_b7_pipeline_step(RafB7Plan *plan) {
    RafB7Bank *ingress;
    RafB7Bank *compute;
    RafB7Bank *egress;
    uint8_t old_read;
    uint8_t old_compute;
    uint8_t old_write;
    int64_t io;
    int rc;
    if (plan == 0) return RAF_B7_EINVAL;
    if (raf_b7_verify_layout(plan) != RAF_B7_OK) return RAF_B7_EVERIFY;

    ingress = &plan->bank[plan->read_index];
    compute = &plan->bank[plan->compute_index];
    egress = &plan->bank[plan->write_index];

    if (egress->state == RAF_B7_BANK_WRITE) {
        if (plan->disk.write_at == 0) return RAF_B7_ESTATE;
        io = plan->disk.write_at(plan->disk.ctx, plan->next_write_offset,
                                 egress->base, egress->used);
        if (io < 0 || (uint64_t)io != (uint64_t)egress->used) {
            raf_b7_append_receipt(plan, RAF_B7_STAGE_EGRESS, 0u,
                                  RAF_B7_EIO, egress, plan->next_write_offset,
                                  egress->crc32c, egress->crc32c);
            return RAF_B7_EIO;
        }
        raf_b7_append_receipt(plan, RAF_B7_STAGE_EGRESS, 0u,
                              RAF_B7_OK, egress, plan->next_write_offset,
                              egress->crc32c, egress->crc32c);
        plan->next_write_offset += egress->used;
        egress->used = 0u;
        egress->crc32c = 0u;
        egress->state = RAF_B7_BANK_EMPTY;
    }

    if (compute->state == RAF_B7_BANK_COMPUTE) {
        rc = raf_b7_compute(plan, compute);
        if (rc != RAF_B7_OK) return rc;
    }

    if (!plan->input_eof && ingress->state == RAF_B7_BANK_EMPTY) {
        if (plan->disk.read_at == 0) return RAF_B7_ESTATE;
        io = plan->disk.read_at(plan->disk.ctx, plan->next_read_offset,
                                ingress->base, ingress->capacity);
        if (io < 0 || (uint64_t)io > (uint64_t)ingress->capacity) {
            raf_b7_append_receipt(plan, RAF_B7_STAGE_INGEST, 0u,
                                  RAF_B7_EIO, ingress, plan->next_read_offset,
                                  0u, 0u);
            return RAF_B7_EIO;
        }
        if (io == 0) {
            plan->input_eof = 1u;
        } else {
            ingress->used = (uint32_t)io;
            ingress->crc32c = raf_b7_crc32c(0u, ingress->base, ingress->used);
            ingress->state = RAF_B7_BANK_COMPUTE;
            raf_b7_append_receipt(plan, RAF_B7_STAGE_INGEST, 0u,
                                  RAF_B7_OK, ingress, plan->next_read_offset,
                                  ingress->crc32c, ingress->crc32c);
            plan->next_read_offset += ingress->used;
        }
    }

    old_read = plan->read_index;
    old_compute = plan->compute_index;
    old_write = plan->write_index;
    plan->read_index = old_write;
    plan->compute_index = old_read;
    plan->write_index = old_compute;
    ++plan->epoch;
    return RAF_B7_OK;
}

int raf_b7_pipeline_done(const RafB7Plan *plan) {
    uint32_t i;
    if (plan == 0 || !plan->input_eof) return 0;
    for (i = 0u; i < RAF_B7_BANK_COUNT; ++i)
        if (plan->bank[i].state != RAF_B7_BANK_EMPTY) return 0;
    return 1;
}

int raf_b7_attest(RafB7Plan *plan, uint32_t witness_crc32c,
                  int external_verification_ok) {
    RafB7Bank synthetic;
    if (plan == 0 || witness_crc32c == 0u || external_verification_ok == 0)
        return RAF_B7_EVERIFY;
    synthetic.base = plan->cache_base;
    synthetic.capacity = plan->cache_bytes;
    synthetic.used = 0u;
    synthetic.crc32c = witness_crc32c;
    synthetic.state = RAF_B7_BANK_EMPTY;
    synthetic.logical_address = (uintptr_t)plan->cache_base;
    plan->attestation_crc32c = witness_crc32c;
    plan->claim_allowed = 1u;
    raf_b7_append_receipt(plan, RAF_B7_STAGE_ATTEST, 0u, RAF_B7_OK,
                          &synthetic, 0u, witness_crc32c, witness_crc32c);
    return RAF_B7_OK;
}

const RafB7Receipt *raf_b7_last_receipt(const RafB7Plan *plan) {
    uint32_t index;
    if (plan == 0 || plan->receipt_count == 0u) return 0;
    index = (plan->receipt_head + RAF_B7_RECEIPT_CAPACITY - 1u) %
            RAF_B7_RECEIPT_CAPACITY;
    return &plan->receipt[index];
}
