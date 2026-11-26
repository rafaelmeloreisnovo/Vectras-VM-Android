/* SPDX-License-Identifier: MIT */
#include "raf_core.h"

void raf_dispatch(const raf_cmd *cmd, raf_result *result) {
    raf_u32 n = 0u;
    raf_status st;
    if (!result) return;
    result->status = RAF_INVALID;
    result->bytes_written = 0u;
    result->checksum = 0u;
    if (!cmd || !cmd->input) return;
    switch (cmd->kind) {
        case RAF_CMD_NORMALIZE:
            st = raf_path_normalize((const raf_u8 *)cmd->input,
                                    cmd->input_n, (raf_u8 *)cmd->output,
                                    cmd->output_capacity, &n);
            result->status = st;
            if (st == RAF_OK) result->bytes_written = n;
            break;
        case RAF_CMD_TRANSLATE:
            st = raf_path_translate(cmd->rules, cmd->rule_count,
                                    (const raf_u8 *)cmd->input, cmd->input_n,
                                    (raf_u8 *)cmd->output,
                                    cmd->output_capacity, &n);
            result->status = st;
            if (st == RAF_OK) result->bytes_written = n;
            break;
        case RAF_CMD_CRC32C:
            result->checksum = raf_crc32c(cmd->input, cmd->input_n);
            result->status = RAF_OK;
            break;
        default:
            result->status = RAF_UNSUPPORTED;
            break;
    }
}
