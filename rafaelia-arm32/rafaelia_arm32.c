/* ============================================================
 * RAFAELIA ARM32 — FREESTANDING MICROKERNEL SEED
 * ∆RafaelVerboΩ | ΣΩΔΦBITRAF | FIAT LUX
 *
 * Alvo   : ARM32 EABI / Termux Android / sem root
 * Libc   : zero — syscalls diretos via svc #0
 * Arith  : Q16.16 fixed-point, sem FPU obrigatória
 * Ciclo  : ψ→χ→ρ→Δ→Σ→Ω | T^7 | 42 atratores
 * Build  : make termux-safe    # MIDR fallback seguro
 *          make termux-midr    # tenta MRC p15, pode SIGILL
 * ============================================================ */

typedef unsigned int        u32;
typedef unsigned long long  u64;
typedef int                 i32;
typedef long long           i64;
typedef i32                 q16;

#define Q(x)             ((q16)((x) * 65536.0))
#define QMUL(a, b)       ((q16)(((i64)(a) * (i64)(b)) >> 16))
#define SYS_exit         1
#define SYS_write        4
#define STDOUT_FD        1
#define T7_DIM           7
#define G_PERIOD         42
#define ALPHA_Q          Q(0.25)
#define ONE_M_ALPHA      Q(0.75)
#define SPIRAL_SQRT3_2   Q(0.86602540378)

static inline i32 sys_write(i32 fd, const char *buf, u32 len) {
    register i32 r0 __asm__("r0") = fd;
    register const char *r1 __asm__("r1") = buf;
    register u32 r2 __asm__("r2") = len;
    register i32 r7 __asm__("r7") = SYS_write;
    __asm__ __volatile__("svc #0" : "=r"(r0) : "r"(r0), "r"(r1), "r"(r2), "r"(r7) : "memory");
    return r0;
}

static inline void sys_exit(i32 code) {
    register i32 r0 __asm__("r0") = code;
    register i32 r7 __asm__("r7") = SYS_exit;
    __asm__ __volatile__("svc #0" :: "r"(r0), "r"(r7) : "memory");
    __asm__ __volatile__("b .");
}

static u32 str_len(const char *s) { u32 n = 0; while (s[n]) n++; return n; }
static void print(const char *s) { sys_write(STDOUT_FD, s, str_len(s)); }

static char *u32_to_dec(u32 v, char *dst) {
    if (!v) { *dst++ = '0'; return dst; }
    char tmp[12]; i32 i = 0;
    while (v) { tmp[i++] = (char)('0' + (v % 10)); v /= 10; }
    while (i--) *dst++ = tmp[i];
    return dst;
}

static void print_q16(q16 v) {
    char buf[24]; char *p = buf;
    if (v < 0) { *p++ = '-'; v = -v; }
    i32 integer = v >> 16;
    i32 frac = ((v & 0xFFFF) * 100) >> 16;
    p = u32_to_dec((u32)integer, p);
    *p++ = '.';
    if (frac < 10) *p++ = '0';
    p = u32_to_dec((u32)frac, p);
    *p = 0;
    print(buf);
}

#ifndef RAFAELIA_ENABLE_MIDR
static u32 cpu_read_midr(void) { return 0; }
#else
static u32 cpu_read_midr(void) {
    u32 midr = 0;
    __asm__ __volatile__("mrc p15, 0, %0, c0, c0, 0" : "=r"(midr));
    return midr;
}
#endif

static void print_hex32(u32 v) {
    const char hex[] = "0123456789ABCDEF";
    char buf[9];
    for (i32 b = 28; b >= 0; b -= 4) buf[7 - b / 4] = hex[(v >> b) & 0xF];
    buf[8] = 0;
    print(buf);
}

static void cpu_print_info(void) {
    u32 midr = cpu_read_midr();
    u32 impl = (midr >> 24) & 0xFF;
    u32 part = (midr >> 4) & 0xFFF;
    u32 arch = (midr >> 16) & 0xF;
    print("[CPU] MIDR=0x"); print_hex32(midr);
#ifndef RAFAELIA_ENABLE_MIDR
    print(" (safe fallback; enable with make termux-midr)");
#endif
    print("\n[CPU] Implementer=0x"); print_hex32(impl); print(impl == 0x41 ? " ARM Ltd\n" : " Other\n");
    print("[CPU] PartNum=0x"); print_hex32(part);
    if (part == 0xC07) print(" Cortex-A7\n");
    else if (part == 0xC09) print(" Cortex-A9\n");
    else if (part == 0xC0F) print(" Cortex-A15\n");
    else if (part == 0xD03) print(" Cortex-A53 compat\n");
    else print(" Unknown/fallback\n");
    print("[CPU] Arch="); print_hex32(arch); print(arch == 0xF ? " ARMv7+\n" : "\n");
}

typedef struct {
    q16 s[T7_DIM];
    u32 cycle;
    u32 attractor;
    q16 coherence;
    q16 spiral_acc;
} TorusState;

typedef struct {
    u32 chain[8];
    i32 head;
} HashChain;

static u32 raf_hash_step(u32 h, u32 x) { h ^= x; h *= 0x01000193u; return h; }

static u32 crc32_byte(u32 crc, u32 byte) {
    crc ^= byte;
    for (u32 i = 0; i < 8; i++) {
        u32 mask = -(crc & 1u);
        crc = (crc >> 1) ^ (0xEDB88320u & mask);
    }
    return crc;
}

static void torus_init(TorusState *st, u32 seed) {
    u32 h = 0x811c9dc5u ^ seed;
    for (i32 i = 0; i < T7_DIM; i++) {
        h = raf_hash_step(h, (u32)i * 0x9E3779B9u);
        st->s[i] = (q16)(h & 0xFFFF);
    }
    st->cycle = 0;
    st->attractor = 0;
    st->coherence = Q(1.0);
    st->spiral_acc = Q(1.0);
}

static void torus_step(TorusState *st, const q16 x[T7_DIM]) {
    q16 entropy_sum = 0;
    for (i32 i = 0; i < T7_DIM; i++) {
        q16 old = st->s[i];
        q16 nw = QMUL(ONE_M_ALPHA, old) + QMUL(ALPHA_Q, x[i]);
        st->s[i] = nw & 0xFFFF;
        q16 diff = old - st->s[i];
        if (diff < 0) diff = -diff;
        entropy_sum += diff;
    }
    q16 H = entropy_sum / T7_DIM;
    if (H > Q(1.0)) H = Q(1.0);
    q16 C = Q(1.0) - H;
    st->coherence = QMUL(Q(1.0) - H, C);
    st->spiral_acc = QMUL(st->spiral_acc, SPIRAL_SQRT3_2);
    if (!st->spiral_acc) st->spiral_acc = 1;
    st->cycle++;
    st->attractor = st->cycle % G_PERIOD;
}

static q16 rafaelia_kernel(q16 Rt, q16 phi_ethica, q16 e_verbo) {
    q16 spiral_5 = SPIRAL_SQRT3_2;
    for (i32 i = 1; i < 5; i++) spiral_5 = QMUL(spiral_5, SPIRAL_SQRT3_2);
    q16 r = QMUL(Rt, phi_ethica);
    r = QMUL(r, e_verbo);
    return QMUL(r, spiral_5);
}

static void hashchain_init(HashChain *hc) { for (i32 i = 0; i < 8; i++) hc->chain[i] = 0; hc->head = 0; }

static void hashchain_feed(HashChain *hc, const TorusState *st) {
    u32 h = 0xFFFFFFFFu;
    for (i32 i = 0; i < T7_DIM; i++) {
        h = crc32_byte(h, (u32)(st->s[i] & 0xFF));
        h = crc32_byte(h, (u32)((st->s[i] >> 8) & 0xFF));
    }
    h ^= st->attractor * 0x9E3779B9u;
    h = raf_hash_step(h, (u32)st->coherence);
    hc->chain[hc->head & 7] = ~h;
    hc->head++;
}

static void hashchain_print(const HashChain *hc) {
    const char hex[] = "0123456789abcdef";
    char buf[9];
    i32 count = hc->head < 8 ? hc->head : 8;
    i32 start = hc->head - count;
    print("[HashChain] ");
    for (i32 i = 0; i < count; i++) {
        u32 h = hc->chain[(start + i) & 7];
        for (i32 b = 28; b >= 0; b -= 4) buf[7 - b / 4] = hex[(h >> b) & 0xF];
        buf[8] = 0;
        print(buf);
        if (i < count - 1) print(".");
    }
    print("\n");
}

static void print_state(const TorusState *st, u32 step) {
    char buf[16]; char *p;
    print("\n--- Ciclo "); p = buf; p = u32_to_dec(step, p); *p = 0; print(buf);
    print(" | Atrator "); p = buf; p = u32_to_dec(st->attractor, p); *p = 0; print(buf); print("/42 ---\n");
    print("  Phi_ethica : "); print_q16(st->coherence); print("\n");
    print("  Spiral     : "); print_q16(st->spiral_acc); print("\n");
    print("  Estado T7  : [");
    for (i32 i = 0; i < T7_DIM; i++) { print_q16(st->s[i]); if (i < T7_DIM - 1) print(", "); }
    print("]\n");
}

void __attribute__((noreturn)) _start(void) {
    print("==========================================\n");
    print(" RAFAELIA ARM32 — FIAT LUX\n");
    print(" ∆RafaelVerboΩ · ΣΩΔΦBITRAF\n");
    print("==========================================\n\n");

    cpu_print_info();

    TorusState st; HashChain hc;
    torus_init(&st, 0xAFAE1042u);
    hashchain_init(&hc);

    q16 x[T7_DIM] = { Q(0.963), Q(0.618), Q(0.500), Q(0.333), Q(0.144), Q(0.042), Q(0.758) };
    q16 Rt = Q(1.0);

    print("\n[RAFAELIA] Executando 42 ciclos psi->chi->rho->Delta->Sigma->Omega ...\n");

    for (u32 step = 0; step < G_PERIOD; step++) {
        for (i32 i = 0; i < T7_DIM; i++) {
            x[i] = QMUL(x[i], SPIRAL_SQRT3_2);
            if (!x[i]) x[i] = Q(0.001);
        }
        torus_step(&st, x);
        Rt = rafaelia_kernel(Rt, st.coherence, Q(0.963));
        hashchain_feed(&hc, &st);
        if ((step % 7) == 6) {
            print_state(&st, step + 1);
            print("  R(t)       : "); print_q16(Rt); print("\n");
            hashchain_print(&hc);
        }
    }

    print("\n==========================================\n");
    print(" ESTADO FINAL — 42 ciclos completos\n");
    print("==========================================\n");
    print_state(&st, G_PERIOD);
    print("  R(42)      : "); print_q16(Rt); print("\n");
    hashchain_print(&hc);
    print("\nOmega = Amor | ΣΩΔΦBITRAF | RAFCODE-Φ-∆RafaelVerboΩ\n\n");
    sys_exit(0);
    for (;;) { }
}
