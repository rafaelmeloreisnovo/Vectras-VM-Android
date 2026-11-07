/* SPDX-License-Identifier: MIT — HOSTED TEST ONLY, never in core. */
#include <stdio.h>
#include "raf_bitops.h"

static int total, failed;
#define CHECK(label, expr) do { ++total; if (!(expr)) { \
    fprintf(stderr, "FAIL %s %d\n", label, __LINE__); ++failed; \
} } while (0)

int main(void) {
    const raf_u32 samples[] = {0u, 1u, 0xffffffffu, 0x80000000u,
                                0x01234567u, 0xdeadbeefu, 0x01020304u};
    raf_u8 bytes[6] = {0xa5u,0u,0u,0u,0u,0x5au};
    raf_u32 v = 0xfeedc0deu;
    raf_u32 i;
    CHECK("reverse known", raf_reverse_bits32(0x01234567u)==0xe6a2c480u);
    CHECK("bswap known", raf_bswap32(0x01020304u)==0x04030201u);
    CHECK("rotl zero", raf_rotl32(0x12345678u, 0u)==0x12345678u);
    CHECK("rotl 32", raf_rotl32(0x12345678u, 32u)==0x12345678u);
    CHECK("rotl 33", raf_rotl32(0x12345678u, 33u)==0x2468acf0u);
    CHECK("rotl 31", raf_rotl32(0x00000001u, 31u)==0x80000000u);
    CHECK("rotl max", raf_rotl32(0x12345678u, 0xffffffffu)==raf_rotl32(0x12345678u,31u));
    for (i = 0u; i < (raf_u32)(sizeof(samples)/sizeof(samples[0])); ++i) {
        CHECK("reverse involution", raf_reverse_bits32(raf_reverse_bits32(samples[i]))==samples[i]);
        CHECK("bswap involution", raf_bswap32(raf_bswap32(samples[i]))==samples[i]);

    }
    CHECK("store bounded", raf_store_be32(bytes+1u,4u,0x12345678u)==RAF_OK &&
          bytes[0]==0xa5u && bytes[1]==0x12u && bytes[2]==0x34u &&
          bytes[3]==0x56u && bytes[4]==0x78u && bytes[5]==0x5au);
    CHECK("load bounded", raf_load_be32(bytes+1u,4u,&v)==RAF_OK && v==0x12345678u);
    CHECK("no read short", raf_load_be32(bytes,3u,&v)==RAF_NO_SPACE && v==0x12345678u);
    CHECK("no write short", raf_store_be32(bytes+1u,3u,0u)==RAF_NO_SPACE && bytes[1]==0x12u);
    CHECK("load null", raf_load_be32(0,4u,&v)==RAF_INVALID && v==0x12345678u);
    CHECK("load null out", raf_load_be32(bytes,4u,0)==RAF_INVALID);
    CHECK("store null", raf_store_be32(0,4u,0u)==RAF_INVALID);
    CHECK("read zero", raf_load_be32(bytes,0u,&v)==RAF_NO_SPACE && v==0x12345678u);
    printf("RAFCODE_BITOPS_TESTS=%d FAILURES=%d\n",total,failed);
    return failed ? 1 : 0;
}
