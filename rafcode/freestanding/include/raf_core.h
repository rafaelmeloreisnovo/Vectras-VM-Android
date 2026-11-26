/* SPDX-License-Identifier: MIT */
/* RAFCODE-Phi — independent C11 freestanding interface. */
#ifndef RAF_FS_CORE_H
#define RAF_FS_CORE_H

/* Compiler+target contract; does not include libc, Linux, JNI or PRoot. */
typedef unsigned char raf_u8;
typedef unsigned int raf_u32;
typedef unsigned long long raf_u64;
typedef __UINTPTR_TYPE__ raf_uptr;
_Static_assert(sizeof(raf_u8) == 1, "requires 8-bit byte");
_Static_assert(sizeof(raf_u32) == 4, "requires 32-bit unsigned int");
_Static_assert(sizeof(raf_u64) == 8, "requires 64-bit unsigned long long");
_Static_assert(sizeof(raf_uptr) >= sizeof(void *), "uintptr width");

#ifndef RAF_ENABLE_AARCH64_CRC32C
#define RAF_ENABLE_AARCH64_CRC32C 0
#endif
#if RAF_ENABLE_AARCH64_CRC32C && (!defined(__aarch64__) || !defined(__ARM_FEATURE_CRC32))
#error "CRC32C hardware opt-in requires AArch64 + CRC32 feature"
#endif
#define RAF_PATH_LIMIT 4096u
#define RAF_RULE_LIMIT 16u

typedef enum {
    RAF_OK = 0,
    RAF_TOKEN_VAZIO = 1,
    RAF_INVALID = 2,
    RAF_NO_SPACE = 3,
    RAF_ESCAPE_DENIED = 4,
    RAF_NO_MAPPING = 5,
    RAF_UNSUPPORTED = 6
} raf_status;

typedef struct {
    void *base;           /* external, writable, caller-owned storage */
    raf_u32 capacity;
    raf_u32 cursor;
} raf_arena;

/* Output is written ONLY on success; previous cursor unchanged on failure. */
raf_status raf_arena_take(raf_arena *arena, raf_u32 n,
                          raf_u32 alignment, void **out);
void raf_zero(void *dst, raf_u32 n);
raf_u32 raf_crc32c(const void *bytes, raf_u32 n);

typedef struct {
    const raf_u8 *guest; raf_u32 guest_n;
    const raf_u8 *host;  raf_u32 host_n;
} raf_bind;

/* No implicit C-string scanning. All byte strings carry explicit lengths.
 * All input/output ranges must not overlap. Linux-like slash syntax only.
 * No symlinks, mounts, ptrace, kernel isolation or filesystem checks. */
raf_status raf_path_normalize(const raf_u8 *input, raf_u32 n,
                              raf_u8 *out, raf_u32 capacity,
                              raf_u32 *out_n);
raf_status raf_path_translate(const raf_bind *rules, raf_u32 count,
                              const raf_u8 *canonical, raf_u32 n,
                              raf_u8 *out, raf_u32 capacity,
                              raf_u32 *out_n);

typedef enum {
    RAF_CMD_NORMALIZE = 1,
    RAF_CMD_TRANSLATE = 2,
    RAF_CMD_CRC32C = 3
} raf_cmd_kind;

typedef struct {
    raf_u32 kind;
    const void *input;
    raf_u32 input_n;
    void *output;
    raf_u32 output_capacity;
    const raf_bind *rules;
    raf_u32 rule_count;
} raf_cmd;

typedef struct {
    raf_status status;
    raf_u32 bytes_written;
    raf_u32 checksum;   /* only meaningful for RAF_CMD_CRC32C */
} raf_result;

/* Single bounded dispatch: no allocator, syscalls, callbacks, threads or I/O.
 * Passing a void* context is intentionally unnecessary: command is complete. */
void raf_dispatch(const raf_cmd *cmd, raf_result *result);

/* OS interaction contract: explicitly supplied by a host, never resolved
 * through imported syscall symbols. This callback is OPTIONAL; no host means
 * RAF_TOKEN_VAZIO. It cannot grant extra kernel privileges. */
typedef raf_status (*raf_port_fn)(void *opaque, raf_u32 service,
                                  const void *input, raf_u32 n,
                                  void *output, raf_u32 capacity,
                                  raf_u32 *produced);
typedef struct {
    void *opaque;
    raf_port_fn invoke;
} raf_port;
raf_status raf_port_invoke(const raf_port *port, raf_u32 service,
                           const void *input, raf_u32 n,
                           void *output, raf_u32 capacity,
                           raf_u32 *produced);

#endif
