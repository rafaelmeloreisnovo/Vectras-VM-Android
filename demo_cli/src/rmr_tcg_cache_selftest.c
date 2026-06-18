/* rmr_tcg_cache_selftest - cache TCG: miss como próxima instrução e
 * mutação seletiva de bits (delta XOR) em vez de substituição do conjunto.
 *
 * Invariantes cobertas:
 *  1. Miss inicial é estado explícito (contado), não falha.
 *  2. Primeira inserção grava apenas os bits 1 do payload (popcount),
 *     preservando o físico esparso do ISOraf.
 *  3. Reinserção idêntica não toca nenhum bit (delta XOR == 0).
 *  4. Mutação de 1 bit no payload custa exatamente 1 bit gravado.
 *  5. Colapso é pegajoso (HOTFIX em rmr_tcg_cache.c): bloco em colapso
 *     responde MISS — estado registrado, não silêncio.
 *  6. Replay: a mesma sequência reproduz a mesma identidade ISOraf.
 */
#include "rmr_tcg_cache.h"

#include <stdio.h>

static RmR_TCGCache g_cache_a;
static RmR_TCGCache g_cache_b;

static u64 popcount_bytes(const u8 *buf, u32 len) {
  u64 total = 0u;
  for (u32 i = 0u; i < len; ++i) {
    u8 v = buf[i];
    while (v) {
      v = (u8)(v & (v - 1u));
      total += 1u;
    }
  }
  return total;
}

static u8 run_sequence(RmR_TCGCache *cache,
                       const u8 *payload_v1,
                       const u8 *payload_v2,
                       u32 len,
                       u32 crc_tag) {
  u32 host_size = 0u;
  if (RmR_TCGCache_Lookup(cache, crc_tag, &host_size) != (const u8 *)0) return 0u;
  if (!RmR_TCGCache_Insert(cache, crc_tag, payload_v1, len, 1u, RMR_ATTR_TOROID, 4u)) return 0u;
  if (RmR_TCGCache_Lookup(cache, crc_tag, &host_size) == (const u8 *)0) return 0u;
  if (!RmR_TCGCache_Insert(cache, crc_tag, payload_v1, len, 1u, RMR_ATTR_TOROID, 4u)) return 0u;
  if (!RmR_TCGCache_Insert(cache, crc_tag, payload_v2, len, 1u, RMR_ATTR_TOROID, 4u)) return 0u;
  return 1u;
}

int main(void) {
  static const u32 crc_tag = 0x52414661u; /* "RAFa" */
  u8 payload_v1[32];
  u8 payload_v2[32];
  u32 host_size = 0u;
  const u8 *hit;
  u64 flipped_initial;
  u64 expected_popcount;

  for (u32 i = 0u; i < (u32)sizeof(payload_v1); ++i) {
    payload_v1[i] = (u8)((i * 37u + 11u) & 0xFFu);
    payload_v2[i] = payload_v1[i];
  }
  payload_v2[7] ^= 0x10u; /* mutação de exatamente 1 bit */

  RmR_TCGCache_Init(&g_cache_a);

  /* 1. miss inicial: estado explícito */
  if (RmR_TCGCache_Lookup(&g_cache_a, crc_tag, &host_size) != (const u8 *)0) {
    printf("FAIL lookup vazio deveria ser miss\n");
    return 1;
  }
  if (g_cache_a.total_misses != 1u || host_size != 0u) {
    printf("FAIL miss nao registrado como estado\n");
    return 1;
  }

  /* 2. primeira insercao: apenas bits 1 sao gravados */
  if (!RmR_TCGCache_Insert(&g_cache_a, crc_tag, payload_v1,
                           (u32)sizeof(payload_v1), 1u, RMR_ATTR_TOROID, 4u)) {
    printf("FAIL insert #1\n");
    return 1;
  }
  expected_popcount = popcount_bytes(payload_v1, (u32)sizeof(payload_v1));
  flipped_initial = RmR_TCGCache_DeltaBitsFlipped(&g_cache_a);
  if (flipped_initial != expected_popcount) {
    printf("FAIL delta inicial=%llu esperado popcount=%llu\n",
           (unsigned long long)flipped_initial,
           (unsigned long long)expected_popcount);
    return 1;
  }

  hit = RmR_TCGCache_Lookup(&g_cache_a, crc_tag, &host_size);
  if (!hit || host_size != (u32)sizeof(payload_v1)) {
    printf("FAIL hit pos-insert\n");
    return 1;
  }
  for (u32 i = 0u; i < host_size; ++i) {
    if (hit[i] != payload_v1[i]) {
      printf("FAIL roundtrip byte %u\n", (unsigned)i);
      return 1;
    }
  }
  if (RmR_TCGCache_HitRatio(&g_cache_a) != 50u) {
    printf("FAIL hit ratio=%u esperado 50\n",
           (unsigned)RmR_TCGCache_HitRatio(&g_cache_a));
    return 1;
  }

  /* 3. reinsercao identica: delta XOR zero, nenhum bit tocado */
  if (!RmR_TCGCache_Insert(&g_cache_a, crc_tag, payload_v1,
                           (u32)sizeof(payload_v1), 1u, RMR_ATTR_TOROID, 4u)) {
    printf("FAIL insert #2 (identico)\n");
    return 1;
  }
  if (RmR_TCGCache_DeltaBitsFlipped(&g_cache_a) != flipped_initial) {
    printf("FAIL reinsercao identica tocou bits: %llu != %llu\n",
           (unsigned long long)RmR_TCGCache_DeltaBitsFlipped(&g_cache_a),
           (unsigned long long)flipped_initial);
    return 1;
  }

  /* 4. mutacao de 1 bit custa exatamente 1 bit gravado */
  if (!RmR_TCGCache_Insert(&g_cache_a, crc_tag, payload_v2,
                           (u32)sizeof(payload_v2), 1u, RMR_ATTR_TOROID, 4u)) {
    printf("FAIL insert #3 (mutado)\n");
    return 1;
  }
  if (RmR_TCGCache_DeltaBitsFlipped(&g_cache_a) != flipped_initial + 1u) {
    printf("FAIL mutacao de 1 bit custou %llu bits\n",
           (unsigned long long)(RmR_TCGCache_DeltaBitsFlipped(&g_cache_a) - flipped_initial));
    return 1;
  }

  /* conteudo residente confere com payload_v2 lendo o store direto
   * (o bloco esta em colapso, entao Lookup responde MISS por politica) */
  {
    u64 base_bit = g_cache_a.index[0].toroidal_addr << 3u;
    u64 mutated_bit = base_bit + (7u * 8u) + 4u; /* byte 7, bit 4 (0x10) */
    u8 expected = (u8)((payload_v2[7] >> 4u) & 1u);
    if (RmR_ISOraf_GetBit(&g_cache_a.store, mutated_bit) != expected) {
      printf("FAIL bit mutado nao residente no store\n");
      return 1;
    }
  }

  /* 5. colapso pegajoso: bloco reescrito responde MISS explicito */
  if (RmR_TCGCache_CollapseCount(&g_cache_a) != 1u) {
    printf("FAIL collapse_count=%u esperado 1\n",
           (unsigned)RmR_TCGCache_CollapseCount(&g_cache_a));
    return 1;
  }
  if (RmR_TCGCache_Lookup(&g_cache_a, crc_tag, &host_size) != (const u8 *)0) {
    printf("FAIL bloco em colapso deveria responder miss\n");
    return 1;
  }

  /* 6. replay deterministico: mesma sequencia, mesma identidade ISOraf */
  RmR_TCGCache_Init(&g_cache_b);
  if (!run_sequence(&g_cache_b, payload_v1, payload_v2,
                    (u32)sizeof(payload_v1), crc_tag)) {
    printf("FAIL replay sequence\n");
    return 1;
  }
  if (RmR_ISOraf_Identity(&g_cache_a.store) != RmR_ISOraf_Identity(&g_cache_b.store)) {
    printf("FAIL replay identidade divergente\n");
    return 1;
  }
  if (RmR_TCGCache_DeltaBitsFlipped(&g_cache_b) != RmR_TCGCache_DeltaBitsFlipped(&g_cache_a)) {
    printf("FAIL replay contadores divergentes\n");
    return 1;
  }

  printf("OK tcg cache selftest flipped=%llu preserved_pct=%u collapse=%u hit_ratio=%u\n",
         (unsigned long long)RmR_TCGCache_DeltaBitsFlipped(&g_cache_a),
         (unsigned)RmR_TCGCache_DeltaPreservedPct(&g_cache_a),
         (unsigned)RmR_TCGCache_CollapseCount(&g_cache_a),
         (unsigned)RmR_TCGCache_HitRatio(&g_cache_a));
  return 0;
}
