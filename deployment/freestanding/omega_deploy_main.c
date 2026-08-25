#include "lowlevel_abi.h"

typedef __UINT32_TYPE__ u32;
typedef __INT32_TYPE__ i32;
typedef __UINT8_TYPE__ u8;
typedef __INTPTR_TYPE__ ip;

typedef struct { u32 w[8]; } F;

static __attribute__((always_inline)) inline u32 rr(u32 x, u32 n) {
    return (x >> n) | (x << (32u - n));
}

/* ARM EABI syscall gate. Hex is intentional only at the critical SVC boundary. */
static __attribute__((always_inline)) inline ip sc3(ip n, ip a, ip b, ip c) {
    register ip r0 __asm__("r0") = a;
    register ip r1 __asm__("r1") = b;
    register ip r2 __asm__("r2") = c;
    register ip r7 __asm__("r7") = n;
    __asm__ volatile(".inst 0xef000000"
                     : "+r"(r0)
                     : "r"(r1), "r"(r2), "r"(r7)
                     : "memory", "cc");
    return r0;
}

static __attribute__((always_inline, noreturn)) inline void ex(u32 x) {
    register ip r0 __asm__("r0") = (ip)x;
    register ip r7 __asm__("r7") = 1;
    __asm__ volatile(".inst 0xef000000"
                     :
                     : "r"(r0), "r"(r7)
                     : "memory", "cc");
    __builtin_unreachable();
}

/* Specialized void micromodules. All are inline: no standalone helper symbols. */
static __attribute__((always_inline)) inline void m1(F *p) {
    u8 a = 0;
    i32 s = (i32)abi_entry_validate_interop((uint16_t)p->w[1],
                                            (uint16_t)p->w[2],
                                            (uint16_t)p->w[3],
                                            (uint16_t)p->w[4],
                                            &a);
    p->w[0] = (u32)s;
    p->w[1] = (u32)a;
}

static __attribute__((always_inline)) inline void m2(F *p) {
    u32 a = p->w[1], b = p->w[2], c = p->w[3], d = p->w[4];
    u32 e = p->w[5], f = p->w[6], g = p->w[7];
    a += rr(b ^ 0x9e3779b9u, 5u);
    b ^= rr(c + 0x7f4a7c15u, 7u);
    c += rr(d ^ a, 11u);
    d ^= rr(e + b, 13u);
    e += rr(f ^ c, 17u);
    f ^= rr(g + d, 19u);
    g += rr(a ^ e, 23u);
    p->w[0] = 0u; p->w[1] = a; p->w[2] = b; p->w[3] = c;
    p->w[4] = d; p->w[5] = e; p->w[6] = f; p->w[7] = g;
}

static __attribute__((always_inline)) inline void m3(F *p) {
    u32 k = p->w[1] ^ 0xa5a5a5a5u;
    p->w[2] ^= k;
    p->w[3] ^= rr(k, 3u);
    p->w[4] ^= rr(k, 7u);
    p->w[5] ^= rr(k, 11u);
    p->w[6] ^= rr(k, 17u);
    p->w[7] ^= rr(k, 23u);
    p->w[0] = 0u;
}

static __attribute__((always_inline)) inline void m4(F *p) {
    p->w[1] = rr(p->w[1], 1u);
    p->w[2] = rr(p->w[2], 3u);
    p->w[3] = rr(p->w[3], 5u);
    p->w[4] = rr(p->w[4], 7u);
    p->w[5] = rr(p->w[5], 11u);
    p->w[6] = rr(p->w[6], 13u);
    p->w[7] = rr(p->w[7], 17u);
    p->w[0] = 0u;
}

static __attribute__((always_inline)) inline void m5(F *p) {
    p->w[1] = p->w[1] ^ p->w[2] ^ p->w[3] ^ p->w[4] ^
              p->w[5] ^ p->w[6] ^ p->w[7];
    p->w[0] = 0u;
}

static __attribute__((always_inline)) inline void m6(F *p) {
    u32 a = p->w[1], b = p->w[2], c = p->w[3];
    p->w[0] = (u32)((a | b | c) == 0u);
    p->w[1] = (a ^ b) + c;
    p->w[2] = (a & b) | (b & c) | (c & a);
}

static __attribute__((always_inline)) inline void m7(F *p) {
    p->w[0] = 0u;
    p->w[1] = 0x52414643u;
    p->w[2] = 0x4f4445a6u;
    p->w[3] = 2u;
    p->w[4] = 32u;
    p->w[5] = 7u;
    p->w[6] = 0u;
    p->w[7] = 0u;
}

__attribute__((noreturn, visibility("default"))) void omega_deploy_main(void);

/*
 * Fixed 32-byte binary protocol:
 *   op=0 exit
 *   op=1 ABI interop gate
 *   op=2 seven-word mix
 *   op=3 xor/rotate transform
 *   op=4 lane rotation
 *   op=5 reduction
 *   op=6 invariant gate
 *   op=7 protocol descriptor
 *
 * One loop is intentional and necessary: service frames. Short reads/writes are
 * rejected instead of introducing tail-fragment loops.
 */
__attribute__((noreturn, visibility("default"))) void omega_deploy_main(void) {
    F p;
    for (;;) {
        if (sc3(3, 0, (ip)&p, 32) != 32) ex(65u);
        u32 o = p.w[0];
        if (o == 0u) ex(p.w[1] & 255u);
        switch (o) {
            case 1u: m1(&p); break;
            case 2u: m2(&p); break;
            case 3u: m3(&p); break;
            case 4u: m4(&p); break;
            case 5u: m5(&p); break;
            case 6u: m6(&p); break;
            case 7u: m7(&p); break;
            default: p.w[0] = 0xffffffffu; break;
        }
        if (sc3(4, 1, (ip)&p, 32) != 32) ex(66u);
    }
}
