#ifndef RMR_TCG_CACHE_H
#define RMR_TCG_CACHE_H

#include "rmr_attractor.h"
#include "rmr_isorf.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_TCG_CACHE_MAX_BLOCKS 65536u
#define RMR_TCG_BLOCK_SIZE 4096u
#define RMR_TCG_HOST_BLOCK_MAX 8192u

#define RMR_TCG_BLOCK_VALIDATED 0x01u
#define RMR_TCG_BLOCK_COLLAPSING 0x02u

typedef struct {
  u32 guest_crc32c;
  u32 host_size;
  u64 toroidal_addr;
  u32 hit_count;
  u16 coherence_score;
  u16 miss_var;
  u32 collapse_epoch;
  u8 arch_host;
  u8 attractor_class;
  u8 flags;
} RmR_TCGBlock;

typedef struct {
  RmR_ISOraf_Store store;
  RmR_TCGBlock index[RMR_TCG_CACHE_MAX_BLOCKS];
  u32 block_count;
  u64 total_hits;
  u64 total_misses;
  u32 collapse_count;
  u32 reuse_count;
  u32 lookup_epoch;
  u64 delta_bits_flipped;
  u64 delta_bits_preserved;
  RmR_ISOraf_Page pages[RMR_TCG_CACHE_MAX_BLOCKS];
  u64 data_words[1048576u];
  u8 host_block_scratch[RMR_TCG_HOST_BLOCK_MAX];
} RmR_TCGCache;

void RmR_TCGCache_Init(RmR_TCGCache *cache);
const u8 *RmR_TCGCache_Lookup(RmR_TCGCache *cache, u32 guest_crc32c, u32 *host_size_out);
u8 RmR_TCGCache_Insert(RmR_TCGCache *cache,
                       u32 guest_crc32c,
                       const u8 *host_block,
                       u32 host_size,
                       u8 arch_host,
                       RmR_AttractorClass attractor_class,
                       u32 miss_score);
u32 RmR_TCGCache_HitRatio(const RmR_TCGCache *cache);
u32 RmR_TCGCache_CollapseCount(const RmR_TCGCache *cache);
u32 RmR_TCGCache_ReuseRate(const RmR_TCGCache *cache);
u64 RmR_TCGCache_DeltaBitsFlipped(const RmR_TCGCache *cache);
u32 RmR_TCGCache_DeltaPreservedPct(const RmR_TCGCache *cache);

#ifdef __cplusplus
}
#endif

#endif
