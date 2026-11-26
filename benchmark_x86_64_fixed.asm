; VECTRA benchmark x86-64 Linux / NASM — isolated host experiment
; Provenance: user-supplied benchmark, revised 2026-11-03.
; No libc, heap, file I/O or Android/JNI dependencies.
; Build: nasm -f elf64 benchmark_x86_64_fixed.asm -o benchmark_x86_64_fixed.o
;        ld -e main -z noexecstack benchmark_x86_64_fixed.o -o benchmark_x86_64_fixed
; Run:   ./benchmark_x86_64_fixed
;
; Times CRC32 (IEEE reflected) over 4096 bytes with serialized RDTSC.
; Samples are *cycles*, NOT CRCs. The XOR of CRCs is an integrity diagnostic.
; Reported stddev = floor(sqrt(population variance)), integral TSC ticks.
; This is not a device-normalized performance comparison or official Android test.

bits 64
default rel

N_SAMPLES        equ 56
BUFFER_SIZE      equ 4096
CRC32_POLY       equ 0xEDB88320
SYS_WRITE        equ 1
SYS_EXIT         equ 60
STDOUT           equ 1

section .data align=64
samples_count       dq 0
stat_sum            dq 0
stat_sum_sq_lo      dq 0
stat_sum_sq_hi      dq 0
stat_mean           dq 0
stat_variance       dq 0
stat_stddev         dq 0
stat_min            dd 0xFFFFFFFF
stat_max            dd 0
checksum_xor        dd 0
cpu_has_avx2        db 0
cpu_has_sse4_2      db 0
cpu_has_popcnt      db 0

crc_test_vector     db '123456789'

str_header          db 10,'VECTRA x86-64 CRC32 BENCHMARK [HOST EXPERIMENT]',10,0
str_cpuid           db '[CPUID availability only; scalar CRC32 in use]',10,0
str_avx2            db '  AVX2 OS-enabled',10,0
str_sse42           db '  SSE4.2 CPU-supported',10,0
str_popcnt          db '  POPCNT CPU-supported',10,0
str_collect         db '[COLLECT 4096-byte CRC32; buffer mutated between samples]',10,0
str_stats           db '[STATS — TSC ticks, integer estimates]',10,0
str_samples         db 'Samples: ',0
str_mean            db 'Mean cycles: ',0
str_stddev          db 'Stddev cycles (floor): ',0
str_min             db 'Min cycles: ',0
str_max             db 'Max cycles: ',0
str_checksum        db 'CRC32 XOR (decimal): ',0
str_bad_crc         db 'ERROR: CRC32 IEEE test vector failed',10,0
str_bad_time        db 'ERROR: measured sample exceeds 32-bit TSC range',10,0
newline             db 10,0

section .bss align=64
work_buffer         resb BUFFER_SIZE
samples             resd N_SAMPLES
tmp_decimal         resb 64

section .text
global main

main:
    ; Deterministic integrity gate before any measurement.
    lea rsi, [crc_test_vector]
    mov ecx, 9
    call crc32_buffer
    cmp eax, 0xCBF43926             ; standard CRC32('123456789')
    jne .crc_failed

    lea rdi, [str_header]
    call print_string
    lea rdi, [str_cpuid]
    call print_string
    call cpuid_detect

    lea rdi, [str_collect]
    call print_string
    call init_buffer
    call collect_samples
    call compute_basic_stats

    lea rdi, [str_stats]
    call print_string
    call print_report

    xor edi, edi
    mov eax, SYS_EXIT
    syscall

.crc_failed:
    lea rdi, [str_bad_crc]
    call print_string
    mov edi, 2
    mov eax, SYS_EXIT
    syscall

; CPUID flags are informational; benchmark always uses software CRC32.
cpuid_detect:
    push rbx
    push r12
    xor eax, eax
    cpuid
    cmp eax, 1
    jb .done
    mov r12d, eax                   ; maximum basic CPUID leaf

    mov eax, 1
    xor ecx, ecx
    cpuid
    mov r10d, ecx
    test ecx, (1 << 20)
    jz .no_sse
    mov byte [cpu_has_sse4_2], 1
    lea rdi, [str_sse42]
    call print_string
.no_sse:
    test r10d, (1 << 23)
    jz .no_popcnt
    mov byte [cpu_has_popcnt], 1
    lea rdi, [str_popcnt]
    call print_string
.no_popcnt:
    ; AVX2 requires AVX+OSXSAVE+XCR0.XMM/YMM.
    cmp r12d, 7
    jb .done
    mov eax, 1
    xor ecx, ecx
    cpuid
    and ecx, ((1 << 27) | (1 << 28))
    cmp ecx, ((1 << 27) | (1 << 28))
    jne .done
    xor ecx, ecx
    xgetbv
    and eax, 6
    cmp eax, 6
    jne .done
    mov eax, 7
    xor ecx, ecx
    cpuid
    test ebx, (1 << 5)
    jz .done
    mov byte [cpu_has_avx2], 1
    lea rdi, [str_avx2]
    call print_string
.done:
    pop r12
    pop rbx
    ret

init_buffer:
    lea rdi, [work_buffer]
    mov ecx, BUFFER_SIZE / 8
    mov rax, 0x9E3779B97F4A7C15
    mov rdx, 0x517CC1B727220A95   ; ADD r64, imm64 is not encodable
.loop:
    mov [rdi], rax
    add rax, rdx
    add rdi, 8
    dec ecx
    jnz .loop
    ret

collect_samples:
    push rbx
    push r12
    push r13
    xor ebx, ebx
.loop:
    cmp ebx, N_SAMPLES
    jae .done

    lea rsi, [work_buffer]
    mov ecx, BUFFER_SIZE
    lfence
    rdtsc
    shl rdx, 32
    or rax, rdx
    mov r12, rax
    lfence
    call crc32_buffer
    mov r13d, eax                   ; CRC returned by the measured operation
    lfence
    rdtsc
    lfence
    shl rdx, 32
    or rax, rdx
    sub rax, r12
    mov rdx, rax
    shr rdx, 32
    jnz .bad_sample                ; avoid truncated timings and invalid stats
    mov [samples + rbx*4], eax
    xor dword [checksum_xor], r13d

    lea rsi, [work_buffer]
    call mix_buffer                 ; outside timed region
    inc ebx
    jmp .loop
.done:
    mov qword [samples_count], N_SAMPLES
    pop r13
    pop r12
    pop rbx
    ret
.bad_sample:
    lea rdi, [str_bad_time]
    call print_string
    mov edi, 3
    mov eax, SYS_EXIT
    syscall

; Input: RSI buffer, RCX byte length. Output: EAX CRC32/IEEE.
crc32_buffer:
    push rbx
    push rcx
    push rdx
    push rsi
    mov eax, 0xFFFFFFFF
    test rcx, rcx
    jz .finish
.byte_loop:
    movzx edx, byte [rsi]
    xor eax, edx
    mov ebx, 8
.bit_loop:
    shr eax, 1
    jnc .no_poly
    xor eax, CRC32_POLY
.no_poly:
    dec ebx
    jnz .bit_loop
    inc rsi
    dec rcx
    jnz .byte_loop
.finish:
    not eax
    pop rsi
    pop rdx
    pop rcx
    pop rbx
    ret

; Reads i+7 and i+11 for 0 <= i < 4080, max index = 4090 < 4096.
mix_buffer:
    xor ecx, ecx
.loop:
    cmp ecx, BUFFER_SIZE - 16
    jae .done
    movzx eax, byte [rsi + rcx + 7]
    xor byte [rsi + rcx], al
    movzx eax, byte [rsi + rcx + 11]
    xor byte [rsi + rcx], al
    inc ecx
    jmp .loop
.done:
    ret

; 32-bit samples, 64-bit sum and 128-bit sum-of-squares.
; Exact population numerator: N * SUM(x^2) - SUM(x)^2 (128-bit).
; Population variance: floor(numerator / N^2), then integer square root.
compute_basic_stats:
    push rbx
    xor r8, r8                      ; sum
    xor r9, r9                      ; sq low
    xor r10, r10                    ; sq high
    mov dword [stat_min], 0xFFFFFFFF
    mov dword [stat_max], 0
    xor ebx, ebx
.loop:
    cmp ebx, N_SAMPLES
    jae .done
    mov eax, [samples + rbx*4]     ; MOV EAX zero-extends into RAX
    cmp eax, [stat_min]
    jae .not_min                   ; unsigned comparison
    mov [stat_min], eax
.not_min:
    cmp eax, [stat_max]
    jbe .not_max                   ; unsigned comparison
    mov [stat_max], eax
.not_max:
    add r8, rax
    mov rcx, rax
    mul rcx                         ; RDX:RAX = sample^2
    add r9, rax
    adc r10, rdx
    inc ebx
    jmp .loop
.done:
    mov [stat_sum], r8
    mov [stat_sum_sq_lo], r9
    mov [stat_sum_sq_hi], r10
    mov rax, r8
    xor edx, edx
    mov ebx, N_SAMPLES
    div rbx
    mov [stat_mean], rax

    mov rax, r9
    mul rbx                         ; N * low64(sumSq)
    mov r8, rax
    mov r9, rdx
    mov rax, r10
    mul rbx                         ; high64(sumSq) * N
    add r9, rax

    mov rax, [stat_sum]
    mul rax                         ; sum^2 (128-bit)
    sub r8, rax
    sbb r9, rdx                     ; exact nonnegative variance numerator

    mov rdx, r9
    mov rax, r8
    mov ebx, N_SAMPLES * N_SAMPLES
    div rbx                         ; 128 / 3136 -> u64 population variance
    mov [stat_variance], rax
    call isqrt_u64
    mov [stat_stddev], rax
    pop rbx
    ret

; floor(sqrt(RAX)), standard unsigned binary restoring algorithm.
isqrt_u64:
    xor r8, r8
    mov r9, 0x4000000000000000
.align:
    cmp r9, rax
    jbe .step
    shr r9, 2
    jmp .align
.step:
    test r9, r9
    jz .done
    lea r10, [r8 + r9]
    cmp rax, r10
    jb .skip
    sub rax, r10
    shr r8, 1
    add r8, r9
    jmp .next
.skip:
    shr r8, 1
.next:
    shr r9, 2
    jmp .step
.done:
    mov rax, r8
    ret

print_report:
    lea rdi, [str_samples]
    call print_string
    mov rax, [samples_count]
    call print_decimal
    call print_newline
    lea rdi, [str_mean]
    call print_string
    mov rax, [stat_mean]
    call print_decimal
    call print_newline
    lea rdi, [str_stddev]
    call print_string
    mov rax, [stat_stddev]
    call print_decimal
    call print_newline
    lea rdi, [str_min]
    call print_string
    mov eax, [stat_min]
    call print_decimal
    call print_newline
    lea rdi, [str_max]
    call print_string
    mov eax, [stat_max]
    call print_decimal
    call print_newline
    lea rdi, [str_checksum]
    call print_string
    mov eax, [checksum_xor]
    call print_decimal
    call print_newline
    ret

print_newline:
    lea rdi, [newline]
    jmp print_string

; Input: RDI points to NUL-terminated string.
; Repeats partial writes; no libc. Fatal write errors terminate this print.
print_string:
    push rax
    push rbx
    push rcx
    push rdx
    push rsi
    push rdi
    mov rbx, rdi
    xor edx, edx
.len:
    cmp byte [rbx + rdx], 0
    je .write
    inc rdx
    jmp .len
.write:
    test rdx, rdx
    jz .done
    mov eax, SYS_WRITE
    mov edi, STDOUT
    mov rsi, rbx
    syscall
    test rax, rax
    jle .done
    add rbx, rax
    sub rdx, rax
    jmp .write
.done:
    pop rdi
    pop rsi
    pop rdx
    pop rcx
    pop rbx
    pop rax
    ret

; Input: RAX unsigned 64-bit integer, decimal output; handles zero.
print_decimal:
    push rax
    push rbx
    push rcx
    push rdx
    push rsi
    push rdi
    lea rsi, [tmp_decimal + 63]
    mov byte [rsi], 0
    mov ebx, 10
    dec rsi
    test rax, rax
    jnz .convert
    mov byte [rsi], '0'
    jmp .print
.convert:
.loop:
    xor edx, edx
    div rbx
    add dl, '0'
    mov [rsi], dl
    dec rsi
    test rax, rax
    jnz .loop
    inc rsi
.print:
    mov rdi, rsi
    call print_string
    pop rdi
    pop rsi
    pop rdx
    pop rcx
    pop rbx
    pop rax
    ret

section .note.GNU-stack noalloc noexec nowrite progbits
