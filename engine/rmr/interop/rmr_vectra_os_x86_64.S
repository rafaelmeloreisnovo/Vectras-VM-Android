/* ═══════════════════════════════════════════════════════════════════════════
   rmr_vectra_os_x86_64.S — VECTRA_OS x86-64 Hardware Primitives
   ─────────────────────────────────────────────────────────────────────────
   Zero-abstraction, register-direct, no stub, no libc.
   All primitives: leaf, no frame pointer, RSP not modified.

   Register contract (System V AMD64 ABI):
     rdi, rsi, rdx, rcx, r8, r9 = args (caller-saved)
     rax = return value (caller-saved)
     rbx, r12..r15, rbp = callee-saved (never used here)

   ISA requirements: x86-64 baseline.  CRC ops require SSE4.2.
   ═══════════════════════════════════════════════════════════════════════════ */

#if defined(__x86_64__)
    .text
    .balign 16

/* ─────────────────────────────────────────────────────────────────────────
   vos_crc32c_hw_x64 — CRC32C via SSE4.2 CRC32 opcode (T33)
   Prototype : u32 vos_crc32c_hw_x64(const u8 *buf, u32 len, u32 init)
   Args      : rdi = buf, esi = len, edx = init
   Returns   : eax = CRC32C result
   Throughput: 8-byte chunks via CRC32Q (1 cycle/8B on Haswell+)
   ───────────────────────────────────────────────────────────────────────── */
#if defined(__SSE4_2__)
    .global vos_crc32c_hw_x64
    .type   vos_crc32c_hw_x64, @function
vos_crc32c_hw_x64:
    movl    %edx, %eax          /* eax = ~init (inverted seed)               */
    notl    %eax

    /* 8-byte bulk loop: CRC32Q on 64-bit words. */
    movl    %esi, %ecx          /* ecx = len                                 */
    shrl    $3, %ecx            /* ecx = len / 8 (word count)                */
    jz      .Ltail4_x64

.Lloop8_x64:
    crc32q  (%rdi), %rax        /* CRC32C accumulate 8 bytes                 */
    addq    $8, %rdi            /* advance pointer (post-increment)          */
    decl    %ecx
    jnz     .Lloop8_x64

    /* 4-byte tail chunk. */
.Ltail4_x64:
    testl   $4, %esi
    jz      .Ltail2_x64
    crc32l  (%rdi), %eax        /* CRC32C on 32-bit word                     */
    addq    $4, %rdi

    /* 2-byte tail chunk. */
.Ltail2_x64:
    testl   $2, %esi
    jz      .Ltail1_x64
    crc32w  (%rdi), %eax        /* CRC32C on 16-bit halfword                 */
    addq    $2, %rdi

    /* 1-byte tail. */
.Ltail1_x64:
    testl   $1, %esi
    jz      .Ldone_x64
    crc32b  (%rdi), %eax        /* CRC32C on 8-bit byte                      */

.Ldone_x64:
    notl    %eax                /* ~crc = final result                       */
    ret
    .size vos_crc32c_hw_x64, .-vos_crc32c_hw_x64
#endif /* __SSE4_2__ */

/* ─────────────────────────────────────────────────────────────────────────
   vos_rdtsc_x64 — Serialized RDTSC (T26)
   Prototype : u64 vos_rdtsc_x64(void)
   Returns   : rax = full 64-bit timestamp (rdx:rax packed)
   Barrier   : LFENCE before RDTSC prevents OOO reordering of measurements.
   Cost      : ~7 cycles (lfence=3, rdtsc=4 on modern Intel)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_rdtsc_x64
    .type   vos_rdtsc_x64, @function
vos_rdtsc_x64:
    lfence                      /* serialize: drain OOO pipe                 */
    rdtsc                       /* edx:eax = TSC                             */
    shlq    $32, %rdx           /* rdx <<= 32                                */
    orq     %rdx, %rax          /* rax = rdx:eax (full 64-bit counter)       */
    ret
    .size vos_rdtsc_x64, .-vos_rdtsc_x64

/* ─────────────────────────────────────────────────────────────────────────
   vos_cmov_u32_x64 — Branchless 32-bit select via CMOV (T11)
   Prototype : u32 vos_cmov_u32_x64(u32 a, u32 b, u32 cond)
   Args      : edi = a, esi = b, edx = cond
   Returns   : eax = a if cond != 0, else b
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_cmov_u32_x64
    .type   vos_cmov_u32_x64, @function
vos_cmov_u32_x64:
    testl   %edx, %edx          /* set ZF if cond == 0                       */
    movl    %esi, %eax          /* eax = b (default: cond == 0 path)         */
    cmovnzl %edi, %eax          /* if ZF==0 (cond≠0): eax = a               */
    ret
    .size vos_cmov_u32_x64, .-vos_cmov_u32_x64

/* ─────────────────────────────────────────────────────────────────────────
   vos_q16_mul_x64 — Q16.16 signed multiply (T09)
   Prototype : u32 vos_q16_mul_x64(u32 a, u32 b)
   Args      : edi = a (Q16.16), esi = b (Q16.16)
   Returns   : eax = (a × b) >> 16 (Q16.16 result)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_q16_mul_x64
    .type   vos_q16_mul_x64, @function
vos_q16_mul_x64:
    movslq  %edi, %rax          /* sign-extend a to 64-bit                   */
    movslq  %esi, %rcx          /* sign-extend b to 64-bit                   */
    imulq   %rcx, %rax          /* rax = a × b (signed 64-bit product)       */
    sarq    $16, %rax           /* arithmetic right shift 16                 */
    ret
    .size vos_q16_mul_x64, .-vos_q16_mul_x64

/* ─────────────────────────────────────────────────────────────────────────
   vos_memzero_n_x64 — 16-byte-aligned SSE zero fill (T08)
   Prototype : void vos_memzero_n_x64(void *dst, u32 len)
   Args      : rdi = dst (16-byte aligned), esi = len (multiple of 16)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_memzero_n_x64
    .type   vos_memzero_n_x64, @function
vos_memzero_n_x64:
    testl   %esi, %esi
    jz      .Lzero_done_x64
    pxor    %xmm0, %xmm0        /* xmm0 = 0x00 × 16                          */
.Lzero_loop_x64:
    movdqa  %xmm0, (%rdi)       /* store 128 bits aligned                    */
    addq    $16, %rdi
    subl    $16, %esi
    jg      .Lzero_loop_x64
.Lzero_done_x64:
    ret
    .size vos_memzero_n_x64, .-vos_memzero_n_x64

/* ─────────────────────────────────────────────────────────────────────────
   vos_phi_hash_x64 — Knuth multiplicative hash (T36)
   Prototype : u32 vos_phi_hash_x64(u64 key, u32 bits)
   Args      : rdi = key, esi = bits
   Returns   : eax = (key × PHI64) >> (64 - bits)
   ───────────────────────────────────────────────────────────────────────── */
    .global vos_phi_hash_x64
    .type   vos_phi_hash_x64, @function
vos_phi_hash_x64:
    movabsq $0x9E3779B97F4A7C15, %rax   /* PHI64 constant                   */
    imulq   %rdi, %rax          /* rax = key × PHI64 (lower 64 bits)         */
    movl    $64, %ecx
    subl    %esi, %ecx          /* shift = 64 - bits                         */
    shrq    %cl, %rax           /* rax >>= (64 - bits)                       */
    ret
    .size vos_phi_hash_x64, .-vos_phi_hash_x64

#endif /* __x86_64__ */
