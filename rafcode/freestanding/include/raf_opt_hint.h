/* SPDX-License-Identifier: MIT */
/* Strict source-comment hints: tooling never executes comment text. */
#ifndef RAF_FS_OPT_HINT_H
#define RAF_FS_OPT_HINT_H
#include "raf_core.h"
typedef enum {
    RAF_OPT_STABLE = 1,
    RAF_OPT_SIZE = 2,
    RAF_OPT_SPEED = 3
} raf_opt_profile;
typedef struct {
    raf_u32 profile;
    raf_u32 unroll;
} raf_opt_hint;
/* Only one exact RAF_OPT block-comment marker.
 * Values: stable|size|speed; unroll: 1|4. Whitespace around
 * the marker is permitted. Unknown/duplicate markers fail closed.
 * Tokens in strings, characters and // line comments are ignored.
 * The marker must precede any non-comment, non-space source token.
 * C line splices, C trigraphs and embedded NUL are rejected anywhere
 * (fail-closed: this bounded scanner is NOT a C preprocessor).
 * If no marker exists, return RAF_TOKEN_VAZIO; never infer defaults.
 * Output is unchanged on failure. Explicit bounded input; no libc. */
raf_status raf_opt_parse(const raf_u8 *src, raf_u32 size, raf_opt_hint *out);
/* Emits only allowlisted -O2|-Os|-O3 and -DRAF_OPT_UNROLL=1|4.
 * Result is NUL terminated. No shell metacharacters or file paths.
 * Output remains unchanged on failure. */
raf_status raf_opt_emit_flags(const raf_opt_hint *hint,
                              raf_u8 *dst, raf_u32 capacity,
                              raf_u32 *produced);
void raf_opt_xor_u32(raf_u32 *dst, const raf_u32 *src,
                     raf_u32 count, raf_u32 mask);
#endif
