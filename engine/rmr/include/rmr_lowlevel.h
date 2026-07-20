// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#ifndef RMR_LOWLEVEL_H
#define RMR_LOWLEVEL_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

uint32_t rmr_lowlevel_rotl32(uint32_t v, uint32_t n);
uint32_t rmr_lowlevel_fold32(uint32_t a, uint32_t b, uint32_t c, uint32_t d);
/*
 * reduce_xor canonical contract:
 * - Processes bytes in input order.
 * - For byte i, XORs it into lane ((i & 3) * 8) of a 32-bit accumulator.
 * - Applies rotl32(acc, 3) after every byte.
 * - Returns 0 for NULL data or len == 0.
 *
 * This is intentionally not equivalent to a plain byte parity XOR.
 */
uint32_t rmr_lowlevel_reduce_xor(const uint8_t* data, size_t len);
uint32_t rmr_lowlevel_checksum32(const uint8_t* data, size_t len, uint32_t seed);

#ifdef __cplusplus
}
#endif

#endif
