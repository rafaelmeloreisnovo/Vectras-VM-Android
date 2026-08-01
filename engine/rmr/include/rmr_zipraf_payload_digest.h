// SPDX-License-Identifier: GPL-2.0-only
#ifndef RMR_ZIPRAF_PAYLOAD_DIGEST_H
#define RMR_ZIPRAF_PAYLOAD_DIGEST_H

#include <stddef.h>
#include <stdint.h>

#include "rmr_zipraf_archive.h"
#include "rmr_zipraf_page_graph.h"

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_ZIPRAF_DIGEST_OK 0
#define RMR_ZIPRAF_DIGEST_ERR_ARG -200
#define RMR_ZIPRAF_DIGEST_ERR_RANGE -201
#define RMR_ZIPRAF_DIGEST_ERR_ALGORITHM -202
#define RMR_ZIPRAF_DIGEST_ERR_MATERIALIZATION_REQUIRED -203
#define RMR_ZIPRAF_DIGEST_ERR_PROVIDER -204

#define RMR_ZIPRAF_DIGEST_SCOPE_STORED_BYTES 1u
#define RMR_ZIPRAF_DIGEST_SCOPE_LOGICAL_BYTES 2u

#define RMR_ZIPRAF_DIGEST_ALGORITHM_SHA256 RMR_ZIPRAF_DIGEST_SHA256
#define RMR_ZIPRAF_DIGEST_ALGORITHM_BLAKE3 RMR_ZIPRAF_DIGEST_BLAKE3

int RmR_ZiprafDigest_Sha256(const void *data,
                            size_t length,
                            uint8_t out_digest[RMR_ZIPRAF_DIGEST_BYTES]);

int RmR_ZiprafDigest_Blake3External(
    const void *data,
    size_t length,
    uint8_t out_digest[RMR_ZIPRAF_DIGEST_BYTES]);

const char *RmR_ZiprafDigest_Blake3ProviderVersion(void);

int RmR_ZiprafArchive_DigestEntry(
    const uint8_t *archive,
    size_t archive_size,
    const RmR_ZiprafArchiveEntry *entry,
    uint8_t scope,
    uint8_t algorithm,
    uint8_t out_digest[RMR_ZIPRAF_DIGEST_BYTES]);

int RmR_ZiprafDigest_EqualConstantTime(
    const uint8_t left[RMR_ZIPRAF_DIGEST_BYTES],
    const uint8_t right[RMR_ZIPRAF_DIGEST_BYTES]);

#ifdef __cplusplus
}
#endif

#endif
