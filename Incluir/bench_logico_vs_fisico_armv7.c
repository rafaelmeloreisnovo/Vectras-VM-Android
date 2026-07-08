/* bench_v3: roda N=20 trials, reporta mediana, para reduzir efeito de ruído
   de scheduling do host sob qemu-arm. */
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <time.h>

extern uint32_t vos_fraf_step_av7(uint32_t fn_q16);

__attribute__((noinline))
uint32_t c_fraf_step_noinline(uint32_t fn_q16) {
  uint32_t scale = 0xDDB4u, offset = 0x31A1Eu;
  int64_t mul = (int64_t)(int32_t)fn_q16 * (int64_t)(int32_t)scale;
  return (uint32_t)(mul >> 16) + offset;
}

static double now_ms(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (double)ts.tv_sec * 1000.0 + (double)ts.tv_nsec / 1e6;
}

#define ITERS 1000000u
#define TRIALS 21

static int cmp_d(const void *a, const void *b) {
  double da = *(const double*)a, db = *(const double*)b;
  return (da > db) - (da < db);
}

int main(void) {
  double c_times[TRIALS], a_times[TRIALS];
  uint32_t acc;
  volatile uint32_t sink; /* impede DCE do loop nos dois lados, igualmente */

  for (int t = 0; t < TRIALS; ++t) {
    acc = 1u << 16;
    double t0 = now_ms();
    for (uint32_t i = 0; i < ITERS; ++i) acc = c_fraf_step_noinline(acc);
    sink = acc;
    c_times[t] = now_ms() - t0;

    acc = 1u << 16;
    double t1 = now_ms();
    for (uint32_t i = 0; i < ITERS; ++i) acc = vos_fraf_step_av7(acc);
    sink = acc;
    a_times[t] = now_ms() - t1;
  }
  (void)sink;

  qsort(c_times, TRIALS, sizeof(double), cmp_d);
  qsort(a_times, TRIALS, sizeof(double), cmp_d);
  double c_med = c_times[TRIALS/2], a_med = a_times[TRIALS/2];

  printf("N=%d trials, ITERS=%u cada\n", TRIALS, ITERS);
  printf("C   mediana: %.3fms  (min=%.3f max=%.3f)\n", c_med, c_times[0], c_times[TRIALS-1]);
  printf("ASM mediana: %.3fms  (min=%.3f max=%.3f)\n", a_med, a_times[0], a_times[TRIALS-1]);
  printf("razao mediana C/ASM: %.4fx\n", c_med/a_med);
  return 0;
}
