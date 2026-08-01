// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_zipraf_page_graph.h"

static int rmr_zipraf_pow2_u32(uint32_t value) {
  return value != 0u && (value & (value - 1u)) == 0u;
}

static int rmr_zipraf_add_overflow_u64(uint64_t a, uint64_t b, uint64_t *out) {
  if (!out || UINT64_MAX - a < b) return 1;
  *out = a + b;
  return 0;
}

static int rmr_zipraf_digest_equal(const uint8_t a[RMR_ZIPRAF_DIGEST_BYTES],
                                   const uint8_t b[RMR_ZIPRAF_DIGEST_BYTES]) {
  uint32_t i;
  uint8_t diff = 0u;
  for (i = 0u; i < RMR_ZIPRAF_DIGEST_BYTES; ++i) diff |= (uint8_t)(a[i] ^ b[i]);
  return diff == 0u;
}

static const RmR_ZiprafPageBlock *rmr_zipraf_find_block(const RmR_ZiprafPageGraph *graph,
                                                         uint32_t block_id) {
  uint32_t i;
  for (i = 0u; i < graph->block_count; ++i) {
    if (graph->blocks[i].block_id == block_id) return &graph->blocks[i];
  }
  return (const RmR_ZiprafPageBlock *)0;
}

static int rmr_zipraf_ranges_overlap(uint64_t a0, uint64_t a_len,
                                     uint64_t b0, uint64_t b_len) {
  uint64_t a1;
  uint64_t b1;
  if (rmr_zipraf_add_overflow_u64(a0, a_len, &a1) ||
      rmr_zipraf_add_overflow_u64(b0, b_len, &b1)) return 1;
  return a0 < b1 && b0 < a1;
}

static int rmr_zipraf_validate_redundancy(const RmR_ZiprafPageBlock *block) {
  uint32_t total_shards;
  uint32_t max_ppm;

  if (block->recovery_claim_ppm > 1000000u) return RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM;

  if (block->redundancy_kind == RMR_ZIPRAF_REDUNDANCY_NONE ||
      block->redundancy_kind == RMR_ZIPRAF_REDUNDANCY_PARITY2_OBSERVE ||
      block->redundancy_kind == RMR_ZIPRAF_REDUNDANCY_ECC32_MASKED_OBSERVE) {
    return block->recovery_claim_ppm == 0u ? RMR_ZIPRAF_OK : RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM;
  }

  total_shards = (uint32_t)block->data_shards + (uint32_t)block->parity_shards;
  if (block->data_shards == 0u || block->parity_shards == 0u || total_shards == 0u)
    return RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM;

  max_ppm = (uint32_t)(((uint64_t)block->parity_shards * 1000000u) / total_shards);
  if (!block->erasure_positions_known || block->recovery_claim_ppm > max_ppm)
    return RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM;

  if (block->redundancy_kind == RMR_ZIPRAF_REDUNDANCY_XOR_SHARD1 && block->parity_shards != 1u)
    return RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM;

  if (block->redundancy_kind == RMR_ZIPRAF_REDUNDANCY_MDS_EXTERNAL_PROOF &&
      !block->external_fec_proof)
    return RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM;

  return RMR_ZIPRAF_OK;
}

int RmR_ZiprafPageGraph_Validate(const RmR_ZiprafPageGraph *graph) {
  uint32_t i;
  uint32_t j;

  if (!graph || !rmr_zipraf_pow2_u32(graph->page_size) || graph->mapping_epoch == 0u)
    return RMR_ZIPRAF_ERR_ARG;
  if (graph->block_count > RMR_ZIPRAF_MAX_BLOCKS || graph->edge_count > RMR_ZIPRAF_MAX_EDGES)
    return RMR_ZIPRAF_ERR_RANGE;

  for (i = 0u; i < graph->block_count; ++i) {
    const RmR_ZiprafPageBlock *block = &graph->blocks[i];
    uint64_t end;
    int redundancy_result;

    if (block->block_id == 0u || block->stored_size == 0u || block->logical_size == 0u)
      return RMR_ZIPRAF_ERR_ARG;
    if (!rmr_zipraf_pow2_u32(block->alignment)) return RMR_ZIPRAF_ERR_ALIGNMENT;
    if (rmr_zipraf_add_overflow_u64(block->archive_offset, block->stored_size, &end) ||
        end > graph->archive_size)
      return RMR_ZIPRAF_ERR_RANGE;
    if ((block->flags & RMR_ZIPRAF_BLOCK_IMMUTABLE) &&
        (block->flags & RMR_ZIPRAF_BLOCK_MUTABLE))
      return RMR_ZIPRAF_ERR_MUTABILITY;
    if ((block->flags & RMR_ZIPRAF_BLOCK_DIRECT_MAP) != 0u) {
      if (block->compression_method != RMR_ZIPRAF_METHOD_STORE ||
          block->stored_size != block->logical_size ||
          (block->archive_offset % block->alignment) != 0u ||
          (block->flags & RMR_ZIPRAF_BLOCK_IMMUTABLE) == 0u ||
          (block->flags & RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED) == 0u ||
          block->digest_kind == RMR_ZIPRAF_DIGEST_NONE)
        return RMR_ZIPRAF_ERR_DIRECT_MAP;
    }
    if ((block->flags & RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE) != 0u &&
        (block->flags & RMR_ZIPRAF_BLOCK_DIRECT_MAP) == 0u)
      return RMR_ZIPRAF_ERR_DIRECT_MAP;
    if ((block->flags & RMR_ZIPRAF_BLOCK_DMA_CANDIDATE) != 0u &&
        (block->archive_offset % graph->page_size) != 0u)
      return RMR_ZIPRAF_ERR_ALIGNMENT;

    redundancy_result = rmr_zipraf_validate_redundancy(block);
    if (redundancy_result != RMR_ZIPRAF_OK) return redundancy_result;

    for (j = i + 1u; j < graph->block_count; ++j) {
      const RmR_ZiprafPageBlock *other = &graph->blocks[j];
      if (other->block_id == block->block_id) return RMR_ZIPRAF_ERR_DUPLICATE;
      if (rmr_zipraf_ranges_overlap(block->archive_offset, block->stored_size,
                                    other->archive_offset, other->stored_size)) {
        const int exact_alias = block->archive_offset == other->archive_offset &&
                                block->stored_size == other->stored_size &&
                                block->digest_kind == other->digest_kind &&
                                rmr_zipraf_digest_equal(block->digest, other->digest);
        if (!exact_alias) return RMR_ZIPRAF_ERR_RANGE;
      }
    }
  }

  for (i = 0u; i < graph->edge_count; ++i) {
    const RmR_ZiprafPageEdge *edge = &graph->edges[i];
    const RmR_ZiprafPageBlock *block = rmr_zipraf_find_block(graph, edge->block_id);
    uint64_t edge_end;
    if (!block || edge->module_id == 0u || edge->length == 0u || edge->core_mask == 0u ||
        (edge->access_flags & (RMR_ZIPRAF_ACCESS_READ | RMR_ZIPRAF_ACCESS_WRITE |
                               RMR_ZIPRAF_ACCESS_EXEC)) == 0u)
      return RMR_ZIPRAF_ERR_EDGE;
    if (rmr_zipraf_add_overflow_u64(edge->local_offset, edge->length, &edge_end) ||
        edge_end > block->logical_size)
      return RMR_ZIPRAF_ERR_EDGE;
    if ((edge->access_flags & RMR_ZIPRAF_ACCESS_WRITE) != 0u &&
        (block->flags & RMR_ZIPRAF_BLOCK_MUTABLE) == 0u)
      return RMR_ZIPRAF_ERR_MUTABILITY;
    if ((edge->access_flags & RMR_ZIPRAF_ACCESS_EXEC) != 0u &&
        (block->flags & RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE) == 0u)
      return RMR_ZIPRAF_ERR_EDGE;

    for (j = i + 1u; j < graph->edge_count; ++j) {
      const RmR_ZiprafPageEdge *other = &graph->edges[j];
      if (edge->block_id == other->block_id && edge->phase == other->phase &&
          ((edge->access_flags | other->access_flags) & RMR_ZIPRAF_ACCESS_WRITE) != 0u &&
          rmr_zipraf_ranges_overlap(edge->local_offset, edge->length,
                                    other->local_offset, other->length))
        return RMR_ZIPRAF_ERR_WRITE_CONFLICT;
    }
  }

  return RMR_ZIPRAF_OK;
}

int RmR_ZiprafPageGraph_CanReuseBlock(const RmR_ZiprafPageBlock *before,
                                      const RmR_ZiprafPageBlock *after) {
  if (!before || !after) return 0;
  if ((before->flags & RMR_ZIPRAF_BLOCK_IMMUTABLE) == 0u ||
      (after->flags & RMR_ZIPRAF_BLOCK_IMMUTABLE) == 0u) return 0;
  return before->logical_size == after->logical_size &&
         before->digest_kind != RMR_ZIPRAF_DIGEST_NONE &&
         before->digest_kind == after->digest_kind &&
         rmr_zipraf_digest_equal(before->digest, after->digest);
}

RmR_ZiprafMagicKind RmR_ZiprafPageGraph_DetectMagic(const uint8_t *bytes, size_t len) {
  if (!bytes) return RMR_ZIPRAF_MAGIC_UNKNOWN;
  if (len >= 4u && bytes[0] == 0x50u && bytes[1] == 0x4bu &&
      bytes[2] == 0x03u && bytes[3] == 0x04u) return RMR_ZIPRAF_MAGIC_ZIP;
  if (len >= 2u && bytes[0] == 0x4du && bytes[1] == 0x5au) return RMR_ZIPRAF_MAGIC_PE_MZ;
  if (len >= 4u && bytes[0] == 0x7fu && bytes[1] == 0x45u &&
      bytes[2] == 0x4cu && bytes[3] == 0x46u) return RMR_ZIPRAF_MAGIC_ELF;
  return RMR_ZIPRAF_MAGIC_UNKNOWN;
}

int RmR_ZiprafPageGraph_CanMapExecutable(const RmR_ZiprafPageBlock *block,
                                         int platform_loader_authorized) {
  if (!block || !platform_loader_authorized) return 0;
  return (block->flags & (RMR_ZIPRAF_BLOCK_DIRECT_MAP |
                          RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE |
                          RMR_ZIPRAF_BLOCK_IMMUTABLE |
                          RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED)) ==
         (RMR_ZIPRAF_BLOCK_DIRECT_MAP |
          RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE |
          RMR_ZIPRAF_BLOCK_IMMUTABLE |
          RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED);
}

int RmR_ZiprafDmaLease_AcceptIrq(RmR_ZiprafDmaLease *lease,
                                 uint64_t current_epoch,
                                 uint64_t transaction_id,
                                 uint64_t now_tick) {
  if (!lease) return RMR_ZIPRAF_ERR_ARG;
  if (lease->state != RMR_ZIPRAF_LEASE_IN_FLIGHT ||
      lease->mapping_epoch != current_epoch ||
      lease->transaction_id != transaction_id ||
      now_tick > lease->expires_tick) {
    lease->state = RMR_ZIPRAF_LEASE_STALE;
    return RMR_ZIPRAF_ERR_STALE;
  }
  lease->state = RMR_ZIPRAF_LEASE_COMPLETE;
  return RMR_ZIPRAF_OK;
}

int RmR_ZiprafDmaLease_Remap(RmR_ZiprafDmaLease *lease,
                             uint64_t new_epoch,
                             uint64_t new_dma_address,
                             uint64_t new_transaction_id,
                             uint64_t new_expires_tick) {
  if (!lease || new_epoch <= lease->mapping_epoch || new_transaction_id == 0u ||
      new_expires_tick == 0u)
    return RMR_ZIPRAF_ERR_ARG;
  if (lease->state != RMR_ZIPRAF_LEASE_QUIESCED) return RMR_ZIPRAF_ERR_STATE;
  lease->mapping_epoch = new_epoch;
  lease->dma_address = new_dma_address;
  lease->transaction_id = new_transaction_id;
  lease->expires_tick = new_expires_tick;
  lease->state = RMR_ZIPRAF_LEASE_ARMED;
  return RMR_ZIPRAF_OK;
}
