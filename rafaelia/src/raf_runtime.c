#include "../include/raf_runtime.h"

#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define RAF_FNV_OFFSET 1469598103934665603ULL
#define RAF_FNV_PRIME 1099511628211ULL

static int ends_with_ci(const char *s, const char *suffix) {
    size_t n = strlen(s), m = strlen(suffix);
    if (m > n) return 0;
    const char *a = s + (n - m);
    for (size_t i = 0; i < m; ++i) {
        char ca = a[i], cb = suffix[i];
        if (ca >= 'A' && ca <= 'Z') ca = (char)(ca - 'A' + 'a');
        if (cb >= 'A' && cb <= 'Z') cb = (char)(cb - 'A' + 'a');
        if (ca != cb) return 0;
    }
    return 1;
}

static int is_doc_file(const char *p) {
    return ends_with_ci(p, ".md") || ends_with_ci(p, ".txt") ||
           ends_with_ci(p, ".rst") || ends_with_ci(p, ".adoc") ||
           ends_with_ci(p, ".html") || ends_with_ci(p, ".pdf");
}

static int is_code_file(const char *p) {
    return ends_with_ci(p, ".c") || ends_with_ci(p, ".h") ||
           ends_with_ci(p, ".cc") || ends_with_ci(p, ".cpp") ||
           ends_with_ci(p, ".java") || ends_with_ci(p, ".kt") ||
           ends_with_ci(p, ".S") || ends_with_ci(p, ".s") ||
           ends_with_ci(p, ".asm") || ends_with_ci(p, ".sh") ||
           ends_with_ci(p, ".py") || ends_with_ci(p, ".rs") ||
           ends_with_ci(p, ".go") || ends_with_ci(p, ".gradle") ||
           ends_with_ci(p, "Makefile");
}

uint64_t raf_fnv1a64_file(const char *path, uint64_t *size_out, int *err_out) {
    unsigned char buf[32768];
    uint64_t h = RAF_FNV_OFFSET;
    uint64_t total = 0;
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        if (err_out) *err_out = errno ? errno : 1;
        if (size_out) *size_out = 0;
        return 0;
    }
    for (;;) {
        size_t n = fread(buf, 1, sizeof(buf), fp);
        if (n) {
            total += (uint64_t)n;
            for (size_t i = 0; i < n; ++i) {
                h ^= (uint64_t)buf[i];
                h *= RAF_FNV_PRIME;
            }
        }
        if (n < sizeof(buf)) {
            if (ferror(fp)) {
                if (err_out) *err_out = errno ? errno : 2;
                fclose(fp);
                if (size_out) *size_out = total;
                return h;
            }
            break;
        }
    }
    fclose(fp);
    if (err_out) *err_out = 0;
    if (size_out) *size_out = total;
    return h;
}

const char *raf_kind_from_path_and_magic(const char *path, const unsigned char *magic, size_t magic_len) {
    if (ends_with_ci(path, ".apk")) return "apk";
    if (ends_with_ci(path, ".xapk")) return "xapk";
    if (ends_with_ci(path, ".zip") || ends_with_ci(path, ".zipraf")) return "zip";
    if (ends_with_ci(path, ".dex")) return "dex";
    if (ends_with_ci(path, ".so")) return "native-so";
    if (ends_with_ci(path, ".img")) return "image";
    if (ends_with_ci(path, ".json") || ends_with_ci(path, ".jsonl")) return "json";
    if (ends_with_ci(path, ".zrf")) return "zrf";
    if (is_doc_file(path)) return "doc";
    if (is_code_file(path)) return "code";
    if (magic_len >= 4 && magic[0] == 'P' && magic[1] == 'K') return "zip-like";
    if (magic_len >= 4 && magic[0] == 0x7f && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') return "elf";
    if (magic_len >= 3 && magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x') return "dex-like";
    return "other";
}

static void stats_add_kind(raf_runtime_stats_t *s, const char *path, const char *kind, uint64_t size) {
    s->total_files++;
    s->total_bytes += size;
    if (is_code_file(path) || strcmp(kind, "code") == 0) s->code_files++;
    else if (is_doc_file(path) || strcmp(kind, "doc") == 0) s->doc_files++;
    else if (strcmp(kind, "apk") == 0) s->apk_files++;
    else if (strcmp(kind, "xapk") == 0) s->xapk_files++;
    else if (strcmp(kind, "zip") == 0 || strcmp(kind, "zip-like") == 0) s->zip_files++;
    else if (strcmp(kind, "dex") == 0 || strcmp(kind, "dex-like") == 0) s->dex_files++;
    else if (strcmp(kind, "native-so") == 0 || strcmp(kind, "elf") == 0) s->so_files++;
    else if (strcmp(kind, "image") == 0) s->img_files++;
    else if (strcmp(kind, "json") == 0) s->json_files++;
    else s->other_files++;
}

static int read_magic(const char *path, unsigned char *magic, size_t cap, size_t *len_out) {
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        if (len_out) *len_out = 0;
        return -1;
    }
    size_t n = fread(magic, 1, cap, fp);
    fclose(fp);
    if (len_out) *len_out = n;
    return 0;
}

static int scan_path(const char *path, FILE *report, raf_runtime_stats_t *stats, int verbose) {
    struct stat st;
    if (lstat(path, &st) != 0) {
        stats->errors++;
        return -1;
    }

    if (S_ISDIR(st.st_mode)) {
        DIR *d = opendir(path);
        if (!d) {
            stats->errors++;
            return -1;
        }
        struct dirent *de;
        while ((de = readdir(d)) != NULL) {
            if (strcmp(de->d_name, ".") == 0 || strcmp(de->d_name, "..") == 0) continue;
            if (strcmp(de->d_name, ".git") == 0) continue;
            size_t need = strlen(path) + 1 + strlen(de->d_name) + 1;
            char *child = (char *)malloc(need);
            if (!child) {
                closedir(d);
                stats->errors++;
                return -1;
            }
            snprintf(child, need, "%s/%s", path, de->d_name);
            scan_path(child, report, stats, verbose);
            free(child);
        }
        closedir(d);
        return 0;
    }

    if (!S_ISREG(st.st_mode)) return 0;

    unsigned char magic[16];
    size_t magic_len = 0;
    read_magic(path, magic, sizeof(magic), &magic_len);

    int err = 0;
    uint64_t size = 0;
    uint64_t fp64 = raf_fnv1a64_file(path, &size, &err);
    if (err) stats->errors++;
    const char *kind = raf_kind_from_path_and_magic(path, magic, magic_len);
    stats_add_kind(stats, path, kind, size);

    if (report) {
        fprintf(report, "%s\t%s\t%llu\t%016llx\t%s\n",
                kind,
                err ? "READ_ERR" : "PASS",
                (unsigned long long)size,
                (unsigned long long)fp64,
                path);
    }
    if (verbose) {
        fprintf(stderr, "[%s] %llu %016llx %s\n",
                kind,
                (unsigned long long)size,
                (unsigned long long)fp64,
                path);
    }
    return 0;
}

int raf_runtime_scan(const raf_runtime_config_t *cfg, raf_runtime_stats_t *stats_out) {
    if (!cfg || !cfg->root_path || !stats_out) return -1;
    memset(stats_out, 0, sizeof(*stats_out));

    FILE *report = NULL;
    if (cfg->report_path && cfg->report_path[0]) {
        report = fopen(cfg->report_path, "wb");
        if (!report) return -2;
        fprintf(report, "kind\tstate\tsize_bytes\tfnv1a64\tpath\n");
    }

    int rc = scan_path(cfg->root_path, report, stats_out, cfg->verbose);
    if (report) fclose(report);
    return rc;
}

void raf_runtime_print_stats(const raf_runtime_stats_t *s) {
    if (!s) return;
    printf("RAFAELIA_RUNTIME_SUMMARY\n");
    printf("total_files=%llu\n", (unsigned long long)s->total_files);
    printf("total_bytes=%llu\n", (unsigned long long)s->total_bytes);
    printf("code_files=%llu\n", (unsigned long long)s->code_files);
    printf("doc_files=%llu\n", (unsigned long long)s->doc_files);
    printf("apk_files=%llu\n", (unsigned long long)s->apk_files);
    printf("xapk_files=%llu\n", (unsigned long long)s->xapk_files);
    printf("zip_files=%llu\n", (unsigned long long)s->zip_files);
    printf("dex_files=%llu\n", (unsigned long long)s->dex_files);
    printf("so_files=%llu\n", (unsigned long long)s->so_files);
    printf("img_files=%llu\n", (unsigned long long)s->img_files);
    printf("json_files=%llu\n", (unsigned long long)s->json_files);
    printf("other_files=%llu\n", (unsigned long long)s->other_files);
    printf("errors=%llu\n", (unsigned long long)s->errors);
}
