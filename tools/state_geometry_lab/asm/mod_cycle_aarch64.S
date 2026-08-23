// AArch64 modular cycle kernel
// x0: pointer data
// x1: length
// x2: modulus (0 is normalized to 1, matching C/Python contract)
// returns x0 = accumulated xor hash

.text
.global mod_cycle_aarch64
.type mod_cycle_aarch64, %function
mod_cycle_aarch64:
    // Keep the native hot path behavior equivalent to the higher-level
    // implementations: modulus 0 means modulus 1, never a zero divisor.
    cmp w2, #0
    cinc w2, w2, eq

    mov x3, #0
    mov x4, #0x7C15
    movk x4, #0x7F4A, lsl #16
    movk x4, #0xB97F, lsl #32
    movk x4, #0x9E37, lsl #48
1:
    cmp x3, x1
    b.ge 2f
    ldr w5, [x0, x3, lsl #2]
    udiv w6, w5, w2
    msub w7, w6, w2, w5
    eor x4, x4, x7
    add x3, x3, #1
    b 1b
2:
    mov x0, x4
    ret
