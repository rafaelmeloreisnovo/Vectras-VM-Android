// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef BITRAF_H
#define BITRAF_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Stable public API for Bitraf engine.
 * Internal engine structs are intentionally hidden.
 */

/* Initializes Bitraf runtime seed state. Returns 0 on success. */
int bitraf_init(uint64_t seed);

/* Deterministic content hash for integrity and signatures. */
uint64_t bitraf_hash(const uint8_t *data, size_t len, uint64_t seed);

/*
 * Encodes input into Bitraf frame format.
 * Returns written bytes, or 0 on error/capacity overflow.
 */
size_t bitraf_compress(const uint8_t *in, size_t in_len,
                       uint8_t *out, size_t out_cap,
                       uint64_t seed);

/*
 * Decodes Bitraf frame back to original content.
 * Returns written bytes, or 0 on error/invalid frame.
 */
size_t bitraf_reconstruct(const uint8_t *in, size_t in_len,
                          uint8_t *out, size_t out_cap,
                          uint64_t seed);

enum {
  BITRAF_RECON_MODE_STRICT = 0,
  BITRAF_RECON_MODE_REPORT = 1
};

enum {
  BITRAF_RECON_STATUS_OK = 0,
  BITRAF_RECON_STATUS_FRAME = 1,
  BITRAF_RECON_STATUS_CHUNK = 2,
  BITRAF_RECON_STATUS_HASH = 3
};

typedef struct bitraf_diag {
  uint32_t status;
  size_t error_offset;
  size_t chunk_index;
  size_t bad_chunk_count;
  uint32_t expected_checksum;
  uint32_t actual_checksum;
} bitraf_diag;

/*
 * Extended decode with diagnostics.
 * strict: first chunk/hash failure returns 0.
 * report: reconstructs buffer and reports chunk/hash failures via diag.
 */
size_t bitraf_reconstruct_ex(const uint8_t *in, size_t in_len,
                             uint8_t *out, size_t out_cap,
                             uint64_t seed, int mode,
                             bitraf_diag *diag);

/*
 * Verifies content hash against expected hash.
 * Returns 1 when valid, 0 otherwise.
 */
int bitraf_verify(const uint8_t *data, size_t len,
                  uint64_t expected_hash, uint64_t seed);

#ifdef __cplusplus
}
#endif

#endif
