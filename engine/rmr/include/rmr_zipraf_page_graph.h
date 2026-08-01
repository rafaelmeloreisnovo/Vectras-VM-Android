// SPDX-License-Identifier: GPL-2.0-only
#ifndef RMR_ZIPRAF_PAGE_GRAPH_H
#define RMR_ZIPRAF_PAGE_GRAPH_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_ZIPRAF_MAX_BLOCKS 32u
#define RMR_ZIPRAF_MAX_EDGES 64u
#define RMR_ZIPRAF_DIGEST_BYTES 32u

#define RMR_ZIPRAF_BLOCK_IMMUTABLE          (1u << 0)
#define RMR_ZIPRAF_BLOCK_MUTABLE            (1u << 1)
#define RMR_ZIPRAF_BLOCK_DIRECT_MAP         (1u << 2)
#define RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE     (1u << 3)
#define RMR_ZIPRAF_BLOCK_DMA_CANDIDATE      (1u << 4)
#define RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED    (1u << 5)
#define RMR_ZIPRAF_BLOCK_COPY_ON_WRITE      (1u << 6)

#define RMR_ZIPRAF_ACCESS_READ              (1u << 0)
#define RMR_ZIPRAF_ACCESS_WRITE             (1u << 1)
#define RMR_ZIPRAF_ACCESS_EXEC              (1u << 2)

#define RMR_ZIPRAF_OK 0
#define RMR_ZIPRAF_ERR_ARG -1
#define RMR_ZIPRAF_ERR_RANGE -2
#define RMR_ZIPRAF_ERR_DUPLICATE -3
#define RMR_ZIPRAF_ERR_ALIGNMENT -4
#define RMR_ZIPRAF_ERR_DIRECT_MAP -5
#define RMR_ZIPRAF_ERR_MUTABILITY -6
#define RMR_ZIPRAF_ERR_EDGE -7
#define RMR_ZIPRAF_ERR_WRITE_CONFLICT -8
#define RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM -9
#define RMR_ZIPRAF_ERR_STALE -10
#define RMR_ZIPRAF_ERR_STATE -11

typedef enum {
  RMR_ZIPRAF_METHOD_STORE = 0,
  RMR_ZIPRAF_METHOD_DEFLATE = 8
} RmR_ZiprafCompressionMethod;

typedef enum {
  RMR_ZIPRAF_DIGEST_NONE = 0,
  RMR_ZIPRAF_DIGEST_SHA256 = 1,
  RMR_ZIPRAF_DIGEST_BLAKE3 = 2
} RmR_ZiprafDigestKind;

typedef enum {
  RMR_ZIPRAF_REDUNDANCY_NONE = 0,
  RMR_ZIPRAF_REDUNDANCY_PARITY2_OBSERVE = 1,
  RMR_ZIPRAF_REDUNDANCY_ECC32_MASKED_OBSERVE = 2,
  RMR_ZIPRAF_REDUNDANCY_XOR_SHARD1 = 3,
  RMR_ZIPRAF_REDUNDANCY_MDS_EXTERNAL_PROOF = 4
} RmR_ZiprafRedundancyKind;

typedef enum {
  RMR_ZIPRAF_MAGIC_UNKNOWN = 0,
  RMR_ZIPRAF_MAGIC_ZIP = 1,
  RMR_ZIPRAF_MAGIC_PE_MZ = 2,
  RMR_ZIPRAF_MAGIC_ELF = 3
} RmR_ZiprafMagicKind;

typedef enum {
  RMR_ZIPRAF_LEASE_FREE = 0,
  RMR_ZIPRAF_LEASE_ARMED = 1,
  RMR_ZIPRAF_LEASE_IN_FLIGHT = 2,
  RMR_ZIPRAF_LEASE_QUIESCED = 3,
  RMR_ZIPRAF_LEASE_COMPLETE = 4,
  RMR_ZIPRAF_LEASE_STALE = 5,
  RMR_ZIPRAF_LEASE_FAULT = 6
} RmR_ZiprafLeaseState;

typedef struct {
  uint32_t block_id;
  uint64_t archive_offset;
  uint64_t stored_size;
  uint64_t logical_size;
  uint32_t alignment;
  uint16_t compression_method;
  uint16_t flags;
  uint8_t digest_kind;
  uint8_t redundancy_kind;
  uint8_t data_shards;
  uint8_t parity_shards;
  uint8_t erasure_positions_known;
  uint8_t external_fec_proof;
  uint16_t reserved0;
  uint32_t recovery_claim_ppm;
  uint8_t digest[RMR_ZIPRAF_DIGEST_BYTES];
} RmR_ZiprafPageBlock;

typedef struct {
  uint32_t module_id;
  uint32_t block_id;
  uint64_t local_offset;
  uint64_t length;
  uint32_t access_flags;
  uint32_t core_mask;
  uint32_t phase;
} RmR_ZiprafPageEdge;

typedef struct {
  uint64_t archive_size;
  uint64_t mapping_epoch;
  uint32_t page_size;
  uint32_t block_count;
  uint32_t edge_count;
  RmR_ZiprafPageBlock blocks[RMR_ZIPRAF_MAX_BLOCKS];
  RmR_ZiprafPageEdge edges[RMR_ZIPRAF_MAX_EDGES];
} RmR_ZiprafPageGraph;

typedef struct {
  uint64_t transaction_id;
  uint32_t block_id;
  uint32_t owner_core_mask;
  uint64_t mapping_epoch;
  uint64_t dma_address;
  uint64_t length;
  uint64_t expires_tick;
  uint8_t state;
} RmR_ZiprafDmaLease;

int RmR_ZiprafPageGraph_Validate(const RmR_ZiprafPageGraph *graph);
int RmR_ZiprafPageGraph_CanReuseBlock(const RmR_ZiprafPageBlock *before,
                                      const RmR_ZiprafPageBlock *after);
RmR_ZiprafMagicKind RmR_ZiprafPageGraph_DetectMagic(const uint8_t *bytes, size_t len);
int RmR_ZiprafPageGraph_CanMapExecutable(const RmR_ZiprafPageBlock *block,
                                         int platform_loader_authorized);
int RmR_ZiprafDmaLease_AcceptIrq(RmR_ZiprafDmaLease *lease,
                                 uint64_t current_epoch,
                                 uint64_t transaction_id,
                                 uint64_t now_tick);
int RmR_ZiprafDmaLease_Remap(RmR_ZiprafDmaLease *lease,
                             uint64_t new_epoch,
                             uint64_t new_dma_address,
                             uint64_t new_transaction_id,
                             uint64_t new_expires_tick);

#ifdef __cplusplus
}
#endif

#endif
