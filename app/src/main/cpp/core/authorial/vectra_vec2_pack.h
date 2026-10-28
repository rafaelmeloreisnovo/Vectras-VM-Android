#ifndef VECTRA_AUTHORIAL_VEC2_PACK_H
#define VECTRA_AUTHORIAL_VEC2_PACK_H

/*
 * RAFAELIA/Vectra scoped addition: packed signed 16-bit lanes in one 32-bit word.
 * Source license: GPL-2.0-only in this Vectras derivative repository.
 * Boundary: pure C, no libc, malloc, GC, syscall, JNI, OS or hidden ABI state.
 * Logical lane layout: bits[15:0]=x, bits[31:16]=y; all inputs are masked.
 * Never left-shift a signed lane: a negative value or sign-bit result may be UB.
 * Hosted JNI adapter owns conversion to jint and any Java array allocation.
 */

typedef __UINT32_TYPE__ vectra_vec2_u32;
typedef __INT32_TYPE__ vectra_vec2_i32;

_Static_assert(sizeof(vectra_vec2_u32) == 4, "Vectra route lane requires 32-bit words");
_Static_assert(sizeof(vectra_vec2_i32) == 4, "Vectra route lane requires 32-bit input");

static inline vectra_vec2_u32 vectra_vec2_pack_bits(vectra_vec2_i32 x,
                                                    vectra_vec2_i32 y) {
    /* 0xffff is the lane mask, 16 is the lane width; unsigned shift is defined. */
    return ((((vectra_vec2_u32)y) & 0xffffu) << 16u) |
           (((vectra_vec2_u32)x) & 0xffffu);
}

#endif /* VECTRA_AUTHORIAL_VEC2_PACK_H */
