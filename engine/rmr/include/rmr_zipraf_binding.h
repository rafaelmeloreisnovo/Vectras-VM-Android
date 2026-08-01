// SPDX-License-Identifier: GPL-2.0-only
#ifndef RMR_ZIPRAF_BINDING_H
#define RMR_ZIPRAF_BINDING_H

#include "rmr_zipraf_archive.h"
#include "rmr_zipraf_page_graph.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  uint32_t block_id;
  uint32_t alignment;
  uint8_t digest_kind;
  uint8_t immutable;
  uint8_t digest_verified;
  uint8_t dma_candidate;
  uint8_t digest[RMR_ZIPRAF_DIGEST_BYTES];
} RmR_ZiprafEntryBinding;

int RmR_ZiprafArchive_BindEntry(const RmR_ZiprafArchiveEntry *entry,
                                const RmR_ZiprafEntryBinding *binding,
                                RmR_ZiprafPageBlock *out_block);

#ifdef __cplusplus
}
#endif

#endif
