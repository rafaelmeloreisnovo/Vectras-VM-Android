/* SPDX-License-Identifier: MIT */
/* Freestanding demonstration: core dispatch + CRC32C + Linux write + exit.
 * NOT a PRoot implementation, filesystem sandbox, QEMU, or Android APK. */
#include "raf_core.h"
#include "raf_linux_abi.h"

int raf_linux_demo(void) {
    static const raf_u8 in[] = "/alpha/./beta/../gamma";
    static const raf_u8 expected[] = "/alpha/gamma";
    static const raf_u8 check[] = "123456789";
    static const raf_u8 newline[] = "\n";
    raf_u8 output[64];
    raf_cmd cmd = { RAF_CMD_NORMALIZE, in, (raf_u32)(sizeof(in)-1u),
                    output, (raf_u32)sizeof(output), (const raf_bind *)0, 0u };
    raf_result result;
    raf_u32 i;
    raf_dispatch(&cmd, &result);
    if (result.status != RAF_OK || result.bytes_written != (sizeof(expected)-1u)) return 21;
    for (i=0u; i<result.bytes_written; ++i) {
        if (output[i]!=expected[i]) return 22;
    }
    cmd.kind = RAF_CMD_CRC32C;
    cmd.input = check;
    cmd.input_n = (raf_u32)(sizeof(check)-1u);
    raf_dispatch(&cmd, &result);
    if (result.status != RAF_OK || result.checksum != 0xe3069283u) return 23;
    if (raf_linux_write(1, output, (raf_u32)(sizeof(expected)-1u)) !=
        (raf_linux_word)(sizeof(expected)-1u)) return 24;
    if (raf_linux_write(1, newline, 1u) != 1) return 25;
    return 0;
}
