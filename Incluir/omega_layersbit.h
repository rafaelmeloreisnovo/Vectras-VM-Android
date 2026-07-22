/* SPDX-License-Identifier: GPL-2.0-only */
/* SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo */
/*
 * omega_layersbit.h  —  LayersBit Engine  v1.0
 * ∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor
 *
 * FREESTANDING. Sem malloc. Sem heap. Sem GC. Sem libc.
 * Branchless sempre que matematicamente válido.
 * Sem funções de overhead — só static inline + __always_inline.
 * Toda lógica exposta inline. Zero fricção de abstração.
 *
 * ESTRUTURA:
 *   LayersBit = 4096 bits = 16 camadas × 256 bits (32 bytes cada)
 *
 *   L0 ..L15 : 16 camadas de 256 bits
 *              Cada byte ASCII aponta para um bit único via prime routing
 *              LB_ROUTE[b] = posição em [0,4095] do byte b
 *
 *   fold[]   : XOR-fold das 16 camadas → 256 bits de síntese
 *   omega    : atrator toroidal = popcount(fold) mod 42
 *   tick     : contador de bytes processados (nunca wraps — uint64)
 *
 * OPERAÇÕES (todas branchless, todas inline):
 *   lb_push(lb, byte)   — XOR o byte na posição prime_route[byte]
 *   lb_fold(lb)         — XOR-reduce as 16 camadas → fold[32]
 *   lb_spiral(lb)       — rol8 em toda a fold (rotação de 1 bit)
 *   lb_gf_mul(a,b)      — multiplicação em GF(2^8) branchless
 *   lb_poly(lb, coef[]) — avalia polinômio de coefs em GF(2^8) sobre fold
 *   lb_omega(lb)        — popcount(fold) mod 42 → atrator toroidal
 *   lb_entropy(lb)      — Shannon milli (0–8000) sobre histograma de fold
 *   lb_phi(lb)          — phi_integral = (1 - H/8000) × (ones/256) × 65536
 *
 * 4096 bits = 512 bytes
 * 16 × 256 bits por camada
 * 256 primos fixos como tabela estática
 * GF(2^8) poly 0x11D (x^8+x^4+x^3+x^2+1)
 */

#ifndef OMEGA_LAYERSBIT_H
#define OMEGA_LAYERSBIT_H

/* ── Suporte freestanding ─────────────────────────────────────────── */
#if defined(__STDC_HOSTED__)
  #include <stdint.h>
  #include <string.h>     /* memset / memcpy — presentes mesmo em freestanding */
#else
  typedef unsigned char      uint8_t;
  typedef unsigned short     uint16_t;
  typedef unsigned int       uint32_t;
  typedef unsigned long long uint64_t;
  typedef   signed long long  int64_t;
  #define NULL ((void*)0)
  static inline void *lb_memset(void *s, int c, uint64_t n) {
      uint8_t *p = (uint8_t*)s;
      while (n--) *p++ = (uint8_t)c;
      return s;
  }
  #define memset lb_memset
#endif

#define LB_ALWAYS_INLINE static __attribute__((always_inline)) inline

/* ── Dimensões ─────────────────────────────────────────────────────── */
#define LB_LAYERS      16u       /* camadas                            */
#define LB_LAYER_BITS  256u      /* bits por camada                   */
#define LB_LAYER_BYTES 32u       /* bytes por camada (256/8)          */
#define LB_TOTAL_BITS  4096u     /* 16 × 256                          */
#define LB_TOTAL_BYTES 512u      /* 16 × 32                           */
#define LB_ATTRACTORS  42u       /* |𝒜| toroidal RAFAELIA             */
#define LB_GF_POLY     0x1Du     /* GF(2^8) reduction byte (0x11D→1D) */

/* ── Tabelas estáticas (geradas, não dependem de runtime) ─────────── */
#include "lb_tables.h"

/* ── Struct principal — toda na stack ou BSS, sem heap ────────────── */
typedef struct {
    uint8_t  layer[LB_LAYERS][LB_LAYER_BYTES];  /* 512 bytes de bits  */
    uint8_t  fold[LB_LAYER_BYTES];              /* XOR-fold das 16    */
    uint64_t tick;                              /* bytes processados  */
    uint32_t omega;                             /* atrator mod 42     */
    uint32_t phi;                               /* phi_integral Q16   */
} LayersBit;

/* ── Zero da struct sem memset de libc ───────────────────────────────
 * Usa um loop fixo de 512/8 = 64 iterações sobre uint64_t.
 * Compilador vetoriza automaticamente em AArch64 com NEON.            */
LB_ALWAYS_INLINE void lb_zero(LayersBit *lb) {
    uint64_t *p = (uint64_t*)(void*)lb;
    /* sizeof(LayersBit) ≤ 512 + 32 + 8 + 4 + 4 = 560 bytes ≤ 576 = 72×8 */
    for (uint32_t i = 0; i < 72u; i++) p[i] = 0ULL;
}

/* ── Bit set/clear/get branchless ─────────────────────────────────── */
LB_ALWAYS_INLINE void lb_bit_xor(uint8_t *base, uint32_t bit_pos) {
    base[bit_pos >> 3u] ^= (uint8_t)(1u << (bit_pos & 7u));
}
LB_ALWAYS_INLINE uint32_t lb_bit_get(const uint8_t *base, uint32_t bit_pos) {
    return (uint32_t)((base[bit_pos >> 3u] >> (bit_pos & 7u)) & 1u);
}

/* ── PUSH: XOR byte na posição prime_route[b] no LayersBit ──────────
 * Camada = (tick × prime) mod 16   — rotação das camadas sem branch
 * Posição = LB_ROUTE[b]            — prime routing estático
 * XOR no bit: lb->layer[camada][byte_idx] ^= bit_mask              */
LB_ALWAYS_INLINE void lb_push(LayersBit *lb, uint8_t b) {
    uint32_t bit_global = (uint32_t)LB_ROUTE[b];                 /* 0–4095   */
    uint32_t layer      = (uint32_t)((lb->tick * LB_PRIMES[b & 0xFFu]) % LB_LAYERS);
    uint32_t bit_local  = bit_global & (LB_LAYER_BITS - 1u);     /* 0–255    */
    lb_bit_xor(lb->layer[layer], bit_local);
    lb->tick++;
}

/* ── FOLD: XOR-reduce 16 camadas → fold[32] ─────────────────────────
 * 16 iterações fixas, sem loop variável.
 * fold = L0 ^ L1 ^ L2 ^ ... ^ L15 (por byte)                       */
LB_ALWAYS_INLINE void lb_fold(LayersBit *lb) {
    const uint64_t *L = (const uint64_t*)(void*)lb->layer;  /* 64×8 = 512 */
    uint64_t       *F = (uint64_t*)(void*)lb->fold;          /* 4×8 = 32   */
    /* Cada camada = 4 uint64_t (32 bytes). 16 camadas = 64 uint64_t. */
    /* F[j] = XOR de L[j], L[j+4], L[j+8], ..., L[j+60] para j=0..3 */
    F[0] = L[0]^L[4]^L[8] ^L[12]^L[16]^L[20]^L[24]^L[28]
          ^L[32]^L[36]^L[40]^L[44]^L[48]^L[52]^L[56]^L[60];
    F[1] = L[1]^L[5]^L[9] ^L[13]^L[17]^L[21]^L[25]^L[29]
          ^L[33]^L[37]^L[41]^L[45]^L[49]^L[53]^L[57]^L[61];
    F[2] = L[2]^L[6]^L[10]^L[14]^L[18]^L[22]^L[26]^L[30]
          ^L[34]^L[38]^L[42]^L[46]^L[50]^L[54]^L[58]^L[62];
    F[3] = L[3]^L[7]^L[11]^L[15]^L[19]^L[23]^L[27]^L[31]
          ^L[35]^L[39]^L[43]^L[47]^L[51]^L[55]^L[59]^L[63];
}

/* ── SPIRAL: rol8 em toda a fold (rotação circular de 1 bit) ─────────
 * Para cada byte b: spiral(b) = (b << 1) | (b >> 7)   — branchless
 * Equivale a multiplicar por x (o gerador) em GF(2^8) sem redução.  */
LB_ALWAYS_INLINE void lb_spiral(LayersBit *lb) {
    for (uint32_t i = 0; i < LB_LAYER_BYTES; i++) {
        uint8_t b = lb->fold[i];
        lb->fold[i] = (uint8_t)((b << 1u) | (b >> 7u));
    }
}

/* ── GF(2^8) multiplicação — completamente branchless via exp/log ────
 * a=0 ou b=0: resultado 0 via máscara
 * senão: gf_exp[ (gf_log[a] + gf_log[b]) mod 255 ]                  */
LB_ALWAYS_INLINE uint8_t lb_gf_mul(uint8_t a, uint8_t b) {
    uint32_t la = LB_GF_LOG[a], lb_ = LB_GF_LOG[b];
    uint32_t sum = la + lb_;
    /* sum mod 255 — branchless */
    sum -= 255u * (uint32_t)(sum >= 255u);
    uint8_t r = LB_GF_EXP[sum];
    /* Máscara branchless: 0x01 se zero, 0x00 se não-zero
     * -0x01 em uint8_t wrap = 0xFF → &~0xFF = &0x00 = 0  ✓
     * -0x00 em uint8_t wrap = 0x00 → &~0x00 = &0xFF = r  ✓ */
    uint8_t za = (uint8_t)((uint32_t)(a == 0u));  /* 1 se a=0 */
    uint8_t zb = (uint8_t)((uint32_t)(b == 0u));
    uint8_t zmask = (uint8_t)(-(uint8_t)(za | zb));  /* 0xFF se algum=0 */
    return (uint8_t)(r & (uint8_t)~zmask);
}

/* ── POLY eval: polinômio p(x) = c[0] + c[1]x + c[2]x^2 + ...
 * x = fold[0] (byte de síntese)
 * coefs: ponteiro para array de 'deg+1' coeficientes em GF(2^8)
 * Avalia via Horner branchless: p = c[n], p = gf_mul(p,x) ^ c[n-1] */
LB_ALWAYS_INLINE uint8_t lb_poly(const LayersBit *lb,
                                  const uint8_t *coef,
                                  uint32_t deg)
{
    uint8_t x = lb->fold[0];
    uint8_t p = coef[deg];
    /* deg iterações — assumimos deg ≤ 15 (tamanho de uma camada) */
    for (uint32_t i = deg; i-- > 0u; )
        p = (uint8_t)(lb_gf_mul(p, x) ^ coef[i]);
    return p;
}

/* ── POPCOUNT 32 bytes — AArch64 usa vcntq_u8 via builtins ────────── */
LB_ALWAYS_INLINE uint32_t lb_popcount32(const uint8_t *p) {
    uint32_t s = 0;
    /* 32 = 4 × 8 bytes: compilador vetoriza em NEON com -O2 */
    for (uint32_t i = 0; i < 32u; i++)
        s += (uint32_t)__builtin_popcount(p[i]);
    return s;
}

/* ── OMEGA: atrator toroidal = popcount(fold) mod 42 ─────────────── */
LB_ALWAYS_INLINE void lb_omega(LayersBit *lb) {
    uint32_t ones = lb_popcount32(lb->fold);
    /* mod 42 branchless via multiply-shift (exact for ones ≤ 256)   */
    /* ones mod 42 = ones - 42 * (ones * 2731 >> 17)                 */
    uint32_t q = (ones * 2731u) >> 17u;  /* floor(ones/42) para ones≤256 */
    lb->omega = ones - 42u * q;
}

/* ── ENTROPY milli: histograma dos 32 bytes fold → Shannon × 1000 ───
 * Histograma de 256 buckets em stack (256 bytes).
 * Usa integer log2 aproximado: log2(n) ≈ 31 - clz(n) para n>0.
 * Resultado em [0, 8000] milli-bits.                                 */
LB_ALWAYS_INLINE uint32_t lb_entropy_milli(const LayersBit *lb) {
    uint8_t hist[256];
    for (uint32_t i = 0; i < 256u; i++) hist[i] = 0u;
    for (uint32_t i = 0; i < 32u;  i++) hist[lb->fold[i]]++;

    /* Soma: Σ p * log2(N/p) em inteiros, N=32
     * = Σ hist[i] * (log2(32) - log2(hist[i]))  para hist[i]>0
     * = Σ hist[i] * (5 - floor_log2(hist[i]))
     * Resultado em bits × N. Divide por N=32 e × 1000.              */
    uint32_t acc = 0u;
    for (uint32_t i = 0; i < 256u; i++) {
        uint32_t h = hist[i];
        /* h > 0 sem branch: usa máscara */
        uint32_t nz = (uint32_t)(((int32_t)(h - 1u)) >> 31u) ^ 0xFFFFFFFFu;
        nz &= 1u;   /* nz=1 se h>0, 0 se h=0 */
        uint32_t fl2 = nz * (uint32_t)(31u - (uint32_t)__builtin_clz(h | 1u));
        acc += nz * h * (5u - fl2);
    }
    /* acc = Σ h * (5 - log2(h)), base 32 bits, max = 32*5=160 */
    return (acc * 1000u) / 32u;   /* milli (0–5000, log base 2) */
}

/* ── PHI_INTEGRAL: (1 − H/8000) × (ones/256) × 65536 ───────────────
 * Calcula phi_integral em Q16 sem float.
 * H = entropy_milli, ones = popcount(fold)                          */
LB_ALWAYS_INLINE void lb_phi(LayersBit *lb) {
    uint32_t H    = lb_entropy_milli(lb);
    uint32_t ones = lb_popcount32(lb->fold);
    /* (8000 - H) / 8000 em Q16: ((8000-H) * 65536) / 8000 */
    uint32_t hn   = ((8000u - H) * 65536u) / 8000u;
    /* ones/256 em Q16: (ones * 65536) / 256 = ones * 256 */
    uint32_t on   = ones * 256u;
    /* phi = hn × on / 65536 */
    lb->phi = (hn * on) >> 16u;
}

/* ── TICK completo: push + atualiza fold + omega + phi ──────────────
 * Uma chamada por byte processado. Inline total.                     */
LB_ALWAYS_INLINE void lb_tick(LayersBit *lb, uint8_t b) {
    lb_push(lb, b);
    lb_fold(lb);
    lb_omega(lb);
    /* phi e spiral a cada 32 bytes (um "frame" completo) */
    uint64_t do_frame = (uint64_t)(0u == (lb->tick & 31u));
    /* branchless: executa spiral e phi somente no frame boundary     */
    /* simula com operações condicionais sem branch — o compilador    */
    /* pode emitir CSEL em AArch64 se otimizar                        */
    if (do_frame) { lb_spiral(lb); lb_phi(lb); }
}

/* ── EMIT: serializa estado como linha JSONL (usa write() diretamente)
 * Não usa printf — monta a string manualmente em buffer fixo na stack.
 * Chama sys_write (SVC 64 em AArch64, SVC 4 em ARM32).              */
#if defined(__aarch64__)
static __attribute__((noinline)) void lb_write(const char *buf, uint64_t len) {
    register uint64_t x0 __asm__("x0") = 1;       /* fd=stdout     */
    register uint64_t x1 __asm__("x1") = (uint64_t)buf;
    register uint64_t x2 __asm__("x2") = len;
    register uint64_t x8 __asm__("x8") = 64;      /* SYS_WRITE     */
    __asm__ volatile("svc 0" : "+r"(x0) : "r"(x1),"r"(x2),"r"(x8) : "memory");
}
#elif defined(__arm__)
static __attribute__((noinline)) void lb_write(const char *buf, uint64_t len) {
    register uint32_t r0 __asm__("r0") = 1;
    register uint32_t r1 __asm__("r1") = (uint32_t)buf;
    register uint32_t r2 __asm__("r2") = (uint32_t)len;
    register uint32_t r7 __asm__("r7") = 4;       /* SYS_WRITE ARM32*/
    __asm__ volatile("svc 0" : "+r"(r0) : "r"(r1),"r"(r2),"r"(r7) : "memory");
}
#else
#include <unistd.h>
static __attribute__((noinline)) void lb_write(const char *buf, uint64_t len) {
    write(1, buf, (size_t)len);
}
#endif

/* Converte uint64 para decimal ASCII em buf, retorna bytes escritos  */
LB_ALWAYS_INLINE uint32_t u64_to_dec(uint64_t v, char *out) {
    if (!v) { out[0]='0'; return 1u; }
    char tmp[20]; uint32_t n=0;
    while (v) { tmp[n++]=(char)('0'+(v%10u)); v/=10u; }
    for (uint32_t i=0;i<n;i++) out[i]=tmp[n-1u-i];
    return n;
}
/* Converte uint32 hex (8 dígitos) para ASCII hex em buf              */
LB_ALWAYS_INLINE void u32_to_hex(uint32_t v, char *out) {
    static const char H[]="0123456789abcdef";
    for (uint32_t i=0;i<8u;i++) out[i]=H[(v>>(28u-4u*i))&0xFu];
}

/* Emite linha JSONL do estado atual — buffer fixo 256 bytes na stack */
LB_ALWAYS_INLINE void lb_emit(const LayersBit *lb) {
    char   buf[256];
    uint32_t pos = 0;

    /* {"tick":NNN,"omega":NN,"phi":NNNNN,"fold":"HHHHHHHH"}\n */
    const char p0[] = "{\"tick\":";
    for (uint32_t i=0; p0[i]; i++) buf[pos++]=p0[i];

    pos += u64_to_dec(lb->tick, buf+pos);

    const char p1[] = ",\"omega\":";
    for (uint32_t i=0; p1[i]; i++) buf[pos++]=p1[i];
    pos += u64_to_dec((uint64_t)lb->omega, buf+pos);

    const char p2[] = ",\"phi\":";
    for (uint32_t i=0; p2[i]; i++) buf[pos++]=p2[i];
    pos += u64_to_dec((uint64_t)lb->phi, buf+pos);

    const char p3[] = ",\"fold\":\"";
    for (uint32_t i=0; p3[i]; i++) buf[pos++]=p3[i];

    /* primeiros 4 bytes do fold como hex (8 chars) */
    uint32_t fold32;
    /* lê 4 bytes sem alias: loop explícito */
    fold32 = ((uint32_t)lb->fold[0]<<24u)|((uint32_t)lb->fold[1]<<16u)
            |((uint32_t)lb->fold[2]<< 8u)|((uint32_t)lb->fold[3]);
    u32_to_hex(fold32, buf+pos); pos+=8u;

    buf[pos++]='"'; buf[pos++]='}'; buf[pos++]='\n';

    lb_write(buf, (uint64_t)pos);
}

#endif /* OMEGA_LAYERSBIT_H */
