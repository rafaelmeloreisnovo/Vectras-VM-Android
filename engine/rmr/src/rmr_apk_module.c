// SPDX-License-Identifier: GPL-2.0-only
// Copyright (C) Rafael M. R. — rafaelmeloreisnovo
#include "rmr_apk_module.h"
#include "rmr_hw_detect.h"

static u32 rmr_strlen(const char *s){
  u32 n = 0u;
  if(!s) return 0u;
  while(s[n] != '\0') n++;
  return n;
}

static int rmr_streq(const char *a, const char *b){
  u32 i = 0u;
  if(!a || !b) return 0;
  while(a[i] != '\0' && b[i] != '\0'){
    if(a[i] != b[i]) return 0;
    i++;
  }
  return (a[i] == '\0' && b[i] == '\0') ? 1 : 0;
}

static int rmr_contains(const char *a, const char *b){
  u32 i, j;
  if(!a || !b) return 0;
  if(b[0] == '\0') return 1;
  for(i = 0u; a[i] != '\0'; ++i){
    for(j = 0u; b[j] != '\0' && a[i + j] != '\0'; ++j){
      if(a[i + j] != b[j]) break;
    }
    if(b[j] == '\0') return 1;
  }
  return 0;
}

static u32 rmr_append_text(char *out, u32 cap, u32 pos, const char *text){
  u32 i = 0u;
  if(!out || !text || pos >= cap) return 0u;
  while(text[i] != '\0'){
    if(pos + 1u >= cap) return 0u;
    out[pos++] = text[i++];
  }
  out[pos] = '\0';
  return pos;
}

static u32 rmr_append_hex32(char *out, u32 cap, u32 pos, u32 v){
  static const char HEX[16] = {
    '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
  };
  int shift;
  if(!out || pos >= cap) return 0u;
  for(shift = 28; shift >= 0; shift -= 4){
    if(pos + 1u >= cap) return 0u;
    out[pos++] = HEX[(v >> (u32)shift) & 0xFu];
  }
  out[pos] = '\0';
  return pos;
}

static u64 rmr_mix64(u64 x){
  x ^= x >> 33;
  x *= 0xFF51AFD7ED558CCDULL;
  x ^= x >> 33;
  x *= 0xC4CEB9FE1A85EC53ULL;
  x ^= x >> 33;
  return x;
}

void RmR_ApkModule_InitProfile(RmR_ApkProfile *out){
  if(!out) return;
  out->abi_mask = RMR_APK_ABI_UNIVERSAL;
  out->min_sdk = 23u;
  out->target_sdk = 28u;
  out->version_code = 365u;
  out->release_signing = 1u;
  out->termux_mode = 0u;
  out->host_abi_mask = 0u;
  out->hw_cacheline_bytes = 64u;
  out->hw_page_bytes = 4096u;
}

u32 RmR_ApkModule_DetectHostAbiMask(void){
#if defined(__aarch64__)
  return RMR_APK_ABI_ARM64_V8A;
#elif defined(__arm__) || defined(_M_ARM)
  return RMR_APK_ABI_ARMEABI_V7A;
#elif defined(__x86_64__) || defined(_M_X64)
  return RMR_APK_ABI_X86_64;
#elif defined(__i386__) || defined(_M_IX86)
  return RMR_APK_ABI_X86;
#else
  return 0u;
#endif
}

u32 RmR_ApkModule_DetectTermuxLike(const char *termux_prefix,
                                   const char *home_path,
                                   const char *shell_path){
  if(rmr_contains(termux_prefix, "com.termux")) return 1u;
  if(rmr_contains(home_path, "/data/data/com.termux")) return 1u;
  if(rmr_contains(shell_path, "/com.termux/")) return 1u;
  return 0u;
}

void RmR_ApkModule_AutotuneProfile(RmR_ApkProfile *out){
  RmR_HW_Info hw;
  if(!out) return;
  RmR_HW_Detect(&hw);
  out->host_abi_mask = RmR_ApkModule_DetectHostAbiMask();
  out->hw_cacheline_bytes = hw.cacheline_bytes;
  out->hw_page_bytes = hw.page_bytes;
}

void RmR_ApkModule_FillStableIdentity(const RmR_ApkProfile *profile,
                                      u32 compile_sdk,
                                      u32 ndk_major,
                                      u32 build_tools_major,
                                      u32 build_tools_minor,
                                      u32 build_tools_patch,
                                      RmR_ApkStableIdentity *out){
  if(!profile || !out) return;
  out->abi_mask = profile->abi_mask;
  out->host_abi_mask = profile->host_abi_mask ? profile->host_abi_mask : RmR_ApkModule_DetectHostAbiMask();
  out->hw_cacheline_bytes = profile->hw_cacheline_bytes;
  out->hw_page_bytes = profile->hw_page_bytes;
  out->compile_sdk = compile_sdk;
  out->min_sdk = profile->min_sdk;
  out->target_sdk = profile->target_sdk;
  out->ndk_major = ndk_major;
  out->build_tools_major = build_tools_major;
  out->build_tools_minor = build_tools_minor;
  out->build_tools_patch = build_tools_patch;
}

u64 RmR_ApkModule_DeterministicFingerprint(const u8 *data, u32 len, u64 seed){
  u32 i;
  u64 state = seed ^ 0x9E3779B97F4A7C15ULL;
  u64 m00 = 0xA24BAED4963EE407ULL;
  u64 m01 = 0x9FB21C651E98DF25ULL;
  u64 m10 = 0xC13FA9A902A6328FULL;
  u64 m11 = 0x91E10DA5C79E7B1DULL;

  if(!data && len != 0u) return 0ULL;

  for(i = 0u; i < len; ++i){
    u64 x = (u64)data[i] + ((u64)i << 8);
    u64 a = (m00 ^ x) + (m01 * (x | 1ULL));
    u64 b = (m10 + (x << 1)) ^ (m11 + (x >> 1));
    state ^= (a + (b << 1));
    state = (state << 7) | (state >> (64 - 7));
    m00 ^= state + 0xD6E8FEB86659FD93ULL;
    m11 += state ^ 0x94D049BB133111EBULL;
  }

  return rmr_mix64(state ^ (u64)len);
}

u64 RmR_ApkModule_StableFingerprint(const RmR_ApkStableIdentity *stable, u64 seed){
  u64 h;
  if(!stable) return 0ULL;
  h = seed ^ 0x243F6A8885A308D3ULL;
  h ^= rmr_mix64((u64)stable->abi_mask | ((u64)stable->host_abi_mask << 32));
  h ^= rmr_mix64((u64)stable->hw_page_bytes | ((u64)stable->hw_cacheline_bytes << 32));
  h ^= rmr_mix64((u64)stable->compile_sdk | ((u64)stable->min_sdk << 16) | ((u64)stable->target_sdk << 32));
  h ^= rmr_mix64((u64)stable->ndk_major | ((u64)stable->build_tools_major << 16)
      | ((u64)stable->build_tools_minor << 32) | ((u64)stable->build_tools_patch << 48));
  return rmr_mix64(h ^ 0x9E3779B97F4A7C15ULL);
}

int RmR_ApkModule_ValidateSigningInputs(const char *keystore,
                                        const char *store_password,
                                        const char *key_alias,
                                        const char *key_password){
  if(rmr_strlen(keystore) == 0u) return 0;
  if(rmr_strlen(store_password) == 0u) return 0;
  if(rmr_strlen(key_alias) == 0u) return 0;
  if(rmr_strlen(key_password) == 0u) return 0;
  if(rmr_streq(key_alias, "androiddebugkey")) return 0;
  return 1;
}

u32 RmR_ApkModule_BuildPlan(const RmR_ApkProfile *profile,
                            const char *keystore,
                            const char *store_password,
                            const char *key_alias,
                            const char *key_password,
                            char *out,
                            u32 out_cap){
  u32 pos = 0u;
  u32 host_mask;
  if(!profile || !out || out_cap < 64u) return 0u;
  out[0] = '\0';

  if(profile->release_signing != 0u){
    if(!RmR_ApkModule_ValidateSigningInputs(keystore, store_password, key_alias, key_password)) return 0u;
  }

  if(profile->termux_mode != 0u){
    pos = rmr_append_text(out, out_cap, pos, "GRADLE_USER_HOME=.gradle TERMUX_BUILD=1 ");
    if(pos == 0u) return 0u;
  }

  pos = rmr_append_text(out, out_cap, pos, "./gradlew --no-daemon :app:clean :app:assembleRelease");
  if(pos == 0u) return 0u;

  pos = rmr_append_text(out, out_cap, pos, " -Pvectras.universal=true -Pvectras.abiMask=0x");
  if(pos == 0u) return 0u;
  pos = rmr_append_hex32(out, out_cap, pos, profile->abi_mask);
  if(pos == 0u) return 0u;

  host_mask = profile->host_abi_mask ? profile->host_abi_mask : RmR_ApkModule_DetectHostAbiMask();
  pos = rmr_append_text(out, out_cap, pos, " -Pvectras.hostAbiMask=0x");
  if(pos == 0u) return 0u;
  pos = rmr_append_hex32(out, out_cap, pos, host_mask);
  if(pos == 0u) return 0u;

  pos = rmr_append_text(out, out_cap, pos, " -Pvectras.hw.cacheline=0x");
  if(pos == 0u) return 0u;
  pos = rmr_append_hex32(out, out_cap, pos, profile->hw_cacheline_bytes);
  if(pos == 0u) return 0u;

  pos = rmr_append_text(out, out_cap, pos, " -Pvectras.hw.page=0x");
  if(pos == 0u) return 0u;
  pos = rmr_append_hex32(out, out_cap, pos, profile->hw_page_bytes);
  if(pos == 0u) return 0u;

  pos = rmr_append_text(out, out_cap, pos, " -Pvectras.compliance.profile=IEEE_NIST_W3C_RFC_GDPR_LGPD");
  if(pos == 0u) return 0u;
  pos = rmr_append_text(out, out_cap, pos, " -Pvectras.signing.ethical=true");
  if(pos == 0u) return 0u;

  if(profile->release_signing != 0u){
    pos = rmr_append_text(out, out_cap, pos, " -Pandroid.injected.signing.store.file=");
    if(pos == 0u) return 0u;
    pos = rmr_append_text(out, out_cap, pos, keystore);
    if(pos == 0u) return 0u;

    pos = rmr_append_text(out, out_cap, pos, " -Pandroid.injected.signing.store.password=");
    if(pos == 0u) return 0u;
    pos = rmr_append_text(out, out_cap, pos, store_password);
    if(pos == 0u) return 0u;

    pos = rmr_append_text(out, out_cap, pos, " -Pandroid.injected.signing.key.alias=");
    if(pos == 0u) return 0u;
    pos = rmr_append_text(out, out_cap, pos, key_alias);
    if(pos == 0u) return 0u;

    pos = rmr_append_text(out, out_cap, pos, " -Pandroid.injected.signing.key.password=");
    if(pos == 0u) return 0u;
    pos = rmr_append_text(out, out_cap, pos, key_password);
    if(pos == 0u) return 0u;
  }

  return pos;
}
