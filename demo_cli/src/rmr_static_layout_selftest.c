// SPDX-License-Identifier: GPL-2.0-only
#include "rmr_static_layout.h"

#include <stdint.h>
#include <stdio.h>

static _Alignas(64) uint8_t arena_a[4096];
static _Alignas(64) uint8_t arena_b[4096];

static int expect_status(const char *name, int actual, int expected) {
  if (actual != expected) {
    fprintf(stderr, "%s: expected %d, got %d\n", name, expected, actual);
    return 0;
  }
  return 1;
}

static int expect_true(const char *name, int condition) {
  if (!condition) {
    fprintf(stderr, "%s: condition failed\n", name);
    return 0;
  }
  return 1;
}

int main(void) {
  static const rmr_static_region_t regions[] = {
      {1u, 0u, 512u, 64u, 0x3fu, 0u,
       RMR_LAYOUT_FIXED_OFFSET, RMR_LAYOUT_REGION_PRESENT,
       RMR_LAYOUT_FLAG_READ_ONLY |
           RMR_LAYOUT_FLAG_NO_RELOCATION_TABLE |
           RMR_LAYOUT_FLAG_FIXED_OFFSET_BITS},
      {2u, 512u, 1024u, 64u, 0u, 0u,
       RMR_LAYOUT_MOVABLE_BASE, RMR_LAYOUT_REGION_PRESENT,
       RMR_LAYOUT_FLAG_NO_RELOCATION_TABLE},
      {3u, 1536u, 0u, 64u, 0u, 0u,
       RMR_LAYOUT_FIXED_OFFSET, RMR_LAYOUT_REGION_EMPTY, 0u},
      {4u, 2048u, 512u, 256u, 0u, 0u,
       RMR_LAYOUT_REMAP_ONLY, RMR_LAYOUT_REGION_PRESENT,
       RMR_LAYOUT_FLAG_ZERO_FILL}
  };
  static const rmr_static_layout_manifest_t manifest = {
      RMR_STATIC_LAYOUT_ABI_VERSION,
      42u,
      4096u,
      64u,
      4u,
      RMR_LAYOUT_BASE_RELATIVE,
      0u,
      0u,
      regions
  };
  rmr_static_layout_binding_t binding;
  void *resolved = NULL;
  void *old_absolute = NULL;
  uint64_t signature;
  int passed = 0;
  int total = 0;

#define CHECK_STATUS(name, expr, expected) \
  do { ++total; if (expect_status((name), (expr), (expected))) ++passed; } while (0)
#define CHECK_TRUE(name, expr) \
  do { ++total; if (expect_true((name), (expr))) ++passed; } while (0)

  CHECK_STATUS("manifest-valid", RmR_StaticLayout_Validate(&manifest), RMR_LAYOUT_OK);

  signature = RmR_StaticLayout_ManifestSignature(&manifest);
  CHECK_TRUE("signature-nonzero", signature != 0u);

  CHECK_STATUS("bind-a",
               RmR_StaticLayout_Bind(&manifest, arena_a, sizeof(arena_a), 7u, &binding),
               RMR_LAYOUT_OK);
  CHECK_TRUE("binding-signature", binding.manifest_signature == signature);

  CHECK_STATUS("resolve-relative",
               RmR_StaticLayout_Resolve(&binding, 2u, 16u, 32u, &resolved),
               RMR_LAYOUT_OK);
  old_absolute = resolved;
  CHECK_TRUE("resolved-address-a", resolved == (void *)(arena_a + 528u));

  CHECK_STATUS("resolve-empty-zero-length",
               RmR_StaticLayout_Resolve(&binding, 3u, 0u, 0u, &resolved),
               RMR_LAYOUT_OK);
  CHECK_TRUE("empty-keeps-position", resolved == (void *)(arena_a + 1536u));

  CHECK_STATUS("reject-region-overrun",
               RmR_StaticLayout_Resolve(&binding, 2u, 1000u, 25u, &resolved),
               RMR_LAYOUT_ERR_BOUNDS);

  CHECK_TRUE("absolute-pointers-current",
             RmR_StaticLayout_CanReuseAbsolutePointers(&binding, arena_a, 7u));
  CHECK_TRUE("absolute-pointers-reject-other-base",
             !RmR_StaticLayout_CanReuseAbsolutePointers(&binding, arena_b, 7u));

  CHECK_STATUS("rebind-b",
               RmR_StaticLayout_Rebind(&binding, arena_b, sizeof(arena_b), 8u),
               RMR_LAYOUT_OK);
  CHECK_STATUS("resolve-after-rebind",
               RmR_StaticLayout_Resolve(&binding, 2u, 16u, 32u, &resolved),
               RMR_LAYOUT_OK);
  CHECK_TRUE("offset-reused-new-base", resolved == (void *)(arena_b + 528u));
  CHECK_TRUE("absolute-address-changed", resolved != old_absolute);
  CHECK_TRUE("offset-plan-reusable",
             RmR_StaticLayout_CanReuseOffsets(&manifest, &manifest));
  CHECK_TRUE("absolute-pointers-new-epoch",
             RmR_StaticLayout_CanReuseAbsolutePointers(&binding, arena_b, 8u));
  CHECK_STATUS("reject-stale-epoch",
               RmR_StaticLayout_Rebind(&binding, arena_b, sizeof(arena_b), 7u),
               RMR_LAYOUT_ERR_STALE);

  CHECK_TRUE("fixed-bits",
             RmR_StaticLayout_PreserveFixedBits(UINT64_MAX,
                                                0xf00000000000000fULL,
                                                0xa000000000000005ULL) ==
                 0xaffffffffffffff5ULL);

  {
    static const rmr_static_region_t overlap_regions[] = {
        {10u, 0u, 128u, 16u, 0u, 0u,
         RMR_LAYOUT_FIXED_OFFSET, RMR_LAYOUT_REGION_PRESENT, 0u},
        {11u, 64u, 128u, 16u, 0u, 0u,
         RMR_LAYOUT_FIXED_OFFSET, RMR_LAYOUT_REGION_PRESENT, 0u}
    };
    static const rmr_static_layout_manifest_t overlap_manifest = {
        RMR_STATIC_LAYOUT_ABI_VERSION, 1u, 512u, 16u, 2u,
        RMR_LAYOUT_BASE_RELATIVE, 0u, 0u, overlap_regions
    };
    CHECK_STATUS("reject-overlap",
                 RmR_StaticLayout_Validate(&overlap_manifest),
                 RMR_LAYOUT_ERR_OVERLAP);
  }

  {
    static const rmr_static_region_t pinned_regions[] = {
        {20u, 0u, 256u, 64u, 0u, 0u,
         RMR_LAYOUT_PINNED_RUNTIME, RMR_LAYOUT_REGION_PRESENT, 0u}
    };
    static const rmr_static_layout_manifest_t pinned_manifest = {
        RMR_STATIC_LAYOUT_ABI_VERSION, 2u, 512u, 64u, 1u,
        RMR_LAYOUT_BASE_RELATIVE, 0u, 0u, pinned_regions
    };
    rmr_static_layout_binding_t pinned_binding;
    CHECK_STATUS("bind-pinned",
                 RmR_StaticLayout_Bind(&pinned_manifest,
                                       arena_a,
                                       sizeof(arena_a),
                                       1u,
                                       &pinned_binding),
                 RMR_LAYOUT_OK);
    CHECK_STATUS("pinned-rejects-base-change",
                 RmR_StaticLayout_Rebind(&pinned_binding,
                                         arena_b,
                                         sizeof(arena_b),
                                         2u),
                 RMR_LAYOUT_ERR_POLICY);
  }

  {
    static const rmr_static_region_t fault_regions[] = {
        {30u, 0u, 64u, 16u, 0u, 0u,
         RMR_LAYOUT_REMAP_ONLY, RMR_LAYOUT_REGION_FAULT, 0u}
    };
    static const rmr_static_layout_manifest_t fault_manifest = {
        RMR_STATIC_LAYOUT_ABI_VERSION, 3u, 64u, 16u, 1u,
        RMR_LAYOUT_BASE_RELATIVE, 0u, 0u, fault_regions
    };
    rmr_static_layout_binding_t fault_binding;
    CHECK_STATUS("bind-fault-map",
                 RmR_StaticLayout_Bind(&fault_manifest,
                                       arena_a,
                                       sizeof(arena_a),
                                       1u,
                                       &fault_binding),
                 RMR_LAYOUT_OK);
    CHECK_STATUS("fault-region-not-readable",
                 RmR_StaticLayout_Resolve(&fault_binding, 30u, 0u, 1u, &resolved),
                 RMR_LAYOUT_ERR_STATE);
  }

  printf("{\"suite\":\"rmr_static_layout\",\"passed\":%d,\"total\":%d,"
         "\"signature\":\"0x%016llx\",\"status\":\"%s\"}\n",
         passed,
         total,
         (unsigned long long)signature,
         passed == total ? "PASS" : "FAIL");

  return passed == total ? 0 : 1;
}
