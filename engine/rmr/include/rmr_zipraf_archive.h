// SPDX-License-Identifier: GPL-2.0-only
#ifndef RMR_ZIPRAF_ARCHIVE_H
#define RMR_ZIPRAF_ARCHIVE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_ZIPRAF_ARCHIVE_MAX_ENTRIES 64u
#define RMR_ZIPRAF_ARCHIVE_MAX_NAME 192u

#define RMR_ZIPRAF_ARCHIVE_OK 0
#define RMR_ZIPRAF_ARCHIVE_ERR_ARG -100
#define RMR_ZIPRAF_ARCHIVE_ERR_TRUNCATED -101
#define RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE -102
#define RMR_ZIPRAF_ARCHIVE_ERR_RANGE -103
#define RMR_ZIPRAF_ARCHIVE_ERR_MULTIDISK -104
#define RMR_ZIPRAF_ARCHIVE_ERR_ZIP64 -105
#define RMR_ZIPRAF_ARCHIVE_ERR_CAPACITY -106
#define RMR_ZIPRAF_ARCHIVE_ERR_NAME -107
#define RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH -108
#define RMR_ZIPRAF_ARCHIVE_ERR_OVERLAP -109
#define RMR_ZIPRAF_ARCHIVE_ERR_DUPLICATE -110
#define RMR_ZIPRAF_ARCHIVE_ERR_POLICY -111

#define RMR_ZIPRAF_ENTRY_ENCRYPTED       (1u << 0)
#define RMR_ZIPRAF_ENTRY_DATA_DESCRIPTOR (1u << 1)
#define RMR_ZIPRAF_ENTRY_UTF8            (1u << 2)
#define RMR_ZIPRAF_ENTRY_UNSAFE_NAME     (1u << 3)
#define RMR_ZIPRAF_ENTRY_DIRECTORY       (1u << 4)
#define RMR_ZIPRAF_ENTRY_UNSUPPORTED     (1u << 5)
#define RMR_ZIPRAF_ENTRY_LAYOUT_MAPPABLE (1u << 6)
#define RMR_ZIPRAF_ENTRY_SYMLINK         (1u << 7)

#define RMR_ZIPRAF_READ_FORWARD 0u
#define RMR_ZIPRAF_READ_REVERSE 1u

#define RMR_ZIPRAF_ACTION_REJECT 0u
#define RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT 1u
#define RMR_ZIPRAF_ACTION_COPY_STORE 2u
#define RMR_ZIPRAF_ACTION_DECOMPRESS 3u

typedef struct {
  uint32_t entry_id;
  uint64_t central_header_offset;
  uint64_t local_header_offset;
  uint64_t payload_offset;
  uint64_t compressed_size;
  uint64_t uncompressed_size;
  uint64_t record_end_offset;
  uint32_t crc32;
  uint32_t external_attributes;
  uint16_t method;
  uint16_t general_purpose_flags;
  uint16_t name_length;
  uint16_t state_flags;
  char name[RMR_ZIPRAF_ARCHIVE_MAX_NAME + 1u];
} RmR_ZiprafArchiveEntry;

typedef struct {
  uint64_t archive_size;
  uint64_t eocd_offset;
  uint64_t central_directory_offset;
  uint64_t central_directory_size;
  uint32_t declared_entries;
  uint32_t parsed_entries;
  uint64_t layout_fingerprint64;
  RmR_ZiprafArchiveEntry entries[RMR_ZIPRAF_ARCHIVE_MAX_ENTRIES];
} RmR_ZiprafArchiveIndex;

typedef struct {
  uint32_t entry_id;
  uint32_t core_bit;
  uint32_t phase;
  uint8_t read_direction;
  uint8_t action;
  uint16_t reserved0;
  uint64_t source_offset;
  uint64_t source_length;
  uint64_t logical_length;
} RmR_ZiprafReadTask;

typedef struct {
  uint32_t task_count;
  uint32_t core_mask;
  uint32_t phase_count;
  uint64_t direct_map_bytes;
  uint64_t copied_store_bytes;
  uint64_t decompress_input_bytes;
  uint64_t decompress_output_bytes;
  uint64_t rejected_bytes;
  RmR_ZiprafReadTask tasks[RMR_ZIPRAF_ARCHIVE_MAX_ENTRIES];
} RmR_ZiprafReadPlan;

typedef struct {
  uint32_t entries_total;
  uint32_t entries_layout_mappable;
  uint32_t entries_copy_store;
  uint32_t entries_decompress;
  uint32_t entries_rejected;
  uint64_t direct_map_bytes;
  uint64_t materialized_input_bytes;
  uint64_t materialized_output_bytes;
} RmR_ZiprafArchiveTelemetry;

int RmR_ZiprafArchive_Parse(const uint8_t *archive,
                            size_t archive_size,
                            uint32_t requested_alignment,
                            RmR_ZiprafArchiveIndex *out_index);
int RmR_ZiprafArchive_BuildReadPlan(const RmR_ZiprafArchiveIndex *index,
                                    uint32_t available_core_mask,
                                    RmR_ZiprafReadPlan *out_plan);
int RmR_ZiprafArchive_Summarize(const RmR_ZiprafArchiveIndex *index,
                                RmR_ZiprafArchiveTelemetry *out_telemetry);
int RmR_ZiprafArchive_IsSafeName(const uint8_t *name, size_t name_length);
uint8_t RmR_ZiprafArchive_ActionForEntry(const RmR_ZiprafArchiveEntry *entry);

#ifdef __cplusplus
}
#endif

#endif
