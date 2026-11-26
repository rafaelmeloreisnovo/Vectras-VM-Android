/* SPDX-License-Identifier: MIT */
#include "raf_core.h"

raf_status raf_port_invoke(const raf_port *port, raf_u32 service,
                           const void *input, raf_u32 n,
                           void *output, raf_u32 capacity,
                           raf_u32 *produced) {
    raf_status st;
    raf_u32 got = 0u;
    if (!produced) return RAF_INVALID;
    *produced = 0u;
    if (!port || !port->invoke) return RAF_TOKEN_VAZIO;
    if (!service || (!input && n) || (!output && capacity)) return RAF_INVALID;
    st = port->invoke(port->opaque, service, input, n, output, capacity, &got);
    if (st == RAF_OK && got <= capacity) {
        *produced = got;
        return RAF_OK;
    }
    return st == RAF_OK ? RAF_INVALID : st;
}
