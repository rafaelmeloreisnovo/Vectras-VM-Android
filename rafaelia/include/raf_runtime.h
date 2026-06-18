#ifndef RAFAELIA_RAF_RUNTIME_H
#define RAFAELIA_RAF_RUNTIME_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct raf_runtime_stats {
    uint64_t total_files;
    uint64_t total_bytes;
    uint64_t code_files;
    uint64_t doc_files;
    uint64_t apk_files;
    uint64_t xapk_files;
    uint64_t zip_files;
    uint64_t dex_files;
    uint64_t so_files;
    uint64_t img_files;
    uint64_t json_files;
    uint64_t other_files;
    uint64_t errors;
} raf_runtime_stats_t;

typedef struct raf_runtime_config {
    const char *root_path;
    const char *report_path;
    int verbose;
} raf_runtime_config_t;

uint64_t raf_fnv1a64_file(const char *path, uint64_t *size_out, int *err_out);
const char *raf_kind_from_path_and_magic(const char *path, const unsigned char *magic, size_t magic_len);
int raf_runtime_scan(const raf_runtime_config_t *cfg, raf_runtime_stats_t *stats_out);
void raf_runtime_print_stats(const raf_runtime_stats_t *s);

#ifdef __cplusplus
}
#endif

#endif
