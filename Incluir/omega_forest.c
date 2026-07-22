// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
/*
 * omega_forest.c  —  Forest Knowledge Graph  v1.0
 * ∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor
 *
 * Constrói a floresta de 42 atratores a partir do corpus OMEGA.
 * Cada nó = uma conversa com vetor [IC, PP, CV] + path de ausência.
 *
 * 5 caminhos:
 *   VOID         — IC_adj < 0.01 & IC < 0.05  (espaço vazio)
 *   FORGOTTEN    — msgs < 10 & processual      (esquecido antes de crescer)
 *   MENOSPREZADO — processual & IC > threshold  (ouro enterrado)
 *   URGENT       — produto_maduro               (pronto, precisa de ação)
 *   PROCESSUAL   — todo o resto                 (massa do corpus)
 *
 * Build AArch64 (Termux):
 *   cc -O3 -march=armv8-a -std=c11 -Wall omega_forest.c -o omega_forest -lm
 * Build ARM32 (Moto E7):
 *   cc -O2 -mfpu=neon -mfloat-abi=softfp -std=c11 omega_forest.c -o omega_forest -lm
 *
 * Uso:
 *   ./omega_forest omega_metrics_v3.jsonl omega_conv_stats.jsonl > forest.jsonl
 *   ./omega_forest --summary  omega_metrics_v3.jsonl omega_conv_stats.jsonl
 *   ./omega_forest --path MENOSPREZADO omega_metrics_v3.jsonl omega_conv_stats.jsonl
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
#include <ctype.h>

typedef uint8_t  u8;
typedef uint32_t u32;
typedef uint64_t u64;
typedef float    f32;

/* ── Constantes RAFAELIA ──────────────────────────────────────────── */
#define RAF_K          42      /* atratores toroidais                 */
#define RAF_DECAY_MAX  0.30f   /* decaimento máximo por antiguidade   */
#define RAF_IC_SD_MULT 1.50f   /* threshold MENOSPREZADO: μ + 1.5σ   */
#define RAF_MSGS_FORG  10      /* limiar FORGOTTEN                    */
#define RAF_IC_VOID    0.05f   /* limiar VOID                         */
#define RAF_ICADJ_VOID 0.01f
#define MAX_CONVS      8192    /* máximo de conversas (> 3572)        */
#define MAX_TITLE      128

/* ── Tipo de caminho ──────────────────────────────────────────────── */
typedef enum {
    PATH_PROCESSUAL   = 0,
    PATH_VOID         = 1,
    PATH_FORGOTTEN    = 2,
    PATH_MENOSPREZADO = 3,
    PATH_URGENT       = 4,
} Path;

static const char *PATH_NAME[] = {
    "PROCESSUAL", "VOID", "FORGOTTEN", "MENOSPREZADO", "URGENT"
};

/* ── Nó do grafo ──────────────────────────────────────────────────── */
typedef struct {
    u32  conv_i;
    u32  msgs;
    f32  IC, PP, CV;
    f32  IC_adj, PP_adj, CV_adj;
    u32  cluster;      /* 0–41                                        */
    u32  parent;       /* conv_i do pai (0xFFFFFFFF = raiz)           */
    uint8_t level;        /* profundidade na árvore (0 = raiz)           */
    f32  weight;       /* IC_adj × PP_adj × (1 − decay)              */
    f32  decay;        /* antiguidade normalizada [0, RAF_DECAY_MAX]  */
    Path path;
    int  is_maduro;    /* 1 se classe = produto_maduro                */
    char title[MAX_TITLE];
} Node;

/* ── Centróide de cluster ─────────────────────────────────────────── */
typedef struct { f32 IC, PP, CV; u32 n; } Centroid;

/* ── Parser JSON minimalista ──────────────────────────────────────── */

/* Extrai valor float de campo "key": num */
static f32 jf(const char *line, const char *key) {
    const char *p = strstr(line, key);
    if (!p) return 0.0f;
    p += strlen(key);
    while (*p == '"' || *p == ':' || *p == ' ') p++;
    return (f32)atof(p);
}

/* Extrai valor int de campo "key": num */
static int ji(const char *line, const char *key) {
    const char *p = strstr(line, key);
    if (!p) return 0;
    p += strlen(key);
    while (*p == '"' || *p == ':' || *p == ' ') p++;
    return atoi(p);
}

/* Extrai string de campo "key": "val" */
static void js(const char *line, const char *key, char *out, int maxlen) {
    const char *p = strstr(line, key);
    if (!p) { out[0]='\0'; return; }
    p += strlen(key);
    while (*p && *p != '"') p++;
    if (*p == '"') p++;
    int i = 0;
    while (*p && *p != '"' && i < maxlen-1) out[i++] = *p++;
    out[i] = '\0';
}

/* ── Distância euclidiana ao quadrado no espaço [IC, PP, CV] ──────── */
static inline f32 dist2(f32 a0, f32 a1, f32 a2,
                         f32 b0, f32 b1, f32 b2) {
    f32 d0 = a0-b0, d1 = a1-b1, d2 = a2-b2;
    return d0*d0 + d1*d1 + d2*d2;
}


/* Sanitiza título: remove chars de controle e escapa aspas */
static void sanitize(char *out, const char *in, int maxlen) {
    int j = 0;
    for (int i = 0; in[i] && j < maxlen-2; i++) {
        unsigned char c = (unsigned char)in[i];
        if (c < 0x20 || c == 0x7f) continue; /* remove ctrl */
        if (c == '"') { out[j++]='\\'; out[j++]='"'; }
        else if (c == '\\') { out[j++]='\\'; out[j++]='\\'; }
        else out[j++] = (char)c;
    }
    out[j] = '\0';
}

/* ── Main ─────────────────────────────────────────────────────────── */
int main(int argc, char **argv) {
    /* Opções */
    int  mode_summary = 0;
    const char *filter_path = NULL;
    char *f_m3 = NULL, *f_cs = NULL;

    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--summary"))       mode_summary = 1;
        else if (!strcmp(argv[i], "--path") && i+1 < argc)
            filter_path = argv[++i];
        else if (!f_m3) f_m3 = argv[i];
        else             f_cs = argv[i];
    }
    if (!f_m3) { fprintf(stderr,"uso: omega_forest [--summary] [--path TIPO] metrics_v3.jsonl conv_stats.jsonl\n"); return 1; }

    /* ── Carrega métricas v3 ── */
    static Node nodes[MAX_CONVS];
    int N = 0;

    FILE *fm = fopen(f_m3, "r");
    if (!fm) { perror(f_m3); return 1; }
    {
        char line[4096];
        while (fgets(line, sizeof(line), fm) && N < MAX_CONVS) {
            Node *nd = &nodes[N];
            nd->conv_i  = (u32)ji(line, "\"conv_i\"");
            nd->msgs    = (u32)ji(line, "\"msgs\"");
            nd->IC      = jf(line, "\"IC\"");
            nd->PP      = jf(line, "\"PP\"");
            nd->CV      = jf(line, "\"CV\"");
            nd->IC_adj  = jf(line, "\"IC_adj\"");
            nd->PP_adj  = jf(line, "\"PP_adj\"");
            nd->CV_adj  = jf(line, "\"CV_adj\"");
            char cls[32]; js(line, "\"class\"", cls, sizeof(cls));
            nd->is_maduro = (strstr(cls, "produto_maduro") != NULL);
            nd->parent  = 0xFFFFFFFF;
            nd->title[0] = '\0';
            N++;
        }
    }
    fclose(fm);

    /* ── Carrega títulos de conv_stats ── */
    if (f_cs) {
        FILE *fc = fopen(f_cs, "r");
        if (fc) {
            char line[8192];
            while (fgets(line, sizeof(line), fc)) {
                int ci = ji(line, "\"conv_i\"");
                if (ci >= 0 && ci < N) {
                    char ttl[MAX_TITLE];
                    js(line, "\"title\"", ttl, sizeof(ttl));
                    sanitize(nodes[ci].title, ttl, MAX_TITLE);
                }
            }
            fclose(fc);
        }
    }

    /* ── Calcula μ e σ de IC para threshold MENOSPREZADO ── */
    f32 ic_sum = 0.0f;
    for (int i = 0; i < N; i++) ic_sum += nodes[i].IC;
    f32 ic_mean = ic_sum / (f32)N;
    f32 ic_var  = 0.0f;
    for (int i = 0; i < N; i++) {
        f32 d = nodes[i].IC - ic_mean; ic_var += d*d;
    }
    f32 ic_sd  = sqrtf(ic_var / (f32)N);
    f32 thresh = ic_mean + RAF_IC_SD_MULT * ic_sd;

    /* ── Classifica caminhos ── */
    int counts[5] = {0};
    for (int i = 0; i < N; i++) {
        Node *nd = &nodes[i];
        f32 t = (f32)nd->conv_i / (f32)N;       /* 0=antigo, 1=recente */
        nd->decay  = RAF_DECAY_MAX * (1.0f - t);
        nd->weight = nd->IC_adj * nd->PP_adj * (1.0f - nd->decay);

        if (nd->IC_adj < RAF_ICADJ_VOID && nd->IC < RAF_IC_VOID)
            nd->path = PATH_VOID;
        else if ((int)nd->msgs < RAF_MSGS_FORG && !nd->is_maduro)
            nd->path = PATH_FORGOTTEN;
        else if (!nd->is_maduro && nd->IC > thresh)
            nd->path = PATH_MENOSPREZADO;
        else if (nd->is_maduro)
            nd->path = PATH_URGENT;
        else
            nd->path = PATH_PROCESSUAL;

        counts[nd->path]++;
    }

    /* ── K-means k=42 em grade [IC×PP] ── */
    /* Sementes: grade 7×6 no espaço [0, 1.5] × [0, 1.75] */
    static Centroid centroids[RAF_K];
    for (int g = 0; g < RAF_K; g++) {
        int ic_bin = g / 6, pp_bin = g % 6;
        centroids[g].IC = (ic_bin + 0.5f) * (1.5f / 7.0f);
        centroids[g].PP = (pp_bin + 0.5f) * (1.75f / 6.0f);
        centroids[g].CV = 0.5f;
        centroids[g].n  = 0;
    }

    for (int iter = 0; iter < 8; iter++) {
        /* Assign */
        for (int i = 0; i < N; i++) {
            Node *nd = &nodes[i];
            int best = 0;
            f32  bd  = 1e30f;
            for (int c = 0; c < RAF_K; c++) {
                f32 d = dist2(nd->IC, nd->PP, nd->CV,
                              centroids[c].IC, centroids[c].PP, centroids[c].CV);
                if (d < bd) { bd = d; best = c; }
            }
            nd->cluster = (u32)best;
        }
        /* Update */
        static f32 sum_ic[RAF_K], sum_pp[RAF_K], sum_cv[RAF_K];
        static int cnt[RAF_K];
        memset(sum_ic,0,sizeof(sum_ic)); memset(sum_pp,0,sizeof(sum_pp));
        memset(sum_cv,0,sizeof(sum_cv)); memset(cnt,0,sizeof(cnt));
        for (int i = 0; i < N; i++) {
            int c = (int)nodes[i].cluster;
            sum_ic[c] += nodes[i].IC; sum_pp[c] += nodes[i].PP;
            sum_cv[c] += nodes[i].CV; cnt[c]++;
        }
        for (int c = 0; c < RAF_K; c++) {
            if (cnt[c]) {
                centroids[c].IC = sum_ic[c] / cnt[c];
                centroids[c].PP = sum_pp[c] / cnt[c];
                centroids[c].CV = sum_cv[c] / cnt[c];
                centroids[c].n  = (u32)cnt[c];
            }
        }
    }

    /* ── Constrói árvore por cluster ── */
    /* Raiz = maior weight no cluster; filhos ligados ao vizinho acima */
    for (int c = 0; c < RAF_K; c++) {
        /* Coleta membros do cluster */
        static int mem[MAX_CONVS]; int nm = 0;
        for (int i = 0; i < N; i++)
            if ((int)nodes[i].cluster == c) mem[nm++] = i;
        if (!nm) continue;

        /* Ordena por weight desc (selection sort — simples, N pequeno) */
        for (int a = 0; a < nm-1; a++)
            for (int b = a+1; b < nm; b++)
                if (nodes[mem[b]].weight > nodes[mem[a]].weight) {
                    int tmp = mem[a]; mem[a] = mem[b]; mem[b] = tmp;
                }

        /* Raiz */
        nodes[mem[0]].parent = 0xFFFFFFFF;
        nodes[mem[0]].level  = 0;

        /* Filhos: cada um conecta ao membro anterior com maior weight */
        for (int k = 1; k < nm; k++) {
            int best_p = mem[0];
            f32 bd = 1e30f;
            for (int p = 0; p < k; p++) {
                f32 d = dist2(nodes[mem[k]].IC, nodes[mem[k]].PP, nodes[mem[k]].CV,
                              nodes[mem[p]].IC, nodes[mem[p]].PP, nodes[mem[p]].CV);
                if (d < bd) { bd = d; best_p = mem[p]; }
            }
            nodes[mem[k]].parent = nodes[best_p].conv_i;
            nodes[mem[k]].level  = (uint8_t)(nodes[best_p].level + 1);
            if (nodes[mem[k]].level > 7) nodes[mem[k]].level = 7;
        }
    }

    /* ── Emite ── */
    if (mode_summary) {
        fprintf(stdout, "{\n");
        fprintf(stdout, "  \"total\": %d,\n", N);
        fprintf(stdout, "  \"ic_mean\": %.4f, \"ic_sd\": %.4f,\n", ic_mean, ic_sd);
        fprintf(stdout, "  \"thresh_menosprezado\": %.4f,\n", thresh);
        for (int p = 0; p < 5; p++)
            fprintf(stdout, "  \"%s\": %d%s\n",
                PATH_NAME[p], counts[p], p<4 ? "," : "");
        fprintf(stdout, "}\n");
        return 0;
    }

    for (int i = 0; i < N; i++) {
        Node *nd = &nodes[i];
        if (filter_path && strcmp(PATH_NAME[nd->path], filter_path)) continue;
        char par_str[16];
        if (nd->parent == 0xFFFFFFFF) snprintf(par_str,16,"null");
        else snprintf(par_str,16,"%u",nd->parent);
        fprintf(stdout,
            "{\"conv_i\":%u,\"cluster\":%u,\"level\":%u,"
            "\"parent\":%s,\"path\":\"%s\","
            "\"IC\":%.4f,\"PP\":%.4f,\"CV\":%.4f,"
            "\"IC_adj\":%.5f,\"PP_adj\":%.5f,"
            "\"weight\":%.5f,\"decay\":%.4f,"
            "\"msgs\":%u,\"class\":\"%s\","
            "\"title\":\"%s\"}\n",
            nd->conv_i, nd->cluster, nd->level,
            par_str,
            PATH_NAME[nd->path],
            nd->IC, nd->PP, nd->CV, nd->IC_adj, nd->PP_adj,
            nd->weight, nd->decay, nd->msgs,
            nd->is_maduro ? "produto_maduro" : "processual",
            nd->title);
    }
    return 0;
}
