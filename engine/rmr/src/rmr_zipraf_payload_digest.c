// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_zipraf_payload_digest.h"

#include <limits.h>
#include <string.h>

#include "blake3.h"

#ifndef RMR_BLAKE3_PROVIDER_COMMIT
#define RMR_BLAKE3_PROVIDER_COMMIT "TOKEN_VAZIO_PROVIDER_COMMIT"
#endif

static const uint32_t rmr_sha256_k[64] = {
  UINT32_C(0x428a2f98), UINT32_C(0x71374491), UINT32_C(0xb5c0fbcf), UINT32_C(0xe9b5dba5),
  UINT32_C(0x3956c25b), UINT32_C(0x59f111f1), UINT32_C(0x923f82a4), UINT32_C(0xab1c5ed5),
  UINT32_C(0xd807aa98), UINT32_C(0x12835b01), UINT32_C(0x243185be), UINT32_C(0x550c7dc3),
  UINT32_C(0x72be5d74), UINT32_C(0x80deb1fe), UINT32_C(0x9bdc06a7), UINT32_C(0xc19bf174),
  UINT32_C(0xe49b69c1), UINT32_C(0xefbe4786), UINT32_C(0x0fc19dc6), UINT32_C(0x240ca1cc),
  UINT32_C(0x2de92c6f), UINT32_C(0x4a7484aa), UINT32_C(0x5cb0a9dc), UINT32_C(0x76f988da),
  UINT32_C(0x983e5152), UINT32_C(0xa831c66d), UINT32_C(0xb00327c8), UINT32_C(0xbf597fc7),
  UINT32_C(0xc6e00bf3), UINT32_C(0xd5a79147), UINT32_C(0x06ca6351), UINT32_C(0x14292967),
  UINT32_C(0x27b70a85), UINT32_C(0x2e1b2138), UINT32_C(0x4d2c6dfc), UINT32_C(0x53380d13),
  UINT32_C(0x650a7354), UINT32_C(0x766a0abb), UINT32_C(0x81c2c92e), UINT32_C(0x92722c85),
  UINT32_C(0xa2bfe8a1), UINT32_C(0xa81a664b), UINT32_C(0xc24b8b70), UINT32_C(0xc76c51a3),
  UINT32_C(0xd192e819), UINT32_C(0xd6990624), UINT32_C(0xf40e3585), UINT32_C(0x106aa070),
  UINT32_C(0x19a4c116), UINT32_C(0x1e376c08), UINT32_C(0x2748774c), UINT32_C(0x34b0bcb5),
  UINT32_C(0x391c0cb3), UINT32_C(0x4ed8aa4a), UINT32_C(0x5b9cca4f), UINT32_C(0x682e6ff3),
  UINT32_C(0x748f82ee), UINT32_C(0x78a5636f), UINT32_C(0x84c87814), UINT32_C(0x8cc70208),
  UINT32_C(0x90befffa), UINT32_C(0xa4506ceb), UINT32_C(0xbef9a3f7), UINT32_C(0xc67178f2)
};

static uint32_t rmr_rotr32(uint32_t value, uint32_t count) {
  return (value >> count) | (value << (32u - count));
}

static uint32_t rmr_load_be32(const uint8_t *p) {
  return ((uint32_t)p[0] << 24) |
         ((uint32_t)p[1] << 16) |
         ((uint32_t)p[2] << 8) |
         (uint32_t)p[3];
}

static void rmr_store_be32(uint8_t *p, uint32_t value) {
  p[0] = (uint8_t)(value >> 24);
  p[1] = (uint8_t)(value >> 16);
  p[2] = (uint8_t)(value >> 8);
  p[3] = (uint8_t)value;
}

static void rmr_sha256_compress(uint32_t state[8], const uint8_t block[64]) {
  uint32_t w[64];
  uint32_t a;
  uint32_t b;
  uint32_t c;
  uint32_t d;
  uint32_t e;
  uint32_t f;
  uint32_t g;
  uint32_t h;
  uint32_t i;

  for (i = 0u; i < 16u; ++i) w[i] = rmr_load_be32(block + i * 4u);
  for (i = 16u; i < 64u; ++i) {
    const uint32_t s0 = rmr_rotr32(w[i - 15u], 7u) ^
                        rmr_rotr32(w[i - 15u], 18u) ^
                        (w[i - 15u] >> 3u);
    const uint32_t s1 = rmr_rotr32(w[i - 2u], 17u) ^
                        rmr_rotr32(w[i - 2u], 19u) ^
                        (w[i - 2u] >> 10u);
    w[i] = w[i - 16u] + s0 + w[i - 7u] + s1;
  }

  a = state[0];
  b = state[1];
  c = state[2];
  d = state[3];
  e = state[4];
  f = state[5];
  g = state[6];
  h = state[7];

  for (i = 0u; i < 64u; ++i) {
    const uint32_t s1 = rmr_rotr32(e, 6u) ^ rmr_rotr32(e, 11u) ^ rmr_rotr32(e, 25u);
    const uint32_t choose = (e & f) ^ ((~e) & g);
    const uint32_t temp1 = h + s1 + choose + rmr_sha256_k[i] + w[i];
    const uint32_t s0 = rmr_rotr32(a, 2u) ^ rmr_rotr32(a, 13u) ^ rmr_rotr32(a, 22u);
    const uint32_t majority = (a & b) ^ (a & c) ^ (b & c);
    const uint32_t temp2 = s0 + majority;
    h = g;
    g = f;
    f = e;
    e = d + temp1;
    d = c;
    c = b;
    b = a;
    a = temp1 + temp2;
  }

  state[0] += a;
  state[1] += b;
  state[2] += c;
  state[3] += d;
  state[4] += e;
  state[5] += f;
  state[6] += g;
  state[7] += h;
}

int RmR_ZiprafDigest_Sha256(const void *data,
                            size_t length,
                            uint8_t out_digest[RMR_ZIPRAF_DIGEST_BYTES]) {
  static const uint32_t initial[8] = {
    UINT32_C(0x6a09e667), UINT32_C(0xbb67ae85), UINT32_C(0x3c6ef372), UINT32_C(0xa54ff53a),
    UINT32_C(0x510e527f), UINT32_C(0x9b05688c), UINT32_C(0x1f83d9ab), UINT32_C(0x5be0cd19)
  };
  uint32_t state[8];
  uint8_t tail[128];
  const uint8_t *bytes = (const uint8_t *)data;
  size_t full_blocks;
  size_t remainder;
  size_t tail_length;
  uint64_t bit_length;
  size_t i;

  if ((!data && length != 0u) || !out_digest) return RMR_ZIPRAF_DIGEST_ERR_ARG;
  if (length > (size_t)(UINT64_MAX / UINT64_C(8))) return RMR_ZIPRAF_DIGEST_ERR_RANGE;

  memcpy(state, initial, sizeof(state));
  full_blocks = length / 64u;
  remainder = length % 64u;
  for (i = 0u; i < full_blocks; ++i) rmr_sha256_compress(state, bytes + i * 64u);

  memset(tail, 0, sizeof(tail));
  if (remainder != 0u) memcpy(tail, bytes + full_blocks * 64u, remainder);
  tail[remainder] = 0x80u;
  tail_length = remainder < 56u ? 64u : 128u;
  bit_length = (uint64_t)length * UINT64_C(8);
  for (i = 0u; i < 8u; ++i)
    tail[tail_length - 1u - i] = (uint8_t)(bit_length >> (i * 8u));
  rmr_sha256_compress(state, tail);
  if (tail_length == 128u) rmr_sha256_compress(state, tail + 64u);

  for (i = 0u; i < 8u; ++i) rmr_store_be32(out_digest + i * 4u, state[i]);
  memset(state, 0, sizeof(state));
  memset(tail, 0, sizeof(tail));
  return RMR_ZIPRAF_DIGEST_OK;
}

int RmR_ZiprafDigest_Blake3External(
    const void *data,
    size_t length,
    uint8_t out_digest[RMR_ZIPRAF_DIGEST_BYTES]) {
  blake3_hasher hasher;
  if ((!data && length != 0u) || !out_digest) return RMR_ZIPRAF_DIGEST_ERR_ARG;
  if (strcmp(RMR_BLAKE3_PROVIDER_COMMIT, "TOKEN_VAZIO_PROVIDER_COMMIT") == 0)
    return RMR_ZIPRAF_DIGEST_ERR_PROVIDER;
  blake3_hasher_init(&hasher);
  blake3_hasher_update(&hasher, data, length);
  blake3_hasher_finalize(&hasher, out_digest, RMR_ZIPRAF_DIGEST_BYTES);
  memset(&hasher, 0, sizeof(hasher));
  return RMR_ZIPRAF_DIGEST_OK;
}

const char *RmR_ZiprafDigest_Blake3ProviderVersion(void) {
  return blake3_version();
}

static int rmr_digest_bounds(size_t archive_size,
                             const RmR_ZiprafArchiveEntry *entry,
                             size_t *out_offset,
                             size_t *out_length) {
  size_t offset;
  size_t length;
  if (!entry || !out_offset || !out_length) return RMR_ZIPRAF_DIGEST_ERR_ARG;
  if (entry->payload_offset > (uint64_t)SIZE_MAX ||
      entry->compressed_size > (uint64_t)SIZE_MAX)
    return RMR_ZIPRAF_DIGEST_ERR_RANGE;
  offset = (size_t)entry->payload_offset;
  length = (size_t)entry->compressed_size;
  if (offset > archive_size || length > archive_size - offset)
    return RMR_ZIPRAF_DIGEST_ERR_RANGE;
  *out_offset = offset;
  *out_length = length;
  return RMR_ZIPRAF_DIGEST_OK;
}

int RmR_ZiprafArchive_DigestEntry(
    const uint8_t *archive,
    size_t archive_size,
    const RmR_ZiprafArchiveEntry *entry,
    uint8_t scope,
    uint8_t algorithm,
    uint8_t out_digest[RMR_ZIPRAF_DIGEST_BYTES]) {
  size_t offset;
  size_t length;
  int status;
  if ((!archive && archive_size != 0u) || !entry || !out_digest)
    return RMR_ZIPRAF_DIGEST_ERR_ARG;
  status = rmr_digest_bounds(archive_size, entry, &offset, &length);
  if (status != RMR_ZIPRAF_DIGEST_OK) return status;

  if (scope == RMR_ZIPRAF_DIGEST_SCOPE_LOGICAL_BYTES) {
    if (entry->method != 0u || entry->compressed_size != entry->uncompressed_size)
      return RMR_ZIPRAF_DIGEST_ERR_MATERIALIZATION_REQUIRED;
  } else if (scope != RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES) {
    return RMR_ZIPRAF_DIGEST_ERR_ARG;
  }

  if (algorithm == RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256)
    return RmR_ZiprafDigest_Sha256(archive + offset, length, out_digest);
  if (algorithm == RMR_ZIPRAF_DIGEST_ALGORITHM_BLAKE3)
    return RmR_ZiprafDigest_Blake3External(archive + offset, length, out_digest);
  return RMR_ZIPRAF_DIGEST_ERR_ALGORITHM;
}

int RmR_ZiprafDigest_EqualConstantTime(
    const uint8_t left[RMR_ZIPRAF_DIGEST_BYTES],
    const uint8_t right[RMR_ZIPRAF_DIGEST_BYTES]) {
  uint8_t difference = 0u;
  size_t i;
  if (!left || !right) return 0;
  for (i = 0u; i < RMR_ZIPRAF_DIGEST_BYTES; ++i)
    difference |= (uint8_t)(left[i] ^ right[i]);
  return difference == 0u;
}
