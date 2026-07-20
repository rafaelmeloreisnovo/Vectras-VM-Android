// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
/* rmr_apk_module.h - módulo determinístico para compilação de APK multi-arquitetura */
#ifndef RMR_APK_MODULE_H
#define RMR_APK_MODULE_H

#include "rmr_types.h"

typedef struct {
  u32 abi_mask;
  u32 min_sdk;
  u32 target_sdk;
  u32 version_code;
  u32 release_signing;
  u32 termux_mode;
  u32 host_abi_mask;
  u32 hw_cacheline_bytes;
  u32 hw_page_bytes;
} RmR_ApkProfile;

typedef struct {
  u32 abi_mask;
  u32 host_abi_mask;
  u32 hw_cacheline_bytes;
  u32 hw_page_bytes;
  u32 compile_sdk;
  u32 min_sdk;
  u32 target_sdk;
  u32 ndk_major;
  u32 build_tools_major;
  u32 build_tools_minor;
  u32 build_tools_patch;
} RmR_ApkStableIdentity;

/* ABI bits: 0=armeabi-v7a, 1=arm64-v8a, 2=x86, 3=x86_64 */
#define RMR_APK_ABI_ARMEABI_V7A (1u << 0)
#define RMR_APK_ABI_ARM64_V8A   (1u << 1)
#define RMR_APK_ABI_X86         (1u << 2)
#define RMR_APK_ABI_X86_64      (1u << 3)
#define RMR_APK_ABI_UNIVERSAL   (RMR_APK_ABI_ARMEABI_V7A | RMR_APK_ABI_ARM64_V8A | RMR_APK_ABI_X86 | RMR_APK_ABI_X86_64)

void RmR_ApkModule_InitProfile(RmR_ApkProfile *out);
void RmR_ApkModule_AutotuneProfile(RmR_ApkProfile *out);
void RmR_ApkModule_FillStableIdentity(const RmR_ApkProfile *profile,
                                      u32 compile_sdk,
                                      u32 ndk_major,
                                      u32 build_tools_major,
                                      u32 build_tools_minor,
                                      u32 build_tools_patch,
                                      RmR_ApkStableIdentity *out);
u32 RmR_ApkModule_DetectHostAbiMask(void);
u32 RmR_ApkModule_DetectTermuxLike(const char *termux_prefix,
                                   const char *home_path,
                                   const char *shell_path);
u64 RmR_ApkModule_DeterministicFingerprint(const u8 *data, u32 len, u64 seed);
u64 RmR_ApkModule_StableFingerprint(const RmR_ApkStableIdentity *stable, u64 seed);
int RmR_ApkModule_ValidateSigningInputs(const char *keystore,
                                        const char *store_password,
                                        const char *key_alias,
                                        const char *key_password);

/*
 * Gera um comando determinístico de build release + assinatura legítima.
 * Retorna tamanho escrito (sem '\0') ou 0 em falha.
 */
u32 RmR_ApkModule_BuildPlan(const RmR_ApkProfile *profile,
                            const char *keystore,
                            const char *store_password,
                            const char *key_alias,
                            const char *key_password,
                            char *out,
                            u32 out_cap);

#endif
