// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
/*
 * omega_neuro_full.c — NeuroMetrics 29 campos v2
 * ∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor
 *
 * Build AArch64 (Termux):
 *   cc -O3 -march=armv8-a+crc -std=c11 -Wall -o omega_neuro_full omega_neuro_full.c -lm
 *
 * Build ARM32 (Moto E7):
 *   cc -O2 -mfpu=neon-vfpv4 -mfloat-abi=softfp -std=c11 -Wall -o omega_neuro_full omega_neuro_full.c -lm
 *
 * Uso:
 *   ./omega_neuro_full < input.jsonl > neuro.jsonl
 *   ./omega_neuro_full --delta < input  # emite por-chunk (não acumulado)
 *   ./omega_neuro_full --summary < input  # só linha final com totais
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
#include <time.h>
#include <ctype.h>

#if defined(__aarch64__) || defined(__ARM_NEON)
  #include <arm_neon.h>
  #define HAS_NEON 1
#else
  #define HAS_NEON 0
#endif

typedef uint8_t  u8;
typedef uint32_t u32;
typedef uint64_t u64;
typedef int64_t  i64;

/* ── Constantes RAFAELIA ───────────────────────────────────────────── */
#define RAF_SPIRAL_Q16  56756u
#define RAF_PHI_Q16    105965u
#define RAF_ATTRACTORS     42u
#define RAF_FRACTAL_DIM  1347u   /* D_KY × 1000 */
#define RAF_CHUNK        65536u
#define RAF_HIST_BINS      256u
#define RAF_ENT_EXPECTED  4500u  /* entropia esperada para texto misto (milli) */

/* ── FNV1a-64 HashSet para s_latent ───────────────────────────────── */
typedef struct { u64 *slots; u64 cap; u64 len; } HashSet;

static u64 fnv1a64(const void *d, u64 n) {
    const u8 *p = (const u8*)d;
    u64 h = 14695981039346656037ULL;
    for (u64 i = 0; i < n; i++) { h ^= p[i]; h *= 1099511628211ULL; }
    return h;
}
static void hs_init(HashSet *s) {
    s->cap = 2048; s->len = 0;
    s->slots = (u64*)calloc(s->cap, sizeof(u64));
}
static void hs_free(HashSet *s)  { free(s->slots); s->slots = NULL; }
static void hs_reset(HashSet *s) {
    memset(s->slots, 0, s->cap * sizeof(u64)); s->len = 0;
}
static void hs_rehash(HashSet *s, u64 nc) {
    u64 *old = s->slots, oc = s->cap;
    s->slots = (u64*)calloc(nc, sizeof(u64)); s->cap = nc; s->len = 0;
    for (u64 i = 0; i < oc; i++) {
        if (!old[i]) continue;
        u64 h = old[i] % nc;
        while (s->slots[h]) h = (h+1) % nc;
        s->slots[h] = old[i]; s->len++;
    }
    free(old);
}
static void hs_add(HashSet *s, u64 k) {
    if (!k) k = 1;
    if (s->len * 4 >= s->cap * 3) hs_rehash(s, s->cap * 2);
    u64 h = k % s->cap;
    while (s->slots[h] && s->slots[h] != k) h = (h+1) % s->cap;
    if (!s->slots[h]) { s->slots[h] = k; s->len++; }
}
static void tokenize_add(HashSet *s, const u8 *buf, u64 n) {
    char w[64]; int wl = 0;
    for (u64 i = 0; i <= n; i++) {
        int c = (i < n) ? buf[i] : 0;
        if (isalnum(c) || c == '_') { if (wl < 63) w[wl++] = (char)tolower(c); }
        else { if (wl >= 4) hs_add(s, fnv1a64(w, (u64)wl)); wl = 0; }
    }
}

/* ── Histograma → Shannon entropy milli ──────────────────────────── */
typedef struct { u64 freq[RAF_HIST_BINS]; u64 total; } Hist;

static void hist_update(Hist *h, const u8 *buf, u64 n) {
    for (u64 i = 0; i < n; i++) h->freq[buf[i]]++;
    h->total += n;
}
static u64 hist_entropy_milli(const Hist *h) {
    if (!h->total) return 0;
    double ent = 0.0, inv = 1.0 / (double)h->total;
    for (u32 i = 0; i < RAF_HIST_BINS; i++) {
        if (!h->freq[i]) continue;
        double p = (double)h->freq[i] * inv;
        ent -= p * log2(p);
    }
    return (u64)(ent * 1000.0 + 0.5);
}
static u64 hist_unique(const Hist *h) {
    u64 u = 0;
    for (u32 i = 0; i < RAF_HIST_BINS; i++) if (h->freq[i]) u++;
    return u;
}

/* ── Contadores NEON ──────────────────────────────────────────────── */
typedef struct {
    u64 bytes, hibit, space, nl, brace, quote, colon, comma;
} CharCnt;

#if HAS_NEON
static inline u64 sum_mask(uint8x16_t m) {
    return (u64)vaddvq_u8(vshrq_n_u8(m, 7));
}
static void charcnt_neon(CharCnt *c, const u8 *p, u64 n) {
    u64 al = n & ~15ULL, rem = n & 15ULL;
    const uint8x16_t vsp  = vdupq_n_u8(' ');
    const uint8x16_t vnl  = vdupq_n_u8('\n');
    const uint8x16_t vlc  = vdupq_n_u8('{');
    const uint8x16_t vrc  = vdupq_n_u8('}');
    const uint8x16_t vq   = vdupq_n_u8('"');
    const uint8x16_t vcol = vdupq_n_u8(':');
    const uint8x16_t vcom = vdupq_n_u8(',');
    while (al) {
        uint8x16_t v = vld1q_u8(p);
        c->hibit += (u64)vaddvq_u8(vshrq_n_u8(v, 7));
        c->space += sum_mask(vceqq_u8(v, vsp));
        c->nl    += sum_mask(vceqq_u8(v, vnl));
        c->quote += sum_mask(vceqq_u8(v, vq));
        c->colon += sum_mask(vceqq_u8(v, vcol));
        c->comma += sum_mask(vceqq_u8(v, vcom));
        c->brace += sum_mask(vorrq_u8(vceqq_u8(v,vlc), vceqq_u8(v,vrc)));
        c->bytes += 16; p += 16; al -= 16;
    }
    while (rem--) {
        u8 x = *p++; c->bytes++;
        if (x & 0x80)          c->hibit++;
        if (x == ' ')          c->space++;
        if (x == '\n')         c->nl++;
        if (x == '"')          c->quote++;
        if (x == ':')          c->colon++;
        if (x == ',')          c->comma++;
        if (x=='{' || x=='}')  c->brace++;
    }
}
#else
static void charcnt_neon(CharCnt *c, const u8 *p, u64 n) {
    for (u64 i = 0; i < n; i++) {
        u8 x = p[i]; c->bytes++;
        if (x & 0x80)         c->hibit++;
        if (x == ' ')         c->space++;
        if (x == '\n')        c->nl++;
        if (x == '"')         c->quote++;
        if (x == ':')         c->colon++;
        if (x == ',')         c->comma++;
        if (x=='{' || x=='}') c->brace++;
    }
}
#endif

/* ── Popcount via vcntq_u8 ou builtin ────────────────────────────── */
static u64 count_ones(const u8 *buf, u64 n) {
#if HAS_NEON
    u64 al = n & ~15ULL, rem = n & 15ULL, acc = 0;
    const u8 *p = buf;
    while (al) {
        acc += (u64)vaddvq_u8(vcntq_u8(vld1q_u8(p)));
        p += 16; al -= 16;
    }
    while (rem--) acc += (u64)__builtin_popcount(*p++);
    return acc;
#else
    u64 acc = 0;
    for (u64 i = 0; i < n; i++) acc += (u64)__builtin_popcount(buf[i]);
    return acc;
#endif
}

/* ── Transition energy & Hamming ─────────────────────────────────── */
static u64 transition_energy(const u8 *buf, u64 n) {
    u64 e = 0;
    for (u64 i = 1; i < n; i++)
        e += (u64)__builtin_popcount((u8)(buf[i] ^ buf[i-1]));
    return e;
}
static u64 hamming_chunks(const u8 *a, const u8 *b, u64 n) {
    u64 h = 0, i = 0;
#if HAS_NEON
    while (i + 15 < n) {
        h += (u64)vaddvq_u8(vcntq_u8(veorq_u8(vld1q_u8(a+i), vld1q_u8(b+i))));
        i += 16;
    }
#endif
    for (; i < n; i++) h += (u64)__builtin_popcount((u8)(a[i]^b[i]));
    return h;
}

/* ── Clamp u64 ──────────────────────────────────────────────────── */
static inline u64 clamp64(u64 v, u64 lo, u64 hi) {
    return v < lo ? lo : v > hi ? hi : v;
}

/* ── Timer ───────────────────────────────────────────────────────── */
static u64 ns_now(void) {
#if defined(__aarch64__)
    u64 v;
    __asm__ volatile("isb\nmrs %0,cntvct_el0":"=r"(v)::"memory");
    return v;
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (u64)ts.tv_sec * 1000000000ULL + (u64)ts.tv_nsec;
#endif
}

/* ── NeuroMetrics struct (29 campos) ────────────────────────────── */
typedef struct {
    u64 s_syn;         /* [01] bytes ≥128 (massa simbólica/Unicode)             */
    u64 s_emerg;       /* [02] bytes totais (volume emergente)                  */
    u64 s_latent;      /* [03] tokens únicos FNV1a64 ≥4 chars                  */
    u64 entropy_raw;   /* [04] Shannon entropy × 1000 (0–8000)                 */
    u64 burst_factor;  /* [05] espaços acumulados (densidade de burst)         */
    u64 iso_ratio;     /* [06] bytes únicos / 256 × 1000 (isolamento)          */
    u64 technical_d;   /* [07] quote+colon+comma (densidade JSON/técnica)       */
    u64 noise_floor;   /* [08] hibit × 1000 / bytes (ruído simbólico ‰)       */
    u64 alpha_wave;    /* [09] newlines (onda estrutural de linhas)            */
    u64 beta_wave;     /* [10] braces {} (onda estrutural JSON)                */
    u64 phi_integral;  /* [11] (1−H/8)×(lat/bytes) Q16 (integral ética φ)    */
    u64 k_constant;    /* [12] 42 (constante toroidal RAFAELIA, invariante)    */
    u64 sync_clock;    /* [13] ticks desde _start (relógio de sincronização)   */
    u64 semantic_gap;  /* [14] |H_cur − H_prev| (salto semântico entre chunks) */
    u64 logic_depth;   /* [15] transition_energy (profundidade lógica local)   */
    u64 token_ratio;   /* [16] quote × 1000 / bytes (razão de tokenização)    */
    u64 bit_depth_10;  /* [17] ones × 1000 / (bytes×8) (profundidade de bits) */
    u64 context_span;  /* [18] nl × 1000 / bytes (amplitude de contexto)      */
    u64 feedback_loop; /* [19] hibit × 1000 / bytes (mesmo que noise_floor)   */
                       /*      → loop: acumulado vs snapshot                   */
    u64 neural_res;    /* [20] sqrt(nl×brace) × 1000 / bytes (ressonância)    */
    u64 cross_entropy; /* [21] |H_cur − RAF_ENT_EXPECTED| (desvio do esperado) */
    u64 weight_bias;   /* [22] hibit × 1000 / max(1,s_latent) (peso/token)    */
    u64 fractal_dim;   /* [23] 1347 (D_KY RAFAELIA, constante verificada)     */
    u64 hamming_dist;  /* [24] XOR popcount prev_chunk↔cur_chunk               */
    u64 kurtosis_val;  /* [25] (space_pm²) / max(1,nl_pm) (curtose normaliz.) */
    u64 skewness_val;  /* [26] |colon−comma| × 1000 / bytes (assimetria)      */
    u64 chi_square;    /* [27] |H − 4500|² / 1000 (χ² vs entropia esperada)   */
    u64 p_value_est;   /* [28] 1000 − clamp(χ²/100, 0, 1000)                  */
    u64 omega_point;   /* [29] (hibit + entropy_milli) mod 42 (atrator)        */
} NeuroMetrics;

/* ── Calcula métricas a partir de acc por chunk ──────────────────── */
static NeuroMetrics compute(
    const CharCnt *cc, const Hist *hist, const HashSet *hs,
    u64 ones, u64 t_logic, u64 hdist,
    u64 prev_ent, u64 t_start)
{
    u64 B   = cc->bytes ? cc->bytes : 1;
    u64 ent = hist_entropy_milli(hist);
    u64 uniq = hist_unique(hist);

    /* phi_integral: (1 − H/8000) × (lat/bytes) × 65536 */
    double H_n   = (double)ent / 8000.0;
    double lat_n = (double)hs->len / (double)B;
    u64 phi_int  = (u64)((1.0 - H_n) * lat_n * 65536.0);

    /* neural_resonance: sqrt(nl × brace) × 1000 / bytes */
    u64 nr = 0;
    if (cc->nl > 0 && cc->brace > 0)
        nr = (u64)(sqrt((double)cc->nl * (double)cc->brace) * 1000.0 / (double)B);

    /* Versão normalizada por promille para kurtosis */
    u64 sp_pm = cc->space * 1000 / B;           /* space ‰ */
    u64 nl_pm = cc->nl > 0 ? cc->nl * 1000 / B : 1;
    u64 kurt  = clamp64(sp_pm * sp_pm / nl_pm, 0, 999999);

    /* chi_square vs entropia esperada RAF_ENT_EXPECTED */
    i64 dent  = (i64)ent - (i64)RAF_ENT_EXPECTED;
    u64 chi2  = clamp64((u64)(dent*dent) / 1000, 0, 1000);

    u64 gap   = ent > prev_ent ? ent - prev_ent : prev_ent - ent;
    u64 crent = ent > RAF_ENT_EXPECTED
                ? ent - RAF_ENT_EXPECTED
                : RAF_ENT_EXPECTED - ent;

    NeuroMetrics m = {
        .s_syn        = cc->hibit,
        .s_emerg      = cc->bytes,
        .s_latent     = hs->len,
        .entropy_raw  = ent,
        .burst_factor = cc->space,
        .iso_ratio    = uniq * 1000 / RAF_HIST_BINS,
        .technical_d  = cc->quote + cc->colon + cc->comma,
        .noise_floor  = cc->hibit * 1000 / B,
        .alpha_wave   = cc->nl,
        .beta_wave    = cc->brace,
        .phi_integral = phi_int,
        .k_constant   = RAF_ATTRACTORS,
        .sync_clock   = ns_now() - t_start,
        .semantic_gap = gap,
        .logic_depth  = t_logic,
        .token_ratio  = cc->quote * 1000 / B,
        .bit_depth_10 = ones * 1000 / (B * 8),
        .context_span = cc->nl * 1000 / B,
        .feedback_loop= cc->hibit * 1000 / B,
        .neural_res   = nr,
        .cross_entropy= crent,
        .weight_bias  = hs->len ? cc->hibit * 1000 / hs->len : 0,
        .fractal_dim  = RAF_FRACTAL_DIM,
        .hamming_dist = hdist,
        .kurtosis_val = kurt,
        .skewness_val = (cc->colon > cc->comma
                         ? (cc->colon - cc->comma) * 1000 / B
                         : (cc->comma - cc->colon) * 1000 / B),
        .chi_square   = chi2,
        .p_value_est  = chi2 < 1000 ? 1000 - chi2 : 0,
        .omega_point  = (cc->hibit + ent) % RAF_ATTRACTORS,
    };
    return m;
}

/* ── Emite linha JSONL ───────────────────────────────────────────── */
static void emit(FILE *out, const NeuroMetrics *m, u64 id, const char *label) {
    fprintf(out,
        "{\"id\":%llu,\"label\":\"%s\","
        "\"s_syn\":%llu,\"s_emerg\":%llu,\"s_latent\":%llu,"
        "\"entropy_raw\":%llu,\"burst_factor\":%llu,\"iso_ratio\":%llu,"
        "\"technical_d\":%llu,\"noise_floor\":%llu,"
        "\"alpha_wave\":%llu,\"beta_wave\":%llu,\"phi_integral\":%llu,"
        "\"k_constant\":%llu,\"sync_clock\":%llu,\"semantic_gap\":%llu,"
        "\"logic_depth\":%llu,\"token_ratio\":%llu,\"bit_depth_10\":%llu,"
        "\"context_span\":%llu,\"feedback_loop\":%llu,\"neural_res\":%llu,"
        "\"cross_entropy\":%llu,\"weight_bias\":%llu,\"fractal_dim\":%llu,"
        "\"hamming_dist\":%llu,\"kurtosis_val\":%llu,\"skewness_val\":%llu,"
        "\"chi_square\":%llu,\"p_value_est\":%llu,\"omega_point\":%llu}\n",
        (unsigned long long)id, label,
        (unsigned long long)m->s_syn,  (unsigned long long)m->s_emerg,
        (unsigned long long)m->s_latent,(unsigned long long)m->entropy_raw,
        (unsigned long long)m->burst_factor,(unsigned long long)m->iso_ratio,
        (unsigned long long)m->technical_d,(unsigned long long)m->noise_floor,
        (unsigned long long)m->alpha_wave,(unsigned long long)m->beta_wave,
        (unsigned long long)m->phi_integral,(unsigned long long)m->k_constant,
        (unsigned long long)m->sync_clock,(unsigned long long)m->semantic_gap,
        (unsigned long long)m->logic_depth,(unsigned long long)m->token_ratio,
        (unsigned long long)m->bit_depth_10,(unsigned long long)m->context_span,
        (unsigned long long)m->feedback_loop,(unsigned long long)m->neural_res,
        (unsigned long long)m->cross_entropy,(unsigned long long)m->weight_bias,
        (unsigned long long)m->fractal_dim,(unsigned long long)m->hamming_dist,
        (unsigned long long)m->kurtosis_val,(unsigned long long)m->skewness_val,
        (unsigned long long)m->chi_square,(unsigned long long)m->p_value_est,
        (unsigned long long)m->omega_point);
    fflush(out);
}

/* ── Main ───────────────────────────────────────────────────────── */
int main(int argc, char **argv) {
    int mode_delta   = 0;  /* --delta: emite per-chunk, reset entre chunks */
    int mode_summary = 0;  /* --summary: só linha final com totais         */

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--delta"))   mode_delta = 1;
        if (!strcmp(argv[i], "--summary")) mode_summary = 1;
    }

    static u8 buf[RAF_CHUNK], prev[RAF_CHUNK];
    int prev_ok = 0;

    /* Acumuladores GLOBAIS */
    CharCnt  cc_g   = {0};
    Hist     hist_g = {{0},0};
    HashSet  hs_g;   hs_init(&hs_g);
    u64 ones_g = 0, tl_g = 0, prev_ent_g = 0;

    /* Acumuladores PER-CHUNK (para --delta) */
    CharCnt  cc_c;
    Hist     hist_c;
    HashSet  hs_c;   hs_init(&hs_c);
    u64 ones_c, tl_c;

    u64 chunk_id = 0;
    u64 t_start  = ns_now();

    for (;;) {
        size_t r = fread(buf, 1, RAF_CHUNK, stdin);
        if (!r) break;

        /* ── Acumula global ── */
        charcnt_neon(&cc_g, buf, (u64)r);
        hist_update (&hist_g, buf, (u64)r);
        tokenize_add(&hs_g,  buf, (u64)r);
        ones_g += count_ones(buf, (u64)r);
        tl_g   += transition_energy(buf, (u64)r);
        u64 hdist = prev_ok ? hamming_chunks(prev, buf, (u64)r) : 0;

        if (mode_delta) {
            /* ── Acumula per-chunk ── */
            memset(&cc_c,  0, sizeof(cc_c));
            memset(&hist_c,0, sizeof(hist_c));
            hs_reset(&hs_c);
            ones_c = tl_c = 0;

            charcnt_neon(&cc_c, buf, (u64)r);
            hist_update (&hist_c, buf, (u64)r);
            tokenize_add(&hs_c,  buf, (u64)r);
            ones_c += count_ones(buf, (u64)r);
            tl_c   += transition_energy(buf, (u64)r);

            u64 cur_ent = hist_entropy_milli(&hist_g);
            NeuroMetrics m = compute(&cc_c, &hist_c, &hs_c,
                                     ones_c, tl_c, hdist,
                                     prev_ent_g, t_start);
            if (!mode_summary) emit(stdout, &m, chunk_id, "chunk");
            prev_ent_g = cur_ent;
        } else {
            u64 cur_ent = hist_entropy_milli(&hist_g);
            NeuroMetrics m = compute(&cc_g, &hist_g, &hs_g,
                                     ones_g, tl_g, hdist,
                                     prev_ent_g, t_start);
            if (!mode_summary) emit(stdout, &m, chunk_id, "accum");
            prev_ent_g = cur_ent;
        }

        memcpy(prev, buf, r);
        prev_ok = 1;
        chunk_id++;
    }

    /* ── Summary final (totais globais) ── */
    if (mode_summary || chunk_id > 1) {
        NeuroMetrics tot = compute(&cc_g, &hist_g, &hs_g,
                                   ones_g, tl_g, 0,
                                   0, t_start);
        emit(stdout, &tot, chunk_id, "total");
    }

    hs_free(&hs_g);
    hs_free(&hs_c);
    return 0;
}
