// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* ═══════════════════════════════════════════════════════════════
   rmr_neon_simd.c — RAFAELIA NEON/SIMD Baremetal Acceleration
   ψ→χ→ρ→Δ→Σ→Ω  |  R(t+1) = R(t) × Φ_ethica × (√3/2)^(πφ)
   ───────────────────────────────────────────────────────────────
   Estratégia:
   - ARM64 NEON: 128-bit vectorization via intrinsics
   - x86_64: SSE4.2 / AVX2 via intrinsics
   - RISCV64: scalar fallback (V extension future)
   - CRC32C, XOR fold, memcpy, φ-step and popcount are acceleration
     candidates; performance promotion requires measured receipts.
   ─────────────────────────────────────────────────────────────── */
#include "rmr_hw_detect.h"
#include "rmr_types.h"
#include "zero.h"
#include "zero_compat.h"

#if defined(__aarch64__)
#  if defined(__has_include)
#    if __has_include(<arm_neon.h>)
#      include <arm_neon.h>
#      define RMR_NEON_AVAILABLE 1
#    else
#      define RMR_NEON_AVAILABLE 0
#    endif
#  else
#    include <arm_neon.h>
#    define RMR_NEON_AVAILABLE 1
#  endif
#endif

#if defined(__aarch64__) && defined(RMR_NEON_AVAILABLE) && (RMR_NEON_AVAILABLE == 1)

u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    if (!data || len == 0u) return 0u;
    uint32x4_t acc = vdupq_n_u32(0u);
    u32 i = 0u;
    for (; i + 16u <= len; i += 16u) {
        const uint8x16_t v = vld1q_u8(data + i);
        acc = veorq_u32(acc, vreinterpretq_u32_u8(v));
    }
    u32 result = vgetq_lane_u32(acc, 0) ^ vgetq_lane_u32(acc, 1)
               ^ vgetq_lane_u32(acc, 2) ^ vgetq_lane_u32(acc, 3);
    for (; i < len; ++i) result ^= (u32)data[i];
    return result;
}

void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    u32 i = 0u;
    for (; i + 64u <= len; i += 64u) {
        const uint8x16x4_t v = vld1q_u8_x4(src + i);
        vst1q_u8_x4(dst + i, v);
    }
    for (; i + 16u <= len; i += 16u) {
        vst1q_u8(dst + i, vld1q_u8(src + i));
    }
    for (; i < len; ++i) dst[i] = src[i];
}

#if defined(__ARM_FEATURE_CRC32)
#  if defined(__has_include)
#    if __has_include(<arm_acle.h>)
#      include <arm_acle.h>
#      define RMR_ARM_CRC32_INTRINSICS_AVAILABLE 1
#    else
#      define RMR_ARM_CRC32_INTRINSICS_AVAILABLE 0
#    endif
#  else
#    include <arm_acle.h>
#    define RMR_ARM_CRC32_INTRINSICS_AVAILABLE 1
#  endif
#else
#  define RMR_ARM_CRC32_INTRINSICS_AVAILABLE 0
#endif

#if RMR_ARM_CRC32_INTRINSICS_AVAILABLE
static u32 rmr_arm_load_le32(const u8 *p) {
    return ((u32)p[0]) |
           ((u32)p[1] << 8) |
           ((u32)p[2] << 16) |
           ((u32)p[3] << 24);
}

static u64 rmr_arm_load_le64(const u8 *p) {
    return ((u64)rmr_arm_load_le32(p)) |
           ((u64)rmr_arm_load_le32(p + 4) << 32);
}

u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed, i = 0u;
    for (; i + 8u <= len; i += 8u) {
        crc = __crc32cd(crc, rmr_arm_load_le64(data + i));
    }
    for (; i + 4u <= len; i += 4u) {
        crc = __crc32cw(crc, rmr_arm_load_le32(data + i));
    }
    for (; i < len; ++i) crc = __crc32cb(crc, data[i]);
    return crc;
}
#else
u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed;
    for (u32 i = 0u; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0u; b < 8u; ++b) {
            const u32 mask = 0u - (crc & 1u);
            crc = (crc >> 1u) ^ (RMR_ZERO_CRC32C_POLY_U32 & mask);
        }
    }
    return crc;
}
#endif

void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    const uint32x4_t phi = vdupq_n_u32(RMR_ZERO_PHI32_U32);
    u32 i = 0u;
    for (; i + 4u <= count; i += 4u) {
        uint32x4_t s = vld1q_u32(states + i);
        s = vmulq_u32(s, phi);
        const uint32x4_t zero_mask = vceqq_u32(s, vdupq_n_u32(0u));
        s = vorrq_u32(s, vandq_u32(zero_mask, vdupq_n_u32(1u)));
        vst1q_u32(states + i, s);
    }
    for (; i < count; ++i) {
        states[i] = states[i] * RMR_ZERO_PHI32_U32;
        if (!states[i]) states[i] = 1u;
    }
}

u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    uint64x2_t acc = vdupq_n_u64(0u);
    u32 i = 0u;
    for (; i + 4u <= count; i += 4u) {
        const uint8x16_t v = vld1q_u8((const u8 *)(data + i));
        const uint8x16_t bits = vcntq_u8(v);
        acc = vpadalq_u32(acc, vpaddlq_u16(vpaddlq_u8(bits)));
    }
    u64 total = vgetq_lane_u64(acc, 0) + vgetq_lane_u64(acc, 1);
    for (; i < count; ++i) {
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        total += (u64)((v * 0x01010101u) >> 24u);
    }
    return (u32)total;
}

#elif defined(__x86_64__) || defined(__i386__)
#  if defined(__has_include)
#    if __has_include(<immintrin.h>)
#      include <immintrin.h>
#      define RMR_X86_INTRINSICS_AVAILABLE 1
#    else
#      define RMR_X86_INTRINSICS_AVAILABLE 0
#    endif
#  else
#    include <immintrin.h>
#    define RMR_X86_INTRINSICS_AVAILABLE 1
#  endif

#if RMR_X86_INTRINSICS_AVAILABLE
static u32 rmr_x86_load_le32(const u8 *p) {
    return ((u32)p[0]) |
           ((u32)p[1] << 8) |
           ((u32)p[2] << 16) |
           ((u32)p[3] << 24);
}

#if defined(__x86_64__)
static u64 rmr_x86_load_le64(const u8 *p) {
    return ((u64)rmr_x86_load_le32(p)) |
           ((u64)rmr_x86_load_le32(p + 4) << 32);
}
#endif

u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    if (!data || len == 0u) return 0u;
    u32 result = 0u, i = 0u;
#if defined(__SSE2__)
    __m128i acc = _mm_setzero_si128();
    for (; i + 16u <= len; i += 16u) {
        const __m128i v = _mm_loadu_si128((const __m128i *)(data + i));
        acc = _mm_xor_si128(acc, v);
    }
    u32 t[4];
    _mm_storeu_si128((__m128i *)t, acc);
    result = t[0] ^ t[1] ^ t[2] ^ t[3];
#endif
    for (; i < len; ++i) result ^= (u32)data[i];
    return result;
}

void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    for (u32 i = 0u; i < len; ++i) dst[i] = src[i];
}

u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
#if defined(__SSE4_2__)
    u32 crc = seed, i = 0u;
#if defined(__x86_64__)
    for (; i + 8u <= len; i += 8u) {
        crc = (u32)_mm_crc32_u64(crc, rmr_x86_load_le64(data + i));
    }
#endif
    for (; i + 4u <= len; i += 4u) {
        crc = _mm_crc32_u32(crc, rmr_x86_load_le32(data + i));
    }
    for (; i < len; ++i) crc = _mm_crc32_u8(crc, data[i]);
    return crc;
#else
    u32 crc = seed;
    for (u32 i = 0u; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0u; b < 8u; ++b) {
            const u32 mask = 0u - (crc & 1u);
            crc = (crc >> 1u) ^ (RMR_ZERO_CRC32C_POLY_U32 & mask);
        }
    }
    return crc;
#endif
}

void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    for (u32 i = 0u; i < count; ++i) {
        states[i] *= RMR_ZERO_PHI32_U32;
        if (!states[i]) states[i] = 1u;
    }
}

u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    u64 total = 0u;
    for (u32 i = 0u; i < count; ++i) {
#if defined(__POPCNT__)
        total += (u64)__builtin_popcount(data[i]);
#else
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        total += (u64)((v * 0x01010101u) >> 24u);
#endif
    }
    return (u32)total;
}
#else
u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    u32 r = 0u;
    for (u32 i = 0u; i < len; ++i) r ^= (u32)data[i];
    return r;
}
void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    for (u32 i = 0u; i < len; ++i) dst[i] = src[i];
}
u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed;
    for (u32 i = 0u; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0u; b < 8u; ++b) {
            const u32 mask = 0u - (crc & 1u);
            crc = (crc >> 1u) ^ (RMR_ZERO_CRC32C_POLY_U32 & mask);
        }
    }
    return crc;
}
void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    for (u32 i = 0u; i < count; ++i) {
        states[i] *= RMR_ZERO_PHI32_U32;
        if (!states[i]) states[i] = 1u;
    }
}
u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    u64 total = 0u;
    for (u32 i = 0u; i < count; ++i) {
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        total += (u64)((v * 0x01010101u) >> 24u);
    }
    return (u32)total;
}
#endif

#else
u32 rmr_neon_xor_fold32(const u8 *data, u32 len) {
    u32 r = 0u;
    for (u32 i = 0u; i < len; ++i) r ^= (u32)data[i];
    return r;
}
void rmr_neon_memcpy(u8 *dst, const u8 *src, u32 len) {
    for (u32 i = 0u; i < len; ++i) dst[i] = src[i];
}
u32 rmr_neon_crc32c(u32 seed, const u8 *data, u32 len) {
    u32 crc = seed;
    for (u32 i = 0u; i < len; ++i) {
        crc ^= data[i];
        for (u32 b = 0u; b < 8u; ++b) {
            const u32 mask = 0u - (crc & 1u);
            crc = (crc >> 1u) ^ (RMR_ZERO_CRC32C_POLY_U32 & mask);
        }
    }
    return crc;
}
void rmr_neon_phi_step_bulk(u32 *states, u32 count) {
    for (u32 i = 0u; i < count; ++i) {
        states[i] *= RMR_ZERO_PHI32_U32;
        if (!states[i]) states[i] = 1u;
    }
}
u32 rmr_neon_popcount_bulk(const u32 *data, u32 count) {
    u64 total = 0u;
    for (u32 i = 0u; i < count; ++i) {
        u32 v = data[i];
        v = v - ((v >> 1u) & 0x55555555u);
        v = (v & 0x33333333u) + ((v >> 2u) & 0x33333333u);
        v = (v + (v >> 4u)) & 0x0F0F0F0Fu;
        total += (u64)((v * 0x01010101u) >> 24u);
    }
    return (u32)total;
}
#endif
