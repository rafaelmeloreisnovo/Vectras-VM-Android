#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include "rmr_zipraf_archive.h"

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

static uint64_t elapsed_ns(const struct timespec *before, const struct timespec *after) {
  const uint64_t sec = (uint64_t)(after->tv_sec - before->tv_sec);
  if (after->tv_nsec >= before->tv_nsec)
    return sec * UINT64_C(1000000000) + (uint64_t)(after->tv_nsec - before->tv_nsec);
  return (sec - 1u) * UINT64_C(1000000000) +
         (uint64_t)(UINT64_C(1000000000) + after->tv_nsec - before->tv_nsec);
}

static int write_all(int fd, const uint8_t *data, size_t length) {
  size_t done = 0u;
  while (done < length) {
    const ssize_t n = write(fd, data + done, length - done);
    if (n < 0) {
      if (errno == EINTR) continue;
      return -1;
    }
    if (n == 0) return -1;
    done += (size_t)n;
  }
  return 0;
}

static int build_aligned_store_zip(uint32_t page_size, uint8_t **out_bytes, size_t *out_size) {
  static const char name[] = "page.bin";
  const size_t name_length = sizeof(name) - 1u;
  const size_t fixed = 30u + name_length;
  size_t extra_length;
  size_t payload_offset;
  size_t central_offset;
  size_t eocd_offset;
  size_t archive_size;
  uint8_t *bytes;
  size_t i;

  if (!out_bytes || !out_size || page_size <= fixed + 4u || page_size > UINT16_MAX)
    return -1;
  extra_length = (size_t)page_size - fixed;
  payload_offset = fixed + extra_length;
  central_offset = payload_offset + page_size;
  eocd_offset = central_offset + 46u + name_length;
  archive_size = eocd_offset + 22u;
  bytes = (uint8_t *)calloc(archive_size, 1u);
  if (!bytes) return -1;

  put32(bytes, 0x04034b50u);
  put16(bytes + 4u, 20u);
  put16(bytes + 6u, 0x0800u);
  put16(bytes + 8u, 0u);
  put32(bytes + 14u, 0x12345678u);
  put32(bytes + 18u, page_size);
  put32(bytes + 22u, page_size);
  put16(bytes + 26u, (uint16_t)name_length);
  put16(bytes + 28u, (uint16_t)extra_length);
  memcpy(bytes + 30u, name, name_length);
  put16(bytes + fixed, 0xcafeu);
  put16(bytes + fixed + 2u, (uint16_t)(extra_length - 4u));
  for (i = 0u; i < page_size; ++i) bytes[payload_offset + i] = (uint8_t)(i * 17u + 3u);

  put32(bytes + central_offset, 0x02014b50u);
  put16(bytes + central_offset + 4u, 20u);
  put16(bytes + central_offset + 6u, 20u);
  put16(bytes + central_offset + 8u, 0x0800u);
  put16(bytes + central_offset + 10u, 0u);
  put32(bytes + central_offset + 16u, 0x12345678u);
  put32(bytes + central_offset + 20u, page_size);
  put32(bytes + central_offset + 24u, page_size);
  put16(bytes + central_offset + 28u, (uint16_t)name_length);
  put32(bytes + central_offset + 42u, 0u);
  memcpy(bytes + central_offset + 46u, name, name_length);

  put32(bytes + eocd_offset, 0x06054b50u);
  put16(bytes + eocd_offset + 8u, 1u);
  put16(bytes + eocd_offset + 10u, 1u);
  put32(bytes + eocd_offset + 12u, (uint32_t)(eocd_offset - central_offset));
  put32(bytes + eocd_offset + 16u, (uint32_t)central_offset);

  *out_bytes = bytes;
  *out_size = archive_size;
  return 0;
}

int main(void) {
  const long page_size_long = sysconf(_SC_PAGESIZE);
  uint32_t page_size;
  uint8_t *fixture = NULL;
  size_t fixture_size = 0u;
  char path[] = "/tmp/zipraf-mmap-XXXXXX";
  int fd = -1;
  uint8_t *mapping = MAP_FAILED;
  RmR_ZiprafArchiveIndex index;
  struct rusage usage_before;
  struct rusage usage_after;
  struct timespec time_before;
  struct timespec time_after;
  volatile uint8_t sample;
  int result = 1;

  if (page_size_long <= 0 || (unsigned long)page_size_long > UINT32_MAX) return 2;
  page_size = (uint32_t)page_size_long;
  if (build_aligned_store_zip(page_size, &fixture, &fixture_size) != 0) return 3;

  fd = mkstemp(path);
  if (fd < 0) goto cleanup;
  if (unlink(path) != 0) goto cleanup;
  if (write_all(fd, fixture, fixture_size) != 0) goto cleanup;
  mapping = (uint8_t *)mmap(NULL, fixture_size, PROT_READ, MAP_PRIVATE, fd, 0);
  if (mapping == MAP_FAILED) goto cleanup;

  if (getrusage(RUSAGE_SELF, &usage_before) != 0) goto cleanup;
  if (clock_gettime(CLOCK_MONOTONIC, &time_before) != 0) goto cleanup;
  if (RmR_ZiprafArchive_Parse(mapping, fixture_size, page_size, &index) !=
      RMR_ZIPRAF_ARCHIVE_OK)
    goto cleanup;
  if (index.parsed_entries != 1u || index.entries[0].payload_offset != page_size ||
      RmR_ZiprafArchive_ActionForEntry(&index.entries[0]) !=
        RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT)
    goto cleanup;

  sample = mapping[index.entries[0].payload_offset];
  sample ^= mapping[index.entries[0].payload_offset + index.entries[0].uncompressed_size - 1u];
  if (sample != (uint8_t)(3u ^ (uint8_t)(((page_size - 1u) * 17u) + 3u))) goto cleanup;
  if (clock_gettime(CLOCK_MONOTONIC, &time_after) != 0) goto cleanup;
  if (getrusage(RUSAGE_SELF, &usage_after) != 0) goto cleanup;

  printf("{\"gate\":\"ZIPRAF_HOST_MMAP_V1\",\"status\":\"PASS\","
         "\"page_size\":%" PRIu32 ",\"archive_bytes\":%zu,"
         "\"payload_offset\":%" PRIu64 ",\"direct_map_bytes\":%" PRIu64 ","
         "\"explicit_user_copy_bytes\":0,\"elapsed_ns\":%" PRIu64 ","
         "\"minor_fault_delta\":%ld,\"major_fault_delta\":%ld,"
         "\"layout_fingerprint64\":\"%016" PRIx64 "\","
         "\"scope\":\"HOST_MMAP_LAYOUT_NOT_ANDROID_DMA_OR_EXECUTION\"}\n",
         page_size,
         fixture_size,
         index.entries[0].payload_offset,
         index.entries[0].uncompressed_size,
         elapsed_ns(&time_before, &time_after),
         usage_after.ru_minflt - usage_before.ru_minflt,
         usage_after.ru_majflt - usage_before.ru_majflt,
         index.layout_fingerprint64);
  result = 0;

cleanup:
  if (mapping != MAP_FAILED) (void)munmap(mapping, fixture_size);
  if (fd >= 0) (void)close(fd);
  free(fixture);
  return result;
}
