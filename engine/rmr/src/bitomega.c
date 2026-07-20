// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "bitomega.h"


static uint32_t clamp01_q16(uint32_t x) {
  if (x > BITOMEGA_Q16_ONE) return BITOMEGA_Q16_ONE;
  return x;
}

static uint32_t q16_mul(uint32_t a, uint32_t b) {
  return (uint32_t)(((uint64_t)a * (uint64_t)b) >> 16u);
}

const char *bitomega_state_name(bitomega_state_t s) {
  switch (s) {
    case BITOMEGA_NEG: return "NEG";
    case BITOMEGA_ZERO: return "ZERO";
    case BITOMEGA_POS: return "POS";
    case BITOMEGA_MIX: return "MIX";
    case BITOMEGA_VOID: return "VOID";
    case BITOMEGA_EDGE: return "EDGE";
    case BITOMEGA_FLOW: return "FLOW";
    case BITOMEGA_LOCK: return "LOCK";
    case BITOMEGA_NOISE: return "NOISE";
    case BITOMEGA_META: return "META";
    default: return "UNKNOWN";
  }
}

const char *bitomega_dir_name(bitomega_dir_t d) {
  switch (d) {
    case BITOMEGA_DIR_NONE: return "NONE";
    case BITOMEGA_DIR_UP: return "UP";
    case BITOMEGA_DIR_DOWN: return "DOWN";
    case BITOMEGA_DIR_FORWARD: return "FORWARD";
    case BITOMEGA_DIR_RECURSE: return "RECURSE";
    case BITOMEGA_DIR_NULL: return "NULL";
    default: return "UNKNOWN";
  }
}

uint32_t bitomega_norm01(uint32_t x) { return clamp01_q16(x); }

uint32_t bitomega_float_to_q16(float x) {
  if (!(x == x)) return 0u;
  if (x <= 0.0f) return 0u;
  if (x >= 1.0f) return BITOMEGA_Q16_ONE;
  return (uint32_t)(x * (float)BITOMEGA_Q16_ONE + 0.5f);
}

float bitomega_q16_to_float(uint32_t x) {
  return (float)clamp01_q16(x) / (float)BITOMEGA_Q16_ONE;
}

bitomega_ctx_t bitomega_ctx_default(uint64_t seed) {
  bitomega_ctx_t c;
  c.coherence_in = 0u;
  c.entropy_in = 0u;
  c.noise_in = 0u;
  c.load = 0u;
  c.seed = seed;
  return c;
}

int bitomega_invariant_ok(const bitomega_node_t *node) {
  if (!node) return 0;
  if (node->coherence > BITOMEGA_Q16_ONE) return 0;
  if (node->entropy > BITOMEGA_Q16_ONE) return 0;

  if (node->state == BITOMEGA_VOID) {
    if (!(node->dir == BITOMEGA_DIR_NONE || node->dir == BITOMEGA_DIR_NULL)) return 0;
  }
  if (node->state == BITOMEGA_META) {
    if (node->coherence < node->entropy) return 0;
  }
  return 1;
}

static void update_fields(bitomega_node_t *n, const bitomega_ctx_t *c) {
  const uint32_t a = 0x00004000u;      /* 0.25 */
  const uint32_t inv_a = 0x0000C000u;  /* 0.75 */
  uint32_t ncoh = clamp01_q16(n->coherence);
  uint32_t nent = clamp01_q16(n->entropy);
  uint32_t ccoh = clamp01_q16(c->coherence_in);
  uint32_t cent = clamp01_q16(c->entropy_in);
  n->coherence = clamp01_q16(q16_mul(inv_a, ncoh) + q16_mul(a, ccoh));
  n->entropy = clamp01_q16(q16_mul(inv_a, nent) + q16_mul(a, cent));
}

bitomega_status_t bitomega_transition(bitomega_node_t *node, const bitomega_ctx_t *ctx) {
  bitomega_ctx_t c;
  uint32_t coh;
  uint32_t ent;
  uint32_t noi;
  uint32_t ld;
  if (!node || !ctx) return BITOMEGA_ERR_ARG;

  c = *ctx;
  c.coherence_in = clamp01_q16(c.coherence_in);
  c.entropy_in = clamp01_q16(c.entropy_in);
  c.noise_in = clamp01_q16(c.noise_in);
  c.load = clamp01_q16(c.load);

  node->coherence = clamp01_q16(node->coherence);
  node->entropy = clamp01_q16(node->entropy);

  update_fields(node, &c);

  coh = node->coherence;
  ent = node->entropy;
  noi = c.noise_in;
  ld = c.load;

  switch (node->state) {
    case BITOMEGA_FLOW:
      if (coh > 0x0000CCCDu && noi < 0x00004CCDu) { node->state = BITOMEGA_LOCK; node->dir = BITOMEGA_DIR_RECURSE; }
      else if (noi > 0x0000B333u || ent > 0x0000B333u) { node->state = BITOMEGA_NOISE; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_LOCK:
      if (noi > 0x00008CCDu || ent > 0x0000A666u) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      else if (ld > 0x0000D99Au) { node->state = BITOMEGA_EDGE; node->dir = BITOMEGA_DIR_FORWARD; }
      break;

    case BITOMEGA_MIX:
      if (coh > ent + 0x0000199Au) { node->state = BITOMEGA_POS; node->dir = BITOMEGA_DIR_UP; }
      else if (ent > coh + 0x0000199Au) { node->state = BITOMEGA_NEG; node->dir = BITOMEGA_DIR_DOWN; }
      else { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_POS:
      if (ld > 0x0000E666u) { node->state = BITOMEGA_EDGE; node->dir = BITOMEGA_DIR_FORWARD; }
      else if (noi > 0x0000999Au) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      break;

    case BITOMEGA_NEG:
      if (noi < 0x00003333u && coh > 0x0000999Au) { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      else if (ent > 0x0000CCCDu) { node->state = BITOMEGA_VOID; node->dir = BITOMEGA_DIR_NULL; }
      break;

    case BITOMEGA_ZERO:
      if (coh > 0x0000B333u && noi < 0x00006666u) { node->state = BITOMEGA_FLOW; node->dir = BITOMEGA_DIR_FORWARD; }
      else if (noi > 0x0000CCCDu) { node->state = BITOMEGA_NOISE; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_EDGE:
      if (ld < 0x0000999Au && coh > 0x0000999Au) { node->state = BITOMEGA_FLOW; node->dir = BITOMEGA_DIR_FORWARD; }
      else if (noi > 0x0000B333u) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      break;

    case BITOMEGA_NOISE:
      if (noi < 0x0000599Au && coh > 0x00008CCDu) { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      else if (ent > 0x0000E666u) { node->state = BITOMEGA_VOID; node->dir = BITOMEGA_DIR_NULL; }
      break;

    case BITOMEGA_VOID:
      if (coh > 0x0000CCCDu && noi < 0x00003333u) { node->state = BITOMEGA_ZERO; node->dir = BITOMEGA_DIR_NONE; }
      break;

    case BITOMEGA_META:
      if (ent > coh) { node->state = BITOMEGA_MIX; node->dir = BITOMEGA_DIR_RECURSE; }
      else if (coh > 0x0000D99Au) { node->state = BITOMEGA_LOCK; node->dir = BITOMEGA_DIR_RECURSE; }
      break;

    default:
      return BITOMEGA_ERR_RANGE;
  }

  if (!bitomega_invariant_ok(node)) {
    node->state = BITOMEGA_ZERO;
    node->dir = BITOMEGA_DIR_NONE;
    node->coherence = clamp01_q16(node->coherence);
    node->entropy = clamp01_q16(node->entropy);
  }
  return BITOMEGA_OK;
}
