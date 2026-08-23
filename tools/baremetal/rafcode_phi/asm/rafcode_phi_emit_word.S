/*
 * rafcode_phi_emit_word.S
 * Núcleo ASM: grava opcode_hex no buffer de saída (palavra 32-bit) e retorna 1 em sucesso.
 * Assinatura C:
 * raf_u32 rafphi_emit_word_asm(raf_u32 opcode_hex, raf_u32 *out_words, raf_u32 cap_words, raf_u32 write_index);
 */

#if defined(__aarch64__)
    .text
    .align 2
    .global rafphi_emit_word_abi
    .type rafphi_emit_word_abi, %function
rafphi_emit_word_abi:
    cmp     w3, w2
    b.hs    1f
    add     x4, x1, w3, uxtw #2
    str     w0, [x4]
    mov     w0, #1
    ret
1:
    mov     w0, #0
    ret

    .global rafphi_emit_word_asm
    .type rafphi_emit_word_asm, %function
rafphi_emit_word_asm:
    b       rafphi_emit_word_abi
#endif

#if defined(__arm__) && !defined(__aarch64__)
    .syntax unified
    .text
    .align 2
    .global rafphi_emit_word_abi
    .type rafphi_emit_word_abi, %function
rafphi_emit_word_abi:
    cmp     r3, r2
    bhs     1f
    str     r0, [r1, r3, lsl #2]
    mov     r0, #1
    bx      lr
1:
    mov     r0, #0
    bx      lr

    .global rafphi_emit_word_asm
    .type rafphi_emit_word_asm, %function
rafphi_emit_word_asm:
    b       rafphi_emit_word_abi
#endif

#if defined(__x86_64__)
    .text
    .global rafphi_emit_word_abi
    .type rafphi_emit_word_abi, @function
rafphi_emit_word_abi:
    cmpl    %edx, %ecx
    jae     .Lfail_x64_abi
    movl    %edi, (%rsi,%rcx,4)
    movl    $1, %eax
    ret
.Lfail_x64_abi:
    xorl    %eax, %eax
    ret

    .global rafphi_emit_word_asm
    .type rafphi_emit_word_asm, @function
rafphi_emit_word_asm:
    jmp     rafphi_emit_word_abi
#endif

#if defined(__ELF__)
  #if defined(__arm__) || defined(__aarch64__)
    .section .note.GNU-stack,"",%progbits
  #else
    .section .note.GNU-stack,"",@progbits
  #endif
#endif
