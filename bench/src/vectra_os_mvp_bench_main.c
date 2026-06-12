/* vectra_os_mvp_bench - G8 do VECTRA_OS_LIVING_SYSTEM_GAP_LEDGER.
 *
 * Camada de prova de benchmark dos 5 kernels MVP (ledger §4 G8):
 *   1. FRAF Q16 (VOS_FRAF_ITERS iterações até F*);
 *   2. CRC32C 4KB (dispatch ativo: HW ou SW);
 *   3. arena alloc 64B (bump + restore);
 *   4. T7 100 steps (RmR_TorusFlow_Step);
 *   5. FSM step 8 estados + classificação de Lyapunov (contração de F*).
 *
 * Saída obrigatória (falsificador §6: relatório sem amostras brutas ou
 * metadados de plataforma invalida o contrato): mediana, p5, p95, amostras
 * brutas, tag de plataforma, flags de compilação, commit e hash do binário.
 *
 * Unidade: ticks do VOS_TICK() (rdtsc/cntvct conforme dispatch) — a fonte
 * é reportada; ciclos de estado, não relógio de parede.
 */
#include "rmr_vectra_os.h"
#include "rmr_torus_flow.h"

#include <stdio.h>

#ifndef RMR_BUILD_COMMIT
#define RMR_BUILD_COMMIT "unknown"
#endif
#ifndef RMR_BENCH_FLAGS_STR
#define RMR_BENCH_FLAGS_STR "(not provided by build)"
#endif

#define MVP_WARMUP 3u

static volatile u64 g_sink; /* anti-DCE: todo kernel deposita aqui */

static u8 g_crc_buf[4096u];
static RmR_TorusFlowState g_torus;

/* ── kernels ────────────────────────────────────────────────────────────── */

static void k1_fraf(void) {
  vos_q16_t fn;
  vos_bool_t ok;
  VOS_FRAF_CONVERGE(fn, ok, VOS_Q16_ONE);
  g_sink ^= (u64)(u32)fn + (u64)ok;
}

static void k2_crc4k(void) {
  g_sink ^= (u64)VOS_CRC32C(g_crc_buf, (u32)sizeof(g_crc_buf), VOS_CHAIN_INIT);
}

static void k3_arena(void) { /* 256 transações alloc(64)+restore */
  for (u32 i = 0u; i < 256u; ++i) {
    VOS_MARK();
    void *p = VOS_ARENA_ALLOC(64u);
    g_sink ^= (u64)p;
    VOS_RESTORE();
  }
}

static void k4_t7(void) { /* "T7 100 steps" — literal do ledger */
  for (u32 i = 0u; i < 100u; ++i) RmR_TorusFlow_Step(&g_torus);
  g_sink ^= (u64)RmR_TorusFlow_Checksum(&g_torus);
}

static void k5_fsm_lyapunov(void) { /* 256 × (FSM 8 estados + contração) */
  static u32 s[8] = {0u, 1u, 2u, 3u, 4u, 5u, 6u, 7u};
  for (u32 r = 0u; r < 256u; ++r) {
    for (u32 i = 0u; i < 8u; ++i) {
      s[i] = (s[i] ^ s[(i + 1u) & 7u] ^ (i << 2u)) & 0xFFu;
    }
    /* Lyapunov: um passo FRAF a partir de F*+δ deve CONTRAIR (λ < 0) */
    {
      vos_q16_t f0 = (vos_q16_t)(VOS_FRAF_STAR_Q16 + (vos_q16_t)(s[0] + 1u));
      vos_q16_t f1 = VOS_FRAF_STEP(f0);
      u32 d0 = (u32)VOS_CSEL_ABS_Q16(VOS_Q16_SUB(f0, VOS_FRAF_STAR_Q16));
      u32 d1 = (u32)VOS_CSEL_ABS_Q16(VOS_Q16_SUB(f1, VOS_FRAF_STAR_Q16));
      g_sink ^= (u64)VOS_CSEL(d1 <= d0, 1u, 0u);
    }
  }
}

/* ── medição ────────────────────────────────────────────────────────────── */

typedef struct {
  const char *name;
  void (*fn)(void);
  u32 ops_per_sample;
} mvp_kernel_t;

static void mvp_sort31(u64 *v) {
  for (u32 i = 1u; i < VOS_BENCH_N; ++i) {
    u64 key = v[i];
    u32 j = i;
    while (j > 0u && v[j - 1u] > key) {
      v[j] = v[j - 1u];
      --j;
    }
    v[j] = key;
  }
}

static void mvp_measure(const mvp_kernel_t *k, u64 *samples_out) {
  for (u32 w = 0u; w < MVP_WARMUP; ++w) k->fn();
  for (u32 i = 0u; i < VOS_BENCH_N; ++i) {
    vos_tick_t t0 = VOS_TICK();
    k->fn();
    samples_out[i] = (u64)VOS_TICK_DELTA(t0);
  }
}

/* FNV-1a 64 do próprio binário — identidade do artefato medido. */
static u64 mvp_binary_hash(void) {
  FILE *f = fopen("/proc/self/exe", "rb");
  u64 h = VOS_FNV_BASIS;
  u8 buf[4096u];
  if (!f) return 0u;
  for (;;) {
    u32 n = (u32)fread(buf, 1u, sizeof(buf), f);
    if (n == 0u) break;
    for (u32 i = 0u; i < n; ++i) h = VOS_FNV_FEED(h, buf[i]);
  }
  fclose(f);
  return h;
}

int main(void) {
  static const mvp_kernel_t kernels[5] = {
      {"fraf_q16_converge", k1_fraf, 1u},
      {"crc32c_4kb", k2_crc4k, 1u},
      {"arena_alloc_64b", k3_arena, 256u},
      {"t7_toroidal_100steps", k4_t7, 100u},
      {"fsm8_lyapunov", k5_fsm_lyapunov, 256u},
  };
  u64 samples[VOS_BENCH_N];
  u32 caps_out[4];
  const char *arch;
  const char *tick_src;

  if (vos_init() != 1u) {
    printf("FAIL vos_init — benchmark sem contrato valido\n");
    return 1;
  }
  vos_caps_report(caps_out);

#if defined(__aarch64__)
  arch = "A64A";
#elif defined(__x86_64__)
  arch = "X64F";
#elif defined(__arm__)
  arch = "ARM7";
#elif defined(__riscv)
  arch = "RVA6";
#else
  arch = "UNKN";
#endif
  tick_src = (caps_out[0] & VOS_CAP_RDTSC) ? "rdtsc"
           : (caps_out[0] & VOS_CAP_CNTVCT) ? "cntvct"
                                            : "sw";

  for (u32 i = 0u; i < (u32)sizeof(g_crc_buf); ++i) {
    g_crc_buf[i] = (u8)((i * 131u + 7u) & 0xFFu);
  }
  RmR_TorusFlow_Init(&g_torus, 0x52414621u);
  RmR_TorusFlow_InjectGrammar(&g_torus, 0x9E3779B9u);

  printf("# VECTRA_OS MVP benchmark proof (G8)\n");
  printf("platform_tag=%s caps=0x%08x tick_source=%s unit=ticks\n",
         arch, (unsigned)caps_out[0], tick_src);
  printf("caps_flags=");
  for (u32 b = 0u; b < 32u; ++b) {
    if (caps_out[0] & (1u << b)) printf("%s ", vos_flag_name(b));
  }
  printf("\ncompiler=%s\n", __VERSION__);
  printf("build_flags=%s\n", RMR_BENCH_FLAGS_STR);
  printf("commit=%s\n", RMR_BUILD_COMMIT);
  printf("binary_fnv64=0x%016llx\n", (unsigned long long)mvp_binary_hash());
  printf("samples_per_kernel=%u warmup=%u\n",
         (unsigned)VOS_BENCH_N, (unsigned)MVP_WARMUP);

  for (u32 k = 0u; k < 5u; ++k) {
    mvp_measure(&kernels[k], samples);
    printf("kernel=%s ops_per_sample=%u raw_ticks=",
           kernels[k].name, (unsigned)kernels[k].ops_per_sample);
    for (u32 i = 0u; i < VOS_BENCH_N; ++i) {
      printf("%llu%s", (unsigned long long)samples[i],
             (i + 1u < VOS_BENCH_N) ? "," : "");
    }
    mvp_sort31(samples);
    printf(" p5=%llu median=%llu p95=%llu median_per_op=%llu\n",
           (unsigned long long)samples[(5u * VOS_BENCH_N) / 100u],
           (unsigned long long)samples[VOS_BENCH_N / 2u],
           (unsigned long long)samples[(95u * VOS_BENCH_N) / 100u],
           (unsigned long long)(samples[VOS_BENCH_N / 2u] /
                                kernels[k].ops_per_sample));
  }

  printf("OK vectra_os_mvp_bench sink=0x%016llx\n",
         (unsigned long long)g_sink);
  return 0;
}
