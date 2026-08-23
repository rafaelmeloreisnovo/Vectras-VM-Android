#ifndef RAFCODE_PHI_ABI_H
#define RAFCODE_PHI_ABI_H

/*
 * RAFCODE❤️PHI ABI
 * C (casca) -> ASM (núcleo) com saída em palavras hex determinísticas.
 */

/* Evita dependência de libc/stdint: tipos fixos locais. */
typedef unsigned char      raf_u8;
typedef unsigned short     raf_u16;
typedef unsigned int       raf_u32;
typedef unsigned long long raf_u64;

typedef enum {
  RAFPHI_ARCH_UNKNOWN = 0,
  RAFPHI_ARCH_AARCH64 = 1,
  RAFPHI_ARCH_AARCH   = RAFPHI_ARCH_AARCH64, /* compatibility alias */
  RAFPHI_ARCH_X86_64  = 2,
  RAFPHI_ARCH_RISCV64 = 3,
  RAFPHI_ARCH_ARMV7   = 4
} rafphi_arch_t;

typedef enum {
  RAFPHI_OP_NOP_A64 = 0xD503201Fu,
  RAFPHI_OP_RET_A64 = 0xD65F03C0u,
  RAFPHI_OP_BRK_A64 = 0xD4200000u,
  RAFPHI_OP_HLT_A64 = 0xD4400000u,

  /* ARM state / ARMv7-A encodings, emitted as little-endian 32-bit words. */
  RAFPHI_OP_NOP_ARMV7 = 0xE320F000u,
  RAFPHI_OP_RET_ARMV7 = 0xE12FFF1Eu, /* bx lr */
  RAFPHI_OP_BRK_ARMV7 = 0xE1200070u, /* bkpt #0 */
  RAFPHI_OP_HLT_ARMV7 = 0xE320F003u, /* wfi */

  /* Compatibility alias retained for older callers. */
  RAFPHI_OP_NOP = RAFPHI_OP_NOP_A64
} rafphi_opcode_t;

typedef struct {
  raf_u32 opcode_hex;
  raf_u32 flags;
} rafphi_emit_word_t;

typedef struct {
  raf_u32 accepted;
  raf_u32 rejected;
  raf_u32 crc32c;
} rafphi_emit_stats_t;

typedef struct {
  raf_u32 violations;
  raf_u32 max_neighbor_distance;
  raf_u64 chain_hash_fnv1a64;
  raf_u32 compile_ok;
} rafphi_vecbit_t;

typedef struct {
  raf_u32 magic;
  raf_u32 version;
  raf_u32 arch;
  raf_u32 word_count;
  raf_u32 crc32c;
  raf_u32 flags;
} rafphi_bin_header_t;

#define RAFPHI_BIN_MAGIC 0x52414650u /* "RAFP" */
#define RAFPHI_BIN_VERSION 0x00010000u

/* Hook ASM/C: serializa uma palavra hex para buffer de saída. */
extern raf_u32 rafphi_emit_word_abi(raf_u32, raf_u32 *, raf_u32, raf_u32);
extern raf_u32 rafphi_emit_word_asm(raf_u32 opcode_hex, raf_u32 *out_words, raf_u32 cap_words, raf_u32 write_index);

/* Casca C: parser mínimo de mnemônicos autorais para hex. */
raf_u32 rafphi_encode_token_to_hex(const char *token, raf_u32 token_len, raf_u32 *out_hex);
raf_u32 rafphi_encode_token_to_hex_arch(rafphi_arch_t arch, const char *token, raf_u32 token_len, raf_u32 *out_hex);

/* Casca C: emite bloco de tokens em formato hex determinístico. */
rafphi_emit_stats_t rafphi_emit_block_hex(const char **tokens, const raf_u32 *token_lens, raf_u32 token_count,
                                          raf_u32 *out_words, raf_u32 cap_words);
rafphi_emit_stats_t rafphi_emit_block_hex_arch(const char **tokens, const raf_u32 *token_lens, raf_u32 token_count,
                                               rafphi_arch_t arch, raf_u32 *out_words, raf_u32 cap_words);

rafphi_arch_t rafphi_detect_native_arch(void);
rafphi_vecbit_t rafphi_vecbit_verify(const raf_u32 *words, raf_u32 word_count, raf_u32 max_neighbor_distance);

#endif
