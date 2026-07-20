// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* rmr_baremetal_compat.c — Instância do arena de memória baremetal */
#define RMR_BAREMETAL_COMPAT_IMPL
#include "rmr_baremetal_compat.h"

/* Arena estático: 1MB alinhado a 64 bytes (cache line) */
__attribute__((aligned(64)))
uint8_t  rmr_arena[RMR_ARENA_SIZE];
uint32_t rmr_arena_ptr = 0u;

void rmr_baremetal_arena_reset(void) {
    rmr_arena_ptr = 0u;
    /* Não zera: performance — kernel_main deve zerar BSS inteiro */
}

uint32_t rmr_baremetal_arena_used(void) {
    return rmr_arena_ptr;
}


#ifdef RMR_USE_ISORF_ALLOCATOR
RmR_ISOraf_Store g_isorf_store;
RmR_ISOraf_Page g_isorf_pages[65536u];
uint64_t g_isorf_data[262144u];

void rmr_isorf_allocator_init(void) {
    RmR_ISOraf_Init(&g_isorf_store,
                    g_isorf_pages, 65536u,
                    g_isorf_data, 262144u,
                    4096u);
}
#endif
