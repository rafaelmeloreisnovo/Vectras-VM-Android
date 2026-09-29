/* RAFCODE-PHI :: BIT-LAYER COLUMN RASTER V0
 * Authorial freestanding reference primitive.
 * No headers. No libc. No heap. No syscalls. No compiler builtins.
 * No floating point. No recursion. No tail calls. No external symbols.
 * Single direct routine; opaque short identifiers; no shadowed locals.
 *
 * Input  : tightly packed RGB8, source width/height.
 * m[]    : byte mask of horizontal source blocks; 0 = absent, nonzero = present.
 * bw     : source columns represented by one mask byte.
 * dw     : requested output width.
 * q      : retained MSB layers, 1..8.
 * Effect : absent blocks collapse from horizontal geometry before projection.
 * Output : tightly packed RGB8, same height, requested width.
 *
 * This is a new authorial G(M) reference. It does not claim historical
 * equivalence with any earlier undocumented decoder.
 */

typedef unsigned char u8;
typedef unsigned int u32;
typedef unsigned long long u64;

int raf_bl0(u8 *o, u32 oc, const u8 *i, u32 sw, u32 sh, u32 dw,
            u32 q, const u8 *m, u32 mn, u32 bw)
{
    u32 a,b,c,d,e,g,j,r,s,t,x,y,z;

    if (!o || !i || !m || !sw || !sh || !dw || !bw || !mn) return 1;
    if (q < 1u || q > 8u || sw > 65535u || dw > 65535u) return 1;
    if ((u64)dw * (u64)sh * 3ull > (u64)oc) return 2;

    b=0u; c=0u;
    for (x=0u; x<sw; x++) {
        if (c==0u && b>=mn) return 1;
        c++;
        if (c==bw) { c=0u; b++; }
    }

    a=0u; b=0u; c=0u;
    for (x=0u; x<sw; x++) {
        a += (u32)(m[b] != 0u);
        c++;
        if (c==bw) { c=0u; b++; }
    }
    if (!a) return 3;

    d = 255u << (8u-q);

    for (y=0u; y<sh; y++) {
        s=0u; b=0u; c=0u;
        while (s<sw && m[b]==0u) {
            s++; c++;
            if (c==bw) { c=0u; b++; }
        }
        if (s>=sw) return 3;

        r=0u; g=0u;
        e = (dw>1u) ? ((dw-1u)>>1u) : 0u;

        for (x=0u; x<dw; x++) {
            while (g<r) {
                do {
                    s++; c++;
                    if (c==bw) { c=0u; b++; }
                } while (s<sw && m[b]==0u);
                if (s>=sw) return 3;
                g++;
            }

            t = (u32)(((u64)y * (u64)sw + (u64)s) * 3ull);
            j = (u32)(((u64)y * (u64)dw + (u64)x) * 3ull);

            for (z=0u; z<3u; z++)
                o[j+z] = (u8)(i[t+z] & (u8)d);

            if (x+1u<dw && dw>1u && a>1u) {
                e += a-1u;
                while (e >= dw-1u) {
                    e -= dw-1u;
                    if (r+1u<a) r++;
                }
            }
        }
    }

    return 0;
}
