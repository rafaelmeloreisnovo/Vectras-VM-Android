/* Hosted test harness; production header remains freestanding and heap-free. */
#include "../../app/src/main/cpp/core/authorial/vectra_vec2_pack.h"

static int check(vectra_vec2_i32 x, vectra_vec2_i32 y,
                 vectra_vec2_u32 want) {
    return vectra_vec2_pack_bits(x, y) == want;
}

int main(void) {
    if (!check(0, 0, 0x00000000u) ||
        !check(-1, -1, 0xffffffffu) ||
        !check(32767, -32768, 0x80007fffu) ||
        !check(-32768, 32767, 0x7fff8000u) ||
        !check(-65536, -65536, 0x00000000u) ||
        !check(0, 32768, 0x80000000u) ||
        !check(32768, 0, 0x00008000u)) {
        return 1;
    }
    /* 65536 deterministic lane vectors; no randomness, time or external data. */
    for (vectra_vec2_u32 i = 0; i < 65536u; ++i) {
        vectra_vec2_i32 x = (vectra_vec2_i32)i - 32768;
        vectra_vec2_i32 y = 32767 - (vectra_vec2_i32)i;
        vectra_vec2_u32 word = vectra_vec2_pack_bits(x, y);
        if ((word & 0xffffu) != (((vectra_vec2_u32)x) & 0xffffu) ||
            ((word >> 16u) & 0xffffu) != (((vectra_vec2_u32)y) & 0xffffu)) {
            return 2;
        }
    }
    return 0;
}
