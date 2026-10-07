#include "lowlevel_abi.h"
#include "catalytic/vcat.h"
#include "catalytic/vcat_bootstrap.h"

#if !defined(__GNUC__) && !defined(__clang__)
#error "The freestanding link probe requires GNU/Clang attributes."
#endif

/*
 * Link-only witness. It deliberately performs no syscall and never returns:
 * execution is a separate, optional device gate. The volatile result keeps a
 * real reference to abi_core_freestanding so the linker cannot prove the
 * archive unused while --gc-sections is active.
 */
static volatile uint32_t g_vectra_freestanding_probe_result;
static volatile vcat_plan g_vectra_catalytic_probe_plan;
static volatile vcat_tar_entry g_vectra_tar_safe_probe;
static volatile vcat_tar_entry g_vectra_tar_unsafe_probe;

static const vcat_u8 kVcatElf32Arm[20] = {
    0x7f, 0x45, 0x4c, 0x46, 1, 1, 1, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 40, 0
};
static const vcat_u8 kVcatDex[8] = {
    0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00
};
static const vcat_u8 kVcatZip[4] = {0x50, 0x4b, 0x03, 0x04};
static const vcat_u8 kVcatQcow2[4] = {0x51, 0x46, 0x49, 0xfb};

static const vcat_u8 kVcatTarSafe[VCAT_TAR_BLOCK_BYTES] = {
    [0] = 'u', [1] = 's', [2] = 'r', [3] = '/', [4] = 'b', [5] = 'i',
    [6] = 'n', [7] = '/', [8] = 'p', [9] = 'r', [10] = 'o', [11] = 'o',
    [12] = 't',
    [100] = '0', [101] = '0', [102] = '0', [103] = '0', [104] = '7',
    [105] = '5', [106] = '5',
    [124] = '0', [125] = '0', [126] = '0', [127] = '0', [128] = '0',
    [129] = '0', [130] = '0', [131] = '0', [132] = '0', [133] = '0',
    [134] = '1',
    [156] = '0',
    [257] = 'u', [258] = 's', [259] = 't', [260] = 'a', [261] = 'r'
};

static const vcat_u8 kVcatTarUnsafe[VCAT_TAR_BLOCK_BYTES] = {
    [0] = '.', [1] = '.', [2] = '/', [3] = 'e', [4] = 's', [5] = 'c',
    [6] = 'a', [7] = 'p', [8] = 'e',
    [100] = '0', [101] = '0', [102] = '0', [103] = '0', [104] = '7',
    [105] = '5', [106] = '5',
    [124] = '0', [125] = '0', [126] = '0', [127] = '0', [128] = '0',
    [129] = '0', [130] = '0', [131] = '0', [132] = '0', [133] = '0',
    [134] = '1',
    [156] = '0',
    [257] = 'u', [258] = 's', [259] = 't', [260] = 'a', [261] = 'r'
};

static const vcat_job kVcatJobs[VCAT_LANES] = {
    {kVcatElf32Arm, 20u, 0u, 0u}, {kVcatDex, 8u, 1u, 0u},
    {kVcatZip, 4u, 2u, 0u},       {kVcatQcow2, 4u, 3u, 0u},
    {kVcatElf32Arm, 20u, 4u, 1u}, {kVcatDex, 8u, 5u, 1u},
    {kVcatZip, 4u, 6u, 1u},       {kVcatQcow2, 4u, 7u, 1u},
    {kVcatElf32Arm, 20u, 8u, 2u}, {kVcatDex, 8u, 9u, 2u},
    {kVcatZip, 4u, 10u, 2u},      {kVcatQcow2, 4u, 11u, 2u},
    {kVcatElf32Arm, 20u, 12u, 3u},{kVcatDex, 8u, 13u, 3u},
    {kVcatZip, 4u, 14u, 3u},      {kVcatQcow2, 4u, 15u, 3u}
};

__attribute__((noreturn, used, visibility("default")))
void vectra_freestanding_probe_entry(void) {
    uint8_t adaptive_bridge = 0U;
    const int status = abi_entry_validate_interop(
        1U,
        0U,
        1U,
        0U,
        &adaptive_bridge);

    vcat_plan16(kVcatJobs, 0x00144000u,
                (vcat_plan *)&g_vectra_catalytic_probe_plan);
    vcat_tar_inspect(kVcatTarSafe, VCAT_TAR_BLOCK_BYTES,
                     (vcat_tar_entry *)&g_vectra_tar_safe_probe);
    vcat_tar_inspect(kVcatTarUnsafe, VCAT_TAR_BLOCK_BYTES,
                     (vcat_tar_entry *)&g_vectra_tar_unsafe_probe);

    g_vectra_freestanding_probe_result =
        ((uint32_t)(status == VECTRA_LL_ERR_OK) << 1U) |
        (uint32_t)(adaptive_bridge != 0U) |
        ((g_vectra_catalytic_probe_plan.ready_mask == 0x0000ffffu) << 2U) |
        ((g_vectra_catalytic_probe_plan.kernel_boundary ==
          VCAT_BOUNDARY_KERNEL_REQUIRED) << 3U) |
        (((g_vectra_tar_safe_probe.flags & VCAT_TAR_F_READY) != 0u) << 4U) |
        ((((g_vectra_tar_unsafe_probe.flags & VCAT_TAR_F_SAFE_PATH) == 0u) &&
          ((g_vectra_tar_unsafe_probe.flags & VCAT_TAR_F_READY) == 0u)) << 5U);

    for (;;) {
        __asm__ __volatile__("" ::: "memory");
    }
}
