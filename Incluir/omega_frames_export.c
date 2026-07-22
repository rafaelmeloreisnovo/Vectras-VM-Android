// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
/*
 * omega_frames_export.c  —  forest.jsonl → frames_seed.json
 * ∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor
 *
 * Fecha o elo entre a classificação (omega_forest) e o consumo
 * (RafaeliaEngine.kt no APK). Lê forest.jsonl, seleciona os N
 * melhores URGENT (por PP_adj) e N melhores MENOSPREZADO (por IC),
 * extrai tags do título, e tenta puxar conteúdo real de
 * omega_msgs.jsonl se presente — senão usa um placeholder marcado
 * explicitamente, para nunca fingir ter texto que não tem.
 *
 * ASSUNÇÃO [HIP] sobre omega_msgs.jsonl (não verificada — ajuste os
 * nomes de campo abaixo se o seu arquivo usar outros rótulos):
 *   {"conv_i":N,"role":"user"|"assistant","content":"...","ts":...}
 *   uma linha por mensagem, na ordem da conversa.
 *
 * Build:
 *   cc -O2 -std=c11 -Wall omega_frames_export.c -o omega_frames_export
 *
 * Uso:
 *   ./omega_frames_export forest.jsonl > frames_seed.json
 *   ./omega_frames_export forest.jsonl omega_msgs.jsonl > frames_seed.json
 *   ./omega_frames_export forest.jsonl omega_msgs.jsonl --top 8 > frames_seed.json
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#define MAX_NODES     8192
#define MAX_TITLE      128
#define MAX_TAGS         8
#define MAX_CONTENT   2000   /* por frame, somando msgs extraídas        */
#define DEFAULT_TOP     10   /* por caminho (URGENT / MENOSPREZADO)      */

typedef struct {
    int   conv_i, msgs;
    float IC, PP, PP_adj;
    char  path[16];
    char  title[MAX_TITLE];
} Node;

/* ── Parser minimalista (mesmo padrão do omega_forest.c) ─────────── */
static float jf(const char *line, const char *key) {
    const char *p = strstr(line, key);
    if (!p) return 0.0f;
    p += strlen(key);
    while (*p=='"'||*p==':'||*p==' ') p++;
    return (float)atof(p);
}
static int ji(const char *line, const char *key) {
    const char *p = strstr(line, key);
    if (!p) return 0;
    p += strlen(key);
    while (*p=='"'||*p==':'||*p==' ') p++;
    return atoi(p);
}
static void js(const char *line, const char *key, char *out, int maxlen) {
    const char *p = strstr(line, key);
    if (!p) { out[0]='\0'; return; }
    p += strlen(key);
    while (*p && *p!='"') p++;
    if (*p=='"') p++;
    int i=0;
    while (*p && *p!='"' && i<maxlen-1) {
        if (*p=='\\' && *(p+1)) p++;  /* pula escape simples */
        out[i++]=*p++;
    }
    out[i]='\0';
}

/* ── Sanitiza para JSON de saída ───────────────────────────────────── */
static void sanitize(char *out, const char *in, int maxlen) {
    int j=0;
    for (int i=0; in[i] && j<maxlen-2; i++) {
        unsigned char c=(unsigned char)in[i];
        if (c<0x20 || c==0x7f) { if (c=='\n'||c=='\t') { out[j++]=' '; } continue; }
        if (c=='"')  { out[j++]='\\'; out[j++]='"'; }
        else if (c=='\\') { out[j++]='\\'; out[j++]='\\'; }
        else out[j++]=(char)c;
    }
    out[j]='\0';
}

/* ── Deriva nível T7 (1-7) a partir do IC ──────────────────────────
 * Heurística declarada (não é lei física): conversas com IC mais alto
 * viram frames de nível mais profundo (mais específico/raro), capeado
 * em [2,7]. Ajustável — é só uma régua monotônica de priorização.   */
static int derive_level(float ic) {
    int lvl = (int)(ic * 4.0f) + 2;
    if (lvl < 2) lvl = 2;
    if (lvl > 7) lvl = 7;
    return lvl;
}

/* ── Extrai até MAX_TAGS palavras ≥4 chars do título, lowercased ──── */
static void extract_tags(const char *title, char tags_out[MAX_TAGS][32], int *n_tags) {
    *n_tags = 0;
    char buf[MAX_TITLE]; strncpy(buf, title, sizeof(buf)-1); buf[sizeof(buf)-1]='\0';
    char *tok = strtok(buf, " \t,.;:!?()[]{}\"'-/\\");
    while (tok && *n_tags < MAX_TAGS) {
        int len = (int)strlen(tok);
        if (len >= 4) {
            char low[32]; int k=0;
            for (; k<len && k<31; k++) low[k]=(char)tolower((unsigned char)tok[k]);
            low[k]='\0';
            /* dedupe simples */
            int dup=0;
            for (int t=0; t<*n_tags; t++) if (!strcmp(tags_out[t], low)) { dup=1; break; }
            if (!dup) { strncpy(tags_out[*n_tags], low, 31); tags_out[*n_tags][31]='\0'; (*n_tags)++; }
        }
        tok = strtok(NULL, " \t,.;:!?()[]{}\"'-/\\");
    }
}

/* ── Tenta extrair conteúdo real de omega_msgs.jsonl para um conv_i ── */
static int try_extract_content(const char *msgs_path, int conv_i, char *out, int maxlen) {
    if (!msgs_path) return 0;
    FILE *f = fopen(msgs_path, "r");
    if (!f) return 0;

    char line[8192];
    int  used = 0, found = 0;
    out[0] = '\0';

    while (fgets(line, sizeof(line), f) && used < maxlen - 100) {
        int ci = ji(line, "\"conv_i\"");
        if (ci != conv_i) continue;
        found = 1;
        char role[16], content[4096];
        js(line, "\"role\"",    role,    sizeof(role));
        js(line, "\"content\"", content, sizeof(content));
        if (!content[0]) continue;
        int room = maxlen - used - 1;
        int piece_len = (int)strlen(content);
        if (piece_len > room) piece_len = room;
        if (piece_len <= 0) break;
        int w = snprintf(out+used, (size_t)(room+1), "[%s] %.*s\n",
                          role[0] ? role : "?", piece_len, content);
        if (w > 0) used += w;
    }
    fclose(f);
    return found;
}

/* ── Comparadores ─────────────────────────────────────────────────── */
static Node *g_nodes;
static int cmp_by_ic(const void *a, const void *b) {
    const Node *na = &g_nodes[*(const int*)a], *nb = &g_nodes[*(const int*)b];
    return (na->IC < nb->IC) - (na->IC > nb->IC);
}
static int cmp_by_ppadj(const void *a, const void *b) {
    const Node *na = &g_nodes[*(const int*)a], *nb = &g_nodes[*(const int*)b];
    return (na->PP_adj < nb->PP_adj) - (na->PP_adj > nb->PP_adj);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "uso: %s forest.jsonl [omega_msgs.jsonl] [--top N]\n", argv[0]);
        return 1;
    }
    const char *forest_path = argv[1];
    const char *msgs_path   = NULL;
    int top_n = DEFAULT_TOP;

    for (int i = 2; i < argc; i++) {
        if (!strcmp(argv[i], "--top") && i+1 < argc) top_n = atoi(argv[++i]);
        else if (!msgs_path) msgs_path = argv[i];
    }

    static Node nodes[MAX_NODES];
    int N = 0;

    FILE *ff = fopen(forest_path, "r");
    if (!ff) { perror(forest_path); return 1; }
    char line[4096];
    while (fgets(line, sizeof(line), ff) && N < MAX_NODES) {
        char path[16]; js(line, "\"path\"", path, sizeof(path));
        /* Só nos interessam URGENT e MENOSPREZADO para virar frame */
        if (strcmp(path,"URGENT") && strcmp(path,"MENOSPREZADO")) continue;

        Node *nd = &nodes[N];
        nd->conv_i = ji(line, "\"conv_i\"");
        nd->msgs   = ji(line, "\"msgs\"");
        nd->IC     = jf(line, "\"IC\"");
        nd->PP     = jf(line, "\"PP\"");
        nd->PP_adj = jf(line, "\"PP_adj\"");
        strncpy(nd->path, path, sizeof(nd->path)-1);
        char ttl[MAX_TITLE]; js(line, "\"title\"", ttl, sizeof(ttl));
        sanitize(nd->title, ttl, MAX_TITLE);
        N++;
    }
    fclose(ff);

    if (!N) { fprintf(stderr, "nenhum nó URGENT/MENOSPREZADO em %s\n", forest_path); return 1; }

    /* Separa índices por caminho */
    static int idx_urgent[MAX_NODES], idx_men[MAX_NODES];
    int nu = 0, nm = 0;
    for (int i = 0; i < N; i++) {
        if (!strcmp(nodes[i].path, "URGENT"))       idx_urgent[nu++] = i;
        else                                         idx_men[nm++]    = i;
    }

    g_nodes = nodes;
    qsort(idx_urgent, (size_t)nu, sizeof(int), cmp_by_ppadj);
    qsort(idx_men,    (size_t)nm, sizeof(int), cmp_by_ic);

    if (nu > top_n) nu = top_n;
    if (nm > top_n) nm = top_n;

    /* ── Emite frames_seed.json ── */
    printf("[\n");
    int total = nu + nm, emitted = 0;
    int real_content_count = 0;

    for (int pass = 0; pass < 2; pass++) {
        int   *idx = pass == 0 ? idx_urgent : idx_men;
        int    cnt = pass == 0 ? nu : nm;
        for (int k = 0; k < cnt; k++) {
            Node *nd = &nodes[idx[k]];

            char tags[MAX_TAGS][32]; int n_tags = 0;
            extract_tags(nd->title, tags, &n_tags);

            char content_raw[MAX_CONTENT];
            int has_real = try_extract_content(msgs_path, nd->conv_i, content_raw, sizeof(content_raw));

            char content[MAX_CONTENT];
            if (has_real && content_raw[0]) {
                char sane[MAX_CONTENT];
                sanitize(sane, content_raw, sizeof(sane));
                snprintf(content, sizeof(content), "%s", sane);
                real_content_count++;
            } else {
                /* Placeholder honesto — nunca finge ter texto que não tem */
                snprintf(content, sizeof(content),
                    "[PLACEHOLDER — conteudo real nao extraido] Conversa '%s' "
                    "(conv_i=%d, %d msgs, IC=%.3f, PP=%.3f). Rode com "
                    "omega_msgs.jsonl no segundo argumento para preencher "
                    "com o texto real desta conversa.",
                    nd->title, nd->conv_i, nd->msgs, (double)nd->IC, (double)nd->PP);
            }

            printf("  {\n");
            printf("    \"id\": \"seed_conv%d\",\n", nd->conv_i);
            printf("    \"name\": \"%s\",\n", nd->title[0] ? nd->title : "(sem titulo)");
            printf("    \"level\": %d,\n", derive_level(nd->IC));
            printf("    \"path\": \"%s\",\n", nd->path);
            printf("    \"IC\": %.4f,\n", (double)nd->IC);
            printf("    \"PP\": %.4f,\n", (double)nd->PP);
            printf("    \"tags\": [");
            for (int t = 0; t < n_tags; t++)
                printf("%s\"%s\"", t ? "," : "", tags[t]);
            printf("],\n");
            printf("    \"content\": \"%s\"\n", content);
            printf("  }%s\n", (++emitted < total) ? "," : "");
        }
    }
    printf("]\n");

    fprintf(stderr,
        "frames_seed.json: %d frames emitidos (%d URGENT, %d MENOSPREZADO) | "
        "conteudo real: %d | placeholder: %d%s\n",
        total, nu, nm, real_content_count, total - real_content_count,
        msgs_path ? "" : "  [omega_msgs.jsonl nao informado — todos placeholder]");

    return 0;
}
