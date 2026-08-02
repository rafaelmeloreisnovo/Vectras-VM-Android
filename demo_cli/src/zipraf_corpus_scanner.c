// SPDX-License-Identifier: GPL-2.0-only
#define _POSIX_C_SOURCE 200809L

#include <ctype.h>
#include <errno.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#include "rmr_zipraf_archive.h"
#include "rmr_zipraf_payload_digest.h"

#define ZIPRAF_CORPUS_MAX_ARCHIVE_BYTES (UINT64_C(256) * 1024u * 1024u)

static const char *action_name(uint8_t action) {
  switch (action) {
    case RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT: return "DIRECT_MAP_LAYOUT";
    case RMR_ZIPRAF_ACTION_COPY_STORE: return "COPY_STORE";
    case RMR_ZIPRAF_ACTION_DECOMPRESS: return "DECOMPRESS";
    default: return "REJECT";
  }
}

static const char *parse_error_name(int code) {
  switch (code) {
    case RMR_ZIPRAF_ARCHIVE_OK: return "OK";
    case RMR_ZIPRAF_ARCHIVE_ERR_ARG: return "ARG";
    case RMR_ZIPRAF_ARCHIVE_ERR_TRUNCATED: return "TRUNCATED";
    case RMR_ZIPRAF_ARCHIVE_ERR_SIGNATURE: return "SIGNATURE";
    case RMR_ZIPRAF_ARCHIVE_ERR_RANGE: return "RANGE";
    case RMR_ZIPRAF_ARCHIVE_ERR_MULTIDISK: return "MULTIDISK";
    case RMR_ZIPRAF_ARCHIVE_ERR_ZIP64: return "ZIP64_UNSUPPORTED_V2";
    case RMR_ZIPRAF_ARCHIVE_ERR_CAPACITY: return "CAPACITY";
    case RMR_ZIPRAF_ARCHIVE_ERR_NAME: return "NAME";
    case RMR_ZIPRAF_ARCHIVE_ERR_LOCAL_MISMATCH: return "LOCAL_MISMATCH";
    case RMR_ZIPRAF_ARCHIVE_ERR_OVERLAP: return "OVERLAP";
    case RMR_ZIPRAF_ARCHIVE_ERR_DUPLICATE: return "DUPLICATE";
    case RMR_ZIPRAF_ARCHIVE_ERR_POLICY: return "POLICY";
    default: return "UNKNOWN";
  }
}

static void write_json_string(FILE *out, const char *text, size_t length) {
  size_t i;
  fputc('"', out);
  for (i = 0u; i < length; ++i) {
    const unsigned char c = (unsigned char)text[i];
    switch (c) {
      case '"': fputs("\\\"", out); break;
      case '\\': fputs("\\\\", out); break;
      case '\b': fputs("\\b", out); break;
      case '\f': fputs("\\f", out); break;
      case '\n': fputs("\\n", out); break;
      case '\r': fputs("\\r", out); break;
      case '\t': fputs("\\t", out); break;
      default:
        if (c < 0x20u) fprintf(out, "\\u%04x", (unsigned)c);
        else fputc((int)c, out);
        break;
    }
  }
  fputc('"', out);
}

static void write_hex(FILE *out, const uint8_t *bytes, size_t length) {
  static const char digits[] = "0123456789abcdef";
  size_t i;
  fputc('"', out);
  for (i = 0u; i < length; ++i) {
    fputc(digits[bytes[i] >> 4u], out);
    fputc(digits[bytes[i] & 15u], out);
  }
  fputc('"', out);
}

static const char *path_basename(const char *path) {
  const char *slash;
  const char *backslash;
  if (!path) return "";
  slash = strrchr(path, '/');
  backslash = strrchr(path, '\\');
  if (!slash || (backslash && backslash > slash)) slash = backslash;
  return slash ? slash + 1 : path;
}

static int suffix_equal_ci(const char *text, const char *suffix) {
  size_t text_len;
  size_t suffix_len;
  size_t i;
  if (!text || !suffix) return 0;
  text_len = strlen(text);
  suffix_len = strlen(suffix);
  if (suffix_len > text_len) return 0;
  for (i = 0u; i < suffix_len; ++i) {
    const unsigned char a = (unsigned char)text[text_len - suffix_len + i];
    const unsigned char b = (unsigned char)suffix[i];
    if (tolower(a) != tolower(b)) return 0;
  }
  return 1;
}

static const char *container_kind(const char *path) {
  if (suffix_equal_ci(path, ".apk")) return "APK_CANDIDATE_BY_EXTENSION";
  if (suffix_equal_ci(path, ".zip")) return "ZIP_BY_EXTENSION";
  return "ZIP_COMPATIBLE_BYTES";
}

static int read_archive(const char *path, uint8_t **out_data, size_t *out_size) {
  FILE *file;
  struct stat st;
  uint8_t *data;
  size_t size;
  size_t done;

  if (!path || !out_data || !out_size) return -1;
  if (stat(path, &st) != 0 || st.st_size < 0) return -1;
  if ((uint64_t)st.st_size > ZIPRAF_CORPUS_MAX_ARCHIVE_BYTES ||
      (uint64_t)st.st_size > (uint64_t)SIZE_MAX)
    return -2;
  size = (size_t)st.st_size;
  data = (uint8_t *)malloc(size == 0u ? 1u : size);
  if (!data) return -3;
  file = fopen(path, "rb");
  if (!file) {
    free(data);
    return -1;
  }
  done = fread(data, 1u, size, file);
  if (done != size || ferror(file)) {
    fclose(file);
    free(data);
    return -1;
  }
  if (fclose(file) != 0) {
    free(data);
    return -1;
  }
  *out_data = data;
  *out_size = size;
  return 0;
}

static int entry_name_equal(const RmR_ZiprafArchiveEntry *entry, const char *name) {
  size_t length;
  if (!entry || !name) return 0;
  length = strlen(name);
  return entry->name_length == length && memcmp(entry->name, name, length) == 0;
}

static void write_state_flags(FILE *out, uint16_t flags) {
  int first = 1;
#define EMIT_FLAG(bit, name) do { \
  if ((flags & (bit)) != 0u) { \
    if (!first) fputc(',', out); \
    write_json_string(out, (name), strlen(name)); \
    first = 0; \
  } \
} while (0)
  fputc('[', out);
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_ENCRYPTED, "ENCRYPTED");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_DATA_DESCRIPTOR, "DATA_DESCRIPTOR");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_UTF8, "UTF8");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_UNSAFE_NAME, "UNSAFE_NAME");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_DIRECTORY, "DIRECTORY");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_UNSUPPORTED, "UNSUPPORTED");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_LAYOUT_MAPPABLE, "LAYOUT_MAPPABLE");
  EMIT_FLAG(RMR_ZIPRAF_ENTRY_SYMLINK, "SYMLINK");
  fputc(']', out);
#undef EMIT_FLAG
}

static int scan_one(FILE *out,
                    const char *path,
                    uint64_t mapping_epoch,
                    uint32_t alignment,
                    uint64_t *entry_total,
                    uint64_t *entry_rejected,
                    uint64_t *stored_bytes,
                    uint64_t *logical_bytes,
                    uint64_t *parse_failures) {
  uint8_t *archive = NULL;
  size_t archive_size = 0u;
  RmR_ZiprafArchiveIndex index;
  uint8_t archive_sha[32];
  uint8_t archive_blake3[32];
  int read_status;
  int parse_status;
  uint32_t i;
  int has_manifest = 0;
  int has_classes = 0;
  int has_resources = 0;

  fputs("{\"input_name\":", out);
  write_json_string(out, path_basename(path), strlen(path_basename(path)));
  fputs(",\"input_path\":", out);
  write_json_string(out, path, strlen(path));
  fputs(",\"container_kind\":", out);
  write_json_string(out, container_kind(path), strlen(container_kind(path)));
  fprintf(out, ",\"mapping_epoch\":%" PRIu64, mapping_epoch);

  read_status = read_archive(path, &archive, &archive_size);
  if (read_status != 0) {
    const char *state = read_status == -2 ? "ARCHIVE_LIMIT_EXCEEDED" : "READ_ERROR";
    fputs(",\"state\":", out);
    write_json_string(out, state, strlen(state));
    fprintf(out, ",\"read_errno\":%d,\"entries\":[]}", errno);
    ++*parse_failures;
    return 0;
  }

  if (RmR_ZiprafDigest_Sha256(archive, archive_size, archive_sha) != RMR_ZIPRAF_DIGEST_OK ||
      RmR_ZiprafDigest_Blake3External(archive, archive_size, archive_blake3) != RMR_ZIPRAF_DIGEST_OK) {
    free(archive);
    fputs(",\"state\":\"DIGEST_PROVIDER_ERROR\",\"entries\":[]}", out);
    ++*parse_failures;
    return 0;
  }

  fprintf(out, ",\"archive_bytes\":%zu,\"archive_sha256\":", archive_size);
  write_hex(out, archive_sha, sizeof(archive_sha));
  fputs(",\"archive_blake3\":", out);
  write_hex(out, archive_blake3, sizeof(archive_blake3));

  parse_status = RmR_ZiprafArchive_Parse(archive, archive_size, alignment, &index);
  if (parse_status != RMR_ZIPRAF_ARCHIVE_OK) {
    fputs(",\"state\":\"PARSE_REJECTED\",\"parse_error\":", out);
    write_json_string(out, parse_error_name(parse_status), strlen(parse_error_name(parse_status)));
    fprintf(out, ",\"parse_code\":%d,\"entries\":[]}", parse_status);
    ++*parse_failures;
    free(archive);
    return 0;
  }

  for (i = 0u; i < index.parsed_entries; ++i) {
    if (entry_name_equal(&index.entries[i], "AndroidManifest.xml")) has_manifest = 1;
    if (entry_name_equal(&index.entries[i], "classes.dex")) has_classes = 1;
    if (entry_name_equal(&index.entries[i], "resources.arsc")) has_resources = 1;
  }

  fputs(",\"state\":\"PARSED\",\"layout_fingerprint64\":\"", out);
  fprintf(out, "%016" PRIx64, index.layout_fingerprint64);
  fprintf(out, "\",\"apk_markers\":{\"android_manifest\":%s,\"classes_dex\":%s,\"resources_arsc\":%s,\"apk_structure_candidate\":%s},\"entries\":[",
          has_manifest ? "true" : "false",
          has_classes ? "true" : "false",
          has_resources ? "true" : "false",
          (has_manifest && has_classes) ? "true" : "false");

  for (i = 0u; i < index.parsed_entries; ++i) {
    const RmR_ZiprafArchiveEntry *entry = &index.entries[i];
    const uint8_t action = RmR_ZiprafArchive_ActionForEntry(entry);
    uint8_t sha[32];
    uint8_t blake3[32];
    int sha_status;
    int blake3_status;

    if (i != 0u) fputc(',', out);
    sha_status = RmR_ZiprafArchive_DigestEntry(
      archive, archive_size, entry,
      RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES,
      RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256,
      sha);
    blake3_status = RmR_ZiprafArchive_DigestEntry(
      archive, archive_size, entry,
      RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES,
      RMR_ZIPRAF_DIGEST_ALGORITHM_BLAKE3,
      blake3);

    fputs("{\"entry_id\":", out);
    fprintf(out, "%" PRIu32, entry->entry_id);
    fputs(",\"name\":", out);
    write_json_string(out, entry->name, entry->name_length);
    fprintf(out,
            ",\"local_header_offset\":%" PRIu64
            ",\"payload_offset\":%" PRIu64
            ",\"stored_size\":%" PRIu64
            ",\"logical_size\":%" PRIu64
            ",\"method\":%u,\"crc32\":\"%08" PRIx32 "\",\"action\":",
            entry->local_header_offset,
            entry->payload_offset,
            entry->compressed_size,
            entry->uncompressed_size,
            (unsigned)entry->method,
            entry->crc32);
    write_json_string(out, action_name(action), strlen(action_name(action)));
    fputs(",\"state_flags\":", out);
    write_state_flags(out, entry->state_flags);

    if (sha_status == RMR_ZIPRAF_DIGEST_OK) {
      fputs(",\"stored_sha256\":", out);
      write_hex(out, sha, sizeof(sha));
    } else {
      fprintf(out, ",\"stored_sha256_error\":%d", sha_status);
    }
    if (blake3_status == RMR_ZIPRAF_DIGEST_OK) {
      fputs(",\"stored_blake3\":", out);
      write_hex(out, blake3, sizeof(blake3));
    } else {
      fprintf(out, ",\"stored_blake3_error\":%d", blake3_status);
    }

    if (action == RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT ||
        action == RMR_ZIPRAF_ACTION_COPY_STORE) {
      fputs(",\"logical_digest_state\":\"SAME_AS_STORED_FOR_STORE\"", out);
    } else if (action == RMR_ZIPRAF_ACTION_DECOMPRESS) {
      fputs(",\"logical_digest_state\":\"MATERIALIZATION_REQUIRED\"", out);
    } else {
      fputs(",\"logical_digest_state\":\"NOT_AUTHORIZED_BY_POLICY\"", out);
    }
    fputs(",\"execution_authorized\":false,\"dma_authorized\":false}", out);

    ++*entry_total;
    if (action == RMR_ZIPRAF_ACTION_REJECT) ++*entry_rejected;
    *stored_bytes += entry->compressed_size;
    *logical_bytes += entry->uncompressed_size;
  }

  fputs("]}", out);
  free(archive);
  return 1;
}

static void usage(const char *program) {
  fprintf(stderr,
          "usage: %s --output MANIFEST.json [--mapping-epoch N] [--alignment N] archive.zip [archive.apk ...]\n",
          program);
}

int main(int argc, char **argv) {
  const char *output_path = NULL;
  uint64_t mapping_epoch = 0u;
  uint32_t alignment = 4096u;
  int first_input = 0;
  int i;
  FILE *out;
  uint64_t archive_total = 0u;
  uint64_t parsed_archives = 0u;
  uint64_t parse_failures = 0u;
  uint64_t entry_total = 0u;
  uint64_t entry_rejected = 0u;
  uint64_t stored_bytes = 0u;
  uint64_t logical_bytes = 0u;

  for (i = 1; i < argc; ++i) {
    if (strcmp(argv[i], "--output") == 0 && i + 1 < argc) {
      output_path = argv[++i];
    } else if (strcmp(argv[i], "--mapping-epoch") == 0 && i + 1 < argc) {
      char *end = NULL;
      mapping_epoch = strtoull(argv[++i], &end, 10);
      if (!end || *end != '\0') {
        usage(argv[0]);
        return 2;
      }
    } else if (strcmp(argv[i], "--alignment") == 0 && i + 1 < argc) {
      char *end = NULL;
      unsigned long value = strtoul(argv[++i], &end, 10);
      if (!end || *end != '\0' || value == 0ul || value > UINT32_MAX ||
          (value & (value - 1ul)) != 0ul) {
        usage(argv[0]);
        return 2;
      }
      alignment = (uint32_t)value;
    } else {
      first_input = i;
      break;
    }
  }

  if (!output_path || first_input == 0 || first_input >= argc) {
    usage(argv[0]);
    return 2;
  }

  out = fopen(output_path, "wb");
  if (!out) {
    perror(output_path);
    return 3;
  }

  fputs("{\"schema_version\":\"zipraf-corpus-manifest.u2.v1\",", out);
  fputs("\"claim_allowed\":false,\"extraction_performed\":false,\"execution_authorized\":false,", out);
  fprintf(out,
          "\"mapping_epoch\":%" PRIu64 ",\"alignment\":%" PRIu32 ","
          "\"archive_limit_bytes\":%" PRIu64 ","
          "\"sha256_provider\":\"RMR_PORTABLE_C\","
          "\"blake3_provider\":\"EXTERNAL_PINNED_C_1.8.2\","
          "\"blake3_provider_commit\":\"%s\",\"archives\":[",
          mapping_epoch,
          alignment,
          ZIPRAF_CORPUS_MAX_ARCHIVE_BYTES,
          RMR_BLAKE3_PROVIDER_COMMIT);

  for (i = first_input; i < argc; ++i) {
    int parsed;
    if (archive_total != 0u) fputc(',', out);
    parsed = scan_one(out, argv[i], mapping_epoch, alignment,
                      &entry_total, &entry_rejected,
                      &stored_bytes, &logical_bytes, &parse_failures);
    ++archive_total;
    if (parsed) ++parsed_archives;
  }

  fprintf(out,
          "],\"summary\":{\"archive_total\":%" PRIu64
          ",\"parsed_archives\":%" PRIu64
          ",\"parse_failures\":%" PRIu64
          ",\"entry_total\":%" PRIu64
          ",\"entry_rejected\":%" PRIu64
          ",\"stored_bytes\":%" PRIu64
          ",\"logical_bytes\":%" PRIu64
          "},\"u2_real_external_corpus\":\"TOKEN_VAZIO\"}\n",
          archive_total,
          parsed_archives,
          parse_failures,
          entry_total,
          entry_rejected,
          stored_bytes,
          logical_bytes);

  if (fclose(out) != 0) return 4;
  return 0;
}
