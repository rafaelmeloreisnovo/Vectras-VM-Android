/* SPDX-License-Identifier: GPL-2.0-only */
/* Hosted JNI transport only; the classifier itself remains freestanding. */
#include <jni.h>
#include <stdint.h>
#include "core/raf_runtime_elf.h"

JNIEXPORT jint JNICALL
Java_com_vectras_vm_runtime_RuntimeAbiSnapshot_nativeProbeElfHeader(
        JNIEnv *env, jclass clazz, jbyteArray input, jint used, jint host) {
    uint8_t bytes[64];
    jsize length;
    (void)clazz;
    if (input == (jbyteArray)0) return (jint)RAF_ELF_NULL_INPUT;
    length = (*env)->GetArrayLength(env, input);
    if (used < 0 || used > 64 || used > length)
        return (jint)RAF_ELF_TRUNCATED;
    if (used > 0) {
        (*env)->GetByteArrayRegion(env, input, 0, used, (jbyte *)bytes);
        if ((*env)->ExceptionCheck(env)) return (jint)RAF_ELF_BAD_HEADER;
    }
    return (jint)raf_runtime_elf_header_probe(
            bytes, (uint32_t)used, (raf_runtime_host_abi)host);
}
