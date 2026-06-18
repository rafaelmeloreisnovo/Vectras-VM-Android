/* SPDX-License-Identifier: GPL-2.0-only
 * conjunto_de_conceitos / abi.h
 * Autoria: Rafael Melo Reis. Clean-room, sem reuso textual de terceiros.
 * ABI freestanding: syscalls Linux por svc/swi inline, sem libc, sem heap.
 * Alvos: aarch64 (primario), armv7 (eabi). Termux/Android = Linux ABI.
 */
#ifndef CDC_ABI_H
#define CDC_ABI_H

/* tipos minimos, sem stdint/stddef (freestanding puro) */
typedef unsigned char        u8;
typedef unsigned short       u16;
typedef unsigned int         u32;
typedef unsigned long        u64;
typedef long                 sz;   /* tamanho/erro com sinal */
typedef void                 v0;

/* ---- numeros de syscall (hex), por arquitetura ---- */
#if defined(__aarch64__)
  #define SC_read   0x3f
  #define SC_write  0x40
  #define SC_close  0x39
  #define SC_socket 0xc6   /* 198 */
  #define SC_connect 0xcb  /* 203 */
  #define SC_clockgt 0x71  /* 113 clock_gettime */
  #define SC_nanosleep 0x65/* 101 */
  #define SC_exit   0x5e   /* 94 exit_group */
#elif defined(__arm__)
  #define SC_read   0x03
  #define SC_write  0x04
  #define SC_close  0x06
  #define SC_socket 0x119  /* 281 */
  #define SC_connect 0x11b /* 283 */
  #define SC_clockgt 0x107 /* 263 */
  #define SC_nanosleep 0xa2/* 162 */
  #define SC_exit   0xf8   /* 248 exit_group */
#elif defined(__x86_64__)
  /* somente porta de referencia de host p/ rodar a logica (sem NEON) */
  #define SC_read   0x00
  #define SC_write  0x01
  #define SC_close  0x03
  #define SC_socket 0x29  /* 41 */
  #define SC_connect 0x2a /* 42 */
  #define SC_clockgt 0xe4 /* 228 */
  #define SC_nanosleep 0x23/* 35 */
  #define SC_exit   0xe7  /* 231 exit_group */
#else
  #error "alvo nao suportado: compile para aarch64, armv7 ou x86_64(host)"
#endif

/* ---- nucleo de syscall: inline asm, registradores fixos ---- */
#if defined(__aarch64__)
static inline sz cdc_sc(sz n, sz a, sz b, sz c, sz d, sz e){
  register sz x8 __asm__("x8")=n;
  register sz x0 __asm__("x0")=a, x1 __asm__("x1")=b, x2 __asm__("x2")=c;
  register sz x3 __asm__("x3")=d, x4 __asm__("x4")=e, x5 __asm__("x5")=0;
  __asm__ volatile("svc #0"
    : "+r"(x0)
    : "r"(x8),"r"(x1),"r"(x2),"r"(x3),"r"(x4),"r"(x5)
    : "memory","cc");
  return x0;
}
#elif defined(__x86_64__)
static inline sz cdc_sc(sz n, sz a, sz b, sz c, sz d, sz e){
  /* ABI x86_64: arg1=rdi arg2=rsi arg3=rdx arg4=r10 arg5=r8 (arg6=r9) */
  sz ret;
  register sz r10 __asm__("r10")=d; register sz r8 __asm__("r8")=e;
  __asm__ volatile("syscall"
    : "=a"(ret)
    : "a"(n),"D"(a),"S"(b),"d"(c),"r"(r10),"r"(r8)
    : "rcx","r11","memory");
  return ret;
}
#else /* __arm__ : EABI usa r7=nr, swi 0 */
static inline sz cdc_sc(sz n, sz a, sz b, sz c, sz d, sz e){
  register sz r7 __asm__("r7")=n;
  register sz r0 __asm__("r0")=a, r1 __asm__("r1")=b, r2 __asm__("r2")=c;
  register sz r3 __asm__("r3")=d, r4 __asm__("r4")=e;
  __asm__ volatile("swi #0"
    : "+r"(r0)
    : "r"(r7),"r"(r1),"r"(r2),"r"(r3),"r"(r4)
    : "memory","cc");
  return r0;
}
#endif

/* fachadas finas (branchless: sem ramo, so repasse) */
static inline sz cdc_write(sz fd, const v0* p, sz n){ return cdc_sc(SC_write,fd,(sz)p,n,0,0); }
static inline sz cdc_read (sz fd, v0* p, sz n){ return cdc_sc(SC_read ,fd,(sz)p,n,0,0); }
static inline sz cdc_close(sz fd){ return cdc_sc(SC_close,fd,0,0,0,0); }
static inline v0 cdc_exit (sz code){ cdc_sc(SC_exit,code,0,0,0,0); for(;;){} }

/* ---- contadores para benchmark ----
 * aarch64: cntvct_el0 (contador virtual) + cntfrq_el0 (Hz).
 * fallback portatil: clock_gettime(MONOTONIC).
 */
#if defined(__aarch64__)
static inline u64 cdc_cycles(v0){ u64 r; __asm__ volatile("mrs %0, cntvct_el0":"=r"(r)); return r; }
static inline u64 cdc_freq  (v0){ u64 r; __asm__ volatile("mrs %0, cntfrq_el0":"=r"(r)); return r; }
#elif defined(__x86_64__)
static inline u64 cdc_cycles(v0){ u32 lo,hi; __asm__ volatile("rdtsc":"=a"(lo),"=d"(hi)); return ((u64)hi<<32)|lo; }
static inline u64 cdc_freq  (v0){ return 0; /* host: TSC sem Hz nominal */ }
#else
static inline u64 cdc_cycles(v0){
  /* armv7 sem acesso a PMU em userspace: usa clock_gettime ns */
  u64 ts[2]; cdc_sc(SC_clockgt,1,(sz)ts,0,0,0); return ts[0]*1000000000UL+ts[1];
}
static inline u64 cdc_freq(v0){ return 1000000000UL; }
#endif

#endif /* CDC_ABI_H */
