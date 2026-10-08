#ifndef VECTRA_CATALYTIC_VCAT_BOOTSTRAP_H
#define VECTRA_CATALYTIC_VCAT_BOOTSTRAP_H

#include "vcat.h"

/*
 * Pure TAR bootstrap planner.
 *
 * Contract:
 * - no libc / stdlib headers or calls;
 * - no heap, JNI, Java, syscalls, filesystem or dynamic loader;
 * - consumes one 512-byte TAR header already present in memory;
 * - emits only numeric metadata and safety flags.
 *
 * Actual file creation/extraction remains a separate kernel/host boundary.
 */

#define VCAT_TAR_BLOCK_BYTES 512u

#define VCAT_TAR_TYPE_UNKNOWN   0u
#define VCAT_TAR_TYPE_REGULAR   1u
#define VCAT_TAR_TYPE_DIRECTORY 2u
#define VCAT_TAR_TYPE_SYMLINK   3u

#define VCAT_TAR_F_MAGIC_OK       0x00000001u
#define VCAT_TAR_F_SAFE_PATH      0x00000002u
#define VCAT_TAR_F_SIZE_OK        0x00000004u
#define VCAT_TAR_F_MODE_OK        0x00000008u
#define VCAT_TAR_F_SUPPORTED_TYPE 0x00000010u
#define VCAT_TAR_F_SAFE_LINK      0x00000020u
#define VCAT_TAR_F_ZERO_BLOCK     0x00000040u
#define VCAT_TAR_F_READY          0x00000080u
#define VCAT_TAR_F_CHECKSUM_OK    0x00000100u

typedef struct {
    vcat_u32 flags;
    vcat_u32 type;
    vcat_u32 mode;
    vcat_u32 size_bytes;
    vcat_u32 path_digest;
    vcat_u32 link_digest;
} vcat_tar_entry;

VCAT_API void vcat_tar_inspect(const vcat_u8 *header,
                               vcat_u32 length,
                               vcat_tar_entry *out);

#endif
