#include <stdio.h>
#include <string.h>

#include "rmr_zipraf_archive.h"
#include "rmr_zipraf_binding.h"

#define BUF_CAP 8192u
#define SPEC_COUNT 6u

static int failures = 0;

#define CHECK(name, expr) do { \
  if (!(expr)) { printf("FAIL %s\n", name); failures++; } \
  else { printf("PASS %s\n", name); } \
} while (0)

typedef struct {
  const char *name;
  uint16_t flags;
  uint16_t method;
  uint16_t extra_length;
  uint32_t compressed_size;
  uint32_t uncompressed_size;
  uint8_t fill;
} EntrySpec;

typedef struct {
  uint8_t bytes[BUF_CAP];
  size_t size;
  size_t eocd;
  size_t central;
  size_t central_record[SPEC_COUNT];
  size_t local[SPEC_COUNT];
  size_t payload[SPEC_COUNT];
} BuiltZip;

static void put16(uint8_t *p, uint16_t value) {
  p[0] = (uint8_t)value;
  p[1] = (uint8_t)(value >> 8);
}

static void put32(uint8_t *p, uint32_t value) {
  p[0] = (uint8_t)value;
  p[1] = (uint8_t)(value >> 8);
  p[2] = (uint8_t)(value >> 16);
  p[3] = (uint8_t)(value >> 24);
}

static const EntrySpec *specs(void) {
  static const EntrySpec data[SPEC_COUNT] = {
    {"code.bin", 0x0800u, 0u, 26u, 4u, 4u, 0x11u},
    {"plain002", 0x0000u, 0u, 0u, 5u, 5u, 0x22u},
    {"packed.bin", 0x0008u, 8u, 0u, 3u, 11u, 0x33u},
    {"../evil0", 0x0001u, 0u, 0u, 2u, 2u, 0x44u},
    {"dir/", 0x0000u, 0u, 0u, 0u, 0u, 0x00u},
    {"method99", 0x0000u, 99u, 0u, 1u, 1u, 0x55u}
  };
  return data;
}

static int build_zip(BuiltZip *out) {
  const EntrySpec *s = specs();
  size_t cursor = 0u;
  uint32_t i;
  if (!out) return 0;
  memset(out, 0, sizeof(*out));

  for (i = 0u; i < SPEC_COUNT; ++i) {
    const size_t name_len = strlen(s[i].name);
    size_t j;
    if (cursor + 30u + name_len + s[i].extra_length + s[i].compressed_size > BUF_CAP)
      return 0;
    out->local[i] = cursor;
    put32(out->bytes + cursor, 0x04034b50u);
    put16(out->bytes + cursor + 4u, 20u);
    put16(out->bytes + cursor + 6u, s[i].flags);
    put16(out->bytes + cursor + 8u, s[i].method);
    put32(out->bytes + cursor + 14u, 0x1000u + i);
    put32(out->bytes + cursor + 18u, (s[i].flags & 0x0008u) ? 0u : s[i].compressed_size);
    put32(out->bytes + cursor + 22u, (s[i].flags & 0x0008u) ? 0u : s[i].uncompressed_size);
    put16(out->bytes + cursor + 26u, (uint16_t)name_len);
    put16(out->bytes + cursor + 28u, s[i].extra_length);
    memcpy(out->bytes + cursor + 30u, s[i].name, name_len);
    cursor += 30u + name_len;
    for (j = 0u; j < s[i].extra_length; ++j) out->bytes[cursor + j] = (uint8_t)(0xa0u + j);
    cursor += s[i].extra_length;
    out->payload[i] = cursor;
    for (j = 0u; j < s[i].compressed_size; ++j) out->bytes[cursor + j] = (uint8_t)(s[i].fill + j);
    cursor += s[i].compressed_size;
    if ((s[i].flags & 0x0008u) != 0u) {
      if (cursor + 16u > BUF_CAP) return 0;
      put32(out->bytes + cursor, 0x08074b50u);
      put32(out->bytes + cursor + 4u, 0x1000u + i);
      put32(out->bytes + cursor + 8u, s[i].compressed_size);
      put32(out->bytes + cursor + 12u, s[i].uncompressed_size);
      cursor += 16u;
    }
  }

  out->central = cursor;
  for (i = 0u; i < SPEC_COUNT; ++i) {
    const size_t name_len = strlen(s[i].name);
    if (cursor + 46u + name_len > BUF_CAP) return 0;
    out->central_record[i] = cursor;
    put32(out->bytes + cursor, 0x02014b50u);
    put16(out->bytes + cursor + 4u, i == 5u ? 0x0314u : 20u);
    put16(out->bytes + cursor + 6u, 20u);
    put16(out->bytes + cursor + 8u, s[i].flags);
    put16(out->bytes + cursor + 10u, s[i].method);
    put32(out->bytes + cursor + 16u, 0x1000u + i);
    put32(out->bytes + cursor + 20u, s[i].compressed_size);
    put32(out->bytes + cursor + 24u, s[i].uncompressed_size);
    put16(out->bytes + cursor + 28u, (uint16_t)name_len);
    put16(out->bytes + cursor + 30u, 0u);
    put16(out->bytes + cursor + 32u, 0u);
    put16(out->bytes + cursor + 34u, 0u);
    put32(out->bytes + cursor + 38u, i == 5u ? (0120777u << 16) : 0u);
    put32(out->bytes + cursor + 42u, (uint32_t)out->local[i]);
    memcpy(out->bytes + cursor + 46u, s[i].name, name_len);
    cursor += 46u + name_len;
  }

  out->eocd = cursor;
  if (cursor + 22u > BUF_CAP) return 0;
  put32(out->bytes + cursor, 0x06054b50u);
  put16(out->bytes + cursor + 4u, 0u);
  put16(out->bytes + cursor + 6u, 0u);
  put16(out->bytes + cursor + 8u, SPEC_COUNT);
  put16(out->bytes + cursor + 10u, SPEC_COUNT);
  put32(out->bytes + cursor + 12u, (uint32_t)(out->eocd - out->central));
  put32(out->bytes + cursor + 16u, (uint32_t)out->central);
  put16(out->bytes + cursor + 20u, 0u);
  cursor += 22u;
  out->size = cursor;
  return 1;
}

static int parse(const BuiltZip *zip, RmR_ZiprafArchiveIndex *index) {
  return RmR_ZiprafArchive_Parse(zip->bytes, zip->size, 64u, index);
}

int main(void) {
  BuiltZip zip;
  BuiltZip bad;
  RmR_ZiprafArchiveIndex index;
  RmR_ZiprafArchiveIndex changed;
  RmR_ZiprafReadPlan plan;
  RmR_ZiprafArchiveTelemetry telemetry;
  RmR_ZiprafEntryBinding binding;
  RmR_ZiprafPageBlock block;
  uint32_t digest_i;
  int rc;

  CHECK("fixture_builds", build_zip(&zip));
  rc = parse(&zip, &index);
  CHECK("real_zip_layout_parses", rc == RMR_ZIPRAF_ARCHIVE_OK);
  CHECK("all_central_entries_bound", index.parsed_entries == SPEC_COUNT);
  CHECK("first_store_payload_is_64_aligned", index.entries[0].payload_offset == 64u);
  CHECK("store_aligned_is_layout_mappable",
        RmR_ZiprafArchive_ActionForEntry(&index.entries[0]) == RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT);
  CHECK("store_unaligned_requires_copy",
        RmR_ZiprafArchive_ActionForEntry(&index.entries[1]) == RMR_ZIPRAF_ACTION_COPY_STORE);
  CHECK("deflate_requires_materialization",
        RmR_ZiprafArchive_ActionForEntry(&index.entries[2]) == RMR_ZIPRAF_ACTION_DECOMPRESS);
  CHECK("unsafe_encrypted_name_is_rejected",
        (index.entries[3].state_flags & (RMR_ZIPRAF_ENTRY_UNSAFE_NAME | RMR_ZIPRAF_ENTRY_ENCRYPTED)) ==
          (RMR_ZIPRAF_ENTRY_UNSAFE_NAME | RMR_ZIPRAF_ENTRY_ENCRYPTED) &&
        RmR_ZiprafArchive_ActionForEntry(&index.entries[3]) == RMR_ZIPRAF_ACTION_REJECT);
  CHECK("directory_is_not_a_payload_task",
        (index.entries[4].state_flags & RMR_ZIPRAF_ENTRY_DIRECTORY) != 0u &&
        RmR_ZiprafArchive_ActionForEntry(&index.entries[4]) == RMR_ZIPRAF_ACTION_REJECT);
  CHECK("unsupported_symlink_method_is_rejected",
        (index.entries[5].state_flags & (RMR_ZIPRAF_ENTRY_UNSUPPORTED | RMR_ZIPRAF_ENTRY_SYMLINK)) ==
          (RMR_ZIPRAF_ENTRY_UNSUPPORTED | RMR_ZIPRAF_ENTRY_SYMLINK) &&
        RmR_ZiprafArchive_ActionForEntry(&index.entries[5]) == RMR_ZIPRAF_ACTION_REJECT);

  CHECK("deterministic_two_core_plan",
        RmR_ZiprafArchive_BuildReadPlan(&index, 0x5u, &plan) == RMR_ZIPRAF_ARCHIVE_OK &&
        plan.task_count == SPEC_COUNT && plan.phase_count == 3u &&
        plan.tasks[0].core_bit == 0x1u && plan.tasks[1].core_bit == 0x4u &&
        plan.tasks[2].read_direction == RMR_ZIPRAF_READ_REVERSE &&
        plan.tasks[4].read_direction == RMR_ZIPRAF_READ_FORWARD);
  CHECK("plan_accounting_separates_map_copy_decompress_reject",
        plan.direct_map_bytes == 4u &&
        plan.copied_store_bytes == 5u &&
        plan.decompress_input_bytes == 3u &&
        plan.decompress_output_bytes == 11u &&
        plan.rejected_bytes == 3u);

  CHECK("telemetry_summary_is_bounded",
        RmR_ZiprafArchive_Summarize(&index, &telemetry) == RMR_ZIPRAF_ARCHIVE_OK &&
        telemetry.entries_layout_mappable == 1u &&
        telemetry.entries_copy_store == 1u &&
        telemetry.entries_decompress == 1u &&
        telemetry.entries_rejected == 3u &&
        telemetry.materialized_input_bytes == 8u &&
        telemetry.materialized_output_bytes == 16u);

  memset(&binding, 0, sizeof(binding));
  binding.block_id = 77u;
  binding.alignment = 64u;
  binding.digest_kind = RMR_ZIPRAF_DIGEST_SHA256;
  binding.immutable = 1u;
  binding.digest_verified = 1u;
  binding.dma_candidate = 1u;
  for (digest_i = 0u; digest_i < RMR_ZIPRAF_DIGEST_BYTES; ++digest_i)
    binding.digest[digest_i] = (uint8_t)(digest_i + 1u);
  CHECK("real_entry_binds_to_page_graph_block",
        RmR_ZiprafArchive_BindEntry(&index.entries[0], &binding, &block) == RMR_ZIPRAF_ARCHIVE_OK &&
        block.block_id == 77u && block.archive_offset == 64u &&
        (block.flags & (RMR_ZIPRAF_BLOCK_DIRECT_MAP | RMR_ZIPRAF_BLOCK_DMA_CANDIDATE |
                        RMR_ZIPRAF_BLOCK_IMMUTABLE | RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED)) ==
          (RMR_ZIPRAF_BLOCK_DIRECT_MAP | RMR_ZIPRAF_BLOCK_DMA_CANDIDATE |
           RMR_ZIPRAF_BLOCK_IMMUTABLE | RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED) &&
        (block.flags & RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE) == 0u);

  binding.block_id = 78u;
  binding.dma_candidate = 0u;
  CHECK("deflate_binds_without_direct_map",
        RmR_ZiprafArchive_BindEntry(&index.entries[2], &binding, &block) == RMR_ZIPRAF_ARCHIVE_OK &&
        block.compression_method == 8u &&
        (block.flags & RMR_ZIPRAF_BLOCK_DIRECT_MAP) == 0u);

  binding.block_id = 79u;
  binding.dma_candidate = 1u;
  CHECK("deflate_cannot_be_promoted_to_dma",
        RmR_ZiprafArchive_BindEntry(&index.entries[2], &binding, &block) ==
          RMR_ZIPRAF_ARCHIVE_ERR_POLICY);

  binding.block_id = 80u;
  binding.dma_candidate = 0u;
  CHECK("unsafe_entry_cannot_bind",
        RmR_ZiprafArchive_BindEntry(&index.entries[3], &binding, &block) ==
          RMR_ZIPRAF_ARCHIVE_ERR_POLICY);

  CHECK("safe_name_accepts_nested_path",
        RmR_ZiprafArchive_IsSafeName((const uint8_t *)"a/b/c.bin", 9u));
  CHECK("safe_name_rejects_dotdot",
        !RmR_ZiprafArchive_IsSafeName((const uint8_t *)"a/../c", 6u));
  CHECK("safe_name_rejects_absolute",
        !RmR_ZiprafArchive_IsSafeName((const uint8_t *)"/root", 5u));
  CHECK("safe_name_rejects_drive_prefix",
        !RmR_ZiprafArchive_IsSafeName((const uint8_t *)"C:\\x", 4u));
  CHECK("safe_name_rejects_reserved_device",
        !RmR_ZiprafArchive_IsSafeName((const uint8_t *)"dir/NUL.txt", 11u));
  CHECK("safe_name_rejects_trailing_dot",
        !RmR_ZiprafArchive_IsSafeName((const uint8_t *)"dir/file.", 9u));

  bad = zip;
  put32(bad.bytes + bad.eocd, 0u);
  CHECK("fault_eocd_signature_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE);

  bad = zip;
  put16(bad.bytes + bad.eocd + 4u, 1u);
  CHECK("fault_multidisk_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_MULTIDISK);

  bad = zip;
  put16(bad.bytes + bad.eocd + 8u, 0xffffu);
  put16(bad.bytes + bad.eocd + 10u, 0xffffu);
  CHECK("fault_zip64_sentinel_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_ZIP64);

  bad = zip;
  put32(bad.bytes + bad.eocd + 16u, (uint32_t)bad.eocd + 1u);
  CHECK("fault_central_range_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_RANGE);

  bad = zip;
  put32(bad.bytes + bad.central, 0u);
  CHECK("fault_central_signature_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE);

  bad = zip;
  put32(bad.bytes + bad.local[1], 0u);
  CHECK("fault_local_signature_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE);

  bad = zip;
  put16(bad.bytes + bad.local[1] + 8u, 8u);
  CHECK("fault_local_central_mismatch_rejected",
        parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH);

  bad = zip;
  put32(bad.bytes + bad.central_record[0] + 20u, 100u);
  put32(bad.bytes + bad.central_record[0] + 24u, 100u);
  put32(bad.bytes + bad.local[0] + 18u, 100u);
  put32(bad.bytes + bad.local[0] + 22u, 100u);
  CHECK("fault_overlapping_payload_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_OVERLAP);

  bad = zip;
  put16(bad.bytes + bad.central_record[0] + 28u, 300u);
  CHECK("fault_name_capacity_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_NAME);

  bad = zip;
  put32(bad.bytes + bad.central_record[1] + 42u, (uint32_t)bad.local[0]);
  put16(bad.bytes + bad.central_record[1] + 8u, 0x0800u);
  put32(bad.bytes + bad.central_record[1] + 16u, 0x1000u);
  put32(bad.bytes + bad.central_record[1] + 20u, 4u);
  put32(bad.bytes + bad.central_record[1] + 24u, 4u);
  memcpy(bad.bytes + bad.central_record[1] + 46u, "code.bin", 8u);
  CHECK("fault_duplicate_local_header_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_DUPLICATE);

  bad = zip;
  bad.bytes[bad.central_record[1] + 46u] ^= 1u;
  CHECK("fault_name_binding_rejected", parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH);

  bad = zip;
  bad.bytes[bad.payload[2] + 3u + 4u] ^= 1u;
  CHECK("fault_data_descriptor_binding_rejected",
        parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH);

  bad = zip;
  memcpy(bad.bytes + bad.local[1] + 30u, "CODE.BIN", 8u);
  memcpy(bad.bytes + bad.central_record[1] + 46u, "CODE.BIN", 8u);
  CHECK("fault_portable_name_collision_rejected",
        parse(&bad, &changed) == RMR_ZIPRAF_ARCHIVE_ERR_DUPLICATE);

  CHECK("layout_fingerprint_is_deterministic",
        parse(&zip, &changed) == RMR_ZIPRAF_ARCHIVE_OK &&
        changed.layout_fingerprint64 == index.layout_fingerprint64 &&
        changed.layout_fingerprint64 != 0u);

  if (failures != 0) return 1;
  puts("ZIPRAF_ARCHIVE_BINDING_KAT PASS");
  return 0;
}
