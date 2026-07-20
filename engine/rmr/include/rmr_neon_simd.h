// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_NEON_SIMD_H
#define RMR_NEON_SIMD_H
/* ────────────────────────────────────────────────────────────
   rmr_neon_simd.h — RAFAELIA NEON/SIMD Acceleration API
   Multi-arch: ARM64 NEON | x86 SSE4.2/AVX2 | scalar fallback
   ──────────────────────────────────────────────────────────── */
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Bulk XOR fold → 32-bit result (plain parity-style XOR reduction).
 *
 * NOTE: this contract differs from rmr_lowlevel_reduce_xor(), which performs
 * lane placement plus rotl32(acc, 3) per byte. Low-level JNI backend reduce_xor
 * is standardized on rmr_lowlevel_reduce_xor semantics.
 */
uint32_t rmr_neon_xor_fold32(const uint8_t *data, uint32_t len);

/** Optimized bulk memcpy (NEON 64-byte per cycle, SSE 128-bit). */
void rmr_neon_memcpy(uint8_t *dst, const uint8_t *src, uint32_t len);

/** Hardware CRC32C (ARM crc32cb/crc32cd; x86 _mm_crc32_u8/u64). */
uint32_t rmr_neon_crc32c(uint32_t seed, const uint8_t *data, uint32_t len);

/** Vectorized φ-step: multiply all states by PHI32 = 0x9E3779B9. */
void rmr_neon_phi_step_bulk(uint32_t *states, uint32_t count);

/** Bulk popcount across array of uint32_t words. */
uint32_t rmr_neon_popcount_bulk(const uint32_t *data, uint32_t count);

#ifdef __cplusplus
}
#endif
#endif /* RMR_NEON_SIMD_H */
