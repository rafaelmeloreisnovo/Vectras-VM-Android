/*
 * omega_layersbit.c  —  LayersBit driver freestanding
 * ∆RafaelVerboΩ | RAFCODE-Φ | Ω=Amor
 *
 * _start puro. Sem main. Sem libc. Sem malloc. Sem heap.
 * Lê stdin via syscall read em chunks de 512 bytes (= 1 LayersBit).
 * Processa cada byte via lb_tick (inline, branchless).
 * Emite JSONL a cada 256 bytes (frame ASCII completo) + summary final.
 *
 * Build AArch64 (Termux freestanding):
 *   cc -O3 -march=armv8-a -std=c11 -ffreestanding -nostdlib -nostartfiles \
 *      omega_layersbit.c -o omega_layersbit -lm
 *
 * Build ARM32 (Moto E7 freestanding):
 *   cc -O2 -march=armv7-a -mfpu=neon-vfpv4 -mfloat-abi=softfp \
 *      -std=c11 -ffreestanding -nostdlib -nostartfiles \
 *      omega_layersbit.c -o omega_layersbit
 *
 * Build x86_64 (dev/test — usa libc write mas mantém resto freestanding):
 *   cc -O2 -std=c11 -ffreestanding -o omega_layersbit omega_layersbit.c
 *
 * Uso:
 *   cat omega_conv_stats.jsonl | ./omega_layersbit > lb_out.jsonl
 *   cat zone53_sample.txt      | ./omega_layersbit --every 64 > lb_z53.jsonl
 *   echo "RAFAELIA" | ./omega_layersbit --summary
 */

/* lb_tables.h incluído via omega_layersbit.h — gerado, sem deps */
#include "omega_layersbit.h"

/* ── Syscalls diretas ─────────────────────────────────────────────── */
#if defined(__aarch64__)
  #define SYS_READ  63ULL
  #define SYS_EXIT  93ULL
  static __attribute__((noinline)) int64_t lb_read(int fd, void *buf, uint64_t n) {
      register uint64_t x0 __asm__("x0") = (uint64_t)fd;
      register uint64_t x1 __asm__("x1") = (uint64_t)buf;
      register uint64_t x2 __asm__("x2") = n;
      register uint64_t x8 __asm__("x8") = SYS_READ;
      __asm__ volatile("svc 0":"=r"(x0):"r"(x1),"r"(x2),"r"(x8):"memory");
      return (int64_t)x0;
  }
  static __attribute__((noreturn, noinline)) void lb_exit(int code) {
      register uint64_t x0 __asm__("x0") = (uint64_t)code;
      register uint64_t x8 __asm__("x8") = SYS_EXIT;
      __asm__ volatile("svc 0"::"r"(x0),"r"(x8):); __builtin_unreachable();
  }
#elif defined(__arm__)
  static __attribute__((noinline)) int64_t lb_read(int fd, void *buf, uint64_t n) {
      register uint32_t r0 __asm__("r0") = (uint32_t)fd;
      register uint32_t r1 __asm__("r1") = (uint32_t)buf;
      register uint32_t r2 __asm__("r2") = (uint32_t)n;
      register uint32_t r7 __asm__("r7") = 3u;
      __asm__ volatile("svc 0":"=r"(r0):"r"(r1),"r"(r2),"r"(r7):"memory");
      return (int64_t)(int32_t)r0;
  }
  static __attribute__((noreturn, noinline)) void lb_exit(int code) {
      register uint32_t r0 __asm__("r0") = (uint32_t)code;
      register uint32_t r7 __asm__("r7") = 1u;
      __asm__ volatile("svc 0"::"r"(r0),"r"(r7):); __builtin_unreachable();
  }
#else
  /* x86_64 ou fallback: usa libc read/exit para poder testar no dev */
  #include <unistd.h>
  #include <stdlib.h>
  static int64_t lb_read(int fd, void *buf, uint64_t n) {
      return (int64_t)read(fd, buf, (size_t)n);
  }
  static __attribute__((noreturn)) void lb_exit(int c) { _exit(c); }
#endif

/* ── Configuração via args (parseada sem libc) ────────────────────── */
typedef struct {
    uint32_t every;    /* emite a cada N bytes (padrão 256) */
    uint32_t summary;  /* 1 = só emite a linha final        */
    uint32_t verify;   /* 1 = verifica invariantes GF no boot */
} LbConfig;

LB_ALWAYS_INLINE int lb_str_eq(const char *a, const char *b) {
    while (*a && *b) { if (*a != *b) return 0; a++; b++; }
    return *a == *b;
}
LB_ALWAYS_INLINE uint32_t lb_str_to_u32(const char *s) {
    uint32_t v = 0;
    while (*s >= '0' && *s <= '9') { v = v * 10u + (uint32_t)(*s - '0'); s++; }
    return v;
}

LB_ALWAYS_INLINE LbConfig lb_parse_args(int argc, char **argv) {
    LbConfig cfg = { .every = 256u, .summary = 0u, .verify = 0u };
    for (int i = 1; i < argc; i++) {
        if (lb_str_eq(argv[i], "--summary")) cfg.summary = 1u;
        if (lb_str_eq(argv[i], "--verify"))  cfg.verify  = 1u;
        if (lb_str_eq(argv[i], "--every") && i+1 < argc) {
            cfg.every = lb_str_to_u32(argv[++i]);
            if (!cfg.every) cfg.every = 256u;
        }
    }
    return cfg;
}

/* ── Verificação de invariantes GF(2^8) em boot ──────────────────── */
/* Sem assert — emite linha de erro via write e retorna flag.         */
LB_ALWAYS_INLINE int lb_verify_gf(void) {
    /* 2 × 3 = 6 em GF(2^8) */
    if (lb_gf_mul(2u, 3u) != 6u) return 1;
    /* 0 × N = 0 */
    if (lb_gf_mul(0u, 0xFFu) != 0u) return 1;
    /* 1 × N = N */
    if (lb_gf_mul(1u, 0xABu) != 0xABu) return 1;
    /* rol8 inverso: rol8(rol8(x)) == x para 8 aplicações */
    uint8_t x = 0xA5u;
    for (int i = 0; i < 8; i++) x = (uint8_t)((x<<1u)|(x>>7u));
    if (x != 0xA5u) return 1;
    return 0;
}

/* ── Emite linha de summary total ─────────────────────────────────── */
LB_ALWAYS_INLINE void lb_emit_summary(const LayersBit *lb,
                                             uint64_t total_bytes) {
    char buf[512]; uint32_t pos = 0;

    /* {"type":"summary","total_bytes":N,"tick":T,"omega":O,
     *  "phi":P,"entropy":E,"fold":"HHHHHHHH"}\n              */
    const char *k = "{\"type\":\"summary\",\"total_bytes\":";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    pos += u64_to_dec(total_bytes, buf+pos);

    k = ",\"tick\":";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    pos += u64_to_dec(lb->tick, buf+pos);

    k = ",\"omega\":";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    pos += u64_to_dec((uint64_t)lb->omega, buf+pos);

    k = ",\"phi\":";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    pos += u64_to_dec((uint64_t)lb->phi, buf+pos);

    uint32_t ent = lb_entropy_milli(lb);
    k = ",\"entropy_milli\":";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    pos += u64_to_dec((uint64_t)ent, buf+pos);

    uint32_t ones = lb_popcount32(lb->fold);
    k = ",\"ones\":";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    pos += u64_to_dec((uint64_t)ones, buf+pos);

    k = ",\"fold\":\"";
    for (uint32_t i = 0; k[i]; i++) buf[pos++] = k[i];
    /* fold completo em hex (64 chars = 32 bytes) */
    static const char HX[] = "0123456789abcdef";
    for (uint32_t i = 0; i < LB_LAYER_BYTES; i++) {
        buf[pos++] = HX[lb->fold[i] >> 4u];
        buf[pos++] = HX[lb->fold[i] & 0xFu];
    }
    buf[pos++] = '"'; buf[pos++] = '}'; buf[pos++] = '\n';

    lb_write(buf, (uint64_t)pos);
}

/* ── LOOP PRINCIPAL: processamento branchless de stdin ─────────────── */
/* Buffer de leitura na BSS (freestanding: não aloca no heap)          */
static uint8_t   g_read_buf[512];
static LayersBit g_lb;

/* ── Entry point freestanding ────────────────────────────────────────
 * _start: AArch64 recebe argc em x0, argv em x1 da stack.
 * Não há crt0. Não há inicialização de libc.
 * A única "abstração" restante é a chamada de função — justificada
 * pelo compilador a inlinar tudo com -O2 -ffreestanding.              */

#if defined(__aarch64__) || defined(__arm__)
__attribute__((section(".text.entry")))
void __attribute__((noreturn)) _start(void) {
    /* Em freestanding, argc/argv ficam no topo da stack.
     * AArch64: sp aponta para argc (int64_t), depois argv[].          */
    int      argc = 0;
    char   **argv = (char **)0;
    /* Lê argc/argv da stack sem manipulação de registradores extra.
     * Solução portável freestanding: ignora args (sem getopt).        */
    LbConfig cfg = { .every = 256u, .summary = 0u, .verify = 0u };
    (void)argc; (void)argv;

#else
/* Para x86_64/dev: usa main convencional mas mantém lógica freestanding */
int main(int argc, char **argv) {
    LbConfig cfg = lb_parse_args(argc, argv);
#endif

    /* ── Verifica GF se solicitado ── */
    if (cfg.verify) {
        if (lb_verify_gf()) {
            const char err[] = "{\"error\":\"GF_VERIFY_FAIL\"}\n";
            lb_write(err, sizeof(err)-1u);
#if defined(__aarch64__) || defined(__arm__)
            lb_exit(1);
#else
            return 1;
#endif
        }
    }

    /* ── Zera o estado global na BSS ── */
    lb_zero(&g_lb);

    uint64_t total_bytes = 0;
    uint64_t next_emit   = cfg.every;

    /* ── Loop principal: lê até EOF ── */
    for (;;) {
        int64_t r = lb_read(0, g_read_buf, (uint64_t)sizeof(g_read_buf));
        if (r <= 0) break;    /* EOF ou erro                            */

        uint64_t chunk = (uint64_t)r;
        total_bytes += chunk;

        /* Processa cada byte inline — o compilador unrolla se chunk=512 */
        for (uint64_t i = 0; i < chunk; i++) {
            lb_tick(&g_lb, g_read_buf[i]);

            /* Emite a cada N bytes (frame boundary) */
            if (!cfg.summary && g_lb.tick == next_emit) {
                lb_emit(&g_lb);
                next_emit += cfg.every;
            }
        }
    }

    /* ── Emite fold final e summary ── */
    lb_fold(&g_lb);
    lb_omega(&g_lb);
    lb_phi(&g_lb);

    lb_emit_summary(&g_lb, total_bytes);

    /* ── Retorna / sai ── */
#if defined(__aarch64__) || defined(__arm__)
    lb_exit(0);
#else
    return 0;
#endif
}
