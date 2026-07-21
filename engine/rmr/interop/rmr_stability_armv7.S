// SPDX-License-Identifier: GPL-2.0-only
.syntax unified
.text
.align 5
.global rmr_stability_step_armv7
.type rmr_stability_step_armv7, %function
// r0 = state ptr (4x32-bit)
// r1 = events ptr
// r2 = count
// returns r0 = stability score
rmr_stability_step_armv7:
    push {r4-r8, lr}
    ldmia r0, {r3-r6}
1:
    cmp r2, #0
    beq 2f
    ldrb r7, [r1], #1
    eor r3, r3, r4
    add r4, r4, r7
    ror r5, r5, #5
    add r5, r5, r3
    eor r6, r6, r5
    add r3, r3, r6
    subs r2, r2, #1
    b 1b
2:
    stmia r0, {r3-r6}
    eor r0, r3, r4
    add r0, r0, r5
    eor r0, r0, r6
    bic r0, r0, #0x80000000
    pop {r4-r8, pc}
.size rmr_stability_step_armv7, .-rmr_stability_step_armv7
