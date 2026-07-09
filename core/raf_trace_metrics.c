#include "raf_trace_metrics.h"

#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define RAF_LINE_MAX 4096
#define RAF_COL_MAX 128

typedef struct RafHeaderMap {
    int t;
    int source;
    int phase;
    int gate;
    int j_n;
    int cluster;
    int delta;
    int C;
    int H;
    int stable_any;
    int escaped;
    int gate_in_peaks;
    int fr_matches_gate;
    int crc;
} RafHeaderMap;

static void raf_trace_summary_zero(RafTraceSummary *out) {
    memset(out, 0, sizeof(*out));
}

static char *trim_ascii(char *s) {
    char *end;
    while (*s && isspace((unsigned char)*s)) s++;
    if (*s == '\0') return s;
    end = s + strlen(s) - 1;
    while (end > s && isspace((unsigned char)*end)) {
        *end = '\0';
        end--;
    }
    return s;
}

static const char *field_at(char **fields, int nfields, int idx) {
    if (idx < 0 || idx >= nfields) return "";
    return fields[idx];
}

static int parse_int(const char *s, int fallback) {
    char *end = NULL;
    long v;
    if (s == NULL || *s == '\0') return fallback;
    errno = 0;
    v = strtol(s, &end, 10);
    if (errno != 0 || end == s) return fallback;
    return (int)v;
}

static int split_csv_row(char *line, char **fields, int max_fields) {
    int n = 0;
    char *p = line;
    char *start = line;

    while (*p && n < max_fields) {
        if (*p == ',') {
            *p = '\0';
            fields[n++] = trim_ascii(start);
            start = p + 1;
        }
        p++;
    }

    if (n < max_fields) {
        fields[n++] = trim_ascii(start);
    }

    return n;
}

static int col_index(char **cols, int ncols, const char *name) {
    int i;
    for (i = 0; i < ncols; i++) {
        if (strcmp(cols[i], name) == 0) return i;
    }
    return -1;
}

static int parse_csv_header_map(char *header, RafHeaderMap *m, char *err, size_t errsz) {
    char *cols[RAF_COL_MAX];
    int ncols;

    ncols = split_csv_row(header, cols, RAF_COL_MAX);

    m->t = col_index(cols, ncols, "t");
    m->source = col_index(cols, ncols, "source");
    m->phase = col_index(cols, ncols, "phase");
    m->gate = col_index(cols, ncols, "gate");
    m->j_n = col_index(cols, ncols, "J_n");
    m->cluster = col_index(cols, ncols, "cluster");
    m->delta = col_index(cols, ncols, "delta");
    m->C = col_index(cols, ncols, "C");
    m->H = col_index(cols, ncols, "H");
    m->stable_any = col_index(cols, ncols, "stable_any");
    m->escaped = col_index(cols, ncols, "escaped");
    m->gate_in_peaks = col_index(cols, ncols, "gate_in_peaks");
    m->fr_matches_gate = col_index(cols, ncols, "fr_matches_gate");
    m->crc = col_index(cols, ncols, "crc");

    if (m->stable_any < 0 || m->gate_in_peaks < 0 || m->gate < 0) {
        snprintf(err, errsz,
                 "missing required columns: need at least gate, stable_any, gate_in_peaks");
        return -1;
    }

    return 0;
}

int raf_trace_summary(const char *path, RafTraceSummary *out) {
    FILE *fp;
    char line[RAF_LINE_MAX];
    char header_err[256];
    RafHeaderMap map;

    if (out == NULL) return -1;
    raf_trace_summary_zero(out);

    if (path == NULL || *path == '\0') {
        fprintf(stderr, "raf_trace_summary: empty path\n");
        return -1;
    }

    fp = fopen(path, "r");
    if (!fp) {
        /* tolerância a arquivo inexistente */
        return 0;
    }

    if (!fgets(line, sizeof(line), fp)) {
        fclose(fp);
        /* tolerância a arquivo vazio */
        return 0;
    }

    if (parse_csv_header_map(line, &map, header_err, sizeof(header_err)) != 0) {
        fprintf(stderr, "raf_trace_summary: %s (%s)\n", header_err, path);
        fclose(fp);
        return -1;
    }

    while (fgets(line, sizeof(line), fp)) {
        char *fields[RAF_COL_MAX];
        int nfields;
        int stable;
        int escaped;
        int is_peak;
        int gate;
        int jn;

        nfields = split_csv_row(line, fields, RAF_COL_MAX);
        (void)nfields;

        stable = parse_int(field_at(fields, nfields, map.stable_any), 0);
        escaped = (map.escaped >= 0) ? parse_int(field_at(fields, nfields, map.escaped), 0) : 0;
        is_peak = parse_int(field_at(fields, nfields, map.gate_in_peaks), 0);
        gate = parse_int(field_at(fields, nfields, map.gate), -1);
        jn = (map.j_n >= 0) ? parse_int(field_at(fields, nfields, map.j_n), -1) : -1;

        out->rows++;
        if (stable) out->stable_count++;
        if (escaped) out->escaped_count++;

        if (is_peak) {
            out->peak_count++;
            if (stable) out->peak_stable_count++;
        } else {
            out->non_peak_count++;
            if (stable) out->non_peak_stable_count++;
        }

        if (map.fr_matches_gate >= 0) {
            int m = parse_int(field_at(fields, nfields, map.fr_matches_gate), 0);
            if (m) out->match_count++;
        }

        if (jn >= 0 && jn < 16) out->jhist[jn]++;
        if (gate >= 0 && gate < 64) out->gate_hist[gate]++;
    }

    fclose(fp);

    out->p_peaks = (out->peak_count > 0)
                       ? ((double)out->peak_stable_count / (double)out->peak_count)
                       : 0.0;
    out->p_non = (out->non_peak_count > 0)
                     ? ((double)out->non_peak_stable_count / (double)out->non_peak_count)
                     : 0.0;
    out->deltaP = out->p_peaks - out->p_non;

    return 0;
}

double raf_trace_deltaP(const char *path) {
    RafTraceSummary s;
    int rc = raf_trace_summary(path, &s);
    if (rc != 0) return 0.0;
    return s.deltaP;
}
