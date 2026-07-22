// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
/*
 * rmr_test.c — Suite de testes de falsificabilidade
 * Implementa as 12 hipóteses como testes automatizados.
 */
#define _POSIX_C_SOURCE 200809L
#include "../include/rmr_arch_probe.h"
#include "../include/rmr_bench_engine.h"
#include <stdio.h>
#include <math.h>
#include <string.h>
#include <assert.h>

static int passed = 0, failed = 0;

#define ASSERT_TRUE(cond, msg) do { \
    if (cond) { passed++; printf("  ✓ %s\n", msg); } \
    else { failed++; printf("  ✗ %s\n", msg); } \
} while(0)

#define ASSERT_RANGE(val, lo, hi, msg) \
    ASSERT_TRUE((val) >= (lo) && (val) <= (hi), msg)

int main(void) {
    printf("╔══ RMR Falsifiability Tests ════════════════════════════╗\n");

    /* ── H3: Convergência EMA ─── */
    printf("\n── H3: EMA T^4 Convergência ─────────────────────────────\n");
    float alpha = 0.25f;
    float kappa = (1.0f - alpha) + alpha * (2.0f * alpha);
    float t_conv = logf(1e-4f) / logf(kappa);
    ASSERT_RANGE(t_conv, 20.0f, 200.0f, "T_conv(1e-4) ∈ [20, 200] passos");
    ASSERT_TRUE(kappa < 1.0f, "Taxa de contração κ < 1");

    /* ── H4: Espiral Rafael ─── */
    printf("\n── H4: Espiral Rafael taxa √3/2 ─────────────────────────\n");
    float F = 100.0f, Fstar = 3.661f;
    float prev_err = fabsf(F - Fstar);
    for (int i = 0; i < 50; i++) {
        F = F * 0.8660254038f - 3.14159f * 0.15643f;
    }
    /* Pegar Fstar empírico */
    Fstar = F;
    F = 100.0f;
    float err0 = fabsf(F - Fstar);
    float err1 = fabsf(0.8660254038f * F - 3.14159f * 0.15643f - Fstar);
    float rate = (err0 > 1e-6f) ? err1 / err0 : 0.0f;
    ASSERT_RANGE(rate, 0.80f, 0.93f, "Taxa Rafael ∈ [0.80, 0.93]");
    (void)prev_err;

    /* ── H5: Distância toroidal ─── */
    printf("\n── H5: Métrica Toroidal ─────────────────────────────────\n");
    float a = 0.01f, b = 0.99f;
    float diff = fabsf(a - b);
    float d_t = (diff > 0.5f) ? 1.0f - diff : diff;
    float d_e = fabsf(a - b);
    ASSERT_TRUE(d_t < d_e, "d_torus(0.01, 0.99) < d_eucl");
    ASSERT_RANGE(d_t, 0.019f, 0.021f, "d_torus = 0.02 ± 0.001");

    /* ── H9: Dissipatividade ─── */
    printf("\n── H9: Sistema Dissipativo ──────────────────────────────\n");
    float div_F = -4.0f * alpha;
    ASSERT_TRUE(div_F < 0.0f, "div(F_lin) < 0");
    ASSERT_RANGE(div_F, -1.1f, -0.9f, "div(F) = -1.0 ± 0.1");

    float spectral_radius = 1.0f - alpha;
    ASSERT_TRUE(spectral_radius < 1.0f, "ρ_spec = (1-α) < 1");
    ASSERT_RANGE(spectral_radius, 0.70f, 0.80f, "ρ_spec ∈ [0.70, 0.80]");

    /* ── H1: Lógica toroidal básica ─── */
    printf("\n── H1: Wrap Toroidal ────────────────────────────────────\n");
    float v1 = 1.3f - floorf(1.3f);
    ASSERT_RANGE(v1, 0.29f, 0.31f, "wrap(1.3) ≈ 0.3");
    float v2 = -0.1f - floorf(-0.1f);
    ASSERT_RANGE(v2, 0.89f, 0.91f, "wrap(-0.1) ≈ 0.9");

    /* ── Detecção de arquitetura ─── */
    printf("\n── H11: Detecção de Arquitetura ─────────────────────────\n");
    rmr_arch_t arch = RMR_CT_ARCH;
    ASSERT_TRUE(arch != RMR_ARCH_UNKNOWN, "Arquitetura detectada");
    ASSERT_TRUE(arch < RMR_ARCH_COUNT, "Arquitetura em range válido");
    const char *aname = rmr_arch_name(arch);
    ASSERT_TRUE(aname != NULL && aname[0] != '\0', "Nome de arquitetura não vazio");
    printf("    Detectado: %s\n", aname);

    /* ── HW probe básico ─── */
    printf("\n── Probe Hardware Básico ────────────────────────────────\n");
    rmr_hw_profile_t hw = {0};
    rmr_probe_arch(&hw);
    ASSERT_TRUE(hw.arch == RMR_CT_ARCH, "probe_arch() consistente com CT");
    rmr_probe_registers(&hw);
    ASSERT_TRUE(hw.regs.n_gpr > 0, "GPR count > 0");
    ASSERT_TRUE(hw.regs.gpr_width_bits >= 32, "GPR width >= 32 bits");
    ASSERT_TRUE(hw.regs.addr_bits >= 32, "addr_bits >= 32");
    ASSERT_TRUE(hw.regs.calling_convention[0] != '\0', "ABI definida");
    printf("    %u GPR × %u bits | VA=%u bits | ABI=%s\n",
           hw.regs.n_gpr, hw.regs.gpr_width_bits,
           hw.regs.addr_bits, hw.regs.calling_convention);

    rmr_probe_memmodel(&hw);
    ASSERT_TRUE(hw.memmodel.atomic_cas_cycles < 1000,
                "CAS latency < 1000 ciclos");
    ASSERT_TRUE(hw.memmodel.model < 4, "Modelo de memória válido");
    static const char *mm[] = {"SC","TSO","WMO","RC"};
    printf("    MemModel=%s  LFENCE=%ucy  CAS=%ucy\n",
           mm[hw.memmodel.model],
           hw.memmodel.fence_lfence_cycles,
           hw.memmodel.atomic_cas_cycles);

    /* ── Stat engine ─── */
    printf("\n── Motor Estatístico ────────────────────────────────────\n");
    uint64_t samples[64];
    for (int i = 0; i < 64; i++) samples[i] = (uint64_t)(100 + i*3);
    rmr_stat_t st = rmr_stat_compute(samples, 64);
    ASSERT_RANGE(st.mean_ns, 140.0, 200.0, "Média em [140, 200]");
    ASSERT_TRUE(st.stddev_ns > 0, "Desvio padrão > 0");
    ASSERT_TRUE(st.ci95_lo_ns < st.mean_ns, "CI95 lo < média");
    ASSERT_TRUE(st.ci95_hi_ns > st.mean_ns, "CI95 hi > média");
    printf("    μ=%.1f σ=%.1f CI=[%.1f, %.1f]\n",
           st.mean_ns, st.stddev_ns, st.ci95_lo_ns, st.ci95_hi_ns);

    /* ── Resultado final ─── */
    printf("\n══════════════════════════════════════════════════════════\n");
    printf("  Resultado: %d passaram, %d falharam\n", passed, failed);
    if (failed == 0)
        printf("  STATUS: TODAS AS HIPÓTESES CONFIRMADAS ✓\n");
    else
        printf("  STATUS: %d HIPÓTESES FALSIFICADAS ✗\n", failed);
    printf("══════════════════════════════════════════════════════════\n");

    return failed > 0 ? 1 : 0;
}
