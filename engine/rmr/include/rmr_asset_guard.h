#ifndef RMR_ASSET_GUARD_H
#define RMR_ASSET_GUARD_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RMR_ASSET_RIGHT_READ_U32      0x00000001u
#define RMR_ASSET_RIGHT_EXEC_U32      0x00000002u
#define RMR_ASSET_RIGHT_MUTATE_U32    0x00000004u
#define RMR_ASSET_RIGHT_ZERO_THRUST_U32 0x80000000u

#define RMR_ASSET_CAP_GENERIC_U32     0x00000001u
#define RMR_ASSET_CAP_INLINE_ASM_U32  0x00000002u
#define RMR_ASSET_CAP_LINEAR_REG_U32  0x00000004u
#define RMR_ASSET_CAP_ARM64_U32       0x00000010u
#define RMR_ASSET_CAP_ARM32_U32       0x00000020u
#define RMR_ASSET_CAP_X86_64_U32      0x00000040u
#define RMR_ASSET_CAP_RISCV64_U32     0x00000080u

#define RMR_ASSET_STATUS_OK_U32        0x00000000u
#define RMR_ASSET_STATUS_FAILSAFE_U32  0x00000001u
#define RMR_ASSET_STATUS_ROLLBACK_U32  0x00000002u
#define RMR_ASSET_STATUS_WATCHDOG_U32  0x00000004u
#define RMR_ASSET_STATUS_RIGHTS_U32    0x00000008u
#define RMR_ASSET_STATUS_ZERO_THRUST_U32 0x00000010u
#define RMR_ASSET_STATUS_ARG_U32       0x00000020u

typedef struct RmR_AssetGuardState {
  uint32_t acc;
  uint32_t len_seen;
  uint32_t entropy_milli;
  uint32_t phase;
} RmR_AssetGuardState;

typedef struct RmR_AssetGuard {
  RmR_AssetGuardState current;
  RmR_AssetGuardState checkpoint;
  uint32_t required_rights;
  uint32_t denied_rights;
  uint32_t capability_bits;
  uint32_t effective_bits;
  uint32_t reg_linear_base;
  uint32_t reg_linear_span;
  uint32_t watchdog_limit;
  uint32_t watchdog_count;
  uint32_t rollback_count;
  uint32_t failsafe_count;
  uint32_t status;
} RmR_AssetGuard;

void RmR_AssetGuard_Init(RmR_AssetGuard *guard, uint32_t required_rights, uint32_t watchdog_limit);
void RmR_AssetGuard_Checkpoint(RmR_AssetGuard *guard);
void RmR_AssetGuard_Rollback(RmR_AssetGuard *guard, uint32_t reason_bits);
uint32_t RmR_AssetGuard_ProbeInlineAsm(RmR_AssetGuard *guard);
uint32_t RmR_AssetGuard_Enter(RmR_AssetGuard *guard, const uint8_t *data, size_t len, uint32_t granted_rights);

#ifdef __cplusplus
}
#endif

#endif
