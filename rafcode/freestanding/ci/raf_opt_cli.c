/* SPDX-License-Identifier: MIT — HOST-ONLY annotation adapter, not freestanding runtime. */
#include <stdio.h>
#include "raf_opt_hint.h"
int main(void) {
    static raf_u8 text[65536u];
    raf_opt_hint hint;
    raf_u8 flags[48];
    raf_u32 used=0u;
    size_t size=fread(text,1u,sizeof(text),stdin);
    if (size==sizeof(text)||ferror(stdin)) {
        fputs("invalid or oversized source\n",stderr);
        return 2;
    }
    if (raf_opt_parse(text,(raf_u32)size,&hint)!=RAF_OK ||
        raf_opt_emit_flags(&hint,flags,sizeof(flags),&used)!=RAF_OK) {
        fputs("missing/unknown/ambiguous RAF_OPT annotation\n",stderr);
        return 3;
    }
    (void)used;
    if (puts((const char *)flags)<0) return 4;
    return 0;
}
