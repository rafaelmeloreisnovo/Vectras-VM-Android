// SPDX-License-Identifier: GPL-2.0-only
// SPDX-FileCopyrightText: Copyright (C) rafaelmeloreisnovo
#pragma once
#include <stddef.h>
#include <stdint.h>

static inline uint32_t rmr_crc32c_update(uint32_t crc, const uint8_t *p, size_t n) {
    while (n--) {
        crc ^= *p++;
        for (uint32_t i = 0u; i < 8u; ++i) {
            const uint32_t mask = (uint32_t)-(int32_t)(crc & 1u);
            crc = (crc >> 1u) ^ (0x82F63B78u & mask);
        }
    }
    return crc;
}
