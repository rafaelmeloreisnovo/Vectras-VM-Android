/*
 * VCAT bootstrap TAR planner.
 *
 * Freestanding computation only. This module intentionally does not perform
 * extraction, chmod, symlink creation, file I/O or process execution.
 */
#include "vcat_bootstrap.h"

static vcat_u32 vcat_tar_mix_byte(vcat_u32 h, vcat_u8 b) {
    h ^= (vcat_u32)b;
    h *= 16777619u;
    return h;
}

static vcat_u32 vcat_tar_digest_field(const vcat_u8 *p, vcat_u32 n) {
    vcat_u32 h = 2166136261u;
    vcat_u32 i = 0u;
    if (p == (const vcat_u8 *)0) return h;
    while (i < n && p[i] != 0u) {
        h = vcat_tar_mix_byte(h, p[i]);
        ++i;
    }
    return h;
}

static int vcat_tar_component_safe(const vcat_u8 *p, vcat_u32 n) {
    vcat_u32 i = 0u;
    vcat_u32 component_len = 0u;
    vcat_u8 component_first = 0u;
    vcat_u8 component_second = 0u;

    if (p == (const vcat_u8 *)0 || n == 0u || p[0] == 0u) return 0;
    if (p[0] == (vcat_u8)'/') return 0;

    while (i < n) {
        const vcat_u8 c = p[i];
        if (c == 0u) {
            /* No hidden suffix after NUL in a fixed-width TAR field. */
            while (++i < n) if (p[i] != 0u) return 0;
            break;
        }
        if (c == (vcat_u8)'/') {
            /* A trailing slash is allowed for a directory entry; an empty
             * interior segment or dot segment has ambiguous semantics. */
            if (component_len == 0u ||
                (component_len == 1u && component_first == (vcat_u8)'.') ||
                (component_len == 2u &&
                 component_first == (vcat_u8)'.' &&
                 component_second == (vcat_u8)'.')) return 0;
            component_len = 0u;
            component_first = 0u;
            component_second = 0u;
        } else {
            /* Reject control characters and alternate path separators. */
            if (c < 32u || c == 127u || c == (vcat_u8)'\\') return 0;
            if (component_len == 0u) component_first = c;
            if (component_len == 1u) component_second = c;
            ++component_len;
        }
        ++i;
    }

    if ((component_len == 1u && component_first == (vcat_u8)'.') ||
        (component_len == 2u &&
         component_first == (vcat_u8)'.' &&
         component_second == (vcat_u8)'.')) return 0;
    return 1;
}

static int vcat_tar_parse_octal(const vcat_u8 *p,
                                vcat_u32 n,
                                vcat_u32 *value_out) {
    vcat_u32 i = 0u;
    vcat_u32 value = 0u;
    int saw_digit = 0;
    int ended = 0;

    if (p == (const vcat_u8 *)0 || value_out == (vcat_u32 *)0) return 0;

    while (i < n) {
        const vcat_u8 c = p[i];
        if (c == 0u || c == (vcat_u8)' ') {
            if (saw_digit) ended = 1;
            ++i;
            continue;
        }
        /* A second value after a NUL/space terminator is not valid octal. */
        if (ended || c < (vcat_u8)'0' || c > (vcat_u8)'7') return 0;
        if (value > 0x1fffffffu) return 0;
        value = (value << 3) + (vcat_u32)(c - (vcat_u8)'0');
        saw_digit = 1;
        ++i;
    }

    if (!saw_digit) return 0;
    *value_out = value;
    return 1;
}

/* POSIX ustar header checksum: eight checksum bytes are treated as spaces.
 * All arithmetic is bounded (< 512 * 255) and independent of libc/runtime. */
static int vcat_tar_checksum_ok(const vcat_u8 *p) {
    vcat_u32 expected = 0u;
    vcat_u32 sum = 0u;
    vcat_u32 i;
    if (!vcat_tar_parse_octal(p + 148u, 8u, &expected)) return 0;
    for (i = 0u; i < VCAT_TAR_BLOCK_BYTES; ++i) {
        sum += (i >= 148u && i < 156u) ? 32u : (vcat_u32)p[i];
    }
    return sum == expected;
}

static int vcat_tar_zero_block(const vcat_u8 *p) {
    vcat_u32 i = 0u;
    while (i < VCAT_TAR_BLOCK_BYTES) {
        if (p[i] != 0u) return 0;
        ++i;
    }
    return 1;
}

VCAT_API void vcat_tar_inspect(const vcat_u8 *header,
                               vcat_u32 length,
                               vcat_tar_entry *out) {
    vcat_u32 flags = 0u;
    vcat_u32 type = VCAT_TAR_TYPE_UNKNOWN;
    vcat_u32 mode = 0u;
    vcat_u32 size = 0u;
    vcat_u32 path_digest = 0u;
    vcat_u32 link_digest = 0u;
    const vcat_u8 *name;
    const vcat_u8 *linkname;
    const vcat_u8 *prefix;
    vcat_u8 typeflag;

    if (out == (vcat_tar_entry *)0) return;

    out->flags = 0u;
    out->type = VCAT_TAR_TYPE_UNKNOWN;
    out->mode = 0u;
    out->size_bytes = 0u;
    out->path_digest = 0u;
    out->link_digest = 0u;

    if (header == (const vcat_u8 *)0 || length < VCAT_TAR_BLOCK_BYTES) return;

    if (vcat_tar_zero_block(header)) {
        out->flags = VCAT_TAR_F_ZERO_BLOCK;
        return;
    }

    name = header;
    linkname = header + 157u;
    prefix = header + 345u;

    if (header[257] == (vcat_u8)'u' &&
        header[258] == (vcat_u8)'s' &&
        header[259] == (vcat_u8)'t' &&
        header[260] == (vcat_u8)'a' &&
        header[261] == (vcat_u8)'r') {
        flags |= VCAT_TAR_F_MAGIC_OK;
    }

    if (vcat_tar_checksum_ok(header)) {
        flags |= VCAT_TAR_F_CHECKSUM_OK;
    }

    if (vcat_tar_component_safe(name, 100u) &&
        (prefix[0] == 0u || vcat_tar_component_safe(prefix, 155u))) {
        flags |= VCAT_TAR_F_SAFE_PATH;
    }

    if (vcat_tar_parse_octal(header + 124u, 12u, &size)) {
        flags |= VCAT_TAR_F_SIZE_OK;
    }

    if (vcat_tar_parse_octal(header + 100u, 8u, &mode)) {
        flags |= VCAT_TAR_F_MODE_OK;
    }

    typeflag = header[156];
    if (typeflag == 0u || typeflag == (vcat_u8)'0') {
        type = VCAT_TAR_TYPE_REGULAR;
        flags |= VCAT_TAR_F_SUPPORTED_TYPE | VCAT_TAR_F_SAFE_LINK;
    } else if (typeflag == (vcat_u8)'5') {
        type = VCAT_TAR_TYPE_DIRECTORY;
        flags |= VCAT_TAR_F_SUPPORTED_TYPE | VCAT_TAR_F_SAFE_LINK;
    } else if (typeflag == (vcat_u8)'2') {
        type = VCAT_TAR_TYPE_SYMLINK;
        if (vcat_tar_component_safe(linkname, 100u)) {
            flags |= VCAT_TAR_F_SUPPORTED_TYPE | VCAT_TAR_F_SAFE_LINK;
        }
    }

    path_digest = vcat_tar_digest_field(prefix, 155u);
    path_digest = vcat_tar_mix_byte(path_digest, (vcat_u8)'/');
    path_digest ^= vcat_tar_digest_field(name, 100u);
    link_digest = vcat_tar_digest_field(linkname, 100u);

    if ((flags & (VCAT_TAR_F_MAGIC_OK |
                  VCAT_TAR_F_CHECKSUM_OK |
                  VCAT_TAR_F_SAFE_PATH |
                  VCAT_TAR_F_SIZE_OK |
                  VCAT_TAR_F_MODE_OK |
                  VCAT_TAR_F_SUPPORTED_TYPE |
                  VCAT_TAR_F_SAFE_LINK)) ==
        (VCAT_TAR_F_MAGIC_OK |
         VCAT_TAR_F_CHECKSUM_OK |
         VCAT_TAR_F_SAFE_PATH |
         VCAT_TAR_F_SIZE_OK |
         VCAT_TAR_F_MODE_OK |
         VCAT_TAR_F_SUPPORTED_TYPE |
         VCAT_TAR_F_SAFE_LINK)) {
        flags |= VCAT_TAR_F_READY;
    }

    out->flags = flags;
    out->type = type;
    out->mode = mode;
    out->size_bytes = size;
    out->path_digest = path_digest;
    out->link_digest = link_digest;
}
