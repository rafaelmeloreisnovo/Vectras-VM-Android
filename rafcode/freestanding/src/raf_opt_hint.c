/* SPDX-License-Identifier: MIT */
#include "raf_opt_hint.h"

typedef struct {
    const char *mark;
    const char *flags;
    raf_u32 profile;
    raf_u32 unroll;
} raf_opt_rule;
static const raf_opt_rule raf_rules[6] = {
    {"RAF_OPT:PROFILE=stable;UNROLL=1", "-O2 -DRAF_OPT_UNROLL=1", RAF_OPT_STABLE,1u},
    {"RAF_OPT:PROFILE=stable;UNROLL=4", "-O2 -DRAF_OPT_UNROLL=4", RAF_OPT_STABLE,4u},
    {"RAF_OPT:PROFILE=size;UNROLL=1",   "-Os -DRAF_OPT_UNROLL=1", RAF_OPT_SIZE,1u},
    {"RAF_OPT:PROFILE=size;UNROLL=4",   "-Os -DRAF_OPT_UNROLL=4", RAF_OPT_SIZE,4u},
    {"RAF_OPT:PROFILE=speed;UNROLL=1",  "-O3 -DRAF_OPT_UNROLL=1", RAF_OPT_SPEED,1u},
    {"RAF_OPT:PROFILE=speed;UNROLL=4",  "-O3 -DRAF_OPT_UNROLL=4", RAF_OPT_SPEED,4u}
};
static int raf_space(raf_u8 c) {
    return c == 32u || c == 9u || c == 10u || c == 13u;
}
static raf_u32 raf_strlen_local(const char *s) {
    raf_u32 n = 0u;
    while (s[n]) ++n;
    return n;
}
static int raf_match(const raf_u8 *s, raf_u32 n, const char *expect) {
    raf_u32 k;
    if (raf_strlen_local(expect) != n) return 0;
    for (k = 0; k < n; ++k)
        if (s[k] != (raf_u8)expect[k]) return 0;
    return 1;
}
static int raf_prefix(const raf_u8 *s, raf_u32 n, const char *prefix) {
    raf_u32 k = 0u;
    while (prefix[k]) {
        if (k >= n || s[k] != (raf_u8)prefix[k]) return 0;
        ++k;
    }
    return 1;
}
raf_status raf_opt_parse(const raf_u8 *src, raf_u32 size, raf_opt_hint *out) {
    raf_u32 i = 0u, found = 0u;
    raf_opt_hint candidate = {0u,0u};
    if (!src || !out || size > 65535u) return RAF_INVALID;
    while (i < size) {
        raf_u8 c = src[i];
        if (c == 0u) return RAF_INVALID;
        if (c == 34u || c == 39u) {
            raf_u8 quote = c;
            int closed = 0;
            ++i;
            while (i < size) {
                if (src[i] == 92u) {
                    if (i + 1u >= size) return RAF_INVALID;
                    i += 2u;
                } else if (src[i++] == quote) {
                    closed = 1;
                    break;
                }
            }
            if (!closed) return RAF_INVALID;
            continue;
        }
        if (c == 47u && i + 1u < size && src[i+1u] == 47u) {
            i += 2u;
            while (i < size && src[i] != 10u) ++i;
            continue;
        }
        if (c == 47u && i + 1u < size && src[i+1u] == 42u) {
            raf_u32 left = i + 2u, right = left, k;
            int recognized = 0;
            while (right + 1u < size &&
                   !(src[right] == 42u && src[right+1u] == 47u)) ++right;
            if (right + 1u >= size) return RAF_INVALID;
            while (left < right && raf_space(src[left])) ++left;
            while (right > left && raf_space(src[right-1u])) --right;
            if (raf_prefix(src+left,right-left,"RAF_OPT:")) {
                if (found) return RAF_INVALID;
                for (k = 0u; k < 6u; ++k) {
                    if (raf_match(src+left,right-left,raf_rules[k].mark)) {
                        candidate.profile = raf_rules[k].profile;
                        candidate.unroll = raf_rules[k].unroll;
                        recognized = 1;
                        break;
                    }
                }
                if (!recognized) return RAF_INVALID;
                found = 1u;
            }
            /* Closing delimiter is at original end, not trimmed right. */
            i += 2u;
            while (i + 1u < size &&
                   !(src[i] == 42u && src[i+1u] == 47u)) ++i;
            i += 2u;
            continue;
        }
        ++i;
    }
    if (!found) return RAF_TOKEN_VAZIO;
    *out = candidate;
    return RAF_OK;
}
raf_status raf_opt_emit_flags(const raf_opt_hint *hint,
                              raf_u8 *dst, raf_u32 capacity,
                              raf_u32 *produced) {
    raf_u32 i,k,n;
    if (!hint || !dst || !produced) return RAF_INVALID;
    for (i=0u;i<6u;++i) {
        if (hint->profile == raf_rules[i].profile &&
            hint->unroll == raf_rules[i].unroll) {
            n=raf_strlen_local(raf_rules[i].flags);
            if (capacity <= n) return RAF_NO_SPACE;
            for (k=0u;k<n;++k) dst[k]=(raf_u8)raf_rules[i].flags[k];
            dst[n]=0u;
            *produced=n;
            return RAF_OK;
        }
    }
    return RAF_INVALID;
}
