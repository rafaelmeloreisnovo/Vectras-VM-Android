#include <stdio.h>
#include <string.h>

#include "rmr_zipraf_page_graph.h"

static int failures = 0;

#define CHECK(name, expr) do { \
  if (!(expr)) { printf("FAIL %s\n", name); failures++; } \
  else { printf("PASS %s\n", name); } \
} while (0)

static void fill_digest(uint8_t out[RMR_ZIPRAF_DIGEST_BYTES], uint8_t seed) {
  uint32_t i;
  for (i = 0u; i < RMR_ZIPRAF_DIGEST_BYTES; ++i) out[i] = (uint8_t)(seed + i);
}

static RmR_ZiprafPageGraph valid_graph(void) {
  RmR_ZiprafPageGraph graph;
  memset(&graph, 0, sizeof(graph));
  graph.archive_size = 65536u;
  graph.mapping_epoch = 7u;
  graph.page_size = 4096u;
  graph.block_count = 2u;
  graph.edge_count = 3u;

  graph.blocks[0].block_id = 1u;
  graph.blocks[0].archive_offset = 4096u;
  graph.blocks[0].stored_size = 4096u;
  graph.blocks[0].logical_size = 4096u;
  graph.blocks[0].alignment = 4096u;
  graph.blocks[0].compression_method = RMR_ZIPRAF_METHOD_STORE;
  graph.blocks[0].flags = RMR_ZIPRAF_BLOCK_IMMUTABLE |
                          RMR_ZIPRAF_BLOCK_DIRECT_MAP |
                          RMR_ZIPRAF_BLOCK_EXEC_CANDIDATE |
                          RMR_ZIPRAF_BLOCK_DMA_CANDIDATE |
                          RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED;
  graph.blocks[0].digest_kind = RMR_ZIPRAF_DIGEST_SHA256;
  graph.blocks[0].redundancy_kind = RMR_ZIPRAF_REDUNDANCY_ECC32_MASKED_OBSERVE;
  fill_digest(graph.blocks[0].digest, 1u);

  graph.blocks[1].block_id = 2u;
  graph.blocks[1].archive_offset = 8192u;
  graph.blocks[1].stored_size = 2048u;
  graph.blocks[1].logical_size = 4096u;
  graph.blocks[1].alignment = 512u;
  graph.blocks[1].compression_method = RMR_ZIPRAF_METHOD_DEFLATE;
  graph.blocks[1].flags = RMR_ZIPRAF_BLOCK_IMMUTABLE |
                          RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED;
  graph.blocks[1].digest_kind = RMR_ZIPRAF_DIGEST_BLAKE3;
  graph.blocks[1].redundancy_kind = RMR_ZIPRAF_REDUNDANCY_NONE;
  fill_digest(graph.blocks[1].digest, 91u);

  graph.edges[0].module_id = 10u;
  graph.edges[0].block_id = 1u;
  graph.edges[0].length = 1024u;
  graph.edges[0].access_flags = RMR_ZIPRAF_ACCESS_READ | RMR_ZIPRAF_ACCESS_EXEC;
  graph.edges[0].core_mask = 1u;
  graph.edges[0].phase = 0u;

  graph.edges[1].module_id = 11u;
  graph.edges[1].block_id = 1u;
  graph.edges[1].local_offset = 1024u;
  graph.edges[1].length = 1024u;
  graph.edges[1].access_flags = RMR_ZIPRAF_ACCESS_READ;
  graph.edges[1].core_mask = 2u;
  graph.edges[1].phase = 0u;

  graph.edges[2].module_id = 12u;
  graph.edges[2].block_id = 2u;
  graph.edges[2].length = 4096u;
  graph.edges[2].access_flags = RMR_ZIPRAF_ACCESS_READ;
  graph.edges[2].core_mask = 4u;
  graph.edges[2].phase = 1u;
  return graph;
}

int main(void) {
  RmR_ZiprafPageGraph graph = valid_graph();
  RmR_ZiprafPageGraph bad;
  RmR_ZiprafPageBlock changed;
  RmR_ZiprafDmaLease lease;
  const uint8_t mz[] = {0x4d, 0x5a, 0, 0};

  CHECK("valid_graph", RmR_ZiprafPageGraph_Validate(&graph) == RMR_ZIPRAF_OK);
  CHECK("stored_direct_map_requires_loader_grant",
        !RmR_ZiprafPageGraph_CanMapExecutable(&graph.blocks[0], 0) &&
         RmR_ZiprafPageGraph_CanMapExecutable(&graph.blocks[0], 1));
  CHECK("mz_is_only_classification",
        RmR_ZiprafPageGraph_DetectMagic(mz, sizeof(mz)) == RMR_ZIPRAF_MAGIC_PE_MZ);

  bad = graph;
  bad.blocks[1].flags |= RMR_ZIPRAF_BLOCK_DIRECT_MAP;
  CHECK("deflate_cannot_direct_map",
        RmR_ZiprafPageGraph_Validate(&bad) == RMR_ZIPRAF_ERR_DIRECT_MAP);

  bad = graph;
  bad.blocks[0].recovery_claim_ppm = 450000u;
  CHECK("ecc_observer_cannot_claim_45_percent_recovery",
        RmR_ZiprafPageGraph_Validate(&bad) == RMR_ZIPRAF_ERR_REDUNDANCY_CLAIM);

  bad = graph;
  bad.blocks[1].flags = RMR_ZIPRAF_BLOCK_MUTABLE | RMR_ZIPRAF_BLOCK_DIGEST_VERIFIED;
  bad.edges[2].access_flags = RMR_ZIPRAF_ACCESS_WRITE;
  bad.edge_count = 4u;
  bad.edges[3] = bad.edges[2];
  bad.edges[3].module_id = 13u;
  bad.edges[3].core_mask = 8u;
  CHECK("same_phase_write_conflict_rejected",
        RmR_ZiprafPageGraph_Validate(&bad) == RMR_ZIPRAF_ERR_WRITE_CONFLICT);

  changed = graph.blocks[0];
  CHECK("same_immutable_digest_reuses_block",
        RmR_ZiprafPageGraph_CanReuseBlock(&graph.blocks[0], &changed));
  changed.digest[0] ^= 1u;
  CHECK("changed_digest_requires_new_block",
        !RmR_ZiprafPageGraph_CanReuseBlock(&graph.blocks[0], &changed));

  memset(&lease, 0, sizeof(lease));
  lease.transaction_id = 42u;
  lease.block_id = 1u;
  lease.owner_core_mask = 1u;
  lease.mapping_epoch = 7u;
  lease.dma_address = 0x1000u;
  lease.length = 4096u;
  lease.expires_tick = 100u;
  lease.state = RMR_ZIPRAF_LEASE_IN_FLIGHT;
  CHECK("irq_current_epoch_accepted",
        RmR_ZiprafDmaLease_AcceptIrq(&lease, 7u, 42u, 99u) == RMR_ZIPRAF_OK &&
        lease.state == RMR_ZIPRAF_LEASE_COMPLETE);

  lease.state = RMR_ZIPRAF_LEASE_IN_FLIGHT;
  CHECK("irq_old_epoch_rejected",
        RmR_ZiprafDmaLease_AcceptIrq(&lease, 8u, 42u, 99u) == RMR_ZIPRAF_ERR_STALE &&
        lease.state == RMR_ZIPRAF_LEASE_STALE);

  lease.state = RMR_ZIPRAF_LEASE_IN_FLIGHT;
  CHECK("remap_requires_quiesce",
        RmR_ZiprafDmaLease_Remap(&lease, 8u, 0x2000u, 43u, 200u) == RMR_ZIPRAF_ERR_STATE);
  lease.state = RMR_ZIPRAF_LEASE_QUIESCED;
  CHECK("quiesced_lease_can_remap",
        RmR_ZiprafDmaLease_Remap(&lease, 8u, 0x2000u, 43u, 200u) == RMR_ZIPRAF_OK &&
        lease.mapping_epoch == 8u && lease.state == RMR_ZIPRAF_LEASE_ARMED);

  if (failures != 0) return 1;
  puts("ZIPRAF_PAGE_GRAPH_KAT PASS");
  return 0;
}
