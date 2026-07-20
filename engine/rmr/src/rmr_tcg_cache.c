// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_tcg_cache.h"

#include "zero_compat.h"

static u8 rmr_isorf_get_byte(const RmR_ISOraf_Store *st, u64 byte_offset) {
  u8 out = 0u;
  u64 bit = byte_offset << 3u;
  for (u32 i = 0u; i < 8u; ++i) {
    out |= (u8)(RmR_ISOraf_GetBit(st, bit + i) << i);
  }
  return out;
}

/* Escrita por delta XOR: o conjunto de 8 bits não é substituído — apenas os
 * bits divergentes entre o byte residente e o byte candidato são tocados.
 * Bits iguais permanecem intactos e bits 0 sobre página ausente nunca alocam
 * página no ISOraf, preservando o físico esparso do store. */
static u8 rmr_isorf_write_byte_delta(RmR_ISOraf_Store *st,
                                     u64 byte_offset,
                                     u8 value,
                                     u32 *flipped_out) {
  u8 current = rmr_isorf_get_byte(st, byte_offset);
  u8 delta = (u8)(current ^ value);
  u64 bit = byte_offset << 3u;
  u32 flipped = 0u;
  for (u32 i = 0u; i < 8u; ++i) {
    if (((delta >> i) & 1u) == 0u) continue;
    if (!RmR_ISOraf_SetBit(st, bit + i, (u8)((value >> i) & 1u))) return 0u;
    flipped += 1u;
  }
  if (flipped_out) *flipped_out = flipped;
  return 1u;
}

static u8 rmr_tcg_block_is_valid(const RmR_TCGBlock *block) {
  if (!block) return 0u;
  if ((block->flags & RMR_TCG_BLOCK_VALIDATED) == 0u) return 0u;
  if ((block->flags & RMR_TCG_BLOCK_COLLAPSING) != 0u) return 0u;
  if (block->host_size == 0u || block->host_size > RMR_TCG_HOST_BLOCK_MAX) return 0u;
  return 1u;
}

static u16 rmr_tcg_stability_score(const RmR_TCGBlock *block) {
  u32 stability;
  u8 bias;
  if (!block) return 0u;
  bias = RmR_Attractor_RetentionBias((RmR_AttractorClass)block->attractor_class);
  stability = (u32)block->coherence_score + (u32)bias;
  if (block->miss_var >= stability) return 0u;
  return (u16)(stability - block->miss_var);
}

static u8 rmr_tcg_should_collapse(const RmR_TCGBlock *block,
                                  u32 host_size,
                                  u8 arch_host,
                                  RmR_AttractorClass attractor_class,
                                  u16 miss_var) {
  u16 candidate_stability;
  u16 current_stability;

  if (!block) return 0u;
  if (!rmr_tcg_block_is_valid(block)) return 0u;

  if (block->host_size != host_size) return 1u;
  if (block->arch_host != arch_host) return 1u;
  if ((u8)attractor_class < block->attractor_class) return 1u;

  candidate_stability = (u16)((u16)RmR_Attractor_RetentionBias(attractor_class) + 128u);
  if (miss_var >= candidate_stability) candidate_stability = 0u;
  else candidate_stability = (u16)(candidate_stability - miss_var);

  current_stability = rmr_tcg_stability_score(block);
  if (candidate_stability + 8u < current_stability) return 1u;

  return 0u;
}

void RmR_TCGCache_Init(RmR_TCGCache *cache) {
  if (!cache) return;
  rmr_mem_set(cache, 0u, sizeof(*cache));
  RmR_ISOraf_Init(&cache->store,
                  cache->pages,
                  RMR_TCG_CACHE_MAX_BLOCKS,
                  cache->data_words,
                  (u32)(sizeof(cache->data_words) / sizeof(cache->data_words[0])),
                  RMR_TCG_HOST_BLOCK_MAX * 8u);
}

const u8 *RmR_TCGCache_Lookup(RmR_TCGCache *cache, u32 guest_crc32c, u32 *host_size_out) {
  if (!cache) return (const u8 *)0;

  for (u32 i = 0u; i < cache->block_count; ++i) {
    RmR_TCGBlock *block = &cache->index[i];
    if (block->guest_crc32c != guest_crc32c) continue;
    if (!rmr_tcg_block_is_valid(block)) break;

    for (u32 k = 0u; k < block->host_size; ++k) {
      cache->host_block_scratch[k] = rmr_isorf_get_byte(&cache->store, block->toroidal_addr + k);
    }

    block->hit_count += 1u;
    block->coherence_score = (u16)(block->coherence_score + (block->coherence_score < 65535u ? 1u : 0u));
    cache->reuse_count += 1u;
    cache->total_hits += 1u;
    if (host_size_out) *host_size_out = block->host_size;
    return cache->host_block_scratch;
  }

  cache->total_misses += 1u;
  if (host_size_out) *host_size_out = 0u;
  return (const u8 *)0;
}

u8 RmR_TCGCache_Insert(RmR_TCGCache *cache,
                       u32 guest_crc32c,
                       const u8 *host_block,
                       u32 host_size,
                       u8 arch_host,
                       RmR_AttractorClass attractor_class,
                       u32 miss_score) {
  RmR_TCGBlock *dst = (RmR_TCGBlock *)0;
  u64 toroidal_addr;
  u16 miss_var;

  if (!cache || !host_block || host_size == 0u || host_size > RMR_TCG_HOST_BLOCK_MAX) return 0u;

  miss_var = (u16)(miss_score > 65535u ? 65535u : miss_score);

  for (u32 i = 0u; i < cache->block_count; ++i) {
    if (cache->index[i].guest_crc32c == guest_crc32c) {
      dst = &cache->index[i];
      break;
    }
  }

  if (!dst) {
    if (cache->block_count >= RMR_TCG_CACHE_MAX_BLOCKS) return 0u;
    dst = &cache->index[cache->block_count++];
    rmr_mem_set(dst, 0u, sizeof(*dst));
  }

  if (rmr_tcg_should_collapse(dst, host_size, arch_host, attractor_class, miss_var)) {
    dst->flags |= RMR_TCG_BLOCK_COLLAPSING;
    dst->collapse_epoch = ++cache->lookup_epoch;
    cache->collapse_count += 1u;
  }

  if (dst->host_size == 0u || dst->host_size > RMR_TCG_HOST_BLOCK_MAX) {
    toroidal_addr = (u64)(dst - &cache->index[0]) * (u64)RMR_TCG_HOST_BLOCK_MAX;
  } else {
    toroidal_addr = dst->toroidal_addr;
  }

  {
    u64 flipped_total = 0u;
    for (u32 i = 0u; i < host_size; ++i) {
      u32 flipped = 0u;
      if (!rmr_isorf_write_byte_delta(&cache->store, toroidal_addr + i,
                                      host_block[i], &flipped)) return 0u;
      flipped_total += flipped;
    }
    cache->delta_bits_flipped += flipped_total;
    cache->delta_bits_preserved += ((u64)host_size << 3u) - flipped_total;
  }

  /* HOTFIX: preserve RMR_TCG_BLOCK_COLLAPSING across the flag reset so that the
   * guard at rmr_tcg_should_collapse() line 25 ("if already collapsing, skip") can
   * actually fire on subsequent updates.  Previously dst->flags = 0u wiped the
   * COLLAPSING bit that was just set on line 130, making the guard permanently dead
   * and causing every qualifying block to increment collapse_count on every update. */
  dst->flags = (dst->flags & RMR_TCG_BLOCK_COLLAPSING) | RMR_TCG_BLOCK_VALIDATED;
  dst->guest_crc32c = guest_crc32c;
  dst->host_size = host_size;
  dst->toroidal_addr = toroidal_addr;
  dst->arch_host = arch_host;
  dst->attractor_class = (u8)attractor_class;
  dst->miss_var = miss_var;
  dst->coherence_score = (u16)(128u + RmR_Attractor_RetentionBias(attractor_class));
  return 1u;
}

u32 RmR_TCGCache_HitRatio(const RmR_TCGCache *cache) {
  u64 total;
  if (!cache) return 0u;
  total = cache->total_hits + cache->total_misses;
  if (!total) return 0u;
  return (u32)((cache->total_hits * 100u) / total);
}

u32 RmR_TCGCache_CollapseCount(const RmR_TCGCache *cache) {
  if (!cache) return 0u;
  return cache->collapse_count;
}

u32 RmR_TCGCache_ReuseRate(const RmR_TCGCache *cache) {
  if (!cache) return 0u;
  if (cache->block_count == 0u) return 0u;
  return (u32)((cache->reuse_count * 100u) / cache->block_count);
}

u64 RmR_TCGCache_DeltaBitsFlipped(const RmR_TCGCache *cache) {
  if (!cache) return 0u;
  return cache->delta_bits_flipped;
}

u32 RmR_TCGCache_DeltaPreservedPct(const RmR_TCGCache *cache) {
  u64 total;
  if (!cache) return 0u;
  total = cache->delta_bits_flipped + cache->delta_bits_preserved;
  if (!total) return 0u;
  return (u32)((cache->delta_bits_preserved * 100u) / total);
}
