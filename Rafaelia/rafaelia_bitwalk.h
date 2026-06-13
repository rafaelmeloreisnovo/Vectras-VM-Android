/*
 * rafaelia_bitwalk.h — operadores de caminhada/inversão na cadeia de bits
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Isto NAO e CRC.
 *
 * CRC continua sendo integridade. BITWALK e operador de visualizacao e direcao:
 *   - continua
 *   - volta
 *   - pula +1
 *   - pula -1
 *   - pula +2
 *   - pula -2
 *   - salta por camada/cor/ponto de vista
 *
 * Objetivo:
 *   Trabalhar a cadeia de bits sem necessariamente extrair/copiar os bits.
 *   O operador muda a posicao logica conforme layer, cor, ponto de vista e
 *   direcao. Assim uma mesma cadeia pode ser vista por mais camadas sem virar
 *   salto fixo de 32 bits.
 *
 * Hot path:
 *   - Header-only
 *   - Sem malloc
 *   - Sem dependencia externa
 *   - Apenas aritmetica inteira
 */
#pragma once
#ifndef RAFAELIA_BITWALK_H
#define RAFAELIA_BITWALK_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum raf_bitwalk_op {
    RAF_BW_CONTINUE = 0,  /* segue no mesmo sentido */
    RAF_BW_BACK     = 1,  /* volta/inverte direcao */
    RAF_BW_FWD1     = 2,  /* pula um para frente */
    RAF_BW_BACK1    = 3,  /* pula um para tras */
    RAF_BW_FWD2     = 4,  /* pula dois para frente */
    RAF_BW_BACK2    = 5,  /* pula dois para tras */
    RAF_BW_LAYER    = 6,  /* salto por layer/ponto de vista */
    RAF_BW_COLOR    = 7   /* salto por cor/camada cromatica */
} raf_bitwalk_op;

typedef struct raf_bitwalk_view {
    uint32_t bit_count;     /* tamanho da cadeia observada */
    uint32_t pos;           /* posicao atual */
    uint32_t layer;         /* layer logico */
    uint32_t color;         /* cor/canal logico */
    uint32_t viewpoint;     /* ponto de vista */
    uint32_t color_layers;  /* camadas por cor; pode passar de 760 */
} raf_bitwalk_view;

typedef struct raf_bitwalk_step_result {
    uint32_t prev_pos;
    uint32_t next_pos;
    uint32_t stride;
    uint32_t layer;
    uint32_t color;
    uint32_t viewpoint;
    raf_bitwalk_op op;
} raf_bitwalk_step_result;

static inline uint32_t raf_bw_mod(uint64_t v, uint32_t n) {
    return n ? (uint32_t)(v % (uint64_t)n) : 0u;
}

static inline uint32_t raf_bw_wrap_add(uint32_t pos, int32_t delta, uint32_t n) {
    uint64_t base;
    if (!n) return 0u;
    if (delta >= 0) return (uint32_t)(((uint64_t)pos + (uint64_t)(uint32_t)delta) % (uint64_t)n);
    base = (uint64_t)n + (uint64_t)pos;
    return (uint32_t)((base - (uint64_t)(uint32_t)(-delta % (int32_t)n)) % (uint64_t)n);
}

static inline uint32_t raf_bw_layer_stride(const raf_bitwalk_view *v) {
    uint32_t color_layers;
    uint32_t x;
    if (!v || !v->bit_count) return 0u;
    color_layers = v->color_layers ? v->color_layers : 760u;

    /* Mistura leve: nao e hash, e escolha de passo de visualizacao. */
    x = (v->layer * 3u) ^ (v->color * 5u) ^ (v->viewpoint * 7u) ^ color_layers;
    x = (x % v->bit_count) + 1u;
    return x;
}

static inline raf_bitwalk_op raf_bw_op_from_parity(uint8_t p0, uint8_t p1) {
    /* p0^p1=0 continua; p0^p1=1 volta. */
    return (raf_bitwalk_op)((p0 ^ p1) ? RAF_BW_BACK : RAF_BW_CONTINUE);
}

static inline raf_bitwalk_op raf_bw_op_from_small_code(uint32_t code) {
    /* Brincadeira operacional: 0 continua, 1 volta, 2/3 pulam 1, 4/5 pulam 2. */
    switch (code % 8u) {
        case 0u: return RAF_BW_CONTINUE;
        case 1u: return RAF_BW_BACK;
        case 2u: return RAF_BW_FWD1;
        case 3u: return RAF_BW_BACK1;
        case 4u: return RAF_BW_FWD2;
        case 5u: return RAF_BW_BACK2;
        case 6u: return RAF_BW_LAYER;
        default: return RAF_BW_COLOR;
    }
}

static inline raf_bitwalk_step_result raf_bw_step(raf_bitwalk_view v, raf_bitwalk_op op) {
    raf_bitwalk_step_result r;
    uint32_t stride = 1u;
    int32_t delta = 0;

    r.prev_pos = v.pos;
    r.layer = v.layer;
    r.color = v.color;
    r.viewpoint = v.viewpoint;
    r.op = op;

    switch (op) {
        case RAF_BW_CONTINUE: delta = 1; break;
        case RAF_BW_BACK:     delta = -1; break;
        case RAF_BW_FWD1:     delta = 2; break;
        case RAF_BW_BACK1:    delta = -2; break;
        case RAF_BW_FWD2:     delta = 3; break;
        case RAF_BW_BACK2:    delta = -3; break;
        case RAF_BW_LAYER:
            stride = raf_bw_layer_stride(&v);
            delta = (int32_t)stride;
            break;
        case RAF_BW_COLOR:
        default:
            stride = (v.color_layers ? v.color_layers : 760u);
            stride = (stride % (v.bit_count ? v.bit_count : 1u)) + 1u;
            delta = (int32_t)stride;
            break;
    }

    r.stride = stride;
    r.next_pos = raf_bw_wrap_add(v.pos, delta, v.bit_count);
    return r;
}

static inline raf_bitwalk_step_result raf_bw_step_from_code(raf_bitwalk_view v, uint32_t code) {
    return raf_bw_step(v, raf_bw_op_from_small_code(code));
}

static inline uint8_t raf_bw_peek_bit64(uint64_t word, uint32_t pos) {
    return (uint8_t)((word >> (pos & 63u)) & 1ULL);
}

static inline uint8_t raf_bw_peek_chain64(const uint64_t *chain, uint32_t words, uint32_t bit_pos) {
    uint32_t w;
    if (!chain || !words) return 0u;
    w = (bit_pos >> 6u) % words;
    return raf_bw_peek_bit64(chain[w], bit_pos);
}

#ifdef __cplusplus
}
#endif

#endif /* RAFAELIA_BITWALK_H */
