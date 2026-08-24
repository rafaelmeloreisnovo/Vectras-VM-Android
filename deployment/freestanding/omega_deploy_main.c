#include "lowlevel_abi.h"

/*
 * Pure deployment witness for the existing abi_core_freestanding archive.
 * No hosted headers, heap, libc, JNI or Android API are reachable here.
 * The architecture entry/exit boundary lives in omega_deploy_start_armv7.S.
 */
__attribute__((used, visibility("default")))
int omega_deploy_main(void) {
    uint8_t adaptive_bridge = 0U;
    const int status = abi_entry_validate_interop(
        1U,
        0U,
        1U,
        0U,
        &adaptive_bridge);

    (void)adaptive_bridge;
    return status == VECTRA_LL_ERR_OK ? 0 : 64;
}
