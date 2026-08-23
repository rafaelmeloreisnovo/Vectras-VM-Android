#include "rafcode_phi_abi.h"

static raf_u32 rafphi_ascii_eq(const char *a, raf_u32 a_len, const char *b) {
  raf_u32 i = 0u;
  while (i < a_len && b[i] != '\0') {
    if (a[i] != b[i]) {
      return 0u;
    }
    i++;
  }
  return (i == a_len && b[i] == '\0') ? 1u : 0u;
}

rafphi_arch_t rafphi_detect_native_arch(void) {
#if defined(__aarch64__)
  return RAFPHI_ARCH_AARCH64;
#elif defined(__arm__) || defined(RMR_ARCH_ARM32)
  return RAFPHI_ARCH_ARMV7;
#elif defined(__x86_64__)
  return RAFPHI_ARCH_X86_64;
#elif defined(__riscv) && (__riscv_xlen == 64)
  return RAFPHI_ARCH_RISCV64;
#else
  return RAFPHI_ARCH_UNKNOWN;
#endif
}

raf_u32 rafphi_encode_token_to_hex_arch(rafphi_arch_t arch, const char *token, raf_u32 token_len, raf_u32 *out_hex) {
  if (!token || token_len == 0u || !out_hex) {
    return 0u;
  }

  if (arch == RAFPHI_ARCH_X86_64) {
    if (rafphi_ascii_eq(token, token_len, "NOP")) { *out_hex = 0x00000090u; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "RET")) { *out_hex = 0x000000C3u; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "BRK")) { *out_hex = 0x000000CCu; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "HLT")) { *out_hex = 0x000000F4u; return 1u; }
    return 0u;
  }

  if (arch == RAFPHI_ARCH_RISCV64) {
    if (rafphi_ascii_eq(token, token_len, "NOP")) { *out_hex = 0x00000013u; return 1u; } /* addi x0,x0,0 */
    if (rafphi_ascii_eq(token, token_len, "RET")) { *out_hex = 0x00008067u; return 1u; } /* jalr x0,0(x1) */
    if (rafphi_ascii_eq(token, token_len, "BRK")) { *out_hex = 0x00100073u; return 1u; } /* ebreak */
    if (rafphi_ascii_eq(token, token_len, "HLT")) { *out_hex = 0x10500073u; return 1u; } /* wfi */
    return 0u;
  }

  if (arch == RAFPHI_ARCH_ARMV7) {
    if (rafphi_ascii_eq(token, token_len, "NOP")) { *out_hex = RAFPHI_OP_NOP_ARMV7; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "RET")) { *out_hex = RAFPHI_OP_RET_ARMV7; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "BRK")) { *out_hex = RAFPHI_OP_BRK_ARMV7; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "HLT")) { *out_hex = RAFPHI_OP_HLT_ARMV7; return 1u; }
    return 0u;
  }

  if (arch == RAFPHI_ARCH_AARCH64) {
    if (rafphi_ascii_eq(token, token_len, "NOP")) { *out_hex = RAFPHI_OP_NOP_A64; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "RET")) { *out_hex = RAFPHI_OP_RET_A64; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "BRK")) { *out_hex = RAFPHI_OP_BRK_A64; return 1u; }
    if (rafphi_ascii_eq(token, token_len, "HLT")) { *out_hex = RAFPHI_OP_HLT_A64; return 1u; }
    return 0u;
  }

  return 0u;
}

raf_u32 rafphi_encode_token_to_hex(const char *token, raf_u32 token_len, raf_u32 *out_hex) {
  return rafphi_encode_token_to_hex_arch(RAFPHI_ARCH_AARCH64, token, token_len, out_hex);
}

/* CRC32C simplificado autoral (polinômio castagnoli refletido). */
static raf_u32 rafphi_crc32c_u32(raf_u32 crc, raf_u32 v) {
  raf_u32 x = crc ^ v;
  raf_u32 i;
  for (i = 0u; i < 32u; i++) {
    raf_u32 lsb = x & 1u;
    x >>= 1u;
    if (lsb) {
      x ^= 0x82F63B78u;
    }
  }
  return x;
}

rafphi_emit_stats_t rafphi_emit_block_hex(const char **tokens, const raf_u32 *token_lens, raf_u32 token_count,
                                          raf_u32 *out_words, raf_u32 cap_words) {
  return rafphi_emit_block_hex_arch(tokens, token_lens, token_count, rafphi_detect_native_arch(), out_words, cap_words);
}

rafphi_emit_stats_t rafphi_emit_block_hex_arch(const char **tokens, const raf_u32 *token_lens, raf_u32 token_count,
                                               rafphi_arch_t arch, raf_u32 *out_words, raf_u32 cap_words) {
  rafphi_emit_stats_t stats;
  stats.accepted = 0u;
  stats.rejected = 0u;
  stats.crc32c = 0u;

  if (!tokens || !token_lens || !out_words || cap_words == 0u) {
    return stats;
  }

  raf_u32 write_index = 0u;
  raf_u32 i;
  for (i = 0u; i < token_count; i++) {
    raf_u32 hex = 0u;
    if (!rafphi_encode_token_to_hex_arch(arch, tokens[i], token_lens[i], &hex)) {
      stats.rejected++;
      continue;
    }

    raf_u32 written = rafphi_emit_word_abi(hex, out_words, cap_words, write_index);
    if (written == 0u) {
      stats.rejected++;
      continue;
    }

    write_index += written;
    stats.accepted++;
    stats.crc32c = rafphi_crc32c_u32(stats.crc32c, hex);
  }

  return stats;
}
