#include "vectra_lowlevel_backend.h"
#include "hardware_profile_bridge_internal.h"

#if defined(__x86_64__)
#include <emmintrin.h>
#include <nmmintrin.h>
#endif

#include "rmr_lowlevel.h"

#if defined(__x86_64__) && (defined(__GNUC__) || defined(__clang__))
#define VECTRA_TARGET_SSE42 __attribute__((target("sse4.2")))
#else
#define VECTRA_TARGET_SSE42
#endif

static uint32_t vectra_crc32c_software(uint32_t initial, const uint8_t* data, size_t len) {
    uint32_t crc = initial;
    for (size_t i = 0; i < len; ++i) {
        crc ^= data[i];
        for (uint32_t b = 0; b < 8u; ++b) {
            const uint32_t mask = (uint32_t)-(int32_t)(crc & 1u);
            crc = (crc >> 1u) ^ (0x82F63B78u & mask);
        }
    }
    return crc;
}

static uint32_t vectra_checksum32_x86_64(const uint8_t* data, size_t len, uint32_t seed) {
    return rmr_lowlevel_checksum32(data, len, seed);
}

static uint32_t vectra_reduce_xor_x86_64(const uint8_t* data, size_t len) {
    /*
     * Global contract: reduce_xor follows rmr_lowlevel_reduce_xor semantics
     * (byte-to-lane fold + rotate-left by 3 per byte), not plain XOR parity.
     */
    return rmr_lowlevel_reduce_xor(data, len);
}

#if defined(__x86_64__)
static VECTRA_TARGET_SSE42 uint32_t vectra_crc32c_x86_64(uint32_t initial, const uint8_t* data, size_t len) {
    uint64_t crc = initial;
    size_t i = 0;
    for (; i + 8u <= len; i += 8u) {
        uint64_t v;
        __builtin_memcpy(&v, data + i, sizeof(v));
        crc = _mm_crc32_u64(crc, v);
    }
    uint32_t crc32 = (uint32_t)crc;
    for (; i < len; ++i) crc32 = _mm_crc32_u8(crc32, data[i]);
    return crc32;
}
#endif

int vectra_backend_x86_64_available(uint32_t simd_mask) {
    return (simd_mask & VECTRA_SIMD_SSE2) != 0u;
}

void vectra_backend_bind_x86_64(vectra_lowlevel_backend_vtable_t* out) {
    const uint32_t simd_mask = vectra_hw_runtime_simd_mask();
    const int has_sse42 = (simd_mask & VECTRA_SIMD_SSE42) != 0u;

    out->name = has_sse42 ? "x86_64-sse42" : "x86_64-softcrc";
    out->reduce_xor = vectra_reduce_xor_x86_64;
    out->checksum32 = vectra_checksum32_x86_64;
#if defined(__x86_64__)
    out->crc32c = has_sse42 ? vectra_crc32c_x86_64 : vectra_crc32c_software;
#else
    out->crc32c = vectra_crc32c_software;
#endif
}

#undef VECTRA_TARGET_SSE42
