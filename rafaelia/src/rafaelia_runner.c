#include "../include/raf_runtime.h"

#include <stdio.h>
#include <string.h>

static void usage(const char *argv0) {
    fprintf(stderr, "usage: %s <root_path> [report.tsv] [-v]\n", argv0);
    fprintf(stderr, "example: %s .. reports/rafaelia_runtime.tsv\n", argv0);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        usage(argv[0]);
        return 2;
    }

    const char *root = argv[1];
    const char *report = NULL;
    int verbose = 0;

    if (argc >= 3 && strcmp(argv[2], "-v") != 0) report = argv[2];
    for (int i = 2; i < argc; ++i) {
        if (strcmp(argv[i], "-v") == 0) verbose = 1;
    }

    raf_runtime_config_t cfg;
    cfg.root_path = root;
    cfg.report_path = report;
    cfg.verbose = verbose;

    raf_runtime_stats_t stats;
    int rc = raf_runtime_scan(&cfg, &stats);
    raf_runtime_print_stats(&stats);

    if (rc != 0) {
        fprintf(stderr, "RAFAELIA runtime finished with rc=%d\n", rc);
        return 1;
    }

    if (report) {
        printf("report=%s\n", report);
    }
    return 0;
}
