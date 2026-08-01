// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_zipraf_binding.h"

#include <string.h>

static int rmr_binding_pow2_u32(uint32_t value) {
  return value != 0u && (value & (value - 1u)) == 0u;
}

int RmR_ZiprafArchive_BindEntry(const RmR_ZiprafArchiveEntry *entry,
                                const RmR_ZiprafEntryBinding *binding,
                                RmR_ZiprafPageBlock *out_block) {
  uint8_t action;
  if (!entry || !binding || !out_block || binding->block_id == 0u ||
      !rmr_binding_pow2_u32(binding->alignment))
    return RMR_ZIPRAF_ARCHIVE_ERR_ARG;

  action = RmR_ZiprafArchive_ActionForEntry(entry);
  if (action == RMR_ZIPRAF_ACTION_REJECT) return RMR_ZIPRAF_ARCHIVE_ERR_POLICY;
  if (binding->digest_verified && binding->digest_kind == RMR_ZIPRAF_DIGEST_NONE)
    return RMR_ZIPRAF_ARCHIVE_ERR_POLICY;
  if (binding->dma_candidate && action != RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT)
    return RMR_ZIPRAF_ARCHIVE_ERR_POLICY;

  memset(out_block, 0, sizeof(*out_block));
  out_block->block_id = binding->block_id;
  out_block->archive_offset = entry->payload_offset;
  out_block->stored_size = entry->compressed_size;
  out_block->logical_size = entry->uncompressed_size;
  out_block->alignment = binding->alignment;
  out_block->compression_method = entry->method;
  out_block->digest_kind = binding->digest_kind;
  out_block->redundancy_kind = RMR_ZIPRAF_REDUNDANCY_NONE;
  memcpy(out_block->digest, binding->digest, RMR_ZIPRAF_DIGEST_BYTES);

  if (binding->immutable) out_block->flags |= RMR_ZIPRAF_BLOCK_IMMUTABLE;
  else out_block->flags |= RMR_ZIPRAF_BLOCK_MUTABLE;
  if (binding->digest_verified) out_block->flags |= RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED;

  if (action == RMR_ZIPRAF_ACTION_DIRECT_MAP_LAYOUT && binding->immutable &&
      binding->digest_verified && binding->digest_kind != RMR_ZIPRAF_DIGEST_NONE &&
      (entry->payload_offset % binding->alignment) == 0u)
    out_block->flags |= RMR_ZIPRAF_BLOCK_DIRECT_MAP;

  if (binding->dma_candidate) {
    if ((out_block->flags & RMR_ZIPRAF_BLOCK_DIRECT_MAP) == 0u)
      return RMR_ZIPRAF_ARCHIVE_ERR_POLICY;
    out_block->flags |= RMR_ZIPRAF_BLOCK_DMA_CANDIDATE;
  }

  return RMR_ZIPRAF_ARCHIVE_OK;
}
