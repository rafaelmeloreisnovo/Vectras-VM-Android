#include "zipraf_bit_layer_phase_a_v1.h"

static vza_i32 sample(vza_u8 value, vza_u8 e1, vza_u8 e2, vza_u8 e4, vza_u8 e8)
{
    vza_u8 p[8];
    vza_u8 out = 0u;
    if (vza_split(value, p) != VZA_OK) return 1;
    if (vza_join(p, &out) != VZA_OK || out != value) return 2;
    if (vza_join_q(p, 1u, &out) != VZA_OK || out != e1) return 3;
    if (vza_join_q(p, 2u, &out) != VZA_OK || out != e2) return 4;
    if (vza_join_q(p, 4u, &out) != VZA_OK || out != e4) return 5;
    if (vza_join_q(p, 8u, &out) != VZA_OK || out != e8) return 6;
    return 0;
}

static vza_i32 order_test(const vza_u8 order[8])
{
    static const vza_u8 p[8] = {1u,0u,1u,0u,0u,1u,0u,1u};
    VzaAccumulatorV1 a;
    vza_u8 out = 0u;
    vza_u32 i;
    vza_acc_reset(&a);
    for (i = 0u; i < 8u; ++i) {
        const vza_u8 k = order[i];
        if (vza_acc_feed(&a, k, p[k]) != VZA_OK) return 1;
    }
    if (vza_acc_result(&a, 0xffu, &out) != VZA_OK) return 2;
    return out == 165u ? 0 : 3;
}

vza_i32 vectras_zipraf_phase_a_selftest(void)
{
    static const vza_u8 oa[8] = {7u,0u,5u,2u,6u,1u,4u,3u};
    static const vza_u8 ob[8] = {0u,1u,2u,3u,4u,5u,6u,7u};
    VzaAccumulatorV1 a;
    vza_u8 out = 0u;
    vza_u32 v;
    vza_u32 q;

    if (vza_header_ok(1u,30u,8u) != VZA_OK) return 10;
    if (vza_header_ok(1u,60u,4u) != VZA_OK) return 11;
    if (vza_header_ok(1u,120u,1u) != VZA_OK) return 12;
    if (vza_header_ok(2u,30u,8u) != VZA_E_VERSION) return 13;
    if (vza_header_ok(1u,31u,8u) != VZA_E_WIDTH) return 14;
    if (vza_header_ok(1u,30u,0u) != VZA_E_Q) return 15;

    if (sample(0u,   0u,0u,0u,0u) != 0) return 20;
    if (sample(1u,   0u,0u,0u,1u) != 0) return 21;
    if (sample(85u,  0u,64u,80u,85u) != 0) return 22;
    if (sample(128u, 128u,128u,128u,128u) != 0) return 23;
    if (sample(165u, 128u,128u,160u,165u) != 0) return 24;
    if (sample(255u, 128u,192u,240u,255u) != 0) return 25;

    for (v = 0u; v <= 255u; ++v) {
        vza_u8 p[8];
        vza_u8 full = 0u;
        if (vza_split((vza_u8)v, p) != VZA_OK) return 26;
        if (vza_join(p, &full) != VZA_OK) return 27;
        if ((vza_u32)full != v) return 28;
        for (q = 1u; q <= 8u; ++q) {
            vza_u8 partial = 0u;
            if (vza_join_q(p, (vza_u8)q, &partial) != VZA_OK) return 29;
            if (partial != (vza_u8)(((vza_u8)v) & vza_qmask((vza_u8)q))) return 30;
        }
    }

    if (order_test(oa) != 0) return 31;
    if (order_test(ob) != 0) return 32;

    vza_acc_reset(&a);
    if (vza_acc_feed(&a,7u,1u) != VZA_OK) return 40;
    if (vza_acc_feed(&a,7u,1u) != VZA_OK) return 41;
    if (vza_acc_feed(&a,7u,0u) != VZA_E_CONFLICT) return 42;
    if (vza_acc_result(&a,0x80u,&out) != VZA_E_CONFLICT) return 43;

    vza_acc_reset(&a);
    if (vza_acc_feed(&a,7u,1u) != VZA_OK) return 44;
    if (vza_acc_result(&a,0xc0u,&out) != VZA_E_INCOMPLETE) return 45;

    return 0;
}

#ifdef VECTRAS_ZIPRAF_PHASE_A_HOSTED_MAIN
int main(void)
{
    return (int)vectras_zipraf_phase_a_selftest();
}
#endif
