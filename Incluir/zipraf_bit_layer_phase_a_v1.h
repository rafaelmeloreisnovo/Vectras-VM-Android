/*
 * Vectras ZIPRAF Bit Layer Phase-A adapter V1.
 *
 * Independent consumer of RafPolimata canonical Phase-A vectors:
 *   merge d52afbc38acf6d9580b32cbf9f7f259fa4afdf4b
 *   vector blob 4c3ba2202afb6f62bb465d435273031ebb16c3b3
 *
 * Geometry-independent only. M and G(M) remain unbound.
 * No includes. No libc. No heap. No syscall.
 */
#ifndef VECTRAS_ZIPRAF_BIT_LAYER_PHASE_A_V1_H
#define VECTRAS_ZIPRAF_BIT_LAYER_PHASE_A_V1_H 1

typedef unsigned char vza_u8;
typedef unsigned int  vza_u32;
typedef signed int    vza_i32;

typedef char vza_assert_u8[(sizeof(vza_u8) == 1u) ? 1 : -1];
typedef char vza_assert_u32[(sizeof(vza_u32) == 4u) ? 1 : -1];

enum {
    VZA_OK = 0,
    VZA_E_ARG = -1,
    VZA_E_VERSION = -2,
    VZA_E_WIDTH = -3,
    VZA_E_Q = -4,
    VZA_E_LAYER = -5,
    VZA_E_CONFLICT = -6,
    VZA_E_INCOMPLETE = -7
};

typedef struct {
    vza_u8 value;
    vza_u8 seen;
    vza_u8 conflict;
    vza_u8 reserved;
} VzaAccumulatorV1;

static inline vza_i32 vza_header_ok(vza_u32 version, vza_u32 width, vza_u8 q)
{
    if (version != 1u) return VZA_E_VERSION;
    if (!(width == 30u || width == 60u || width == 120u)) return VZA_E_WIDTH;
    if (q == 0u || q > 8u) return VZA_E_Q;
    return VZA_OK;
}

static inline vza_u8 vza_qmask(vza_u8 q)
{
    return (q >= 1u && q <= 8u)
        ? (vza_u8)(0xffu & (0xffu << (8u - (vza_u32)q)))
        : 0u;
}

static inline vza_i32 vza_split(vza_u8 value, vza_u8 planes[8])
{
    vza_u32 k;
    if (!planes) return VZA_E_ARG;
    for (k = 0u; k != 8u; ++k)
        planes[k] = (vza_u8)((value & (vza_u8)(1u << k)) != 0u);
    return VZA_OK;
}

static inline vza_i32 vza_join(const vza_u8 planes[8], vza_u8 *out)
{
    vza_u32 k;
    vza_u8 value = 0u;
    if (!planes || !out) return VZA_E_ARG;
    for (k = 0u; k != 8u; ++k) {
        if (planes[k] > 1u) return VZA_E_LAYER;
        if (planes[k] != 0u)
            value = (vza_u8)(value | (vza_u8)(1u << k));
    }
    *out = value;
    return VZA_OK;
}

static inline vza_i32 vza_join_q(const vza_u8 planes[8], vza_u8 q, vza_u8 *out)
{
    vza_u8 value;
    vza_i32 rc;
    if (!out) return VZA_E_ARG;
    if (q == 0u || q > 8u) return VZA_E_Q;
    rc = vza_join(planes, &value);
    if (rc != VZA_OK) return rc;
    *out = (vza_u8)(value & vza_qmask(q));
    return VZA_OK;
}

static inline void vza_acc_reset(VzaAccumulatorV1 *a)
{
    if (!a) return;
    a->value = 0u;
    a->seen = 0u;
    a->conflict = 0u;
    a->reserved = 0u;
}

static inline vza_i32 vza_acc_feed(VzaAccumulatorV1 *a, vza_u8 layer, vza_u8 bit)
{
    vza_u8 mask;
    if (!a) return VZA_E_ARG;
    if (layer > 7u || bit > 1u) return VZA_E_LAYER;
    mask = (vza_u8)(1u << layer);

    if ((a->seen & mask) != 0u) {
        vza_u8 prior = (vza_u8)((a->value & mask) != 0u);
        if (prior != bit) {
            a->conflict = (vza_u8)(a->conflict | mask);
            return VZA_E_CONFLICT;
        }
        return VZA_OK;
    }

    a->seen = (vza_u8)(a->seen | mask);
    if (bit != 0u) a->value = (vza_u8)(a->value | mask);
    return VZA_OK;
}

static inline vza_i32 vza_acc_result(
    const VzaAccumulatorV1 *a, vza_u8 required, vza_u8 *out)
{
    if (!a || !out) return VZA_E_ARG;
    if (a->conflict != 0u) return VZA_E_CONFLICT;
    if ((a->seen & required) != required) return VZA_E_INCOMPLETE;
    *out = (vza_u8)(a->value & required);
    return VZA_OK;
}

#endif
