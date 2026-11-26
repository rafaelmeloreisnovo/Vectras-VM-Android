/* SPDX-License-Identifier: MIT */
#include "raf_core.h"

/* Known canonical form: / or /component[/component...], no dot or control. */
static int canonical(const raf_u8 *p, raf_u32 n) {
    raf_u32 i, seg = 1u;
    if (!p || !n || n > RAF_PATH_LIMIT || p[0] != '/') return 0;
    if (n == 1u) return 1;
    if (p[n - 1u] == '/') return 0;
    for (i = 1u; i <= n; ++i) {
        if (i == n || p[i] == '/') {
            raf_u32 sz = i - seg;
            if (!sz || (sz == 1u && p[seg] == '.') ||
                (sz == 2u && p[seg] == '.' && p[seg + 1u] == '.')) return 0;
            seg = i + 1u;
        } else if (p[i] == 0u || p[i] < 32u || p[i] == 127u) return 0;
    }
    return 1;
}

raf_status raf_path_normalize(const raf_u8 *p, raf_u32 n,
                              raf_u8 *o, raf_u32 cap, raf_u32 *used) {
    raf_u32 i, w = 1u;
    if (!p || !o || !used || n == 0u || n > RAF_PATH_LIMIT ||
        p[0] != '/' || cap < 2u) return RAF_INVALID;
    i = 1u;
    while (i < n) {
        raf_u32 st, sz, k;
        while (i < n && p[i] == '/') ++i;
        if (i == n) break;
        st = i;
        while (i < n && p[i] != '/') {
            if (p[i] == 0u || p[i] < 32u || p[i] == 127u)
                return RAF_INVALID;
            ++i;
        }
        sz = i - st;
        if (sz == 1u && p[st] == '.') continue;
        if (sz == 2u && p[st] == '.' && p[st + 1u] == '.') {
            if (w == 1u) return RAF_ESCAPE_DENIED;
            while (w > 1u && o[w - 1u] != '/') --w;
            if (w > 1u) --w;
            continue;
        }
        if (w > 1u) {
            if (w >= cap - 1u) return RAF_NO_SPACE;
            o[w++] = '/';
        }
        if (sz >= cap - w) return RAF_NO_SPACE; /* reserve NUL */
        for (k = 0u; k < sz; ++k) o[w++] = p[st + k];
    }
    o[0] = '/'; o[w] = 0u;
    *used = w;
    return RAF_OK;
}

static int eq(const raf_u8 *a, const raf_u8 *b, raf_u32 n) {
    raf_u32 i;
    for (i = 0u; i < n; ++i) if (a[i] != b[i]) return 0;
    return 1;
}

raf_status raf_path_translate(const raf_bind *rules, raf_u32 count,
                              const raf_u8 *p, raf_u32 n,
                              raf_u8 *out, raf_u32 cap, raf_u32 *used) {
    const raf_bind *best = (const raf_bind *)0;
    raf_u32 i, k, suffix, w = 0u;
    if (!rules || count == 0u) return RAF_NO_MAPPING;
    if (count > RAF_RULE_LIMIT || !canonical(p, n) || !out || !used ||
        cap == 0u) return RAF_INVALID;
    for (i = 0u; i < count; ++i) {
        const raf_bind *r = &rules[i];
        if (!canonical(r->guest, r->guest_n) ||
            !canonical(r->host, r->host_n)) return RAF_INVALID;
        if (r->guest_n > n || !eq(r->guest, p, r->guest_n)) continue;
        if (r->guest_n > 1u && n > r->guest_n && p[r->guest_n] != '/')
            continue; /* /a is not a prefix of /ab */
        if (!best || r->guest_n > best->guest_n) best = r;
    }
    if (!best) return RAF_NO_MAPPING;
    suffix = best->guest_n == 1u ? 1u : best->guest_n;
    /* Compute complete output length *before* writing: no partial writes. */
    {
        raf_u32 skip = 0u, add_slash = 0u;
        raf_u32 suffix_n;
        if (suffix < n) {
            if (best->host[best->host_n - 1u] == '/' && p[suffix] == '/')
                skip = 1u;
            if (best->host[best->host_n - 1u] != '/' && p[suffix] != '/')
                add_slash = 1u;
        }
        suffix_n = n - suffix - skip;
        if (best->host_n >= cap ||
            add_slash > (cap - 1u) - best->host_n ||
            suffix_n > (cap - 1u) - best->host_n - add_slash)
            return RAF_NO_SPACE;
        for (k = 0u; k < best->host_n; ++k) out[w++] = best->host[k];
        if (add_slash) out[w++] = '/';
        suffix += skip;
        for (k = suffix; k < n; ++k) out[w++] = p[k];
    }
    out[w] = 0u;
    *used = w;
    return RAF_OK;
}
