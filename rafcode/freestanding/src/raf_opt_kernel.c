/* SPDX-License-Identifier: MIT */
/* RAF_OPT:PROFILE=stable;UNROLL=4 */
#include "raf_opt_hint.h"
#ifndef RAF_OPT_UNROLL
#define RAF_OPT_UNROLL 1
#endif
#if RAF_OPT_UNROLL != 1 && RAF_OPT_UNROLL != 4
#error "RAF_OPT_UNROLL is allowlisted to 1 or 4"
#endif

/* Non-overlapping buffers; caller controls bounds and storage.
 * Compiler guidance is NOT a promise of actual unrolling. */
void raf_opt_xor_u32(raf_u32 *dst, const raf_u32 *src,
                     raf_u32 count, raf_u32 mask) {
    raf_u32 i;
    if (!dst || !src) return;
#if RAF_OPT_UNROLL == 4
#if defined(__clang__)
#pragma clang loop unroll_count(4)
#elif defined(__GNUC__)
#pragma GCC unroll 4
#endif
#endif
    for (i=0u; i<count; ++i) dst[i]=src[i]^mask;
}

/* Final link only, MUST NOT run: no process exit or OS dependency. */
volatile raf_u32 raf_opt_link_sink;
void raf_opt_link_probe(void) {
    const raf_u8 comment[]="/* RAF_OPT:PROFILE=size;UNROLL=4 */";
    raf_opt_hint hint={0u,0u};
    raf_u8 flags[40];
    raf_u32 used=0u;
    raf_u32 src[4]={1u,2u,3u,4u}, dst[4]={0u,0u,0u,0u};
    raf_opt_xor_u32(dst,src,4u,0x5au);
    if (raf_opt_parse(comment,sizeof(comment)-1u,&hint)==RAF_OK &&
        raf_opt_emit_flags(&hint,flags,40u,&used)==RAF_OK)
        raf_opt_link_sink=dst[0]+used+(raf_u32)flags[0];
    for (;;) { }
}
