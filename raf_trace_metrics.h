#ifndef RAF_TRACE_METRICS_H
#define RAF_TRACE_METRICS_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct RafTraceSummary {
    long rows;
    long stable_count;
    long escaped_count;
    long peak_count;
    long peak_stable_count;
    long non_peak_count;
    long non_peak_stable_count;
    long match_count;
    double p_peaks;
    double p_non;
    double deltaP;
    long jhist[16];
    long gate_hist[64];
} RafTraceSummary;

double raf_trace_deltaP(const char *path);
int raf_trace_summary(const char *path, RafTraceSummary *out);

#ifdef __cplusplus
}
#endif

#endif
