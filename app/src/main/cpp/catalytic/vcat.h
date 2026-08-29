#ifndef VECTRA_CATALYTIC_VCAT_H
#define VECTRA_CATALYTIC_VCAT_H

/*
 * VCAT: deterministic freestanding catalytic core.
 * Runtime contract: no libc headers, allocator, threads, syscalls, JNI, Java,
 * dynamic loading, symbol-name lookup or external runtime dependency.
 *
 * The kernel boundary is data, not execution: actual PRoot/rootfs installation,
 * file I/O and process creation are deliberately outside this pure core.
 */

typedef unsigned char vcat_u8;
typedef unsigned short vcat_u16;
typedef unsigned int vcat_u32;

#define VCAT_LANES 16u

#define VCAT_KIND_UNKNOWN 0u
#define VCAT_KIND_ELF 1u
#define VCAT_KIND_DEX 2u
#define VCAT_KIND_ZIP 3u
#define VCAT_KIND_TAR 4u
#define VCAT_KIND_OAT 5u
#define VCAT_KIND_VDEX 6u
#define VCAT_KIND_QCOW2 7u
#define VCAT_KIND_ANDROID_SPARSE 8u

#define VCAT_F_VALID 0x00000001u
#define VCAT_F_ARCHIVE 0x00000002u
#define VCAT_F_BYTECODE 0x00000004u
#define VCAT_F_IMAGE 0x00000008u
#define VCAT_F_ELF32 0x00000010u
#define VCAT_F_ELF64 0x00000020u
#define VCAT_F_LITTLE_ENDIAN 0x00000040u
#define VCAT_F_EXEC 0x00000080u
#define VCAT_F_SHARED 0x00000100u
#define VCAT_F_READY 0x00000200u

#define VCAT_BOUNDARY_PURE 0u
#define VCAT_BOUNDARY_KERNEL_REQUIRED 1u

#if defined(__clang__) || defined(__GNUC__)
#define VCAT_API __attribute__((visibility("hidden")))
#else
#define VCAT_API
#endif

typedef struct {
    const vcat_u8 *p;
    vcat_u32 n;
    vcat_u32 id;
    vcat_u32 phase;
} vcat_job;

typedef struct {
    vcat_u32 id[VCAT_LANES];
    vcat_u32 phase[VCAT_LANES];
    vcat_u32 kind[VCAT_LANES];
    vcat_u32 flags[VCAT_LANES];
    vcat_u32 digest[VCAT_LANES];
    vcat_u32 source_index[VCAT_LANES];
    vcat_u32 ready_mask;
    vcat_u32 aggregate;
    vcat_u32 kernel_boundary;
} vcat_plan;

VCAT_API void vcat_classify(const vcat_u8 *p, vcat_u32 n,
                            vcat_u32 *kind, vcat_u32 *flags,
                            vcat_u32 *digest);
VCAT_API void vcat_plan16(const vcat_job *jobs, vcat_u32 seed,
                          vcat_plan *out);

#endif
