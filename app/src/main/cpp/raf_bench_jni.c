/* SPDX-License-Identifier: GPL-2.0-only */
/* Hosted JNI boundary: NOT part of the freestanding kernel. */
#include <jni.h>
#include <stdint.h>
#include "core/raf_bench_probe.h"

JNIEXPORT jint JNICALL
Java_com_vectras_vm_benchmark_FreestandingMicrobench_nativeFold(
        JNIEnv *env, jclass clazz, jint seed, jint iterations) {
    (void)env;
    (void)clazz;
    if (iterations < 0 || iterations > 8192) return (jint)0;
    return (jint)raf_bench_xorshift32((uint32_t)seed, (uint32_t)iterations);
}
