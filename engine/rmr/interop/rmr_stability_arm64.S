// SPDX-License-Identifier: GPL-2.0-only
.text
.align 6
.global rmr_stability_step_arm64
.type rmr_stability_step_arm64, %function
// x0 = pointer to state[4] (s0..s3)
// x1 = pointer to fault event byte stream
// x2 = number of events
// returns x0 = stability score (0..2^63-1)
// linear register cadence: x0..x8
rmr_stability_step_arm64:
    ldp x3, x4, [x0]
    ldp x5, x6, [x0, #16]
    mov x7, x1
    mov x8, x2
1:
    cbz x8, 2f
    ldrb w1, [x7], #1
    eor x3, x3, x4
    add x4, x4, x1
    ror x5, x5, #7
    add x5, x5, x3
    eor x6, x6, x5
    eor x1, x6, x5
    add x3, x3, x1
    sub x8, x8, #1
    b 1b
2:
    stp x3, x4, [x0]
    stp x5, x6, [x0, #16]
    eor x0, x3, x4
    add x0, x0, x5
    eor x0, x0, x6
    and x0, x0, #0x7fffffffffffffff
    ret
.size rmr_stability_step_arm64, .-rmr_stability_step_arm64
