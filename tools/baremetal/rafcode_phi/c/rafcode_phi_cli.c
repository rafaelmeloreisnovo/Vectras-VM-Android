#include "rafcode_phi_abi.h"

#include <stdio.h>

static raf_u32 rafphi_len(const char *s) {
  raf_u32 n = 0u;
  if (!s) return 0u;
  while (s[n] != '\0') n++;
  return n;
}

static raf_u32 rafphi_ascii_eq_full(const char *a, const char *b) {
  raf_u32 i = 0u;
  if (!a || !b) return 0u;
  while (a[i] != '\0' && b[i] != '\0') {
    if (a[i] != b[i]) return 0u;
    i++;
  }
  return (a[i] == '\0' && b[i] == '\0') ? 1u : 0u;
}

static const char *rafphi_arch_name(rafphi_arch_t arch) {
  switch (arch) {
    case RAFPHI_ARCH_AARCH64: return "aarch64";
    case RAFPHI_ARCH_ARMV7: return "armv7";
    case RAFPHI_ARCH_X86_64: return "x86_64";
    case RAFPHI_ARCH_RISCV64: return "riscv64";
    default: return "unknown";
  }
}

static raf_u32 rafphi_vecbit_threshold_for_arch(rafphi_arch_t arch) {
  /*
   * VecBit is a continuity policy, not an ISA-validity oracle. The frozen
   * ARMv7 token set has a valid RET(BX LR)->BRK(BKPT #0) neighbor distance
   * of 17 bits, while the older cross-ISA default was 16. Keep the policy
   * explicit per ISA instead of rejecting valid ARMv7 instruction words.
   */
  return (arch == RAFPHI_ARCH_ARMV7) ? 17u : 16u;
}

static rafphi_arch_t rafphi_arch_from_arg(const char *arg) {
  if (rafphi_ascii_eq_full(arg, "aarch64")) return RAFPHI_ARCH_AARCH64;
  if (rafphi_ascii_eq_full(arg, "armv7") || rafphi_ascii_eq_full(arg, "armeabi-v7a")) return RAFPHI_ARCH_ARMV7;
  if (rafphi_ascii_eq_full(arg, "x86_64")) return RAFPHI_ARCH_X86_64;
  if (rafphi_ascii_eq_full(arg, "riscv64")) return RAFPHI_ARCH_RISCV64;
  return RAFPHI_ARCH_UNKNOWN;
}

static int rafphi_write_hex_file(const char *path, rafphi_arch_t arch, const raf_u32 *words, raf_u32 count, raf_u32 crc32c) {
  FILE *f = fopen(path, "wb");
  if (!f) return 0;
  fprintf(f, "RAFCODE_PHI_HEX v=0x%08X arch=%u words=%u crc32c=0x%08X\n", RAFPHI_BIN_VERSION, (raf_u32)arch, count, crc32c);
  raf_u32 i;
  for (i = 0u; i < count; i++) fprintf(f, "%08X\n", words[i]);
  fclose(f);
  return 1;
}

static int rafphi_write_bin_file(const char *path, rafphi_arch_t arch, const raf_u32 *words, raf_u32 count, raf_u32 crc32c) {
  FILE *f = fopen(path, "wb");
  if (!f) return 0;
  rafphi_bin_header_t h;
  h.magic = RAFPHI_BIN_MAGIC;
  h.version = RAFPHI_BIN_VERSION;
  h.arch = (raf_u32)arch;
  h.word_count = count;
  h.crc32c = crc32c;
  h.flags = 0u;
  if (fwrite(&h, sizeof(h), 1u, f) != 1u) { fclose(f); return 0; }
  if (count > 0u && fwrite(words, sizeof(raf_u32), count, f) != count) { fclose(f); return 0; }
  fclose(f);
  return 1;
}

int main(int argc, char **argv) {
  const char *tokens[512];
  raf_u32 lens[512];
  raf_u32 out_words[512];
  raf_u32 n = 0u;
  const char *out_prefix = 0;
  rafphi_arch_t arch = rafphi_detect_native_arch();

  int i = 1;
  while (i < argc) {
    if (rafphi_ascii_eq_full(argv[i], "--arch")) {
      if ((i + 1) >= argc) { fprintf(stderr, "missing value for --arch\n"); return 2; }
      arch = rafphi_arch_from_arg(argv[i + 1]);
      if (arch == RAFPHI_ARCH_UNKNOWN) { fprintf(stderr, "invalid --arch value\n"); return 2; }
      i += 2;
      continue;
    }
    if (rafphi_ascii_eq_full(argv[i], "--out-prefix")) {
      if ((i + 1) >= argc) { fprintf(stderr, "missing value for --out-prefix\n"); return 2; }
      out_prefix = argv[i + 1];
      i += 2;
      continue;
    }
    if (n >= 512u) { fprintf(stderr, "token limit reached (512)\n"); return 2; }
    tokens[n] = argv[i];
    lens[n] = rafphi_len(argv[i]);
    n++;
    i++;
  }

  if (n == 0u) {
    fprintf(stderr, "usage: %s [--arch aarch64|armv7|x86_64|riscv64] [--out-prefix PATH] TOKEN...\n", argv[0]);
    return 2;
  }

  rafphi_emit_stats_t stats = rafphi_emit_block_hex_arch(tokens, lens, n, arch, out_words, 512u);
  const raf_u32 vecbit_threshold = rafphi_vecbit_threshold_for_arch(arch);
  rafphi_vecbit_t vec = rafphi_vecbit_verify(out_words, stats.accepted, vecbit_threshold);

  printf("rafcode_phi.arch=%s\n", rafphi_arch_name(arch));
  printf("rafcode_phi.tokens=%u\n", n);
  printf("rafcode_phi.accepted=%u\n", stats.accepted);
  printf("rafcode_phi.rejected=%u\n", stats.rejected);
  printf("rafcode_phi.crc32c=0x%08X\n", stats.crc32c);
  printf("rafcode_phi.vecbit.threshold=%u\n", vecbit_threshold);
  printf("rafcode_phi.vecbit.max_neighbor_distance=%u\n", vec.max_neighbor_distance);
  printf("rafcode_phi.vecbit.violations=%u\n", vec.violations);
  printf("rafcode_phi.vecbit.chain_hash=0x%016llX\n", (unsigned long long)vec.chain_hash_fnv1a64);
  printf("rafcode_phi.compile_ok=%u\n", vec.compile_ok);

  raf_u32 w;
  for (w = 0u; w < stats.accepted; w++) printf("word[%u]=0x%08X\n", w, out_words[w]);

  if (out_prefix) {
    char hex_path[1024];
    char bin_path[1024];
    int hn = snprintf(hex_path, sizeof(hex_path), "%s.hex", out_prefix);
    int bn = snprintf(bin_path, sizeof(bin_path), "%s.bin", out_prefix);
    if (hn <= 0 || bn <= 0 || hn >= (int)sizeof(hex_path) || bn >= (int)sizeof(bin_path)) {
      fprintf(stderr, "output path too long\n");
      return 3;
    }
    if (!rafphi_write_hex_file(hex_path, arch, out_words, stats.accepted, stats.crc32c)) return 3;
    if (!rafphi_write_bin_file(bin_path, arch, out_words, stats.accepted, stats.crc32c)) return 3;
    printf("rafcode_phi.hex=%s\n", hex_path);
    printf("rafcode_phi.bin=%s\n", bin_path);
  }

  return (stats.rejected == 0u && vec.compile_ok == 1u) ? 0 : 1;
}
