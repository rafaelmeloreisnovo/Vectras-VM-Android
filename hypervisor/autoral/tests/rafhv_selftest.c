/* SPDX-License-Identifier: GPL-2.0-only */
/* Hosted harness ONLY; it is not linked into the freestanding object. */
#include "../rafhv.h"
#include <stdio.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #x); return 1; } } while (0)

struct backend { uint32_t calls; uint32_t target; int failure; };
static int mock_enter(void *ctx, uint64_t *pc, struct rafhv_exit *event) {
    struct backend *b = (struct backend *)ctx;
    b->calls++;
    (*pc)++;
    if (b->failure) return -1;
    if (b->calls == b->target) {
        event->reason = RAFHV_EXIT_HALT;
        event->detail = 7u;
    }
    return 0;
}
static int mock_io(void *ctx, uint64_t *pc, struct rafhv_exit *event) {
    (void)ctx; (void)pc;
    event->reason = RAFHV_EXIT_IO;
    event->detail = 8u;
    return 0;
}
static int mock_invalid(void *ctx, uint64_t *pc, struct rafhv_exit *event) {
    (void)ctx; (void)pc;
    event->reason = 99u;
    return 0;
}
int main(void) {
    struct rafhv_vm v;
    uint8_t ram[16] = {0};
    uint8_t rom[8] = {0};
    uint8_t out = 0u;
    struct backend b = {0u, 3u, 0};
    int tests = 0;
    CHECK(rafhv_init((struct rafhv_vm *)0) == RAFHV_INVALID); tests++;
    CHECK(rafhv_init(&v) == RAFHV_OK && v.state == RAFHV_READY); tests++;
    CHECK(rafhv_map(&v, 0x1000u, 16u, ram, RAFHV_MEM_R | RAFHV_MEM_W) == RAFHV_OK); tests++;
    CHECK(rafhv_map(&v, 0x1008u, 8u, rom, RAFHV_MEM_R) == RAFHV_OVERLAP); tests++;
    CHECK(rafhv_map(&v, 0x1010u, 8u, rom, RAFHV_MEM_R) == RAFHV_OK); tests++;
    CHECK(rafhv_map(&v, UINT64_MAX - 3u, 8u, rom, RAFHV_MEM_R) == RAFHV_RANGE); tests++;
    CHECK(rafhv_map(&v, 0x3000u, 8u, rom, RAFHV_MEM_W) == RAFHV_INVALID); tests++;
    CHECK(rafhv_map(&v, 0x3000u, 8u, rom, 0x100u) == RAFHV_INVALID); tests++;
    CHECK(rafhv_write8(&v, 0x100fu, 0x5au) == RAFHV_OK); tests++;
    CHECK(rafhv_read8(&v, 0x100fu, &out) == RAFHV_OK && out == 0x5au); tests++;
    CHECK(rafhv_read8(&v, 0x1018u, &out) == RAFHV_RANGE); tests++;
    CHECK(rafhv_write8(&v, 0x1010u, 42u) == RAFHV_PERMISSION); tests++;
    CHECK(rafhv_run(&v, mock_enter, &b, 0u) == RAFHV_INVALID && v.state == RAFHV_READY); tests++;
    CHECK(rafhv_run(&v, mock_enter, &b, RAFHV_MAX_STEPS+1u) == RAFHV_INVALID); tests++;
    CHECK(rafhv_run(&v, mock_enter, &b, 2u) == RAFHV_OK && v.state == RAFHV_PAUSED && v.steps == 2u && v.last_exit.reason == RAFHV_EXIT_BUDGET); tests++;
    CHECK(rafhv_map(&v, 0x4000u, 8u, rom, RAFHV_MEM_R) == RAFHV_STATE); tests++;
    CHECK(rafhv_run(&v, mock_enter, &b, 3u) == RAFHV_OK && v.state == RAFHV_HALTED && v.last_exit.reason == RAFHV_EXIT_HALT && v.last_exit.detail == 7u); tests++;
    CHECK(rafhv_run(&v, mock_enter, &b, 1u) == RAFHV_STATE); tests++;
    CHECK(rafhv_reset(&v) == RAFHV_OK && v.region_count == 0u && ram[15] == 0x5au); tests++;
    CHECK(rafhv_run(&v, mock_io, 0, 1u) == RAFHV_OK && v.state == RAFHV_PAUSED && v.last_exit.reason == RAFHV_EXIT_IO); tests++;
    CHECK(rafhv_run(&v, mock_invalid, 0, 1u) == RAFHV_BACKEND && v.state == RAFHV_FAULTED); tests++;
    CHECK(rafhv_reset(&v) == RAFHV_OK); tests++;
    b.failure=1;
    CHECK(rafhv_run(&v, mock_enter, &b, 1u) == RAFHV_BACKEND && v.state == RAFHV_FAULTED && v.pc == 0u); tests++;
    printf("RAFHV V0.1 PASS_SCOPED %d assertions (hosted harness; no VM execution)\n", tests);
    return 0;
}
