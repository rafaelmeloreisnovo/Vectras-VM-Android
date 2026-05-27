#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "raf_trace_metrics.h"

#define MAX_PATHS 16
#define INPUT_BUF 1024

static int file_exists(const char *path) {
    FILE *fp;
    if (path == NULL || *path == '\0') return 0;
    fp = fopen(path, "r");
    if (!fp) return 0;
    fclose(fp);
    return 1;
}

static void print_summary(const char *path, const RafTraceSummary *s) {
    printf("\n[trace] %s\n", path);
    printf("  rows=%ld stable=%ld escaped=%ld\n", s->rows, s->stable_count, s->escaped_count);
    printf("  p_peaks=%.6f p_non=%.6f deltaP=%.6f\n", s->p_peaks, s->p_non, s->deltaP);
    printf("  peak_count=%ld non_peak_count=%ld matches=%ld\n",
           s->peak_count, s->non_peak_count, s->match_count);
}

void show_universal_trace(const char *path) {
    RafTraceSummary s;
    int rc;

    if (!file_exists(path)) {
        printf("[aviso] Trace ausente: %s\n", path ? path : "(null)");
        return;
    }

    rc = raf_trace_summary(path, &s);
    if (rc != 0) {
        printf("[erro] Falha ao analisar trace: %s\n", path);
        return;
    }

    print_summary(path, &s);
}

void compare_traces(const char **paths, int n) {
    int i;
    for (i = 0; i < n; i++) {
        if (!paths[i] || !*paths[i]) continue;
        show_universal_trace(paths[i]);
    }
}

void show_geolm_clusters(const char *trace_path) {
    printf("\n[GEOLM] Clusterização por gate_hist e J_n\n");
    show_universal_trace(trace_path);
}

void show_vectras_qemu_trace(const char *trace_path) {
    printf("\n[VECTRAS/QEMU] Resumo de execução\n");
    show_universal_trace(trace_path);
}

static void print_menu(void) {
    puts("\n===== GBS3 Color / BBS-Termux =====");
    puts(" 1) universal trace summary");
    puts(" 2) compare traces");
    puts(" 3) geolm clusters");
    puts(" 4) vectras qemu trace");
    puts(" 5) universal trace summary (preset)");
    puts(" 6) compare traces (preset)");
    puts(" 7) geolm clusters (preset)");
    puts(" 8) vectras qemu trace (preset)");
    puts(" p) opcao legado P");
    puts(" c) opcao legado C");
    puts(" e) opcao legado E");
    puts(" 0) sair");
    printf("> ");
}

static void read_line(char *buf, size_t cap) {
    if (!fgets(buf, (int)cap, stdin)) {
        buf[0] = '\0';
        return;
    }
    buf[strcspn(buf, "\r\n")] = '\0';
}

int main(void) {
    char cmd[32];
    char path_a[INPUT_BUF];
    char path_b[INPUT_BUF];
    const char *preset[2] = {"trace.csv", "trace_qemu.csv"};

    for (;;) {
        print_menu();
        read_line(cmd, sizeof(cmd));

        switch (cmd[0]) {
            case '1':
                printf("trace path: ");
                read_line(path_a, sizeof(path_a));
                show_universal_trace(path_a);
                break;
            case '2': {
                const char *arr[MAX_PATHS];
                int n = 0;
                printf("trace path #1: ");
                read_line(path_a, sizeof(path_a));
                printf("trace path #2: ");
                read_line(path_b, sizeof(path_b));
                if (path_a[0]) arr[n++] = path_a;
                if (path_b[0]) arr[n++] = path_b;
                compare_traces(arr, n);
                break;
            }
            case '3':
                printf("trace path: ");
                read_line(path_a, sizeof(path_a));
                show_geolm_clusters(path_a);
                break;
            case '4':
                printf("trace path: ");
                read_line(path_a, sizeof(path_a));
                show_vectras_qemu_trace(path_a);
                break;
            case '5':
                show_universal_trace(preset[0]);
                break;
            case '6':
                compare_traces(preset, 2);
                break;
            case '7':
                show_geolm_clusters(preset[0]);
                break;
            case '8':
                show_vectras_qemu_trace(preset[1]);
                break;
            case 'p':
            case 'P':
                puts("[legado] opcao P preservada.");
                break;
            case 'c':
            case 'C':
                puts("[legado] opcao C preservada.");
                break;
            case 'e':
            case 'E':
                puts("[legado] opcao E preservada.");
                break;
            case '0':
                puts("saindo.");
                return 0;
            default:
                puts("opcao invalida.");
                break;
        }
    }
}
