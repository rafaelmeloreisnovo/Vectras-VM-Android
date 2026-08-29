/*
 * Amalgamated into lowlevel_abi.c so VCAT inherits the already-enforced
 * abi_core_freestanding target without adding another hosted/native surface.
 * No loops, recursion, libc, heap, atomics, syscalls or runtime symbol lookup.
 */
#include "vcat.h"

#if defined(__clang__) || defined(__GNUC__)
#pragma GCC diagnostic error "-Wshadow"
#endif

#if defined(__clang__)
#define VCAT_NOTAIL __attribute__((disable_tail_calls))
#elif defined(__GNUC__)
#define VCAT_NOTAIL __attribute__((optimize("no-optimize-sibling-calls")))
#else
#define VCAT_NOTAIL
#endif

static vcat_u32 vcat_mix(vcat_u32 x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

static vcat_u32 vcat_rot(vcat_u32 x, vcat_u32 r) {
    r &= 31u;
    return r != 0u ? ((x << r) | (x >> (32u - r))) : x;
}

static vcat_u32 vcat_sig8(const vcat_u8 *p, vcat_u32 n) {
    vcat_u32 x = vcat_mix(n ^ 0x56434154u);
    if (p == (const vcat_u8 *)0 || n == 0u) return x;
    x = vcat_mix(x ^ (vcat_u32)p[0]);
    if (n > 1u) x = vcat_mix(x ^ ((vcat_u32)p[1] << 8));
    if (n > 2u) x = vcat_mix(x ^ ((vcat_u32)p[2] << 16));
    if (n > 3u) x = vcat_mix(x ^ ((vcat_u32)p[3] << 24));
    if (n > 4u) x = vcat_mix(x ^ (vcat_u32)p[4]);
    if (n > 5u) x = vcat_mix(x ^ ((vcat_u32)p[5] << 8));
    if (n > 6u) x = vcat_mix(x ^ ((vcat_u32)p[6] << 16));
    if (n > 7u) x = vcat_mix(x ^ ((vcat_u32)p[7] << 24));
    return x;
}

VCAT_NOTAIL VCAT_API void vcat_classify(const vcat_u8 *p, vcat_u32 n,
                                        vcat_u32 *kind, vcat_u32 *flags,
                                        vcat_u32 *digest) {
    vcat_u32 k = VCAT_KIND_UNKNOWN;
    vcat_u32 f = 0u;
    vcat_u32 d = vcat_sig8(p, n);

    if (p != (const vcat_u8 *)0 && n >= 4u) {
        if (p[0] == 0x7fu && p[1] == 0x45u && p[2] == 0x4cu && p[3] == 0x46u) {
            k = VCAT_KIND_ELF;
            f = VCAT_F_VALID;
            if (n > 5u) {
                if (p[4] == 1u) f |= VCAT_F_ELF32;
                if (p[4] == 2u) f |= VCAT_F_ELF64;
                if (p[5] == 1u) f |= VCAT_F_LITTLE_ENDIAN;
            }
            if (n > 17u && p[5] == 1u) {
                const vcat_u16 t = (vcat_u16)((vcat_u16)p[16] | ((vcat_u16)p[17] << 8));
                if (t == 2u) f |= VCAT_F_EXEC;
                if (t == 3u) f |= VCAT_F_SHARED;
            }
        } else if (p[0] == 0x64u && p[1] == 0x65u && p[2] == 0x78u && p[3] == 0x0au) {
            k = VCAT_KIND_DEX;
            f = VCAT_F_VALID | VCAT_F_BYTECODE;
        } else if (p[0] == 0x50u && p[1] == 0x4bu &&
                   (p[2] == 0x03u || p[2] == 0x05u || p[2] == 0x07u) &&
                   (p[3] == 0x04u || p[3] == 0x06u || p[3] == 0x08u)) {
            k = VCAT_KIND_ZIP;
            f = VCAT_F_VALID | VCAT_F_ARCHIVE;
        } else if (p[0] == 0x6fu && p[1] == 0x61u && p[2] == 0x74u && p[3] == 0x0au) {
            k = VCAT_KIND_OAT;
            f = VCAT_F_VALID | VCAT_F_BYTECODE;
        } else if (p[0] == 0x76u && p[1] == 0x64u && p[2] == 0x65u && p[3] == 0x78u) {
            k = VCAT_KIND_VDEX;
            f = VCAT_F_VALID | VCAT_F_BYTECODE;
        } else if (p[0] == 0x51u && p[1] == 0x46u && p[2] == 0x49u && p[3] == 0xfbu) {
            k = VCAT_KIND_QCOW2;
            f = VCAT_F_VALID | VCAT_F_IMAGE;
        } else if (p[0] == 0x3au && p[1] == 0xffu && p[2] == 0x26u && p[3] == 0xedu) {
            k = VCAT_KIND_ANDROID_SPARSE;
            f = VCAT_F_VALID | VCAT_F_IMAGE | VCAT_F_LITTLE_ENDIAN;
        }
    }

    if (k == VCAT_KIND_UNKNOWN && p != (const vcat_u8 *)0 && n >= 263u &&
        p[257] == 0x75u && p[258] == 0x73u && p[259] == 0x74u &&
        p[260] == 0x61u && p[261] == 0x72u) {
        k = VCAT_KIND_TAR;
        f = VCAT_F_VALID | VCAT_F_ARCHIVE;
    }

    if (k != VCAT_KIND_UNKNOWN) f |= VCAT_F_READY;
    if (kind != (vcat_u32 *)0) *kind = k;
    if (flags != (vcat_u32 *)0) *flags = f;
    if (digest != (vcat_u32 *)0) *digest = d;
}

static VCAT_NOTAIL void vcat_emit(const vcat_job *j, vcat_u32 src,
                                  vcat_u32 lane, vcat_plan *o) {
    vcat_u32 k;
    vcat_u32 f;
    vcat_u32 d;
    vcat_classify(j->p, j->n, &k, &f, &d);
    o->id[lane] = j->id;
    o->phase[lane] = j->phase;
    o->kind[lane] = k;
    o->flags[lane] = f;
    o->digest[lane] = d;
    o->source_index[lane] = src;
    if ((f & VCAT_F_READY) != 0u) o->ready_mask |= (1u << lane);
    o->aggregate ^= vcat_rot(vcat_mix(d ^ j->id ^ j->phase ^ lane), lane);
}

VCAT_NOTAIL VCAT_API void vcat_plan16(const vcat_job *jobs, vcat_u32 seed,
                                      vcat_plan *out) {
    vcat_u32 a;
    vcat_u32 b;
    if (jobs == (const vcat_job *)0 || out == (vcat_plan *)0) return;

    /* a is odd => gcd(a,16)=1 => collision-free permutation of all 16 lanes. */
    a = (vcat_mix(seed) | 1u) & 15u;
    b = (vcat_mix(seed ^ 0x9e3779b9u) >> 4) & 15u;
    out->ready_mask = 0u;
    out->aggregate = vcat_mix(seed ^ 0x16c4716eu);
    out->kernel_boundary = VCAT_BOUNDARY_KERNEL_REQUIRED;

#define VCAT_E(I) vcat_emit(&jobs[(I)], (I), ((a * (I)) + b) & 15u, out)
    VCAT_E(0u);  VCAT_E(1u);  VCAT_E(2u);  VCAT_E(3u);
    VCAT_E(4u);  VCAT_E(5u);  VCAT_E(6u);  VCAT_E(7u);
    VCAT_E(8u);  VCAT_E(9u);  VCAT_E(10u); VCAT_E(11u);
    VCAT_E(12u); VCAT_E(13u); VCAT_E(14u); VCAT_E(15u);
#undef VCAT_E
}

#undef VCAT_NOTAIL
