// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_zipraf_archive.h"

#include <string.h>

#define RMR_ZIP_LOCAL_SIG 0x04034b50u
#define RMR_ZIP_CENTRAL_SIG 0x02014b50u
#define RMR_ZIP_EOCD_SIG 0x06054b50u
#define RMR_ZIP_EOCD_MIN 22u
#define RMR_ZIP_MAX_COMMENT 65535u

static uint16_t rmr_le16(const uint8_t *p) {
  return (uint16_t)((uint16_t)p[0] | ((uint16_t)p[1] << 8));
}

static uint32_t rmr_le32(const uint8_t *p) {
  return (uint32_t)p[0] |
         ((uint32_t)p[1] << 8) |
         ((uint32_t)p[2] << 16) |
         ((uint32_t)p[3] << 24);
}

static int rmr_add_size(size_t a, size_t b, size_t *out) {
  if (!out || SIZE_MAX - a < b) return 1;
  *out = a + b;
  return 0;
}

static int rmr_range(size_t offset, size_t length, size_t limit) {
  size_t end;
  return !rmr_add_size(offset, length, &end) && end <= limit;
}

static int rmr_pow2_u32(uint32_t value) {
  return value != 0u && (value & (value - 1u)) == 0u;
}

static uint64_t rmr_fnv1a64(const uint8_t *data, size_t len, uint64_t state) {
  size_t i;
  uint64_t h = state;
  for (i = 0u; i < len; ++i) {
    h ^= (uint64_t)data[i];
    h *= UINT64_C(1099511628211);
  }
  return h;
}

static uint64_t rmr_fnv1a64_u64(uint64_t value, uint64_t state) {
  uint8_t bytes[8];
  uint32_t i;
  for (i = 0u; i < 8u; ++i) bytes[i] = (uint8_t)(value >> (i * 8u));
  return rmr_fnv1a64(bytes, sizeof(bytes), state);
}

static int rmr_find_eocd(const uint8_t *archive, size_t archive_size, size_t *out_offset) {
  size_t floor;
  size_t pos;
  if (!archive || !out_offset || archive_size < RMR_ZIP_EOCD_MIN)
    return RMR_ZIPRAF_ARCHIVE_ERR_TRUNCATED;
  floor = archive_size > (RMR_ZIP_EOCD_MIN + RMR_ZIP_MAX_COMMENT)
            ? archive_size - (RMR_ZIP_EOCD_MIN + RMR_ZIP_MAX_COMMENT)
            : 0u;
  pos = archive_size - RMR_ZIP_EOCD_MIN;
  for (;;) {
    if (rmr_le32(archive + pos) == RMR_ZIP_EOCD_SIG) {
      const uint16_t comment_length = rmr_le16(archive + pos + 20u);
      size_t end;
      if (!rmr_add_size(pos, RMR_ZIP_EOCD_MIN + (size_t)comment_length, &end) &&
          end == archive_size) {
        *out_offset = pos;
        return RMR_ZIPRAF_ARCHIVE_OK;
      }
    }
    if (pos == floor) break;
    --pos;
  }
  return RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE;
}

static int rmr_segment_is_dotdot(const uint8_t *name, size_t start, size_t end) {
  return end - start == 2u && name[start] == (uint8_t)'.' && name[start + 1u] == (uint8_t)'.';
}

static uint8_t rmr_ascii_lower(uint8_t c) {
  if (c >= (uint8_t)'A' && c <= (uint8_t)'Z') return (uint8_t)(c + ((uint8_t)'a' - (uint8_t)'A'));
  return c;
}

static int rmr_segment_is_reserved(const uint8_t *name, size_t start, size_t end) {
  uint8_t folded[8];
  size_t base_end = start;
  size_t length;
  size_t i;
  while (base_end < end && name[base_end] != (uint8_t)'.') ++base_end;
  length = base_end - start;
  if (length == 0u || length > sizeof(folded)) return 0;
  for (i = 0u; i < length; ++i) folded[i] = rmr_ascii_lower(name[start + i]);
  if (length == 3u &&
      ((folded[0] == 'c' && folded[1] == 'o' && folded[2] == 'n') ||
       (folded[0] == 'p' && folded[1] == 'r' && folded[2] == 'n') ||
       (folded[0] == 'a' && folded[1] == 'u' && folded[2] == 'x') ||
       (folded[0] == 'n' && folded[1] == 'u' && folded[2] == 'l')))
    return 1;
  if (length == 4u &&
      ((folded[0] == 'c' && folded[1] == 'o' && folded[2] == 'm') ||
       (folded[0] == 'l' && folded[1] == 'p' && folded[2] == 't')) &&
      folded[3] >= '1' && folded[3] <= '9')
    return 1;
  return 0;
}

static int rmr_names_portably_equal(const RmR_ZiprafArchiveEntry *a,
                                    const RmR_ZiprafArchiveEntry *b) {
  size_t i;
  if (a->name_length != b->name_length) return 0;
  for (i = 0u; i < a->name_length; ++i) {
    uint8_t ac = (uint8_t)a->name[i];
    uint8_t bc = (uint8_t)b->name[i];
    if (ac == (uint8_t)'\\') ac = (uint8_t)'/';
    if (bc == (uint8_t)'\\') bc = (uint8_t)'/';
    if (rmr_ascii_lower(ac) != rmr_ascii_lower(bc)) return 0;
  }
  return 1;
}

int RmR_ZiprafArchive_IsSafeName(const uint8_t *name, size_t name_length) {
  size_t i;
  size_t segment_start = 0u;
  if (!name || name_length == 0u) return 0;
  if (name[0] == (uint8_t)'/' || name[0] == (uint8_t)'\\') return 0;
  if (name_length >= 2u &&
      ((name[0] >= (uint8_t)'A' && name[0] <= (uint8_t)'Z') ||
       (name[0] >= (uint8_t)'a' && name[0] <= (uint8_t)'z')) &&
      name[1] == (uint8_t)':') return 0;

  for (i = 0u; i <= name_length; ++i) {
    const int at_end = i == name_length;
    const int separator = !at_end && (name[i] == (uint8_t)'/' || name[i] == (uint8_t)'\\');
    if (!at_end && name[i] == 0u) return 0;
    if (at_end || separator) {
      const size_t segment_length = i - segment_start;
      if (segment_length == 0u) {
        if (at_end && segment_start == name_length && name_length > 0u) break;
        return 0;
      }
      if ((segment_length == 1u && name[segment_start] == (uint8_t)'.') ||
          rmr_segment_is_dotdot(name, segment_start, i) ||
          name[i - 1u] == (uint8_t)' ' || name[i - 1u] == (uint8_t)'.' ||
          rmr_segment_is_reserved(name, segment_start, i))
        return 0;
      segment_start = i + 1u;
    }
  }
  return 1;
}

static int rmr_payloads_overlap(const RmR_ZiprafArchiveEntry *a,
                                const RmR_ZiprafArchiveEntry *b) {
  uint64_t a_end;
  uint64_t b_end;
  if (a->compressed_size == 0u || b->compressed_size == 0u) return 0;
  if (UINT64_MAX - a->payload_offset < a->compressed_size ||
      UINT64_MAX - b->payload_offset < b->compressed_size) return 1;
  a_end = a->payload_offset + a->compressed_size;
  b_end = b->payload_offset + b->compressed_size;
  return a->payload_offset < b_end && b->payload_offset < a_end;
}

uint8_t RmR_ZiprafArchive_ActionForEntry(const RmR_ZiprafArchiveEntry *entry) {
  if (!entry) return RMR_ZIPRAF_ACTION_REJECT;
  if ((entry->state_flags & (RMR_ZIPRAF_ENTRY_ENCRYPTED |
                             RMR_ZIPRAF_ENTRY_UNSAFE_NAME |
                             RMR_ZIPRAF_ENTRY_DIRECTORY |
                             RMR_ZIPRAF_ENTRY_UNSUPPORTED |
                             RMR_ZIPRAF_ENTRY_SYMLINK)) != 0u)
    return RMR_ZIPRAF_ACTION_REJECT;
  if ((entry->state_flags & RMR_ZIPRAF_ENTRY_LAYOUT_MAPPABLE) != 0u)
    return RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT;
  if (entry->method == 0u) return RMR_ZIPRAF_ACTION_COPY_STORE;
  if (entry->method == 8u) return RMR_ZIPRAF_ACTION_DECOMPRESS;
  return RMR_ZIPRAF_ACTION_REJECT;
}

int RmR_ZiprafArchive_Parse(const uint8_t *archive,
                            size_t archive_size,
                            uint32_t requested_alignment,
                            RmR_ZiprafArchiveIndex *out_index) {
  size_t eocd_offset;
  size_t central_offset;
  size_t central_size;
  size_t cursor;
  size_t central_end;
  uint16_t disk_number;
  uint16_t central_disk;
  uint16_t entries_disk;
  uint16_t entries_total;
  uint32_t i;
  uint64_t fingerprint = UINT64_C(14695981039346656037);
  int result;

  if (!archive || !out_index || !rmr_pow2_u32(requested_alignment))
    return RMR_ZIPRAF_ARCHIVE_ERR_ARG;
  memset(out_index, 0, sizeof(*out_index));

  result = rmr_find_eocd(archive, archive_size, &eocd_offset);
  if (result != RMR_ZIPRAF_ARCHIVE_OK) return result;

  disk_number = rmr_le16(archive + eocd_offset + 4u);
  central_disk = rmr_le16(archive + eocd_offset + 6u);
  entries_disk = rmr_le16(archive + eocd_offset + 8u);
  entries_total = rmr_le16(archive + eocd_offset + 10u);
  central_size = (size_t)rmr_le32(archive + eocd_offset + 12u);
  central_offset = (size_t)rmr_le32(archive + eocd_offset + 16u);

  if (disk_number != 0u || central_disk != 0u || entries_disk != entries_total)
    return RMR_ZIPRAF_ARCHIVE_ERR_MULTIDISK;
  if (entries_total == UINT16_MAX || central_size == UINT32_MAX || central_offset == UINT32_MAX)
    return RMR_ZIPRAF_ARCHIVE_ERR_ZIP64;
  if (entries_total > RMR_ZIPRAF_ARCHIVE_MAX_ENTRIES)
    return RMR_ZIPRAF_ARCHIVE_ERR_CAPACITY;
  if (!rmr_range(central_offset, central_size, archive_size) ||
      rmr_add_size(central_offset, central_size, &central_end) ||
      central_end > eocd_offset)
    return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;

  out_index->archive_size = (uint64_t)archive_size;
  out_index->eocd_offset = (uint64_t)eocd_offset;
  out_index->central_directory_offset = (uint64_t)central_offset;
  out_index->central_directory_size = (uint64_t)central_size;
  out_index->declared_entries = (uint32_t)entries_total;

  cursor = central_offset;
  for (i = 0u; i < (uint32_t)entries_total; ++i) {
    RmR_ZiprafArchiveEntry *entry = &out_index->entries[i];
    size_t record_size;
    size_t local_offset;
    size_t payload_offset;
    uint16_t name_length;
    uint16_t extra_length;
    uint16_t comment_length;
    uint16_t local_name_length;
    uint16_t local_extra_length;
    uint16_t local_flags;
    uint16_t local_method;
    uint16_t disk_start;
    uint16_t version_made_by;
    uint32_t external_attributes;
    uint32_t local_crc32;
    uint32_t local_compressed32;
    uint32_t local_uncompressed32;
    uint32_t compressed32;
    uint32_t uncompressed32;
    uint32_t local_offset32;
    uint32_t j;

    if (!rmr_range(cursor, 46u, central_end)) return RMR_ZIPRAF_ARCHIVE_ERR_TRUNCATED;
    if (rmr_le32(archive + cursor) != RMR_ZIP_CENTRAL_SIG)
      return RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE;

    entry->central_header_offset = (uint64_t)cursor;
    version_made_by = rmr_le16(archive + cursor + 4u);
    entry->general_purpose_flags = rmr_le16(archive + cursor + 8u);
    entry->method = rmr_le16(archive + cursor + 10u);
    entry->crc32 = rmr_le32(archive + cursor + 16u);
    compressed32 = rmr_le32(archive + cursor + 20u);
    uncompressed32 = rmr_le32(archive + cursor + 24u);
    name_length = rmr_le16(archive + cursor + 28u);
    extra_length = rmr_le16(archive + cursor + 30u);
    comment_length = rmr_le16(archive + cursor + 32u);
    disk_start = rmr_le16(archive + cursor + 34u);
    external_attributes = rmr_le32(archive + cursor + 38u);
    entry->external_attributes = external_attributes;
    local_offset32 = rmr_le32(archive + cursor + 42u);

    if (compressed32 == UINT32_MAX || uncompressed32 == UINT32_MAX ||
        local_offset32 == UINT32_MAX)
      return RMR_ZIPRAF_ARCHIVE_ERR_ZIP64;
    if (disk_start != 0u) return RMR_ZIPRAF_ARCHIVE_ERR_MULTIDISK;
    if (name_length == 0u || name_length > RMR_ZIPRAF_ARCHIVE_MAX_NAME)
      return RMR_ZIPRAF_ARCHIVE_ERR_NAME;
    if (rmr_add_size(46u, (size_t)name_length, &record_size) ||
        rmr_add_size(record_size, (size_t)extra_length, &record_size) ||
        rmr_add_size(record_size, (size_t)comment_length, &record_size) ||
        !rmr_range(cursor, record_size, central_end))
      return RMR_ZIPRAF_ARCHIVE_ERR_TRUNCATED;

    entry->entry_id = i + 1u;
    entry->compressed_size = (uint64_t)compressed32;
    entry->uncompressed_size = (uint64_t)uncompressed32;
    entry->local_header_offset = (uint64_t)local_offset32;
    entry->name_length = name_length;
    memcpy(entry->name, archive + cursor + 46u, name_length);
    entry->name[name_length] = '\0';

    if ((entry->general_purpose_flags & (0x0001u | 0x0040u | 0x2000u)) != 0u)
      entry->state_flags |= RMR_ZIPRAF_ENTRY_ENCRYPTED;
    if ((entry->general_purpose_flags & 0x0008u) != 0u)
      entry->state_flags |= RMR_ZIPRAF_ENTRY_DATA_DESCRIPTOR;
    if ((entry->general_purpose_flags & 0x0800u) != 0u)
      entry->state_flags |= RMR_ZIPRAF_ENTRY_UTF8;
    if (!RmR_ZiprafArchive_IsSafeName(archive + cursor + 46u, name_length))
      entry->state_flags |= RMR_ZIPRAF_ENTRY_UNSAFE_NAME;
    if (entry->name[name_length - 1u] == '/' || entry->name[name_length - 1u] == '\\')
      entry->state_flags |= RMR_ZIPRAF_ENTRY_DIRECTORY;
    if (entry->method != 0u && entry->method != 8u)
      entry->state_flags |= RMR_ZIPRAF_ENTRY_UNSUPPORTED;
    if ((version_made_by >> 8) == 3u &&
        (((external_attributes >> 16) & 0170000u) == 0120000u))
      entry->state_flags |= RMR_ZIPRAF_ENTRY_SYMLINK;

    local_offset = (size_t)local_offset32;
    if (!rmr_range(local_offset, 30u, central_offset))
      return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;
    if (rmr_le32(archive + local_offset) != RMR_ZIP_LOCAL_SIG)
      return RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE;
    local_flags = rmr_le16(archive + local_offset + 6u);
    local_method = rmr_le16(archive + local_offset + 8u);
    local_crc32 = rmr_le32(archive + local_offset + 14u);
    local_compressed32 = rmr_le32(archive + local_offset + 18u);
    local_uncompressed32 = rmr_le32(archive + local_offset + 22u);
    local_name_length = rmr_le16(archive + local_offset + 26u);
    local_extra_length = rmr_le16(archive + local_offset + 28u);
    if (local_method != entry->method || local_flags != entry->general_purpose_flags ||
        local_name_length != name_length)
      return RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH;
    if (!rmr_range(local_offset + 30u, (size_t)local_name_length + (size_t)local_extra_length,
                   central_offset))
      return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;
    if (memcmp(archive + local_offset + 30u, archive + cursor + 46u, name_length) != 0)
      return RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH;
    if (rmr_add_size(local_offset + 30u, (size_t)local_name_length, &payload_offset) ||
        rmr_add_size(payload_offset, (size_t)local_extra_length, &payload_offset) ||
        !rmr_range(payload_offset, (size_t)compressed32, central_offset))
      return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;
    entry->payload_offset = (uint64_t)payload_offset;
    {
      size_t record_end = payload_offset + (size_t)compressed32;
      if ((entry->general_purpose_flags & 0x0008u) == 0u) {
        if (local_crc32 != entry->crc32 || local_compressed32 != compressed32 ||
            local_uncompressed32 != uncompressed32)
          return RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH;
      } else {
        size_t descriptor = record_end;
        uint32_t descriptor_crc;
        uint32_t descriptor_compressed;
        uint32_t descriptor_uncompressed;
        if (!rmr_range(descriptor, 12u, central_offset))
          return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;
        if (rmr_le32(archive + descriptor) == 0x08074b50u) {
          if (!rmr_range(descriptor, 16u, central_offset))
            return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;
          descriptor += 4u;
          record_end += 16u;
        } else {
          record_end += 12u;
        }
        descriptor_crc = rmr_le32(archive + descriptor);
        descriptor_compressed = rmr_le32(archive + descriptor + 4u);
        descriptor_uncompressed = rmr_le32(archive + descriptor + 8u);
        if (descriptor_crc != entry->crc32 || descriptor_compressed != compressed32 ||
            descriptor_uncompressed != uncompressed32)
          return RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH;
      }
      entry->record_end_offset = (uint64_t)record_end;
    }

    if (entry->method == 0u && entry->compressed_size == entry->uncompressed_size &&
        (entry->state_flags & (RMR_ZIPRAF_ENTRY_ENCRYPTED |
                               RMR_ZIPRAF_ENTRY_UNSAFE_NAME |
                               RMR_ZIPRAF_ENTRY_DIRECTORY |
                               RMR_ZIPRAF_ENTRY_SYMLINK)) == 0u &&
        (entry->payload_offset % requested_alignment) == 0u)
      entry->state_flags |= RMR_ZIPRAF_ENTRY_LAYOUT_MAPPABLE;

    for (j = 0u; j < i; ++j) {
      const RmR_ZiprafArchiveEntry *other = &out_index->entries[j];
      if (other->local_header_offset == entry->local_header_offset ||
          rmr_names_portably_equal(other, entry))
        return RMR_ZIPRAF_ARCHIVE_ERR_DUPLICATE;
      if (rmr_payloads_overlap(other, entry) ||
          (entry->local_header_offset < other->record_end_offset &&
           other->local_header_offset < entry->record_end_offset))
        return RMR_ZIPRAF_ARCHIVE_ERR_OVERLAP;
    }

    fingerprint = rmr_fnv1a64_u64(entry->entry_id, fingerprint);
    fingerprint = rmr_fnv1a64_u64(entry->payload_offset, fingerprint);
    fingerprint = rmr_fnv1a64_u64(entry->compressed_size, fingerprint);
    fingerprint = rmr_fnv1a64_u64(entry->uncompressed_size, fingerprint);
    fingerprint = rmr_fnv1a64_u64((uint64_t)entry->crc32, fingerprint);
    fingerprint = rmr_fnv1a64((const uint8_t *)entry->name, name_length, fingerprint);

    cursor += record_size;
    out_index->parsed_entries = i + 1u;
  }

  if (cursor != central_end) return RMR_ZIPRAF_ARCHIVE_ERR_RANGE;
  out_index->layout_fingerprint64 = fingerprint;
  return RMR_ZIPRAF_ARCHIVE_OK;
}

static uint32_t rmr_popcount32(uint32_t value) {
  uint32_t count = 0u;
  while (value != 0u) {
    value &= value - 1u;
    ++count;
  }
  return count;
}

static uint32_t rmr_nth_core_bit(uint32_t mask, uint32_t ordinal) {
  uint32_t i;
  for (i = 0u; i < 32u; ++i) {
    const uint32_t bit = UINT32_C(1) << i;
    if ((mask & bit) != 0u) {
      if (ordinal == 0u) return bit;
      --ordinal;
    }
  }
  return 0u;
}

int RmR_ZiprafArchive_BuildReadPlan(const RmR_ZiprafArchiveIndex *index,
                                    uint32_t available_core_mask,
                                    RmR_ZiprafReadPlan *out_plan) {
  uint32_t core_count;
  uint32_t i;
  if (!index || !out_plan || available_core_mask == 0u)
    return RMR_ZIPRAF_ARCHIVE_ERR_ARG;
  if (index->parsed_entries > RMR_ZIPRAF_ARCHIVE_MAX_ENTRIES)
    return RMR_ZIPRAF_ARCHIVE_ERR_CAPACITY;

  memset(out_plan, 0, sizeof(*out_plan));
  out_plan->core_mask = available_core_mask;
  core_count = rmr_popcount32(available_core_mask);

  for (i = 0u; i < index->parsed_entries; ++i) {
    const RmR_ZiprafArchiveEntry *entry = &index->entries[i];
    RmR_ZiprafReadTask *task = &out_plan->tasks[out_plan->task_count];
    const uint8_t action = RmR_ZiprafArchive_ActionForEntry(entry);
    task->entry_id = entry->entry_id;
    task->core_bit = rmr_nth_core_bit(available_core_mask, i % core_count);
    task->phase = i / core_count;
    task->read_direction = (uint8_t)((task->phase & 1u) != 0u
                                      ? RMR_ZIPRAF_READ_REVERSE
                                      : RMR_ZIPRAF_READ_FORWARD);
    task->action = action;
    task->source_offset = entry->payload_offset;
    task->source_length = entry->compressed_size;
    task->logical_length = entry->uncompressed_size;
    ++out_plan->task_count;

    if (action == RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT)
      out_plan->direct_map_bytes += entry->uncompressed_size;
    else if (action == RMR_ZIPRAF_ACTION_COPY_STORE)
      out_plan->copied_store_bytes += entry->uncompressed_size;
    else if (action == RMR_ZIPRAF_ACTION_DECOMPRESS) {
      out_plan->decompress_input_bytes += entry->compressed_size;
      out_plan->decompress_output_bytes += entry->uncompressed_size;
    } else {
      out_plan->rejected_bytes += entry->compressed_size;
    }
  }

  out_plan->phase_count = out_plan->task_count == 0u
                            ? 0u
                            : ((out_plan->task_count - 1u) / core_count) + 1u;
  return RMR_ZIPRAF_ARCHIVE_OK;
}

int RmR_ZiprafArchive_Summarize(const RmR_ZiprafArchiveIndex *index,
                                RmR_ZiprafArchiveTelemetry *out_telemetry) {
  uint32_t i;
  if (!index || !out_telemetry) return RMR_ZIPRAF_ARCHIVE_ERR_ARG;
  memset(out_telemetry, 0, sizeof(*out_telemetry));
  out_telemetry->entries_total = index->parsed_entries;
  for (i = 0u; i < index->parsed_entries; ++i) {
    const RmR_ZiprafArchiveEntry *entry = &index->entries[i];
    const uint8_t action = RmR_ZiprafArchive_ActionForEntry(entry);
    if (action == RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT) {
      ++out_telemetry->entries_layout_mappable;
      out_telemetry->direct_map_bytes += entry->uncompressed_size;
    } else if (action == RMR_ZIPRAF_ACTION_COPY_STORE) {
      ++out_telemetry->entries_copy_store;
      out_telemetry->materialized_input_bytes += entry->compressed_size;
      out_telemetry->materialized_output_bytes += entry->uncompressed_size;
    } else if (action == RMR_ZIPRAF_ACTION_DECOMPRESS) {
      ++out_telemetry->entries_decompress;
      out_telemetry->materialized_input_bytes += entry->compressed_size;
      out_telemetry->materialized_output_bytes += entry->uncompressed_size;
    } else {
      ++out_telemetry->entries_rejected;
    }
  }
  return RMR_ZIPRAF_ARCHIVE_OK;
}
