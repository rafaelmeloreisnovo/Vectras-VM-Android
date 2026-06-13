/* SPDX-License-Identifier: GPL-2.0-only
 * conjunto_de_conceitos / prim.c
 * Autoria: Rafael Melo Reis. Clean-room.
 * Primitivas freestanding + kernels SIMD. Sem libc, sem heap, sem GC.
 * NEON em aarch64; fallback escalar em armv7/sem-NEON.
 * Estrategia: blocagem por linha de cache (64 B), sem alocacao dinamica.
 */
#include "../include/abi.h"

#if defined(__aarch64__)
#include <arm_neon.h>
#endif

/* ---- simbolos que o compilador pode emitir (nostdlib precisa deles) ---- */

v0* memset(v0* d, int c, unsigned long n){
  u8* p=(u8*)d; u8 v=(u8)c; unsigned long i=0;
#if defined(__aarch64__)
  uint8x16_t q=vdupq_n_u8(v);
  for(; i+64<=n; i+=64){            /* 4x16 = uma linha de cache por iteracao */
    vst1q_u8(p+i+ 0,q); vst1q_u8(p+i+16,q);
    vst1q_u8(p+i+32,q); vst1q_u8(p+i+48,q);
  }
  for(; i+16<=n; i+=16) vst1q_u8(p+i,q);
#endif
  for(; i<n; i++) p[i]=v;
  return d;
}

v0* memcpy(v0* d, const v0* s, unsigned long n){
  u8* a=(u8*)d; const u8* b=(const u8*)s; unsigned long i=0;
#if defined(__aarch64__)
  for(; i+64<=n; i+=64){
    vst1q_u8(a+i+ 0,vld1q_u8(b+i+ 0)); vst1q_u8(a+i+16,vld1q_u8(b+i+16));
    vst1q_u8(a+i+32,vld1q_u8(b+i+32)); vst1q_u8(a+i+48,vld1q_u8(b+i+48));
  }
  for(; i+16<=n; i+=16) vst1q_u8(a+i,vld1q_u8(b+i));
#endif
  for(; i<n; i++) a[i]=b[i];
  return d;
}

int memcmp(const v0* x, const v0* y, unsigned long n){
  const u8* a=(const u8*)x; const u8* b=(const u8*)y;
  for(unsigned long i=0;i<n;i++){ int d=(int)a[i]-(int)b[i]; if(d) return d; }
  return 0;
}

/* ---- utilidades sem heap ---- */

sz cdc_strlen(const char* s){ sz n=0; while(s[n]) n++; return n; }

/* u64 -> decimal em buffer fornecido; retorna comprimento. branchless no laco */
sz cdc_u2s(u64 v, char* out){
  char tmp[20]; sz n=0;
  do{ tmp[n++]=(char)('0'+(v%10)); v/=10; }while(v);
  for(sz i=0;i<n;i++) out[i]=tmp[n-1-i];
  return n;
}

/* ---- kernel SIMD de exemplo: soma de bytes (reducao) ----
 * Mede largura de banda de leitura + ALU vetorial. NEON acumula em 16 lanes.
 */
u64 cdc_sum8_scalar(const u8* p, sz n){
  u64 s=0; for(sz i=0;i<n;i++) s+=p[i]; return s;
}

u64 cdc_sum8_simd(const u8* p, sz n){
#if defined(__aarch64__)
  uint64x2_t acc=vdupq_n_u64(0); sz i=0;
  for(; i+16<=n; i+=16){
    uint8x16_t v=vld1q_u8(p+i);     /* 16 bytes / ciclo de issue */
    acc=vpadalq_u32(acc, vpaddlq_u16(vpaddlq_u8(v)));
  }
  u64 s=vgetq_lane_u64(acc,0)+vgetq_lane_u64(acc,1);
  for(; i<n; i++) s+=p[i];
  return s;
#else
  return cdc_sum8_scalar(p,n);
#endif
}
