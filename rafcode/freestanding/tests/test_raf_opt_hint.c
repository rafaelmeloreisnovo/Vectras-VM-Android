/* SPDX-License-Identifier: MIT — hosted tests, never included in core. */
#include <stdio.h>
#include "raf_opt_hint.h"
static int pass,fail;
#define CHECK(expr) do { if (expr) ++pass; else { ++fail; \
    fprintf(stderr,"FAIL line %d\n",__LINE__); } } while(0)

static raf_status parse_cstr(const char *s,raf_opt_hint *h) {
    raf_u32 n=0u;
    while(s[n]) ++n;
    return raf_opt_parse((const raf_u8 *)s,n,h);
}
int main(void) {
    raf_opt_hint h={99u,99u};
    raf_u8 flags[48];
    raf_u32 used=77u,i;
    raf_u32 src[17],dst[19];
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=stable;UNROLL=4 */",&h)==RAF_OK &&
          h.profile==RAF_OPT_STABLE && h.unroll==4u);
    CHECK(raf_opt_emit_flags(&h,flags,48u,&used)==RAF_OK &&
          used==22u && flags[0]=='-' && flags[1]=='O' &&
          flags[2]=='2' && flags[used]==0u);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=size;UNROLL=1 */",&h)==RAF_OK &&
          h.profile==RAF_OPT_SIZE && h.unroll==1u);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=speed;UNROLL=4 */",&h)==RAF_OK &&
          h.profile==RAF_OPT_SPEED && h.unroll==4u);
    h.profile=99u;h.unroll=99u;
    CHECK(parse_cstr("ordinary code",&h)==RAF_TOKEN_VAZIO &&
          h.profile==99u);
    CHECK(parse_cstr("\"/* RAF_OPT:PROFILE=speed;UNROLL=4 */\"",&h)==RAF_TOKEN_VAZIO);
    CHECK(parse_cstr("// RAF_OPT:PROFILE=speed;UNROLL=4\n",&h)==RAF_TOKEN_VAZIO);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=turbo;UNROLL=4 */",&h)==RAF_INVALID);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=stable;UNROLL=8 */",&h)==RAF_INVALID);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=stable;UNROLL=4;CMD=sh */",&h)==RAF_INVALID);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=stable;UNROLL=4 */ /* RAF_OPT:PROFILE=size;UNROLL=4 */",&h)==RAF_INVALID);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=stable;UNROLL=4",&h)==RAF_INVALID);
    CHECK(parse_cstr("\"unterminated",&h)==RAF_INVALID);
    CHECK(parse_cstr("/* RAF_OPT:PROFILE=stable;UNROLL=4 */\n int x=0;",&h)==RAF_OK);
    CHECK(raf_opt_parse(0,1u,&h)==RAF_INVALID);
    CHECK(raf_opt_parse((const raf_u8 *)"x",65536u,&h)==RAF_INVALID);
    h.profile=RAF_OPT_STABLE;h.unroll=4u;
    CHECK(raf_opt_emit_flags(&h,flags,4u,&used)==RAF_NO_SPACE &&
          used==77u);
    h.unroll=8u;
    CHECK(raf_opt_emit_flags(&h,flags,48u,&used)==RAF_INVALID);
    h.unroll=4u;
    CHECK(raf_opt_emit_flags(&h,flags,48u,0)==RAF_INVALID);
    for(i=0u;i<17u;++i) src[i]=i*17u+3u;
    for(i=0u;i<19u;++i) dst[i]=0xfeed0000u;
    raf_opt_xor_u32(dst+1u,src,17u,0xa5a5a5a5u);
    CHECK(dst[0]==0xfeed0000u && dst[18]==0xfeed0000u);
    for(i=0u;i<17u;++i) CHECK(dst[i+1u]==(src[i]^0xa5a5a5a5u));
    raf_opt_xor_u32(0,src,17u,0u);
    raf_opt_xor_u32(dst,0,17u,0u);
    CHECK(dst[0]==0xfeed0000u);
    printf("RAFCODE_COMMENT_FLAGS_TESTS=%d FAILURES=%d\n",pass+fail,fail);
    return fail?1:0;
}
