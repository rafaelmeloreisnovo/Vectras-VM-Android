#include "omega_layersbit.h"

typedef struct {
    uint64_t pre;
    LayersBit state;
    uint64_t post;
} GuardedLayersBit;

int main(void) {
    GuardedLayersBit g;
    uint8_t *p = (uint8_t *)(void *)&g.state;
    g.pre = 0x1122334455667788ULL;
    g.post = 0x8877665544332211ULL;

    for (uint32_t i = 0u; i < (uint32_t)sizeof(g.state); i++) p[i] = 0xA5u;
    lb_zero(&g.state);

    if (g.pre != 0x1122334455667788ULL) return 1;
    if (g.post != 0x8877665544332211ULL) return 2;
    for (uint32_t i = 0u; i < (uint32_t)sizeof(g.state); i++) if (p[i] != 0u) return 3;
    if (sizeof(LayersBit) != 560u) return 4;

    /* Exhaust the complete lb_omega input domain: fold has 0..256 one-bits. */
    for (uint32_t ones = 0u; ones <= 256u; ones++) {
        for (uint32_t i = 0u; i < LB_LAYER_BYTES; i++) g.state.fold[i] = 0u;
        uint32_t full = ones >> 3u;
        uint32_t rem = ones & 7u;
        for (uint32_t i = 0u; i < full; i++) g.state.fold[i] = 0xFFu;
        if (rem != 0u && full < LB_LAYER_BYTES) {
            g.state.fold[full] = (uint8_t)((1u << rem) - 1u);
        }
        lb_omega(&g.state);
        if (g.state.omega != (ones % LB_ATTRACTORS)) return 5;
    }
    return 0;
}
