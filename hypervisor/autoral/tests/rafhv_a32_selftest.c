/* SPDX-License-Identifier: GPL-2.0-only */
/* Hosted fixture executes real encoded A32 instructions, still not a guest OS. */
#include "../rafhv_a32.h"
#include <stdio.h>
#define T(x) do { if (!(x)) { fprintf(stderr, "A32 FAIL %d: %s\n", __LINE__, #x); return 1; } tests++; } while (0)
int main(void) {
    uint8_t mem[128] = {0};
    struct rafhv_vm v;
    struct rafhv_a32_cpu cpu = {0};
    uint32_t i,j;
    int tests = 0;
    const uint32_t program[] = {
        UINT32_C(0xe3a00005), /* mov r0,#5 */
        UINT32_C(0xe2800007), /* add r0,r0,#7 */
        UINT32_C(0xe3a01040), /* mov r1,#64 */
        UINT32_C(0xe5810000), /* str r0,[r1] */
        UINT32_C(0xe5912000), /* ldr r2,[r1] */
        UINT32_C(0xef000001)  /* svc #1 => host-visible IO exit */
    };
    for (i=0u; i<6u; ++i)
        for (j=0u; j<4u; ++j)
            mem[4u*i+j] = (uint8_t)(program[i] >> (8u*j));
    T(rafhv_init(&v) == RAFHV_OK);
    T(rafhv_map(&v, 0, sizeof mem, mem, RAFHV_MEM_R|RAFHV_MEM_W|RAFHV_MEM_X) == RAFHV_OK);
    cpu.vm = &v;
    T(rafhv_run(&v,rafhv_a32_enter,&cpu,10u) == RAFHV_OK);
    T(v.state == RAFHV_PAUSED && v.last_exit.reason == RAFHV_EXIT_IO && v.last_exit.detail == 1u);
    T(v.pc == 24u && v.steps == 6u);
    T(cpu.r[0] == 12u && cpu.r[1] == 64u && cpu.r[2] == 12u);
    T(mem[64] == 12u && mem[65] == 0u && mem[66] == 0u && mem[67] == 0u);
    T(rafhv_reset(&v) == RAFHV_OK);
    T(rafhv_map(&v,0,sizeof mem,mem,RAFHV_MEM_R) == RAFHV_OK);
    T(rafhv_run(&v,rafhv_a32_enter,&cpu,1u) == RAFHV_OK && v.last_exit.reason == RAFHV_EXIT_FAULT && v.last_exit.detail == RAFHV_A32_FETCH);
    T(rafhv_reset(&v) == RAFHV_OK);
    T(rafhv_map(&v,0,sizeof mem,mem,RAFHV_MEM_R|RAFHV_MEM_X) == RAFHV_OK);
    v.pc=1u;
    T(rafhv_run(&v,rafhv_a32_enter,&cpu,1u) == RAFHV_OK && v.last_exit.reason == RAFHV_EXIT_FAULT && v.last_exit.detail == RAFHV_A32_BAD_PC);
    T(rafhv_reset(&v) == RAFHV_OK);
    T(rafhv_map(&v,0,sizeof mem,mem,RAFHV_MEM_R|RAFHV_MEM_X) == RAFHV_OK);
    v.pc=12u; /* STR r0,[r1] to readonly memory */
    T(rafhv_run(&v,rafhv_a32_enter,&cpu,1u) == RAFHV_OK && v.last_exit.reason == RAFHV_EXIT_FAULT && v.last_exit.detail == RAFHV_A32_MEMORY && mem[64] == 12u);
    mem[32] = 0xfeu; mem[33] = 0xffu; mem[34] = 0xffu; mem[35] = 0xeau; /* b . */
    T(rafhv_reset(&v) == RAFHV_OK);
    T(rafhv_map(&v,0,sizeof mem,mem,RAFHV_MEM_R|RAFHV_MEM_X) == RAFHV_OK);
    v.pc=32u;
    T(rafhv_run(&v,rafhv_a32_enter,&cpu,4u) == RAFHV_OK && v.last_exit.reason == RAFHV_EXIT_BUDGET && v.pc == 32u && v.steps == 4u);
    mem[3] = 0x13u; /* conditional movne unsupported -> fail closed */
    T(rafhv_reset(&v) == RAFHV_OK);
    T(rafhv_map(&v,0,sizeof mem,mem,RAFHV_MEM_R|RAFHV_MEM_X) == RAFHV_OK);
    T(rafhv_run(&v,rafhv_a32_enter,&cpu,1u) == RAFHV_OK && v.last_exit.reason == RAFHV_EXIT_FAULT && v.last_exit.detail == RAFHV_A32_UNSUPPORTED);
    printf("RAFHV A32 subset PASS_SCOPED %d assertions\n",tests);
    return 0;
}
