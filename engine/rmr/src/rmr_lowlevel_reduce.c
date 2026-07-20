// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_lowlevel.h"

uint32_t rmr_lowlevel_reduce_xor(const uint8_t* data, size_t len) {
    if (!data || len == 0u) return 0u;

    const uint8_t* p = data;
    const uint8_t* const end4 = data + (len & ~(size_t)3u);
    uint32_t acc = 0u;

    while (p < end4) {
        /*
         * Unroll fixo de 4 bytes: mantém o padrão de lanes (i&3) e
         * reduz branches/controle de laço em hot path JNI.
         */
        acc ^= (uint32_t)p[0];
        acc = rmr_lowlevel_rotl32(acc, 3u);

        acc ^= (uint32_t)p[1] << 8u;
        acc = rmr_lowlevel_rotl32(acc, 3u);

        acc ^= (uint32_t)p[2] << 16u;
        acc = rmr_lowlevel_rotl32(acc, 3u);

        acc ^= (uint32_t)p[3] << 24u;
        acc = rmr_lowlevel_rotl32(acc, 3u);

        p += 4;
    }

    while (p < data + len) {
        const uint32_t shift = (uint32_t)((size_t)(p - data) & 3u) * 8u;
        acc ^= (uint32_t)(*p) << shift;
        acc = rmr_lowlevel_rotl32(acc, 3u);
        ++p;
    }

    return acc;
}

uint32_t rmr_lowlevel_checksum32(const uint8_t* data, size_t len, uint32_t seed) {
    if (!data || len == 0u) return seed;

    uint32_t state = seed ^ 0xA5A5A5A5u;
    const uint8_t* p = data;
    const uint8_t* const end4 = data + (len & ~(size_t)3u);
    uint32_t i = 0u;

    while (p < end4) {
        state ^= (uint32_t)p[0] + 0x9Eu + (i + 0u);
        state = rmr_lowlevel_rotl32(state, 7u);
        state *= 0x45D9F3Bu;

        state ^= (uint32_t)p[1] + 0x9Eu + (i + 1u);
        state = rmr_lowlevel_rotl32(state, 7u);
        state *= 0x45D9F3Bu;

        state ^= (uint32_t)p[2] + 0x9Eu + (i + 2u);
        state = rmr_lowlevel_rotl32(state, 7u);
        state *= 0x45D9F3Bu;

        state ^= (uint32_t)p[3] + 0x9Eu + (i + 3u);
        state = rmr_lowlevel_rotl32(state, 7u);
        state *= 0x45D9F3Bu;

        p += 4;
        i += 4u;
    }

    while (p < data + len) {
        state ^= (uint32_t)(*p) + 0x9Eu + i;
        state = rmr_lowlevel_rotl32(state, 7u);
        state *= 0x45D9F3Bu;
        ++p;
        ++i;
    }

    return state ^ (uint32_t)len;
}
