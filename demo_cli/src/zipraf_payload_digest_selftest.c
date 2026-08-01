#include <stdio.h>
#include <string.h>

#include "rmr_zipraf_payload_digest.h"

#ifndef RMR_BLAKE3_PROVIDER_COMMIT
#define RMR_BLAKE3_PROVIDER_COMMIT "TOKEN_VAZIO_PROVIDER_COMMIT"
#endif

static unsigned checks = 0u;
static unsigned failures = 0u;

static void check(const char *name, int condition) {
  ++checks;
  if (!condition) {
    ++failures;
    fprintf(stderr, "FAIL %s\n", name);
  }
}

static uint8_t hex_nibble(char c) {
  if (c >= '0' && c <= '9') return (uint8_t)(c - '0');
  if (c >= 'a' && c <= 'f') return (uint8_t)(c - 'a' + 10);
  if (c >= 'A' && c <= 'F') return (uint8_t)(c - 'A' + 10);
  return 0xffu;
}

static int digest_equals_hex(const uint8_t digest[32], const char *hex) {
  size_t i;
  if (!digest || !hex || strlen(hex) != 64u) return 0;
  for (i = 0u; i < 32u; ++i) {
    const uint8_t hi = hex_nibble(hex[i * 2u]);
    const uint8_t lo = hex_nibble(hex[i * 2u + 1u]);
    if (hi > 15u || lo > 15u || digest[i] != (uint8_t)((hi << 4u) | lo)) return 0;
  }
  return 1;
}

int main(void) {
  static const uint8_t abc[] = {'a', 'b', 'c'};
  static const uint8_t archive[] = {0x99u, 0x88u, 'a', 'b', 'c', 0x77u};
  uint8_t sha_empty[32];
  uint8_t sha_abc[32];
  uint8_t b3_empty[32];
  uint8_t b3_abc[32];
  uint8_t entry_sha[32];
  uint8_t entry_b3[32];
  uint8_t mutated_sha[32];
  uint8_t mutated[sizeof(archive)];
  RmR_ZiprafArchiveEntry entry;
  int status;

  check("sha256_empty_status",
        RmR_ZiprafDigest_Sha256(NULL, 0u, sha_empty) == RMR_ZIPRAF_DIGEST_OK);
  check("sha256_empty_kat",
        digest_equals_hex(sha_empty,
          "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
  check("sha256_abc_status",
        RmR_ZiprafDigest_Sha256(abc, sizeof(abc), sha_abc) == RMR_ZIPRAF_DIGEST_OK);
  check("sha256_abc_kat",
        digest_equals_hex(sha_abc,
          "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));

  check("blake3_provider_version",
        strcmp(RmR_ZiprafDigest_Blake3ProviderVersion(), "1.8.2") == 0);
  check("blake3_empty_status",
        RmR_ZiprafDigest_Blake3External(NULL, 0u, b3_empty) == RMR_ZIPRAF_DIGEST_OK);
  check("blake3_empty_kat",
        digest_equals_hex(b3_empty,
          "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262"));
  check("blake3_abc_status",
        RmR_ZiprafDigest_Blake3External(abc, sizeof(abc), b3_abc) == RMR_ZIPRAF_DIGEST_OK);
  check("blake3_abc_kat",
        digest_equals_hex(b3_abc,
          "6437b3ac38465133ffb63b75273a8db548c558465d79db03fd359c6cd5bd9d85"));

  memset(&entry, 0, sizeof(entry));
  entry.payload_offset = 2u;
  entry.compressed_size = 3u;
  entry.uncompressed_size = 3u;
  entry.method = 0u;

  check("stored_entry_sha256_matches_abc",
        RmR_ZiprafArchive_DigestEntry(
          archive, sizeof(archive), &entry,
          RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES,
          RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256,
          entry_sha) == RMR_ZIPRAF_DIGEST_OK &&
        RmR_ZiprafDigest_EqualConstantTime(entry_sha, sha_abc));

  check("logical_store_blake3_matches_abc",
        RmR_ZiprafArchive_DigestEntry(
          archive, sizeof(archive), &entry,
          RMR_ZIPRAF_DIGEST_SCOPE_LOGICAL_BYTES,
          RMR_ZIPRAF_DIGEST_ALGORITHM_BLAKE3,
          entry_b3) == RMR_ZIPRAF_DIGEST_OK &&
        RmR_ZiprafDigest_EqualConstantTime(entry_b3, b3_abc));

  entry.method = 8u;
  status = RmR_ZiprafArchive_DigestEntry(
    archive, sizeof(archive), &entry,
    RMR_ZIPRAF_DIGEST_SCOPE_LOGICAL_BYTES,
    RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256,
    entry_sha);
  check("deflate_logical_digest_requires_materialization",
        status == RMR_ZIPRAF_DIGEST_ERR_MATERIALIZATION_REQUIRED);

  entry.method = 0u;
  entry.payload_offset = sizeof(archive);
  entry.compressed_size = 1u;
  check("entry_bounds_rejected",
        RmR_ZiprafArchive_DigestEntry(
          archive, sizeof(archive), &entry,
          RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES,
          RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256,
          entry_sha) == RMR_ZIPRAF_DIGEST_ERR_RANGE);

  entry.payload_offset = 2u;
  entry.compressed_size = 3u;
  check("unknown_algorithm_rejected",
        RmR_ZiprafArchive_DigestEntry(
          archive, sizeof(archive), &entry,
          RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES,
          99u,
          entry_sha) == RMR_ZIPRAF_DIGEST_ERR_ALGORITHM);

  memcpy(mutated, archive, sizeof(mutated));
  mutated[3] ^= 1u;
  check("single_bit_mutation_changes_sha256",
        RmR_ZiprafArchive_DigestEntry(
          mutated, sizeof(mutated), &entry,
          RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES,
          RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256,
          mutated_sha) == RMR_ZIPRAF_DIGEST_OK &&
        !RmR_ZiprafDigest_EqualConstantTime(mutated_sha, sha_abc));

  check("constant_time_equality_accepts_equal",
        RmR_ZiprafDigest_EqualConstantTime(sha_abc, sha_abc));
  check("constant_time_equality_rejects_different",
        !RmR_ZiprafDigest_EqualConstantTime(sha_abc, b3_abc));

  if (failures != 0u) return 1;
  printf("{\"gate\":\"ZIPRAF_PAYLOAD_DIGEST_U1_V1\","
         "\"status\":\"PASS\",\"checks\":%u,"
         "\"sha256_backend\":\"RMR_PORTABLE_C\","
         "\"blake3_backend\":\"EXTERNAL_PINNED_C_1.8.2\","
         "\"blake3_provider_commit\":\"%s\","
         "\"scope\":\"STORED_BYTES_AND_STORE_LOGICAL_BYTES_NO_DEFLATE_MATERIALIZATION\"}\n",
         checks, RMR_BLAKE3_PROVIDER_COMMIT);
  return 0;
}
