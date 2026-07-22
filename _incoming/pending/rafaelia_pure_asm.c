// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
#define TORUS_LEN 1024
#define SAMPLES 14

// Definições manuais (sem .h) para evitar fricção de headers
typedef struct {
    float v[TORUS_LEN] __attribute__((aligned(16)));
    long long ns;
} rafaelia_t;

// Syscall write manual para ARM32 (r7=4)
static void sys_write(const char* s, int len) {
    __asm__ volatile (
        "mov r0, #1 \n\t"
        "mov r1, %[buf] \n\t"
        "mov r2, %[len] \n\t"
        "mov r7, #4 \n\t"
        "svc #0 \n\t"
        : : [buf] "r" (s), [len] "r" (len) : "r0", "r1", "r2", "r7"
    );
}

void _start() {
    static rafaelia_t p, in, o;
    float a = 0.25f, ia = 0.75f, s = 1.4012f;
    
    // Warmup e inicialização manual
    for(int k=0; k<TORUS_LEN; k++) { p.v[k] = 1.0f; in.v[k] = 0.5f; }

    sys_write("INICIANDO 14 AMOSTRAS NEON...\n", 30);

    for(int step = 0; step < SAMPLES; step++) {
        float *ptr_p = p.v;
        float *ptr_i = in.v;
        float *ptr_o = o.v;
        int count = TORUS_LEN;

        // Início da medição (instrução de barreira para evitar reordenação)
        __asm__ volatile ("" ::: "memory");

        __asm__ volatile (
            "vdup.32 q8, %[a]            \n\t" 
            "vdup.32 q9, %[ia]           \n\t" 
            "vdup.32 q10, %[s]           \n\t" 
            "1:                          \n\t"
            "vld1.32 {q0, q1}, [%[pp]]!  \n\t" 
            "vld1.32 {q2, q3}, [%[pi]]!  \n\t" 
            "vmul.f32 q4, q0, q9         \n\t" 
            "vmul.f32 q5, q2, q10        \n\t" 
            "vmla.f32 q4, q5, q8         \n\t" 
            "vmul.f32 q6, q1, q9         \n\t" 
            "vmul.f32 q7, q3, q10        \n\t" 
            "vmla.f32 q6, q7, q8         \n\t" 
            "vst1.32 {q4, q5}, [%[po]]!  \n\t" 
            "subs %[count], %[count], #8 \n\t" 
            "bne 1b                      \n\t" 
            : [pp] "+r" (ptr_p), [pi] "+r" (ptr_i), [po] "+r" (ptr_o), [count] "+r" (count)
            : [a] "r" (a), [ia] "r" (ia), [s] "r" (s)
            : "q0","q1","q2","q3","q4","q5","q6","q7","q8","q9","q10","cc","memory"
        );

        // Feedback visual minimalista (um ponto por amostra)
        sys_write(".", 1);
    }

    sys_write("\nCONCLUIDO.\n", 12);

    // Syscall exit (r7=1)
    __asm__ volatile ("mov r0, #0 \n\t mov r7, #1 \n\t svc #0");
}
