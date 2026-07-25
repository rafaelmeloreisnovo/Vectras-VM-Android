#include "lowlevel_abi.h"

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

__attribute__((noreturn, used, visibility("default")))
void vectra_freestanding_probe_entry(void) {
    uint8_t adaptive_bridge = 0U;
    const int status = abi_entry_validate_interop(
        1U,
        0U,
        1U,
        0U,
        &adaptive_bridge);

    g_vectra_freestanding_probe_result =
        ((uint32_t)(status == VECTRA_LL_ERR_OK) << 1U) |
        (uint32_t)(adaptive_bridge != 0U);

    for (;;) {
        __asm__ __volatile__("" ::: "memory");
    }
}
