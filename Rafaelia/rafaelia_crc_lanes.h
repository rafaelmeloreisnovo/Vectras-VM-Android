/*
 * rafaelia_crc_lanes.h — CRC-LANES 2→64 para endereçamento serial/paralelo
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Objetivo:
 *   Manter CRC32C Castagnoli como verificador padrão, mas adicionar uma
 *   camada de codificação por largura variável (2..64 bits) para usar CRC
 *   como instrução de lane/endereço: par/ímpar, serial/paralelo, DB9/DB25,
 *   pinos lógicos e roteamento de buffer.
 *
 * Contrato:
 *   - Header-only, C99, sem malloc, sem libc obrigatória além de stdint/stddef.
 *   - Nenhuma alocação no hot path.
 *   - CRC32C permanece disponível como lane width=32 com poly 0x82F63B78.
 *   - CRC-LANES não substitui hash criptográfico; é sensor/instrução de
 *     integridade e roteamento geométrico.
 */
#pragma once
#ifndef RAFAELIA_CRC_LANES_H
#define RAFAELIA_CRC_LANES_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RAF_CRC_LANE_MIN_WIDTH 2u
#define RAF_CRC_LANE_MAX_WIDTH 64u

#define RAF_PORT_MODE_SERIAL   1u
#define RAF_PORT_MODE_PARALLEL 2u
#define RAF_PORT_DB9_PINS      9u
#define RAF_PORT_DB25_PINS     25u

/* Polinômios refletidos para loop LSB-first. */
#define RAF_CRC32C_REFLECTED_POLY 0x82F63B78ULL
#define RAF_CRC64_REFLECTED_POLY  0xC96C5795D7870F42ULL

typedef struct raf_crc_lane_cfg {
    uint8_t  width;   /* 2..64 */
    uint64_t poly;    /* refletido, sem o termo x^width */
    uint64_t init;
    uint64_t xorout;
} raf_crc_lane_cfg;

typedef struct raf_crc_lane_instr {
    uint8_t  pin;      /* pino lógico 1..9 / 1..25 / 1..N */
    uint8_t  pins;     /* cardinalidade da porta lógica */
    uint8_t  mode;     /* RAF_PORT_MODE_SERIAL ou RAF_PORT_MODE_PARALLEL */
    uint8_t  parity;   /* endereço par=0, ímpar=1 */
    uint8_t  width;    /* largura CRC selecionada */
    uint64_t addr;     /* endereço lógico observado */
    uint64_t crc;      /* valor codificado já mascarado */
} raf_crc_lane_instr;

static inline uint64_t raf_crc_lane_mask(uint8_t width) {
    if (width >= 64u) return 0xFFFFFFFFFFFFFFFFULL;
    if (width < RAF_CRC_LANE_MIN_WIDTH) width = RAF_CRC_LANE_MIN_WIDTH;
    return (1ULL << width) - 1ULL;
}

static inline uint8_t raf_crc_lane_width_valid(uint8_t width) {
    return (uint8_t)(width >= RAF_CRC_LANE_MIN_WIDTH && width <= RAF_CRC_LANE_MAX_WIDTH);
}

static inline uint64_t raf_crc_lane_default_poly(uint8_t width) {
    uint64_t mask = raf_crc_lane_mask(width);

    /* Casos clássicos úteis no barramento lógico. */
    if (width == 8u)  return 0x8CULL & mask;       /* CRC-8 Dallas/Maxim refletido */
    if (width == 16u) return 0xA001ULL & mask;     /* CRC-16/IBM refletido */
    if (width == 32u) return RAF_CRC32C_REFLECTED_POLY & mask;
    if (width == 64u) return RAF_CRC64_REFLECTED_POLY;

    /* Para lanes não padronizadas: dobra o CRC64 refletido para a largura. */
    return (RAF_CRC64_REFLECTED_POLY ^ (RAF_CRC32C_REFLECTED_POLY << 1u) ^ (uint64_t)width) & mask;
}

static inline raf_crc_lane_cfg raf_crc_lane_default_cfg(uint8_t width) {
    raf_crc_lane_cfg cfg;
    if (!raf_crc_lane_width_valid(width)) width = 32u;
    cfg.width  = width;
    cfg.poly   = raf_crc_lane_default_poly(width);
    cfg.init   = raf_crc_lane_mask(width);
    cfg.xorout = raf_crc_lane_mask(width);
    return cfg;
}

static inline uint64_t raf_crc_lane_step(uint64_t crc, uint8_t byte, const raf_crc_lane_cfg *cfg) {
    uint8_t width = (cfg && raf_crc_lane_width_valid(cfg->width)) ? cfg->width : 32u;
    uint64_t mask = raf_crc_lane_mask(width);
    uint64_t poly = cfg ? (cfg->poly & mask) : (RAF_CRC32C_REFLECTED_POLY & mask);

    crc = (crc ^ (uint64_t)byte) & mask;
    for (uint8_t i = 0u; i < 8u; i++) {
        uint64_t bit = crc & 1ULL;
        crc >>= 1u;
        if (bit) crc ^= poly;
        crc &= mask;
    }
    return crc & mask;
}

static inline uint64_t raf_crc_lane_run(const void *data, size_t len, const raf_crc_lane_cfg *cfg) {
    const uint8_t *p = (const uint8_t *)data;
    uint8_t width = (cfg && raf_crc_lane_width_valid(cfg->width)) ? cfg->width : 32u;
    uint64_t mask = raf_crc_lane_mask(width);
    uint64_t crc = cfg ? (cfg->init & mask) : mask;
    uint64_t xorout = cfg ? (cfg->xorout & mask) : mask;

    if (!p && len) return 0u;
    for (size_t i = 0u; i < len; i++) crc = raf_crc_lane_step(crc, p[i], cfg);
    return (crc ^ xorout) & mask;
}

/*
 * Mapeia pino lógico para largura CRC 2..64.
 * Serial:   DB9  pinos 1..9  → widths 2..10; DB25 → 2..26.
 * Paralelo: desloca uma página de pinos, separando lane física e lane lógica.
 */
static inline uint8_t raf_crc_lane_pin_width(uint8_t pin, uint8_t pins, uint8_t mode) {
    uint16_t base;
    if (pins == 0u || pin == 0u || pin > pins) return 0u;
    base = (uint16_t)pin;
    if (mode == RAF_PORT_MODE_PARALLEL) base = (uint16_t)(base + pins);
    return (uint8_t)(RAF_CRC_LANE_MIN_WIDTH + ((base - 1u) % 63u));
}

static inline uint8_t raf_crc_lane_pin_from_addr(uint64_t addr, uint8_t pins) {
    if (pins == 0u) return 0u;
    return (uint8_t)((addr % (uint64_t)pins) + 1u);
}

static inline uint8_t raf_crc_lane_parity_from_addr(uint64_t addr) {
    return (uint8_t)(addr & 1ULL);
}

/*
 * Instrutor de CRC-lane: endereço → pin/paridade/width → CRC variável.
 * A paridade entra no seed para separar endereço par e ímpar sem branch pesado.
 */
static inline raf_crc_lane_instr raf_crc_lane_encode_addr(
    const void *data,
    size_t len,
    uint64_t addr,
    uint8_t pins,
    uint8_t mode
) {
    raf_crc_lane_instr out;
    uint8_t pin = raf_crc_lane_pin_from_addr(addr, pins);
    uint8_t width = raf_crc_lane_pin_width(pin, pins, mode);
    uint8_t parity = raf_crc_lane_parity_from_addr(addr);
    raf_crc_lane_cfg cfg;
    uint64_t mask;
    uint64_t phase;

    if (!width) width = 32u;
    cfg = raf_crc_lane_default_cfg(width);
    mask = raf_crc_lane_mask(width);

    /* endereço/paridade como fase explícita: par/ímpar vira instrução. */
    phase = ((addr << 1u) ^ (uint64_t)parity ^ ((uint64_t)pin << 8u) ^ ((uint64_t)mode << 16u)) & mask;
    cfg.init ^= phase;

    out.pin = pin;
    out.pins = pins;
    out.mode = mode;
    out.parity = parity;
    out.width = width;
    out.addr = addr;
    out.crc = raf_crc_lane_run(data, len, &cfg);
    return out;
}

static inline raf_crc_lane_instr raf_crc_lane_db9_serial(const void *data, size_t len, uint64_t addr) {
    return raf_crc_lane_encode_addr(data, len, addr, RAF_PORT_DB9_PINS, RAF_PORT_MODE_SERIAL);
}

static inline raf_crc_lane_instr raf_crc_lane_db25_parallel(const void *data, size_t len, uint64_t addr) {
    return raf_crc_lane_encode_addr(data, len, addr, RAF_PORT_DB25_PINS, RAF_PORT_MODE_PARALLEL);
}

#ifdef __cplusplus
}
#endif

#endif /* RAFAELIA_CRC_LANES_H */
