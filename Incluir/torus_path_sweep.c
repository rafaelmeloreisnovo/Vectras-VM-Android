/* torus_path_sweep.c
 *
 * Path-sweep orchestrator for the torus/spiral trajectory generator, built
 * on top of the state matrices A[8x5] (40 states) and B[7x3] (21 states).
 *
 * What this program actually does (no more, no less):
 *   1. Enumerates every (row, col) cell of A and B as an input path (theta,
 *      phi) on the torus, and walks the trajectory for N steps with the
 *      original torus()/spiral() functions.
 *   2. Computes per-path statistics: mean/stddev curvature (the log_base
 *      metric from the original program), path energy (sum of squared
 *      step-to-step displacement), and a Shannon entropy estimate over a
 *      coarse spatial histogram of the path (a real, standard definition of
 *      entropy -- not a physics claim about the system).
 *   3. Groups each path into a "dB bin" = floor(10*log2(energy+1)), i.e. a
 *      logarithmic/binary energy grouping, as requested.
 *   4. Projects the path summary through tm_toroidal_map(), the exact same
 *      deterministic update rule already implemented in tm_kernel.c (same
 *      alpha=0.25 exponential smoothing, same wrap01, same 42-state
 *      attractor index), so the resulting 7D state (u,v,psi,chi,rho,delta,
 *      sigma) is consistent with the rest of this codebase instead of being
 *      a new, ad hoc formula. The update logic is duplicated here (not
 *      linked against tm_kernel.c, which is a freestanding/no-libc
 *      translation unit) but is byte-for-byte the same arithmetic.
 *   5. Cross-checks the combinatorial counts quoted for the A/B state space
 *      (pairs, 2x2 blocks, permutations, tensor product, 70x7 cycle) by
 *      computing them programmatically, so those numbers are verified
 *      rather than asserted.
 *
 * Nothing here is a scientific claim about physics, cosmology, or anything
 * external. It is a deterministic, documented data-processing pipeline over
 * the state matrices, suitable to run on a phone/laptop via a plain C
 * compiler (no network, no external dependencies beyond libc).
 *
 * Build:
 *   cc -std=c11 -O2 -lm -o torus_path_sweep torus_path_sweep.c
 * Run:
 *   ./torus_path_sweep > sweep_output.csv
 */

#include <stdio.h>
#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define PATH_STEPS 100
#define HIST_BINS 8 /* per axis, for the entropy histogram: 8^3 = 512 cells */

typedef unsigned int u32;

typedef struct {
    double x, y, z;
} Vec3;

typedef struct {
    double u, v, psi, chi, rho, delta, sigma;
} ToroidalState;

/* ---- original torus/spiral kernel (unchanged) ---- */

static double log_base(double n, double b) {
    return log(n) / log(b);
}

static Vec3 torus(double R, double r, double theta, double phi) {
    Vec3 v;
    v.x = (R + r * cos(theta)) * cos(phi);
    v.y = (R + r * cos(theta)) * sin(phi);
    v.z = r * sin(theta);
    return v;
}

static Vec3 spiral(Vec3 v, double t) {
    Vec3 out;
    double angle = t;
    out.x = v.x * cos(angle) - v.y * sin(angle);
    out.y = v.x * sin(angle) + v.y * cos(angle);
    out.z = v.z + 0.1 * t;
    return out;
}

/* ---- toroidal 7D projection, mirrors tm_kernel.c::tm_toroidal_map ---- */

static double tm_wrap01(double x) {
    double y = x;
    while (y >= 1.0) y -= 1.0;
    while (y < 0.0) y += 1.0;
    return y;
}

static u32 tm_attractor42_index(const ToroidalState *s) {
    u32 a = (u32)(s->u * 7.0) % 7U;
    u32 b = (u32)(s->v * 6.0) % 6U;
    return (a * 6U + b) % 42U;
}

static void tm_toroidal_map(ToroidalState *s, u32 entropy_milli, u32 hash_mix) {
    const double alpha = 0.25;
    double hin = (double)(entropy_milli & 0xFFFFU) / 65535.0;
    double cin = (double)(hash_mix & 0xFFFFU) / 65535.0;
    double h = (1.0 - alpha) * s->rho + alpha * hin;
    double c = (1.0 - alpha) * s->delta + alpha * cin;
    double phi = (1.0 - h) * c;

    s->u = tm_wrap01(s->u + phi + 0.3819660112501051);
    s->v = tm_wrap01(s->v + h + 0.5 * phi);
    s->psi = tm_wrap01(s->psi + c + 0.3333333333333333 * phi);
    s->chi = tm_wrap01(s->chi + 0.75 * h + 0.25 * c);
    s->rho = tm_wrap01(h);
    s->delta = tm_wrap01(c);
    s->sigma = tm_wrap01((double)tm_attractor42_index(s) / 42.0);
}

/* ---- per-path statistics ---- */

typedef struct {
    double curv_mean;
    double curv_std;
    double energy;
    double entropy_bits;
} PathStats;

static int hist_index(Vec3 p, double half_extent) {
    /* map [-half_extent, +half_extent] into HIST_BINS buckets per axis */
    int bx = (int)(((p.x + half_extent) / (2.0 * half_extent)) * HIST_BINS);
    int by = (int)(((p.y + half_extent) / (2.0 * half_extent)) * HIST_BINS);
    int bz = (int)(((p.z + half_extent) / (2.0 * half_extent)) * HIST_BINS);
    if (bx < 0) bx = 0;
    if (bx >= HIST_BINS) bx = HIST_BINS - 1;
    if (by < 0) by = 0;
    if (by >= HIST_BINS) by = HIST_BINS - 1;
    if (bz < 0) bz = 0;
    if (bz >= HIST_BINS) bz = HIST_BINS - 1;
    return (bx * HIST_BINS + by) * HIST_BINS + bz;
}

static PathStats compute_path(double R, double r, double theta0, double phi0) {
    PathStats stats;
    Vec3 path[PATH_STEPS];
    double curv[PATH_STEPS];
    int hist[HIST_BINS * HIST_BINS * HIST_BINS];
    double sum_curv = 0.0, sum_sq_curv = 0.0, energy = 0.0;
    int i;

    memset(hist, 0, sizeof(hist));

    for (i = 0; i < PATH_STEPS; i++) {
        double t = i * 0.1;
        Vec3 v = torus(R, r, theta0 + t, phi0 + 0.5 * t);
        Vec3 s = spiral(v, t);
        path[i] = s;
        curv[i] = log_base(i + 1, 7);
        sum_curv += curv[i];
        sum_sq_curv += curv[i] * curv[i];
        if (i > 0) {
            double dx = path[i].x - path[i - 1].x;
            double dy = path[i].y - path[i - 1].y;
            double dz = path[i].z - path[i - 1].z;
            energy += dx * dx + dy * dy + dz * dz;
        }
        hist[hist_index(s, R + r + 0.1 * PATH_STEPS)]++;
    }

    stats.curv_mean = sum_curv / PATH_STEPS;
    stats.curv_std = sqrt(sum_sq_curv / PATH_STEPS - stats.curv_mean * stats.curv_mean);
    stats.energy = energy;

    /* Shannon entropy (bits) over the position histogram */
    stats.entropy_bits = 0.0;
    for (i = 0; i < HIST_BINS * HIST_BINS * HIST_BINS; i++) {
        if (hist[i] == 0) continue;
        double p = (double)hist[i] / PATH_STEPS;
        stats.entropy_bits -= p * log_base(p, 2.0);
    }

    return stats;
}

/* ---- combinatorial cross-check for the A[8x5]/B[7x3] state space ---- */

static long choose2(long n) {
    return n * (n - 1) / 2;
}

static void print_combinatorial_check(void) {
    const long a_states = 8L * 5L;
    const long b_states = 7L * 3L;
    const long a_2x2_adj = 7L * 4L;       /* (rows-1)*(cols-1) adjacent 2x2 blocks */
    const long b_2x2_adj = 6L * 2L;
    const long perms_per_block = 24L;     /* 4! */
    const long a_2x2_general = choose2(8L) * choose2(5L);
    const long b_2x2_general = choose2(7L) * choose2(3L);

    fprintf(stderr, "# combinatorial cross-check (computed, not asserted)\n");
    fprintf(stderr, "# A states=%ld (want 40)\n", a_states);
    fprintf(stderr, "# B states=%ld (want 21)\n", b_states);
    fprintf(stderr, "# pairs_A=%ld (want 780)\n", choose2(a_states));
    fprintf(stderr, "# pairs_B=%ld (want 210)\n", choose2(b_states));
    fprintf(stderr, "# cross_A_B=%ld (want 840)\n", a_states * b_states);
    fprintf(stderr, "# pairs_of_pairs=%ld (want 163800)\n", choose2(a_states) * choose2(b_states));
    fprintf(stderr, "# blocks_2x2_A_adjacent=%ld (want 28)\n", a_2x2_adj);
    fprintf(stderr, "# blocks_2x2_B_adjacent=%ld (want 12)\n", b_2x2_adj);
    fprintf(stderr, "# A_2x2_adjacent_perm=%ld (want 672)\n", a_2x2_adj * perms_per_block);
    fprintf(stderr, "# B_2x2_adjacent_perm=%ld (want 288)\n", b_2x2_adj * perms_per_block);
    fprintf(stderr, "# A_2x2_general_perm=%ld (want 6720)\n", a_2x2_general * perms_per_block);
    fprintf(stderr, "# B_2x2_general_perm=%ld (want 1512)\n", b_2x2_general * perms_per_block);
    fprintf(stderr, "# tensor_relacional=%ld (want 840)\n", 8L * 5L * 7L * 3L);
    fprintf(stderr, "# cycle_70x7=%ld (want 490)\n", 70L * 7L);
    fprintf(stderr, "# half_cycle=%ld (want 35)\n", (70L * 7L) / 14L);
}

/* ---- sweep driver ---- */

static void sweep_matrix(const char *label, int rows, int cols, double R, double r) {
    int row, col, idx = 0;
    for (row = 0; row < rows; row++) {
        for (col = 0; col < cols; col++, idx++) {
            double theta = (2.0 * M_PI * row) / rows;
            double phi = (2.0 * M_PI * col) / cols;
            PathStats stats = compute_path(R, r, theta, phi);

            u32 energy_milli = (u32)(fmod(stats.energy * 1000.0, 65536.0));
            u32 hash_mix = (u32)(fmod((stats.curv_mean + stats.entropy_bits) * 100000.0, 65536.0));
            int db_group = (int)(10.0 * log_base(stats.energy + 1.0, 2.0));

            ToroidalState t7 = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
            tm_toroidal_map(&t7, energy_milli, hash_mix);

            printf("%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%u\n",
                   label, idx, row, col, theta, phi,
                   stats.curv_mean, stats.curv_std, stats.energy,
                   db_group, stats.entropy_bits,
                   t7.u, t7.v, t7.psi, t7.chi, t7.rho, t7.delta,
                   t7.sigma, tm_attractor42_index(&t7));
        }
    }
}

int main(void) {
    double R = 3.0, r = 1.0;

    printf("matrix,state_idx,row,col,theta,phi,curv_mean,curv_std,energy,db_group,entropy_bits,u,v,psi,chi,rho,delta,sigma,attractor42\n");
    sweep_matrix("A", 8, 5, R, r);
    sweep_matrix("B", 7, 3, R, r);

    print_combinatorial_check();
    return 0;
}
