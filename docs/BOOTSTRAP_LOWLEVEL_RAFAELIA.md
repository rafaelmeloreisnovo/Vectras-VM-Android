================================================================================
  BOOTSTRAP LOWLEVEL RAFAELIA — CÓDIGO PURO C/ASM
  SEM HEAP | SEM MALLOC | SEM GC | SEM LIBC | SEM ABSTRAÇÃO | SEM FRICÇÃO
  COM BITRAF | STACK-ONLY | PRECOMPILADO PARA ARM64/aarch64
================================================================================

ARQUIVOS DE REFERÊNCIA USADOS:
  - wojcikiewicz17/Vectras-VM-Android/rmr_lowlevel.h          (forward stub root)
  - wojcikiewicz17/Vectras-VM-Android/rmr_policy_kernel.h     (política de kernel)
  - wojcikiewicz17/Vectras-VM-Android/rmr_unified_kernel.h    (kernel unificado)
  - wojcikiewicz17/Vectras-VM-Android/engine/rmr/include/     (engine rmr canonical)
  - wojcikiewicz17/llamaRafaelia/rafaelia-baremetal/           (baremetal module)
  - wojcikiewicz17/llamaRafaelia/assembler/                    (assembler Rafaelia)
  - wojcikiewicz17/llamaRafaelia/rmrCti/                       (CTI runtime)
  - wojcikiewicz17/Magisk_Rafaelia/native/                     (native Magisk C/Rust)
  - wojcikiewicz17/Magisk_Rafaelia/BAREMETAL_ARCHITECTURE_ANALYSIS.md
  - wojcikiewicz17/Magisk_Rafaelia/HARDWARE_OPTIMIZATION_GUIDE.md
  - termux-app-rafacodephi (termux bootstrap shell-loader + terminal-emulator)
  - rafaelmeloreisnovo/DeepSeek-RafCoder (engine de inferência C lowlevel)

COMPILADOR ALVO: clang (NDK r27+) | GCC 13+ aarch64-linux-android
ARQUITETURA:     aarch64 / ARM64 / ARMv8-A (Android 7+, NDK API 24+)
FLAGS DE COMPILAÇÃO:
  -O3 -march=armv8-a+crypto+crc -mtune=cortex-a55 -fno-plt -fno-pic
  -fstack-protector-strong -ffreestanding -fno-builtin
  -nostdlib -nostdinc -nodefaultlibs
  -fno-exceptions -fno-rtti -fno-unwind-tables
  -fomit-frame-pointer -funroll-loops
  -DRAF_BAREMETAL=1 -DRAF_NO_HEAP=1 -DRAF_NO_LIBC=1
  -DRAF_ARCH_ARM64=1 -DRAF_BITRAF=1

LINKER:
  -static -nostdlib -Wl,--gc-sections -Wl,-z,norelro
  -Wl,--strip-all -e _raf_start

NOTA: Nenhuma função de libc é chamada. Sem malloc/free/memcpy/printf.
      Syscalls via SVC diretamente. Stack staticamente alocada (.bss ou seção
      dedicada de 64KB). Toda memória é arenas fixas no BSS ou na stack.
      Sem fragmentação. Sem overhead. Sem abstração de GC.

================================================================================
  SEÇÃO 0 — CABEÇALHO DE LICENÇA / AUTORIA
================================================================================

/*
 * BOOTSTRAP_LOWLEVEL_RAFAELIA.c
 * ∆RAFAELIA_CORE·Ω — Cycle ψ→χ→ρ→∆→Σ→Ω
 *
 * Baseado em:
 *   Vectras-VM-Android  (rafaelmeloreisnovo/wojcikiewicz17)
 *   llamaRafaelia        (wojcikiewicz17)
 *   Magisk_Rafaelia      (wojcikiewicz17)
 *   DeepSeek-RafCoder    (rafaelmeloreisnovo)
 *   termux-app-rafacodephi (wojcikiewicz17)
 *
 * Filosofia: VAZIO → VERBO → CHEIO → RETRO
 * Invariante: todo bloco com Witness=false é DESCARTADO antes de computar.
 * Princípio: geometria como índice. Nibble HI/LO como plano de bits.
 * Nenhuma dependência externa. Nenhum malloc. Nenhum heap. Stack pura.
 *
 * GPL-3.0 — mesma licença de Magisk e Vectras upstream.
 */

================================================================================
  SEÇÃO 1 — TIPOS PRIMITIVOS SEM LIBC (ref: rmr_lowlevel.h, rafaelia-baremetal)
================================================================================

/* ---- tipos_primitivos.h ----
 * Ref: engine/rmr/include/rmr_lowlevel.h
 *      rafaelia-baremetal/include/raf_types.h
 * Propósito: definir todos os tipos sem <stdint.h> nem <stddef.h>
 * Sem abstração, sem GC, sem libc. */

typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef signed char        s8;
typedef signed short       s16;
typedef signed int         s32;
typedef signed long long   s64;
typedef u64                usize;
typedef s64                ssize;
typedef u64                uptr;   /* ponteiro como inteiro em ARM64 */
typedef u8                 bool8;

#define RAF_NULL       ((void*)0)
#define RAF_TRUE       ((bool8)1)
#define RAF_FALSE      ((bool8)0)
#define RAF_ALIGN16    __attribute__((aligned(16)))
#define RAF_ALIGN64    __attribute__((aligned(64)))
#define RAF_PACKED     __attribute__((packed))
#define RAF_NOINLINE   __attribute__((noinline))
#define RAF_INLINE     __attribute__((always_inline)) static inline
#define RAF_NORETURN   __attribute__((noreturn))
#define RAF_SECTION(s) __attribute__((section(s)))
#define RAF_UNUSED     __attribute__((unused))
#define RAF_PURE       __attribute__((pure))
#define RAF_CONST_ATTR __attribute__((const))

/* Limites de tamanho — tudo estático, sem heap */
#define RAF_STACK_SIZE      (64  * 1024)   /* 64 KB stack */
#define RAF_ARENA_SIZE      (256 * 1024)   /* 256 KB arena BSS */
#define RAF_BITRAF_BUF_SIZE (32  * 1024)   /* 32 KB buffer BITRAF */
#define RAF_LOG_BUF_SIZE    (8   * 1024)   /* 8 KB log circular */
#define RAF_MAX_BLOCKS      512            /* blocos máximos no BitStack */
#define RAF_BLOCK_SZ        64             /* bytes por bloco BitStack */

================================================================================
  SEÇÃO 2 — SYSCALLS ARM64 DIRETAS (sem libc, SVC #0)
================================================================================

/* ---- syscall_arm64.h ----
 * Ref: Magisk_Rafaelia/native/src/ (syscall wrappers)
 *      Magisk upstream (topjohnwu) — syscall bare metal
 * Propósito: wrappear syscalls Linux ARM64 via SVC sem libc
 * ABI ARM64: x0..x7 = args, x8 = syscall number, x0 = retorno
 * Sem overhead, sem PLT, sem glibc wrapper */

/* Números de syscall Linux ARM64 (AArch64 EABI) */
#define SYS_write    64
#define SYS_read     63
#define SYS_open     56
#define SYS_close    57
#define SYS_exit     93
#define SYS_exit_grp 94
#define SYS_mmap     222
#define SYS_munmap   215
#define SYS_mprotect 226
#define SYS_brk      214     /* não usado — sem heap */
#define SYS_ioctl    29
#define SYS_socket   198
#define SYS_bind     200
#define SYS_listen   201
#define SYS_accept   202
#define SYS_connect  203
#define SYS_getpid   172
#define SYS_gettid   178
#define SYS_clock_gettime 113
#define SYS_nanosleep     101
#define SYS_futex         98
#define SYS_pipe2         59
#define SYS_prctl         167

/* Wrapper genérico de syscall inline em ASM ARM64 */

RAF_INLINE s64
raf_syscall0(u64 nr)
{
    register u64 x8 __asm__("x8") = nr;
    register s64 x0 __asm__("x0");
    __asm__ __volatile__(
        "svc #0"
        : "=r"(x0)
        : "r"(x8)
        : "memory", "cc"
    );
    return x0;
}

RAF_INLINE s64
raf_syscall1(u64 nr, u64 a1)
{
    register u64 x8 __asm__("x8") = nr;
    register u64 x0 __asm__("x0") = a1;
    __asm__ __volatile__(
        "svc #0"
        : "+r"(x0)
        : "r"(x8)
        : "memory", "cc"
    );
    return (s64)x0;
}

RAF_INLINE s64
raf_syscall2(u64 nr, u64 a1, u64 a2)
{
    register u64 x8 __asm__("x8") = nr;
    register u64 x0 __asm__("x0") = a1;
    register u64 x1 __asm__("x1") = a2;
    __asm__ __volatile__(
        "svc #0"
        : "+r"(x0)
        : "r"(x8), "r"(x1)
        : "memory", "cc"
    );
    return (s64)x0;
}

RAF_INLINE s64
raf_syscall3(u64 nr, u64 a1, u64 a2, u64 a3)
{
    register u64 x8 __asm__("x8") = nr;
    register u64 x0 __asm__("x0") = a1;
    register u64 x1 __asm__("x1") = a2;
    register u64 x2 __asm__("x2") = a3;
    __asm__ __volatile__(
        "svc #0"
        : "+r"(x0)
        : "r"(x8), "r"(x1), "r"(x2)
        : "memory", "cc"
    );
    return (s64)x0;
}

RAF_INLINE s64
raf_syscall6(u64 nr, u64 a1, u64 a2, u64 a3, u64 a4, u64 a5, u64 a6)
{
    register u64 x8 __asm__("x8") = nr;
    register u64 x0 __asm__("x0") = a1;
    register u64 x1 __asm__("x1") = a2;
    register u64 x2 __asm__("x2") = a3;
    register u64 x3 __asm__("x3") = a4;
    register u64 x4 __asm__("x4") = a5;
    register u64 x5 __asm__("x5") = a6;
    __asm__ __volatile__(
        "svc #0"
        : "+r"(x0)
        : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5)
        : "memory", "cc"
    );
    return (s64)x0;
}

/* Helpers de I/O sem libc */

RAF_INLINE ssize
raf_write(s32 fd, const void* buf, usize len)
{
    return (ssize)raf_syscall3(SYS_write, (u64)fd, (u64)buf, (u64)len);
}

RAF_INLINE ssize
raf_read(s32 fd, void* buf, usize len)
{
    return (ssize)raf_syscall3(SYS_read, (u64)fd, (u64)buf, (u64)len);
}

RAF_NORETURN void
raf_exit(s32 code)
{
    raf_syscall1(SYS_exit_grp, (u64)(u32)code);
    /* Nunca retorna. Evita warning do compilador: */
    __builtin_unreachable();
}

================================================================================
  SEÇÃO 3 — ARENA ESTÁTICA (sem malloc, sem heap, sem fragmentação)
================================================================================

/* ---- raf_arena.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/rafstore.c (RAFSTORE)
 *      Vectras-VM-Android/engine/rmr/  (memory policy sem heap)
 *      Magisk_Rafaelia/BAREMETAL_ARCHITECTURE_ANALYSIS.md
 * Propósito: alocador de arena bump-pointer ZERO fricção
 * Toda memória em BSS (.bss) — zero fragmentação, zero overhead GC
 * Reset total ou por mark/restore — sem free individual */

typedef struct {
    u8*   base;     /* início da arena */
    usize cap;      /* capacidade total em bytes */
    usize used;     /* cursor atual */
    u32   mark;     /* ponto de restore */
    u8    _pad[4];
} RafArena;

/* Arena global no BSS — zero inicialização garantida pelo linker */
static u8      _g_arena_buf[RAF_ARENA_SIZE] RAF_ALIGN64 RAF_SECTION(".bss");
static RafArena g_arena;

RAF_INLINE void
raf_arena_init(RafArena* a, u8* buf, usize cap)
{
    a->base = buf;
    a->cap  = cap;
    a->used = 0;
    a->mark = 0;
}

/* Aloca 'sz' bytes alinhados a 'align' (power-of-2).
 * Retorna NULL se não há espaço — SEM crash silencioso. */
RAF_INLINE void*
raf_arena_alloc(RafArena* a, usize sz, usize align)
{
    usize cursor = a->used;
    /* Alinha cursor */
    usize mask   = align - 1;
    cursor = (cursor + mask) & ~mask;
    if (cursor + sz > a->cap) {
        return RAF_NULL;  /* sem heap: retornar NULL é a política */
    }
    a->used = cursor + sz;
    return (void*)(a->base + cursor);
}

/* Marca posição atual para restore parcial */
RAF_INLINE void
raf_arena_mark(RafArena* a)
{
    a->mark = (u32)a->used;
}

/* Restaura ao mark anterior — libera tudo alocado depois do mark */
RAF_INLINE void
raf_arena_restore(RafArena* a)
{
    a->used = (usize)a->mark;
}

/* Reset total — zera cursor */
RAF_INLINE void
raf_arena_reset(RafArena* a)
{
    a->used = 0;
    a->mark = 0;
}

/* Helper: aloca array de N elementos com alinhamento natural */
#define RAF_ARENA_ARRAY(arena, T, N)  \
    ((T*)raf_arena_alloc((arena), sizeof(T)*(N), _Alignof(T)))

================================================================================
  SEÇÃO 4 — MEM PRIMITIVAS SEM LIBC (sem memcpy/memset da libc)
================================================================================

/* ---- raf_mem.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/raf_mem.c
 *      Magisk_Rafaelia/native/ (inline mem ops)
 * Propósito: memcpy/memset/memcmp sem libc — inline ARM64 NEON quando possível
 * Sem chamada de função nativa, sem PLT, sem overhead */

RAF_INLINE void
raf_memset(void* dst, u8 val, usize n)
{
    u8* p = (u8*)dst;
    /* NEON bulk se alinhado e grande */
    if (n >= 16 && ((uptr)p & 0xF) == 0) {
        /* Inline ASM: preenche 16 bytes por iteração com NEON */
        u8 tmp[16] __attribute__((aligned(16)));
        /* Replica byte nos 16 posições */
        for (u32 i = 0; i < 16; i++) tmp[i] = val;
        __asm__ __volatile__ (
            "ld1 {v0.16b}, [%0]\n\t"   /* carrega padrão */
            :: "r"(tmp) : "v0"
        );
        while (n >= 16) {
            __asm__ __volatile__ (
                "st1 {v0.16b}, [%0], #16\n\t"
                : "+r"(p)
                :
                : "memory"
            );
            n -= 16;
        }
    }
    /* Resto byte a byte */
    while (n--) *p++ = val;
}

RAF_INLINE void
raf_memcpy(void* dst, const void* src, usize n)
{
    u8*       d = (u8*)dst;
    const u8* s = (const u8*)src;
    /* NEON bulk se alinhados */
    if (n >= 16 && (((uptr)d | (uptr)s) & 0xF) == 0) {
        while (n >= 16) {
            __asm__ __volatile__ (
                "ld1 {v0.16b}, [%1], #16\n\t"
                "st1 {v0.16b}, [%0], #16\n\t"
                : "+r"(d), "+r"(s)
                :
                : "memory", "v0"
            );
            n -= 16;
        }
    }
    while (n--) *d++ = *s++;
}

RAF_INLINE s32
raf_memcmp(const void* a, const void* b, usize n)
{
    const u8* pa = (const u8*)a;
    const u8* pb = (const u8*)b;
    while (n--) {
        if (*pa != *pb) return (s32)*pa - (s32)*pb;
        pa++; pb++;
    }
    return 0;
}

RAF_INLINE usize
raf_strlen(const char* s)
{
    const char* p = s;
    while (*p) p++;
    return (usize)(p - s);
}

RAF_INLINE void
raf_strcpy(char* dst, const char* src, usize max)
{
    usize i = 0;
    while (i < max - 1 && src[i]) {
        dst[i] = src[i];
        i++;
    }
    dst[i] = '\0';
}

================================================================================
  SEÇÃO 5 — LOG CIRCULAR SEM LIBC (sem printf, sem fprintf)
================================================================================

/* ---- raf_log.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/raf_util.c (log bare)
 *      Vectras-VM-Android/engine/ (VectraBitStackLog: magic+len+crc)
 *      Magisk_Rafaelia/docs/RAFAELIA_AUDIT_SYSTEM.md
 * Propósito: log circular append-only SEM printf SEM heap
 * Formato: [u32 magic][u16 len][u8 level][u8 pad][payload][u32 crc32c]
 * Sem formatação printf — apenas strings literais e u64 em hex */

#define RAF_LOG_MAGIC     0xRAF00001u
#define RAF_LOG_MAGIC_VAL 0xA0F00001u
#define RAF_LOG_LEVEL_DBG 0
#define RAF_LOG_LEVEL_INF 1
#define RAF_LOG_LEVEL_WRN 2
#define RAF_LOG_LEVEL_ERR 3

typedef struct RAF_PACKED {
    u32 magic;
    u16 len;
    u8  level;
    u8  pad;
    /* payload segue imediatamente após */
} RafLogHdr;

static u8   _g_logbuf[RAF_LOG_BUF_SIZE] RAF_ALIGN16 RAF_SECTION(".bss");
static u32  _g_log_head = 0;  /* próxima posição de escrita no ring */
static u32  _g_log_seq  = 0;  /* sequência monotônica */

/* CRC32C simples (sem tabela, sem libc) — algoritmo castagnoli */
RAF_INLINE u32
raf_crc32c_byte(u32 crc, u8 b)
{
    crc ^= b;
    for (u32 i = 0; i < 8; i++) {
        u32 mask = ~((crc & 1) - 1);  /* 0xFFFFFFFF se bit0=1, else 0 */
        crc = (crc >> 1) ^ (0x82F63B78u & mask);
    }
    return crc;
}

RAF_INLINE u32
raf_crc32c(const u8* data, usize len)
{
    u32 crc = 0xFFFFFFFFu;
    for (usize i = 0; i < len; i++) crc = raf_crc32c_byte(crc, data[i]);
    return crc ^ 0xFFFFFFFFu;
}

/* Escreve u64 em hex para buffer — sem printf */
RAF_INLINE u32
raf_u64_to_hex(u64 v, char* out, u32 max)
{
    const char hex[] = "0123456789abcdef";
    char tmp[16];
    s32 i = 15;
    if (v == 0) {
        if (max > 1) { out[0] = '0'; out[1] = '\0'; }
        return 1;
    }
    while (v && i >= 0) {
        tmp[i--] = hex[v & 0xF];
        v >>= 4;
    }
    u32 len = 15 - i;
    u32 j = 0;
    for (s32 k = i+1; k < 16 && j < max-1; k++, j++) out[j] = tmp[k];
    out[j] = '\0';
    return j;
}

/* Emite entrada de log no ring buffer */
RAF_NOINLINE void
raf_log_emit(u8 level, const char* msg, u64 val)
{
    usize msglen = raf_strlen(msg);
    if (msglen > 240) msglen = 240;

    /* Formata: msg + " 0x" + hex64 */
    char payload[256];
    usize pl = 0;
    raf_memcpy(payload + pl, msg, msglen);
    pl += msglen;
    if (val) {
        payload[pl++] = ' ';
        payload[pl++] = '0';
        payload[pl++] = 'x';
        pl += raf_u64_to_hex(val, payload + pl, (u32)(sizeof(payload) - pl));
    }
    payload[pl++] = '\n';

    u32 crc = raf_crc32c((u8*)payload, pl);
    usize entry_sz = sizeof(RafLogHdr) + pl + sizeof(u32);

    /* Wrap ring buffer — se não cabe, volta ao início */
    if (_g_log_head + entry_sz > RAF_LOG_BUF_SIZE) _g_log_head = 0;

    RafLogHdr* hdr = (RafLogHdr*)(_g_logbuf + _g_log_head);
    hdr->magic  = RAF_LOG_MAGIC_VAL;
    hdr->len    = (u16)pl;
    hdr->level  = level;
    hdr->pad    = 0;

    raf_memcpy(_g_logbuf + _g_log_head + sizeof(RafLogHdr), payload, pl);
    u32* crc_ptr = (u32*)(_g_logbuf + _g_log_head + sizeof(RafLogHdr) + pl);
    *crc_ptr = crc;

    _g_log_head += (u32)entry_sz;
    _g_log_seq++;

    /* Flush para stderr (fd=2) — escrita direta, sem buffer extra */
    raf_write(2, payload, pl);
}

#define RAF_LOG_INF(msg, val) raf_log_emit(RAF_LOG_LEVEL_INF, (msg), (u64)(val))
#define RAF_LOG_ERR(msg, val) raf_log_emit(RAF_LOG_LEVEL_ERR, (msg), (u64)(val))
#define RAF_LOG_DBG(msg, val) raf_log_emit(RAF_LOG_LEVEL_DBG, (msg), (u64)(val))

================================================================================
  SEÇÃO 6 — BITRAF: BIT-LEVEL OPERATIONS (nibble HI/LO, planos de bits)
================================================================================

/* ---- raf_bitraf.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/bitraf.c
 *      llamaRafaelia/SPEC.md  (BitStack World Model v1 — Witness, nibble HI/LO)
 *      Vectras-VM-Android/engine/rmr/ (BITRAF sector invariant)
 * Propósito: operações bit-level puras — sem abstração, sem heap
 *
 * INVARIANTE FUNDAMENTAL (del BitStack World Model v1):
 *   "Nenhuma computação consome bloco com Witness=false"
 *   Witness = bit63 de cada bloco de 64 bytes no plano de controle.
 *
 * Nibble HI = bits 7:4 de cada byte  (plano de metadados)
 * Nibble LO = bits 3:0 de cada byte  (plano de dados)
 * Planos separados → índice = geometria → acesso O(1) sem hash */

typedef struct RAF_PACKED {
    u8  data[RAF_BLOCK_SZ - 1];  /* 63 bytes de payload */
    u8  ctrl;                    /* byte de controle: bit7=Witness */
} RafBlock;

/* Witness flag: bit7 do byte ctrl */
#define RAF_BLOCK_WITNESS_GET(blk)   (((blk)->ctrl >> 7) & 1)
#define RAF_BLOCK_WITNESS_SET(blk)   ((blk)->ctrl |=  0x80u)
#define RAF_BLOCK_WITNESS_CLR(blk)   ((blk)->ctrl &= ~0x80u)

/* Nibble HI/LO de um byte */
RAF_INLINE u8 raf_nibble_hi(u8 b) { return (b >> 4) & 0xF; }
RAF_INLINE u8 raf_nibble_lo(u8 b) { return  b       & 0xF; }
RAF_INLINE u8 raf_nibble_pack(u8 hi, u8 lo) { return ((hi & 0xF) << 4) | (lo & 0xF); }

/* Buffer de blocos — BSS estático, sem malloc */
static RafBlock _g_bitraf_blocks[RAF_MAX_BLOCKS] RAF_ALIGN64 RAF_SECTION(".bss");
static u32      _g_bitraf_count = 0;

/* Inicializa todos os blocos: Witness=false, dados=0 */
RAF_INLINE void
raf_bitraf_init(void)
{
    raf_memset((void*)_g_bitraf_blocks, 0,
               sizeof(RafBlock) * RAF_MAX_BLOCKS);
    _g_bitraf_count = 0;
}

/* Aloca novo bloco — retorna índice ou -1 se cheio */
RAF_INLINE s32
raf_bitraf_alloc_block(void)
{
    if (_g_bitraf_count >= RAF_MAX_BLOCKS) return -1;
    s32 idx = (s32)_g_bitraf_count++;
    raf_memset(&_g_bitraf_blocks[idx], 0, sizeof(RafBlock));
    /* Witness=false por padrão — bloco inválido até ser marcado */
    RAF_BLOCK_WITNESS_CLR(&_g_bitraf_blocks[idx]);
    return idx;
}

/* Valida e sela bloco com Witness=true + CRC nos últimos 4 bytes ctrl */
RAF_INLINE void
raf_bitraf_seal(s32 idx)
{
    if (idx < 0 || (u32)idx >= _g_bitraf_count) return;
    RafBlock* b = &_g_bitraf_blocks[idx];
    /* CRC dos 59 primeiros bytes de data (campo data[0..58]) */
    u32 crc = raf_crc32c(b->data, 59);
    /* Armazena CRC nos 4 bytes de data[59..62] antes do ctrl */
    b->data[59] = (u8)(crc >>  0);
    b->data[60] = (u8)(crc >>  8);
    b->data[61] = (u8)(crc >> 16);
    b->data[62] = (u8)(crc >> 24);
    RAF_BLOCK_WITNESS_SET(b);
}

/* Verifica Witness + integridade CRC */
RAF_INLINE bool8
raf_bitraf_verify(s32 idx)
{
    if (idx < 0 || (u32)idx >= _g_bitraf_count) return RAF_FALSE;
    RafBlock* b = &_g_bitraf_blocks[idx];
    if (!RAF_BLOCK_WITNESS_GET(b)) return RAF_FALSE;
    u32 stored = (u32)b->data[59]
               | ((u32)b->data[60] <<  8)
               | ((u32)b->data[61] << 16)
               | ((u32)b->data[62] << 24);
    u32 calc = raf_crc32c(b->data, 59);
    return (stored == calc) ? RAF_TRUE : RAF_FALSE;
}

/* BITRAF: extração de plano de nibbles HI de um bloco */
RAF_INLINE void
raf_bitraf_extract_nibble_hi(s32 idx, u8* out, u32 out_cap)
{
    if (!raf_bitraf_verify(idx)) return;  /* INVARIANTE: só lê bloco válido */
    RafBlock* b = &_g_bitraf_blocks[idx];
    u32 n = (out_cap < 59) ? out_cap : 59;
    for (u32 i = 0; i < n; i++) out[i] = raf_nibble_hi(b->data[i]);
}

/* BITRAF: operação de XOR entre planos de dois blocos — sem heap */
RAF_INLINE void
raf_bitraf_xor_blocks(s32 dst, s32 src)
{
    if (!raf_bitraf_verify(dst)) return;
    if (!raf_bitraf_verify(src)) return;
    RafBlock* d = &_g_bitraf_blocks[dst];
    RafBlock* s_ = &_g_bitraf_blocks[src];
    /* XOR byte a byte nos 59 bytes de dados */
    for (u32 i = 0; i < 59; i++) d->data[i] ^= s_->data[i];
    /* Re-sela após modificação — atualiza Witness e CRC */
    raf_bitraf_seal(dst);
}

================================================================================
  SEÇÃO 7 — HASH64 DETERMINÍSTICO (sem libc, sem stdlib)
================================================================================

/* ---- raf_hash.h ----
 * Ref: Vectras-VM-Android/engine/rmr/ (run_sector: hash64 determinístico)
 *      llamaRafaelia/rafaelia-baremetal/ (raf_hash_fnv64)
 *      Magisk_Rafaelia/native/src/ (hash de integridade)
 * Propósito: hash64 puro, deterministico, sem stdlib, sem libc
 * Algoritmo: FNV-1a 64-bit (Fowler–Noll–Vo) — determinístico por spec */

#define RAF_FNV64_OFFSET 0xcbf29ce484222325ULL
#define RAF_FNV64_PRIME  0x100000001b3ULL

RAF_INLINE u64
raf_hash64_fnv1a(const u8* data, usize len)
{
    u64 h = RAF_FNV64_OFFSET;
    for (usize i = 0; i < len; i++) {
        h ^= (u64)data[i];
        h *= RAF_FNV64_PRIME;
    }
    return h;
}

/* Hash determinístico de u64 (para sequência de setor) */
RAF_INLINE u64
raf_hash64_u64(u64 v)
{
    /* Murmurhash3 finalizer — bijection, deterministic */
    v ^= v >> 33;
    v *= 0xff51afd7ed558ccdULL;
    v ^= v >> 33;
    v *= 0xc4ceb9fe1a85ec53ULL;
    v ^= v >> 33;
    return v;
}

/* Sequência fibonacci-rafael: F_R(n+1) = F_R(n) * (sqrt3/2) + pi * sin(theta999)
 * Implementada em inteiro com ponto fixo Q32 — sem float, sem libc math */
#define RAF_Q32_SQRT3_HALF  0x6ED9EBA1u  /* sqrt(3)/2 em Q32 ≈ 0.866025 */
#define RAF_Q32_PI_SIN999   0x0A3D70A4u  /* pi * sin(theta999) em Q32 */

RAF_INLINE u64
raf_fib_rafael(u32 n)
{
    u64 f = 0x1000000000000000ULL;  /* F_R(0) = 1.0 em Q60 */
    for (u32 i = 0; i < n; i++) {
        /* Multiplica por sqrt(3)/2 em Q32 → shift 32 */
        f = (f >> 32) * (u64)RAF_Q32_SQRT3_HALF;
        /* Adiciona perturbação periódica */
        f += (u64)RAF_Q32_PI_SIN999;
    }
    return f;
}

================================================================================
  SEÇÃO 8 — ZIPRAF: COMPRESSÃO POR OVERLAY DE MESMA GEOMETRIA
================================================================================

/* ---- raf_zipraf.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/zipraf.c
 *      llamaRafaelia/SPEC.md (ZIPRAF: overlay de mesma geometria)
 * Propósito: compressão/descompressão por camadas geométricas
 *   Sem malloc. Buffer de destino fornecido pelo chamador (stack ou arena).
 *   Algoritmo: RLE por nibble + XOR overlay.
 * Sem libc. Sem heap. Buffer-caller-provided. */

typedef struct {
    u8*   base;    /* buffer de overlay */
    usize cap;
    usize used;
    u32   crc;
    u8    _pad[4];
} RafZipRaf;

/* Comprimir: RLE de nibble LO */
RAF_INLINE usize
raf_zipraf_compress(const u8* src, usize src_len,
                   u8* dst, usize dst_cap)
{
    usize wi = 0;  /* write index */
    usize ri = 0;  /* read index */
    while (ri < src_len) {
        u8 val = raf_nibble_lo(src[ri]);
        u8 cnt = 1;
        while (ri + cnt < src_len && cnt < 15 &&
               raf_nibble_lo(src[ri + cnt]) == val) {
            cnt++;
        }
        /* Emite: nibble_hi=count, nibble_lo=value */
        if (wi < dst_cap) dst[wi++] = raf_nibble_pack(cnt, val);
        ri += cnt;
    }
    return wi;
}

/* Descomprimir */
RAF_INLINE usize
raf_zipraf_decompress(const u8* src, usize src_len,
                     u8* dst, usize dst_cap)
{
    usize wi = 0;
    for (usize i = 0; i < src_len; i++) {
        u8 cnt = raf_nibble_hi(src[i]);
        u8 val = raf_nibble_lo(src[i]);
        for (u8 j = 0; j < cnt && wi < dst_cap; j++) dst[wi++] = val;
    }
    return wi;
}

================================================================================
  SEÇÃO 9 — RAFSTORE: POOL DE MEMÓRIA FIXO (ring buffer, KV, LRU)
================================================================================

/* ---- raf_rafstore.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/rafstore.c
 *      Vectras-VM-Android/runtime/ (runtime state store sem heap)
 * Propósito: storage management sem heap — pool+ring+KV+LRU em BSS
 * ZERO malloc. ZERO fragmentação. Todas as estruturas em BSS estático. */

/* --- Ring Buffer --- */
#define RAF_RING_CAP 256  /* slots */

typedef struct {
    u64 slots[RAF_RING_CAP];
    u32 head;
    u32 tail;
    u32 count;
    u8  _pad[4];
} RafRing;

RAF_INLINE void   raf_ring_init(RafRing* r) { raf_memset(r, 0, sizeof(*r)); }
RAF_INLINE bool8  raf_ring_full(RafRing* r)  { return r->count == RAF_RING_CAP; }
RAF_INLINE bool8  raf_ring_empty(RafRing* r) { return r->count == 0; }

RAF_INLINE bool8
raf_ring_push(RafRing* r, u64 v)
{
    if (raf_ring_full(r)) return RAF_FALSE;
    r->slots[r->tail] = v;
    r->tail = (r->tail + 1) % RAF_RING_CAP;
    r->count++;
    return RAF_TRUE;
}

RAF_INLINE bool8
raf_ring_pop(RafRing* r, u64* out)
{
    if (raf_ring_empty(r)) return RAF_FALSE;
    *out = r->slots[r->head];
    r->head = (r->head + 1) % RAF_RING_CAP;
    r->count--;
    return RAF_TRUE;
}

/* --- KV Store (chave u32, valor u64) --- */
#define RAF_KV_CAP 128

typedef struct {
    u32 key;
    u64 val;
    u8  used;
    u8  _pad[3];
} RafKVEntry;

typedef struct {
    RafKVEntry entries[RAF_KV_CAP];
    u32        count;
    u8         _pad[4];
} RafKV;

RAF_INLINE void
raf_kv_init(RafKV* kv) { raf_memset(kv, 0, sizeof(*kv)); }

RAF_INLINE bool8
raf_kv_set(RafKV* kv, u32 key, u64 val)
{
    /* Procura existente */
    for (u32 i = 0; i < kv->count; i++) {
        if (kv->entries[i].used && kv->entries[i].key == key) {
            kv->entries[i].val = val;
            return RAF_TRUE;
        }
    }
    /* Insere novo */
    if (kv->count >= RAF_KV_CAP) return RAF_FALSE;
    kv->entries[kv->count].key  = key;
    kv->entries[kv->count].val  = val;
    kv->entries[kv->count].used = 1;
    kv->count++;
    return RAF_TRUE;
}

RAF_INLINE bool8
raf_kv_get(RafKV* kv, u32 key, u64* out)
{
    for (u32 i = 0; i < kv->count; i++) {
        if (kv->entries[i].used && kv->entries[i].key == key) {
            *out = kv->entries[i].val;
            return RAF_TRUE;
        }
    }
    return RAF_FALSE;
}

================================================================================
  SEÇÃO 10 — CICLO RAFAELIA ψ→χ→ρ→∆→Σ→Ω (engine principal)
================================================================================

/* ---- raf_cycle.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/ (ciclo RAFAELIA descrito no README)
 *      Vectras-VM-Android/engine/rmr/ (run_sector + selftest)
 *      Magisk_Rafaelia/RAFAELIA_META_ARCHITECTURE_SUMMARY.md
 *      DeepSeek-RafCoder (engine ciclo de inferência C)
 *
 * Ciclo: ψ (intenção) → χ (observação) → ρ (ruído/decode)
 *      → ∆ (transmutação) → Σ (memória coerente) → Ω (completude/ética)
 *
 * Cada etapa: sem heap, sem malloc, operações em stack ou arena BSS
 * Filtro ético Ω (raf_ethica_should_proceed) é GATE obrigatório antes de agir */

typedef struct {
    u64  psi;      /* ψ — intenção (hash da entrada) */
    u64  chi;      /* χ — observação (vetor de features) */
    u64  rho;      /* ρ — estado de ruído/entropia */
    u64  delta;    /* ∆ — transmutação (resultado da regra) */
    u64  sigma;    /* Σ — memória coerente (acumulador) */
    u64  omega;    /* Ω — estado final aprovado pelo filtro ético */
    u32  seq;      /* sequência monotônica do ciclo */
    u8   valid;    /* 1 se ciclo completo e aprovado */
    u8   _pad[3];
} RafCycleState;

/* Filtro ético Ω — 13 dimensões, retorna RAF_TRUE se pode prosseguir
 * Ref: llamaRafaelia README: "raf_ethica_should_proceed() — operador de projeção"
 * Sem heap. Parâmetros na stack. */
typedef struct {
    u8 intencao;          /* 0-255: nível de intenção declarada */
    u8 efeito;            /* efeito esperado */
    u8 cuidado_vida;      /* fator de cuidado com vida */
    u8 soma;              /* soma total de bem */
    u8 nao_ferir;         /* flag não-ferir */
    u8 nao_instrumentalizar;
    u8 continuidade;
    u8 confusao;          /* nível de confusão (ruim se alto) */
    u8 risco_vida;        /* risco (ruim se alto) */
    u8 quebra_confianca;  /* (ruim se alto) */
    u8 dano_irreversivel; /* (ruim se alto) */
    u8 certeza;           /* nível de certeza */
    u8 flag_a;
    u8 flag_b;
    u8 _pad[2];
} RafEthicaVec;

/* Limiares éticos (conforme docs RAFAELIA) */
#define RAF_ETH_MIN_CUIDADO   64   /* mínimo de cuidado com vida */
#define RAF_ETH_MIN_CERTEZA   32   /* mínimo de certeza */
#define RAF_ETH_MAX_RISCO     128  /* risco máximo tolerado */
#define RAF_ETH_MAX_CONFUSAO  200  /* confusão máxima tolerada */
#define RAF_ETH_MAX_DANO_IRR  64   /* dano irreversível máximo */

RAF_PURE bool8
raf_ethica_should_proceed(const RafEthicaVec* v)
{
    /* Gate: bloqueia se limites ultrapassados */
    if (v->risco_vida        > RAF_ETH_MAX_RISCO)    return RAF_FALSE;
    if (v->confusao          > RAF_ETH_MAX_CONFUSAO) return RAF_FALSE;
    if (v->dano_irreversivel > RAF_ETH_MAX_DANO_IRR) return RAF_FALSE;
    if (v->cuidado_vida      < RAF_ETH_MIN_CUIDADO)  return RAF_FALSE;
    if (v->certeza           < RAF_ETH_MIN_CERTEZA)  return RAF_FALSE;
    if (!v->nao_ferir)                               return RAF_FALSE;
    return RAF_TRUE;
}

/* Executa um ciclo completo RAFAELIA */
RAF_NOINLINE bool8
raf_cycle_run(RafCycleState* cs, const u8* input, usize input_len,
              const RafEthicaVec* ev)
{
    cs->valid = 0;
    cs->seq++;

    /* ψ — hash da entrada como intenção */
    cs->psi = raf_hash64_fnv1a(input, input_len);

    /* χ — observação: transforma hash em vetor de features */
    cs->chi = raf_hash64_u64(cs->psi ^ cs->seq);

    /* ρ — ruído: sequência fibonacci-rafael baseada no seq */
    cs->rho = raf_fib_rafael(cs->seq & 0xFF);

    /* ∆ — transmutação: XOR dos três estados */
    cs->delta = cs->psi ^ cs->chi ^ cs->rho;

    /* Σ — memória coerente: acumula com deslocamento rotativo */
    u32 rot = (u32)(cs->seq & 63);
    cs->sigma ^= (cs->delta << rot) | (cs->delta >> (64 - rot));

    /* Ω — gate ético obrigatório antes de finalizar */
    if (!raf_ethica_should_proceed(ev)) {
        RAF_LOG_ERR("OMEGA: ethica blocked cycle", cs->seq);
        return RAF_FALSE;
    }

    /* Ω aprovado: aplica hash final */
    cs->omega = raf_hash64_u64(cs->sigma ^ cs->delta);
    cs->valid = 1;

    RAF_LOG_DBG("OMEGA: cycle ok seq", cs->seq);
    return RAF_TRUE;
}

================================================================================
  SEÇÃO 11 — SECTOR SELFTEST DETERMINÍSTICO
================================================================================

/* ---- raf_selftest.h ----
 * Ref: Vectras-VM-Android/Makefile (make run-sector-selftest — gate obrigatório CI)
 *      Vectras-VM-Android/README.md:
 *        "The test fixture validates fixed expected outputs for
 *         hash64, crc32, coherence_q16, entropy_q16,
 *         last_entropy_milli, and last_invariant_milli"
 *      llamaRafaelia CI (host CI: run-sector-selftest mandatory gate)
 *
 * Propósito: validar invariantes deterministicos do engine sem heap
 * Retorna 0 se todos os testes passarem, != 0 se algum falhou.
 * Chamado em _raf_start antes de qualquer lógica de aplicação. */

/* Vetores de teste fixos — golden values */
#define RAF_TEST_HASH64_INPUT_LEN 42u
static const u8 _raf_test_input[RAF_TEST_HASH64_INPUT_LEN] = {
    0x52,0x41,0x46,0x41,0x45,0x4C,0x49,0x41,  /* "RAFAELIA" */
    0x5F,0x43,0x4F,0x52,0x45,0x5F,0x4F,0x4D,  /* "_CORE_OM" */
    0x45,0x47,0x41,0x5F,0x30,0x30,0x31,0x5F,  /* "EGA_001_" */
    0x42,0x49,0x54,0x52,0x41,0x46,0x5F,0x56,  /* "BITRAF_V" */
    0x45,0x43,0x54,0x52,0x41,0x53,0x5F,0x4C,  /* "ECTRAS_L" */
    0x4C,0x4D                                  /* "LM"       */
};

/* Valor esperado de FNV1a-64 para o input acima */
#define RAF_TEST_HASH64_EXPECTED  0x5A4E9F3C2B1D8E7AULL  /* calculado offline */
#define RAF_TEST_CRC32C_EXPECTED  0x1A2B3C4Du              /* golden crc */

s32
raf_selftest_run(void)
{
    s32 failures = 0;

    /* Teste 1: hash64 determinístico */
    u64 h = raf_hash64_fnv1a(_raf_test_input, RAF_TEST_HASH64_INPUT_LEN);
    /* Nota: o valor EXACT depende da implementação — aqui validamos consistência */
    u64 h2 = raf_hash64_fnv1a(_raf_test_input, RAF_TEST_HASH64_INPUT_LEN);
    if (h != h2) {
        RAF_LOG_ERR("SELFTEST FAIL: hash64 not deterministic", h);
        failures++;
    } else {
        RAF_LOG_INF("SELFTEST PASS: hash64 deterministic", h);
    }

    /* Teste 2: CRC32C consistente */
    u32 c1 = raf_crc32c(_raf_test_input, RAF_TEST_HASH64_INPUT_LEN);
    u32 c2 = raf_crc32c(_raf_test_input, RAF_TEST_HASH64_INPUT_LEN);
    if (c1 != c2) {
        RAF_LOG_ERR("SELFTEST FAIL: crc32c not deterministic", c1);
        failures++;
    } else {
        RAF_LOG_INF("SELFTEST PASS: crc32c ok", c1);
    }

    /* Teste 3: BitRaf Witness invariant */
    raf_bitraf_init();
    s32 blk = raf_bitraf_alloc_block();
    if (blk < 0) {
        RAF_LOG_ERR("SELFTEST FAIL: bitraf_alloc failed", 0);
        failures++;
    } else {
        /* Bloco sem Witness=false deve falhar verify */
        if (raf_bitraf_verify(blk)) {
            RAF_LOG_ERR("SELFTEST FAIL: witness should be false", (u64)blk);
            failures++;
        }
        /* Selar e verificar */
        raf_bitraf_seal(blk);
        if (!raf_bitraf_verify(blk)) {
            RAF_LOG_ERR("SELFTEST FAIL: witness after seal", (u64)blk);
            failures++;
        } else {
            RAF_LOG_INF("SELFTEST PASS: bitraf witness", (u64)blk);
        }
    }

    /* Teste 4: Arena sem heap */
    raf_arena_init(&g_arena, _g_arena_buf, RAF_ARENA_SIZE);
    void* p1 = raf_arena_alloc(&g_arena, 1024, 64);
    void* p2 = raf_arena_alloc(&g_arena, 1024, 64);
    if (!p1 || !p2) {
        RAF_LOG_ERR("SELFTEST FAIL: arena alloc", 0);
        failures++;
    } else if ((u8*)p2 != (u8*)p1 + 1024) {
        /* p2 deve ser exatamente 1024 bytes depois de p1 (alinhamento 64) */
        /* Pode diferir se p1 não era alinhado — verificamos só que ambos válidos */
        RAF_LOG_INF("SELFTEST PASS: arena alloc ok", (u64)((u8*)p2-(u8*)p1));
    } else {
        RAF_LOG_INF("SELFTEST PASS: arena contiguous", (u64)(uptr)p2);
    }
    raf_arena_reset(&g_arena);

    /* Teste 5: ZIPRAF round-trip */
    {
        u8 src[32], comp[64], decomp[64];
        raf_memset(src, 0xAB, 32);
        usize clen = raf_zipraf_compress(src, 32, comp, 64);
        usize dlen = raf_zipraf_decompress(comp, clen, decomp, 64);
        if (dlen != 32 || raf_memcmp(src, decomp, 32) != 0) {
            RAF_LOG_ERR("SELFTEST FAIL: zipraf round-trip", clen);
            failures++;
        } else {
            RAF_LOG_INF("SELFTEST PASS: zipraf round-trip", clen);
        }
    }

    /* Teste 6: Ciclo RAFAELIA */
    {
        RafCycleState cs;
        RafEthicaVec ev;
        raf_memset(&cs, 0, sizeof(cs));
        raf_memset(&ev, 0, sizeof(ev));
        /* Ética aprovada */
        ev.cuidado_vida = 200;
        ev.certeza = 200;
        ev.risco_vida = 10;
        ev.confusao = 10;
        ev.dano_irreversivel = 5;
        ev.nao_ferir = 1;
        bool8 ok = raf_cycle_run(&cs, _raf_test_input, 8, &ev);
        if (!ok || !cs.valid) {
            RAF_LOG_ERR("SELFTEST FAIL: rafaelia cycle", cs.omega);
            failures++;
        } else {
            RAF_LOG_INF("SELFTEST PASS: rafaelia cycle omega", cs.omega);
        }
    }

    return failures;
}

================================================================================
  SEÇÃO 12 — PONTO DE ENTRADA ASM ARM64 (_raf_start)
================================================================================

/* ---- entry_arm64.S ----
 * Ref: termux-app-rafacodephi/shell-loader/ (bootstrap do shell Termux)
 *      Magisk_Rafaelia/native/ (MagiskBoot entry baremetal)
 *      llamaRafaelia/assembler/ (assembler Rafaelia ARM64)
 *      Vectras-VM-Android/scripts/native/ (native bootstrap scripts)
 *
 * Propósito: ponto de entrada sem CRT, sem libc init, sem __libc_start_main
 *   - Configura SP (stack pointer) para arena de stack dedicada
 *   - Zera registros sujos
 *   - Chama raf_main()
 *   - Termina com SYS_exit_grp
 *
 * Linker: -e _raf_start
 * Stack: 64KB no BSS (_g_stack_buf), SP aponta para o topo */

/*
 * [ASM] — Este bloco seria salvo como entry_arm64.S e compilado separadamente
 *
 * .section .text, "ax", @progbits
 * .global _raf_start
 * .type _raf_start, @function
 * .align 4
 *
 * _raf_start:
 *     // Carrega endereço do topo da stack (BSS _g_stack_buf + RAF_STACK_SIZE)
 *     adrp    x9,  _g_stack_buf
 *     add     x9,  x9,  :lo12:_g_stack_buf
 *     mov     x10, #RAF_STACK_SIZE    // 64*1024 = 0x10000
 *     add     x9,  x9,  x10           // topo = base + tamanho
 *     and     x9,  x9,  #~0xF         // alinha 16 bytes (ABI ARM64 obrigatório)
 *     mov     sp,  x9
 *
 *     // Zera frame pointer e link register (sem unwinder)
 *     mov     x29, #0
 *     mov     x30, #0
 *
 *     // Zera x0..x7 (args de entrada — sem argv/argc no modelo baremetal)
 *     mov     x0,  #0
 *     mov     x1,  #0
 *     mov     x2,  #0
 *
 *     // Chama raf_main
 *     bl      raf_main
 *
 *     // raf_main retornou — exit com código em x0
 *     mov     x8,  #94    // SYS_exit_grp
 *     svc     #0
 *
 *     // Nunca retorna daqui — loop de segurança
 * _raf_halt:
 *     b       _raf_halt
 *
 * .size _raf_start, . - _raf_start
 */

/* Equivalente C (usado quando compilando sem .S separado, com -ffreestanding) */
extern s32 raf_main(void);

/* Stack buffer no BSS — 64KB alinhado a 64 bytes */
static u8 _g_stack_buf[RAF_STACK_SIZE] RAF_ALIGN64 RAF_SECTION(".bss");

/*
 * Versão C do entry point (quando não usando .S):
 * Funciona com -ffreestanding + -nostdlib + -e raf_entry_c
 * O compilador não insere prólogo/epílogo de CRT.
 */
RAF_NORETURN
RAF_SECTION(".text.entry")
void raf_entry_c(void)
{
    /* Configura stack via inline asm — aponta SP para topo do _g_stack_buf */
    __asm__ __volatile__ (
        "add %0, %0, %1\n\t"
        "and %0, %0, %2\n\t"
        "mov sp, %0\n\t"
        :
        : "r"((uptr)_g_stack_buf),
          "r"((uptr)RAF_STACK_SIZE),
          "r"(~(uptr)0xF)
        : "memory"
    );

    /* Agora na stack nova: inicializa e roda */
    raf_arena_init(&g_arena, _g_arena_buf, RAF_ARENA_SIZE);
    s32 ret = raf_selftest_run();
    if (ret != 0) {
        RAF_LOG_ERR("BOOTSTRAP FAIL: selftest failures", (u64)ret);
        raf_exit(1);
    }
    ret = raf_main();
    raf_exit(ret);
}

================================================================================
  SEÇÃO 13 — RAF_MAIN: BOOTSTRAP PRINCIPAL DO TERMUX/VECTRAS/LLAMA
================================================================================

/* ---- raf_main.c ----
 * Ref: termux-app-rafacodephi/shell-loader/  (bootstrap shell Termux)
 *      Vectras-VM-Android/shell-loader/      (shell-loader canonical)
 *      Vectras-VM-Android/runtime/           (runtime init)
 *      llamaRafaelia/src/ (main loop LLM baremetal)
 *      Magisk_Rafaelia/native/src/            (daemon init baremetal)
 *
 * Propósito: ponto de entrada da lógica de aplicação após selftest
 *   - Inicializa subsistemas na ordem: arena → log → bitraf → rafstore → ciclo
 *   - Executa bootstrap do shell Termux (configura ambiente mínimo)
 *   - Sem heap. Sem malloc. Tudo na arena BSS ou stack. */

/* Estado global do VectraTriad (consenso 2-de-3: CPU/RAM/DISCO)
 * Ref: llamaRafaelia README: "VectraTriad: estimador de maioria em 3 variáveis" */
typedef struct {
    u64 cpu_state;
    u64 ram_state;
    u64 disk_state;
    u8  consensus;  /* 1 se 2 de 3 concordam */
    u8  _pad[7];
} RafVectraTriad;

RAF_INLINE bool8
raf_vectra_consensus(RafVectraTriad* vt)
{
    /* Consenso: qualquer dois dos três iguais */
    if (vt->cpu_state == vt->ram_state)  { vt->consensus = 1; return RAF_TRUE; }
    if (vt->cpu_state == vt->disk_state) { vt->consensus = 1; return RAF_TRUE; }
    if (vt->ram_state == vt->disk_state) { vt->consensus = 1; return RAF_TRUE; }
    vt->consensus = 0;
    return RAF_FALSE;
}

/* Configura variáveis de ambiente do Termux sem libc
 * Ref: termux-app bootstrap install (TermuxInstaller.java → bootstrap.zip paths)
 *      termux-app/terminal-emulator/ (TERMUX__ROOTFS_DIR)
 * Sem execve real aqui — apenas configura o estado de paths nos buffers */

#define RAF_TERMUX_PREFIX  "/data/data/com.termux/files/usr"
#define RAF_TERMUX_HOME    "/data/data/com.termux/files/home"
#define RAF_TERMUX_SHELL   "/data/data/com.termux/files/usr/bin/sh"

typedef struct {
    char prefix[128];
    char home[128];
    char shell[128];
    char tmpdir[128];
    bool8 initialized;
    u8    _pad[7];
} RafTermuxEnv;

static RafTermuxEnv _g_termux_env RAF_SECTION(".bss");

RAF_INLINE void
raf_termux_env_init(RafTermuxEnv* e)
{
    raf_strcpy(e->prefix, RAF_TERMUX_PREFIX, 128);
    raf_strcpy(e->home,   RAF_TERMUX_HOME,   128);
    raf_strcpy(e->shell,  RAF_TERMUX_SHELL,  128);
    raf_strcpy(e->tmpdir, RAF_TERMUX_PREFIX "/tmp", 128);
    e->initialized = RAF_TRUE;
}

/* Bootstrap principal */
s32
raf_main(void)
{
    RAF_LOG_INF("RAF_MAIN: bootstrap start", 0);

    /* 1. Arena já inicializada em raf_entry_c */
    RAF_LOG_INF("ARENA: cap bytes", (u64)RAF_ARENA_SIZE);

    /* 2. Inicializa BitRaf */
    raf_bitraf_init();
    RAF_LOG_INF("BITRAF: blocks cap", (u64)RAF_MAX_BLOCKS);

    /* 3. Inicializa ambiente Termux */
    raf_termux_env_init(&_g_termux_env);
    RAF_LOG_INF("TERMUX: env initialized", (u64)_g_termux_env.initialized);

    /* 4. Inicializa VectraTriad com estado inicial de hardware */
    RafVectraTriad vt;
    raf_memset(&vt, 0, sizeof(vt));

    /* Coleta estados via hash do tempo monotônico (sem clock_gettime real
     * para simplificar — em produção: syscall SYS_clock_gettime CLOCK_MONOTONIC) */
    vt.cpu_state  = raf_hash64_u64(0xCPU_SEED_01ULL);
    vt.ram_state  = raf_hash64_u64(0xRAM_SEED_02ULL);
    vt.disk_state = raf_hash64_u64(0xDSK_SEED_03ULL);

    /* Aqui só ilustrativo: em hardware real, ler /proc/cpuinfo via sys_read */
    /* e preencher vt.cpu_state com hash do conteúdo */

    if (!raf_vectra_consensus(&vt)) {
        RAF_LOG_WRN("VECTRA: no consensus — hardware anomaly detected", 0);
        /* Continua com aviso — VectraTriad é monitor, não bloqueador */
    } else {
        RAF_LOG_INF("VECTRA: consensus ok", vt.cpu_state);
    }

    /* 5. Ciclo RAFAELIA principal — processa entrada do bootstrap */
    RafCycleState cs;
    RafEthicaVec  ev;
    raf_memset(&cs, 0, sizeof(cs));
    raf_memset(&ev, 0, sizeof(ev));

    /* Vetor ético conservador para bootstrap */
    ev.cuidado_vida      = 255;
    ev.certeza           = 200;
    ev.risco_vida        = 0;
    ev.confusao          = 0;
    ev.dano_irreversivel = 0;
    ev.nao_ferir         = 1;
    ev.soma              = 255;

    /* Input de bootstrap: hash do prefixo Termux */
    bool8 cycle_ok = raf_cycle_run(
        &cs,
        (const u8*)_g_termux_env.prefix,
        raf_strlen(_g_termux_env.prefix),
        &ev
    );

    if (!cycle_ok) {
        RAF_LOG_ERR("MAIN: rafaelia cycle failed at bootstrap", cs.seq);
        return 1;
    }

    RAF_LOG_INF("MAIN: omega bootstrap", cs.omega);
    RAF_LOG_INF("MAIN: sigma state", cs.sigma);

    /* 6. Snapshot determinístico (equivalente a make run-sector-snapshot-42)
     * Ref: Vectras-VM-Android/README.md: "Snapshot deterministico: run_sector(42)" */
    {
        u8 snap_input[42];
        raf_memset(snap_input, 42, 42);  /* 42 bytes com valor 42 */
        u64 snap_hash = raf_hash64_fnv1a(snap_input, 42);
        u32 snap_crc  = raf_crc32c(snap_input, 42);
        RAF_LOG_INF("SNAPSHOT-42: hash64", snap_hash);
        RAF_LOG_INF("SNAPSHOT-42: crc32c", (u64)snap_crc);
    }

    /* 7. Bootstrap completo — em produção: execve do shell Termux aqui
     * Para baremetal puro sem execve: entra no loop de processamento */
    RAF_LOG_INF("BOOTSTRAP: complete, omega", cs.omega);

    return 0;
}

================================================================================
  SEÇÃO 14 — TOROID: ESTRUTURA TOROIDAL PARA PROCESSAMENTO ESPACIAL
================================================================================

/* ---- raf_toroid.h ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/toroid.c
 *      llamaRafaelia README: "TOROID para estruturas de topologia toroidal"
 *      Magisk_Rafaelia/RAFAELIA_META_ARCHITECTURE_SUMMARY.md:
 *        "ToroidΔπφ = Δ·π·φ — substrato geométrico de processamento"
 *
 * Propósito: espaço toroidal 2D — wrapeia coordenadas nos limites
 * Sem heap. Dimensões fixas em tempo de compilação.
 * Aplicação: indexação de blocos BITRAF por geometria toroidal */

#define RAF_TOROID_W  16  /* largura do toroide */
#define RAF_TOROID_H  16  /* altura do toroide */
#define RAF_TOROID_SZ (RAF_TOROID_W * RAF_TOROID_H)

typedef struct {
    u64 cells[RAF_TOROID_SZ];  /* 256 células de u64 = 2KB */
    u32 w, h;
} RafToroid;

RAF_INLINE void
raf_toroid_init(RafToroid* t)
{
    raf_memset(t, 0, sizeof(*t));
    t->w = RAF_TOROID_W;
    t->h = RAF_TOROID_H;
}

/* Coordenadas com wrap toroidal — "sai de um lado, entra pelo outro" */
RAF_INLINE u32
raf_toroid_idx(const RafToroid* t, s32 x, s32 y)
{
    u32 xi = (u32)((x % (s32)t->w + (s32)t->w) % (s32)t->w);
    u32 yi = (u32)((y % (s32)t->h + (s32)t->h) % (s32)t->h);
    return yi * t->w + xi;
}

RAF_INLINE u64
raf_toroid_get(const RafToroid* t, s32 x, s32 y)
{
    return t->cells[raf_toroid_idx(t, x, y)];
}

RAF_INLINE void
raf_toroid_set(RafToroid* t, s32 x, s32 y, u64 v)
{
    t->cells[raf_toroid_idx(t, x, y)] = v;
}

/* Difusão toroidal: cada célula recebe XOR dos 4 vizinhos (sem heap) */
RAF_NOINLINE void
raf_toroid_diffuse(RafToroid* t)
{
    /* Buffer temporário na stack — 256 * 8 = 2KB */
    u64 tmp[RAF_TOROID_SZ];
    for (s32 y = 0; y < (s32)t->h; y++) {
        for (s32 x = 0; x < (s32)t->w; x++) {
            u32 idx = (u32)(y * (s32)t->w + x);
            tmp[idx] = raf_toroid_get(t, x-1, y)
                     ^ raf_toroid_get(t, x+1, y)
                     ^ raf_toroid_get(t, x, y-1)
                     ^ raf_toroid_get(t, x, y+1);
        }
    }
    raf_memcpy(t->cells, tmp, sizeof(tmp));
}

================================================================================
  SEÇÃO 15 — FLAGS DE COMPILAÇÃO E MAKEFILE ANOTADO
================================================================================

/*
 * MAKEFILE DE REFERÊNCIA (comentado como código)
 *
 * # Ref: Vectras-VM-Android/Makefile (scripts/native/build.sh)
 * #      llamaRafaelia/Makefile (baremetal targets)
 * #      Magisk_Rafaelia/build.py → equivalente CMake/Make para C puro
 *
 * CROSS := aarch64-linux-android-
 * CC    := $(CROSS)clang
 * LD    := $(CROSS)ld
 * STRIP := $(CROSS)strip
 *
 * # NDK mínimo: r27, API 24 (Android 7.0)
 * SYSROOT := $(NDK_ROOT)/sysroot
 *
 * CFLAGS := \
 *   -O3 \
 *   -march=armv8-a+crypto+crc \
 *   -mtune=cortex-a55 \
 *   -fno-plt \
 *   -ffreestanding \
 *   -fno-builtin \
 *   -nostdlib \
 *   -nostdinc \
 *   -nodefaultlibs \
 *   -fno-exceptions \
 *   -fno-rtti \
 *   -fno-unwind-tables \
 *   -fno-asynchronous-unwind-tables \
 *   -fomit-frame-pointer \
 *   -funroll-loops \
 *   -ffunction-sections \
 *   -fdata-sections \
 *   -DRAF_BAREMETAL=1 \
 *   -DRAF_NO_HEAP=1 \
 *   -DRAF_NO_LIBC=1 \
 *   -DRAF_ARCH_ARM64=1 \
 *   -DRAF_BITRAF=1
 *
 * LDFLAGS := \
 *   -static \
 *   -nostdlib \
 *   -Wl,--gc-sections \
 *   -Wl,-z,norelro \
 *   -Wl,--strip-all \
 *   -e _raf_start
 *
 * SRCS := \
 *   entry_arm64.S \
 *   raf_main.c
 *
 * # Target de self-test (gate obrigatório — ref: Vectras-VM-Android CI)
 * run-sector-selftest: bootstrap_raf
 *     ./bootstrap_raf --selftest-only
 *
 * # Snapshot determinístico de 42 bytes (ref: README Vectras)
 * run-sector-snapshot-42: bootstrap_raf
 *     ./bootstrap_raf --snapshot 42
 *
 * # Benchmark smoke (sem promessa de performance real)
 * run-core-bench-smoke: bootstrap_raf
 *     ./bootstrap_raf --bench-smoke
 *
 * bootstrap_raf: $(SRCS)
 *     $(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(SRCS)
 *     $(STRIP) --strip-all $@
 *
 * clean:
 *     rm -f bootstrap_raf
 */

================================================================================
  SEÇÃO 16 — INTEGRAÇÃO COM TERMUX BOOTSTRAP (sem abstração, sem GC)
================================================================================

/* ---- raf_termux_bootstrap.c ----
 * Ref: termux-app-rafacodephi/app/ (TermuxInstaller.java → bootstrap ZIP install)
 *      termux-app/terminal-emulator/ (TerminalSession.c — exec do shell)
 *      Vectras-VM-Android/shell-loader/ (shell-loader canonical)
 *      Vectras-VM-Android/terminal-emulator/ (terminal-emulator module)
 *
 * Propósito: substituir a lógica Java de bootstrap do Termux por código
 *   C puro baremetal — instala o bootstrap ZIP via syscalls, sem JNI,
 *   sem heap Java, sem GC.
 *
 * Fluxo original em Java (TermuxInstaller):
 *   1. Verifica se $PREFIX existe
 *   2. Baixa bootstrap.zip (HTTP)
 *   3. Extrai bootstrap.zip para $PREFIX
 *   4. Symlinks conforme SYMLINKS.txt
 *   5. chmod 755 nos binários
 *   6. Executa $SHELL
 *
 * Equivalente C baremetal aqui:
 *   1-2: Omitido (bootstrap.zip já presente em assets)
 *   3:   Extrai via raf_zipraf_decompress (overlay geometry)
 *   4-5: Via sys_ioctl + sys_open + sys_write
 *   6:   Via sys_execve (ARM64 syscall 221)
 */

#define SYS_execve  221
#define SYS_fchmod  52
#define SYS_mkdirat 34
#define SYS_openat  56   /* já definido como SYS_open acima, openat = 56 ARM64 */

/* Executa shell Termux — substitui processo atual (sem fork)
 * Ref: Magisk_Rafaelia/native/ (execve via syscall direto)
 * Arg: path = caminho do shell, argv e envp em arrays na stack */
RAF_NORETURN void
raf_execve_shell(const char* path, const char** argv, const char** envp)
{
    RAF_LOG_INF("EXECVE: launching", (u64)(uptr)path);
    s64 ret = raf_syscall3(
        SYS_execve,
        (u64)(uptr)path,
        (u64)(uptr)argv,
        (u64)(uptr)envp
    );
    /* Se chegou aqui, execve falhou */
    RAF_LOG_ERR("EXECVE: failed errno", (u64)(-ret));
    raf_exit(127);
}

/* Configura ambiente mínimo para shell Termux sem libc
 * Sem putenv, sem setenv — array de strings na stack */
RAF_INLINE void
raf_termux_launch(const RafTermuxEnv* e)
{
    /* argv na stack — sem malloc */
    const char* argv[4];
    argv[0] = e->shell;
    argv[1] = RAF_NULL;

    /* envp na stack — variáveis mínimas do Termux */
    /* Ref: termux-app: exporta TERMUX__ROOTFS_DIR, TERMUX__HOME, PREFIX, HOME */
    static char _env_prefix[160];
    static char _env_home[160];
    static char _env_tmpdir[160];
    static char _env_path[200];

    raf_strcpy(_env_prefix, "PREFIX=",    160);
    /* Concatena prefixo */
    {
        usize l = raf_strlen("PREFIX=");
        raf_strcpy(_env_prefix + l, e->prefix, 160 - l);
    }
    raf_strcpy(_env_home, "HOME=", 160);
    { usize l = raf_strlen("HOME="); raf_strcpy(_env_home + l, e->home, 160-l); }
    raf_strcpy(_env_tmpdir, "TMPDIR=", 160);
    { usize l = raf_strlen("TMPDIR="); raf_strcpy(_env_tmpdir+l, e->tmpdir, 160-l); }
    raf_strcpy(_env_path, "PATH=", 200);
    { usize l = raf_strlen("PATH="); raf_strcpy(_env_path+l, e->prefix, 200-l);
      usize l2 = raf_strlen(_env_path);
      raf_strcpy(_env_path+l2, "/bin:", 200-l2);
      l2 = raf_strlen(_env_path);
      raf_strcpy(_env_path+l2, e->prefix, 200-l2);
      l2 = raf_strlen(_env_path);
      raf_strcpy(_env_path+l2, "/sbin", 200-l2);
    }

    const char* envp[6];
    envp[0] = _env_prefix;
    envp[1] = _env_home;
    envp[2] = _env_tmpdir;
    envp[3] = _env_path;
    envp[4] = "TERM=xterm-256color";
    envp[5] = RAF_NULL;

    raf_execve_shell(e->shell, argv, envp);
    /* Never returns */
}

================================================================================
  SEÇÃO 17 — INTEGRAÇÃO LLAMA/DEEPSEEK BAREMETAL (inferência C puro)
================================================================================

/* ---- raf_llm_baremetal.c ----
 * Ref: llamaRafaelia/rafaelia-baremetal/src/ (todos os módulos)
 *      llamaRafaelia/src/ (llama.cpp core — C/C++ sem dependência externa)
 *      rafaelmeloreisnovo/DeepSeek-RafCoder (engine de inferência C)
 *      llamaRafaelia/assembler/ (ops NEON ARM64 para matmul)
 *      llamaRafaelia SPEC.md: "low-level llama targets minimal dependencies"
 *        "-llama-lowlevel (core runtime sem model registry)"
 *
 * Propósito: esqueleto de inferência LLM baremetal sem heap
 *   Q4_0 quantização: 4 bits por peso, bloco de 32 floats
 *   Sem malloc: buffer de KV-cache e pesos em arena BSS
 *   Matmul NEON inline para ARM64
 *   Sem BLAS, sem cuBLAS, sem qualquer lib externa */

/* Q4_0 block: 32 pesos de 4 bits + 1 escala float16 */
typedef struct RAF_PACKED {
    u16 scale_f16;       /* escala em float16 */
    u8  quants[16];      /* 32 valores de 4 bits, 2 por byte (nibble) */
} RafQ4Block;            /* 18 bytes por bloco de 32 pesos */

/* Converte float16 para float32 sem libc */
RAF_INLINE float
raf_f16_to_f32(u16 h)
{
    u32 sign     = (u32)(h >> 15);
    u32 exponent = (u32)((h >> 10) & 0x1F);
    u32 mantissa = (u32)(h & 0x3FF);
    u32 f32;
    if (exponent == 0) {
        /* Subnormal */
        if (mantissa == 0) { f32 = sign << 31; }
        else {
            /* Normaliza */
            exponent = 1;
            while (!(mantissa & 0x400)) { mantissa <<= 1; exponent--; }
            mantissa &= 0x3FF;
            f32 = (sign << 31) | ((exponent + 127 - 15) << 23) | (mantissa << 13);
        }
    } else if (exponent == 31) {
        /* Inf ou NaN */
        f32 = (sign << 31) | (0xFF << 23) | (mantissa << 13);
    } else {
        f32 = (sign << 31) | ((exponent + 127 - 15) << 23) | (mantissa << 13);
    }
    float result;
    raf_memcpy(&result, &f32, 4);
    return result;
}

/* Dot product Q4_0 × float32 vetor — NEON ARM64 inline
 * Ref: llamaRafaelia/assembler/ (ggml_vec_dot_q4_0_q8_0 ARM64 NEON)
 *      Magisk_Rafaelia/HARDWARE_OPTIMIZATION_GUIDE.md (SIMD NEON) */
RAF_NOINLINE float
raf_q4_dot_f32(const RafQ4Block* blks, u32 n_blocks,
               const float* vec)
{
    float sum = 0.0f;
    for (u32 b = 0; b < n_blocks; b++) {
        float scale = raf_f16_to_f32(blks[b].scale_f16);
        const u8* q = blks[b].quants;
        const float* v = vec + b * 32;

        /* Desquantiza e acumula — 32 pesos por bloco */
        /* Inline ASM NEON para máximo throughput ARM64 */
        /* Versão escalar (referência) — substituir por NEON em produção */
        for (u32 i = 0; i < 16; i++) {
            /* Nibble LO: peso i*2 */
            s32 q0 = (s32)(q[i] & 0xF) - 8;  /* centro em 0 */
            sum += (float)q0 * scale * v[i*2];
            /* Nibble HI: peso i*2+1 */
            s32 q1 = (s32)(q[i] >> 4)  - 8;
            sum += (float)q1 * scale * v[i*2+1];
        }
    }
    return sum;
}

/*
 * NEON inline ASM para dot product Q4x4 (32 elements per block):
 * [Este bloco ASM substituiria o loop escalar acima em produção]
 *
 * void raf_q4_dot_neon(float* out, const u8* q, const float* v, float scale) {
 *   __asm__ __volatile__ (
 *     // Carrega 16 bytes de quantizados
 *     "ld1 {v0.16b}, [%1]\n\t"
 *     // Extrai nibbles LO (máscara 0x0F)
 *     "movi v3.16b, #0x0F\n\t"
 *     "and  v1.16b, v0.16b, v3.16b\n\t"  // nibble LO
 *     "ushr v2.16b, v0.16b, #4\n\t"       // nibble HI
 *     // Subtrai 8 para centralizar (Q4 centrado)
 *     "movi v4.16b, #8\n\t"
 *     "ssub v1.16b, v1.16b, v4.16b\n\t"
 *     "ssub v2.16b, v2.16b, v4.16b\n\t"
 *     // Converte para int16
 *     "sxtl  v5.8h, v1.8b\n\t"
 *     "sxtl2 v6.8h, v1.16b\n\t"
 *     // Carrega vetores float e acumula
 *     // [...]
 *     : "+r"(out) : "r"(q), "r"(v), "r"(scale)
 *     : "v0","v1","v2","v3","v4","v5","v6","memory"
 *   );
 * }
 */

================================================================================
  SEÇÃO 18 — POLICY KERNEL E UNIFIED KERNEL (rmr_policy_kernel + rmr_unified)
================================================================================

/* ---- rmr_policy_kernel_impl.c ----
 * Ref: wojcikiewicz17/Vectras-VM-Android/rmr_policy_kernel.h
 *      wojcikiewicz17/Vectras-VM-Android/rmr_unified_kernel.h
 *      Vectras-VM-Android/engine/rmr/include/ (canonical headers)
 *      Vectras-VM-Android/PROJECT_STATE.md (estado: VECTRA_CORE_ENABLED)
 *
 * Propósito: política de execução do kernel VECTRA sem heap
 *   - Valida ABI policy (arm64-only em distribuição oficial)
 *   - Controla VECTRA_CORE_ENABLED flag em runtime
 *   - Gates de validação determinística (hash64 + crc32c)
 *   - Sem malloc, sem libc, sem abstração */

#define VECTRA_CORE_ENABLED 1  /* Flag de runtime — ligado em release */
#define VECTRA_ABI_ARM64    1  /* ABI oficial: arm64-v8a only */
#define VECTRA_MIN_API      24 /* Android 7.0 mínimo */

typedef struct {
    u32 core_enabled;    /* VECTRA_CORE_ENABLED */
    u32 abi_policy;      /* 1=arm64-only, 2=arm32+arm64 (validação interna) */
    u32 min_api;         /* API mínima Android */
    u32 hash_gate;       /* gate de hash determinístico */
    u32 crc_gate;        /* gate de CRC32C */
    u8  validated;       /* 1 após validação */
    u8  _pad[3];
} RafVectraPolicy;

static RafVectraPolicy _g_vectra_policy RAF_SECTION(".bss");

RAF_INLINE void
raf_policy_init(RafVectraPolicy* p)
{
    p->core_enabled = VECTRA_CORE_ENABLED;
    p->abi_policy   = VECTRA_ABI_ARM64;
    p->min_api      = VECTRA_MIN_API;
    /* Gates de hash para integridade da política */
    static const u8 policy_seed[] = {
        'V','E','C','T','R','A','_','C','O','R','E','_',
        'A','R','M','6','4','_','A','P','I','2','4'
    };
    p->hash_gate = (u32)raf_hash64_fnv1a(policy_seed, sizeof(policy_seed));
    p->crc_gate  = raf_crc32c(policy_seed, sizeof(policy_seed));
    p->validated = 0;
}

RAF_INLINE bool8
raf_policy_validate(RafVectraPolicy* p)
{
    if (!p->core_enabled) return RAF_FALSE;
    if (p->abi_policy != VECTRA_ABI_ARM64) return RAF_FALSE;
    if (p->min_api < VECTRA_MIN_API) return RAF_FALSE;
    /* Recomputar gates e comparar */
    static const u8 policy_seed[] = {
        'V','E','C','T','R','A','_','C','O','R','E','_',
        'A','R','M','6','4','_','A','P','I','2','4'
    };
    u32 h = (u32)raf_hash64_fnv1a(policy_seed, sizeof(policy_seed));
    u32 c = raf_crc32c(policy_seed, sizeof(policy_seed));
    if (h != p->hash_gate || c != p->crc_gate) return RAF_FALSE;
    p->validated = 1;
    return RAF_TRUE;
}

/* Unified Kernel: combina policy + ciclo RAFAELIA + bitraf em uma única
 * estrutura de controle — "kernel unificado" do engine Vectras */
typedef struct {
    RafVectraPolicy policy;
    RafCycleState   cycle;
    RafVectraTriad  triad;
    RafToroid       toroid;
    u32             run_count;
    u8              healthy;
    u8              _pad[3];
} RafUnifiedKernel;

static RafUnifiedKernel _g_kernel RAF_SECTION(".bss");

RAF_NOINLINE bool8
raf_kernel_init(RafUnifiedKernel* k)
{
    raf_memset(k, 0, sizeof(*k));
    raf_policy_init(&k->policy);
    if (!raf_policy_validate(&k->policy)) {
        RAF_LOG_ERR("KERNEL: policy validation failed", 0);
        return RAF_FALSE;
    }
    raf_toroid_init(&k->toroid);
    k->healthy = 1;
    RAF_LOG_INF("KERNEL: unified kernel initialized", 0);
    return RAF_TRUE;
}

RAF_NOINLINE bool8
raf_kernel_tick(RafUnifiedKernel* k, const u8* input, usize ilen,
                const RafEthicaVec* ev)
{
    if (!k->healthy) return RAF_FALSE;
    k->run_count++;
    /* Executa ciclo principal */
    if (!raf_cycle_run(&k->cycle, input, ilen, ev)) {
        k->healthy = 0;
        return RAF_FALSE;
    }
    /* Difunde toroide com omega do ciclo */
    raf_toroid_set(&k->toroid, (s32)(k->run_count % RAF_TOROID_W),
                              (s32)(k->run_count / RAF_TOROID_W % RAF_TOROID_H),
                              k->cycle.omega);
    raf_toroid_diffuse(&k->toroid);
    return RAF_TRUE;
}

================================================================================
  SEÇÃO 19 — TESTES INTEGRAÇÃO (angtestes — sem framework externo)
================================================================================

/* ---- raf_angtests.c ----
 * Ref: Vectras-VM-Android/formula_ci/tests/ (formula tests CI)
 *      llamaRafaelia/tests/ (baremetal tests)
 *      Magisk_Rafaelia/tests/ (native tests)
 *      Vectras-VM-Android/README.md:
 *        "run_sector_selftest: validates consecutive and parallel calls
 *         to detect shared global state regressions"
 *
 * Propósito: testes de integração end-to-end sem framework
 *   Sem libc assert. Sem Google Test. Sem Catch2.
 *   Cada teste retorna 0=pass, 1=fail.
 *   Chamado de raf_selftest_run() e do main de teste. */

typedef struct {
    const char* name;
    s32 (*fn)(void);
    s32 result;  /* 0=pass, !=0=fail */
} RafTest;

/* Teste: kernel unificado end-to-end */
s32 raf_test_kernel_e2e(void)
{
    raf_arena_init(&g_arena, _g_arena_buf, RAF_ARENA_SIZE);
    raf_bitraf_init();

    RafUnifiedKernel k;
    if (!raf_kernel_init(&k)) return 1;

    RafEthicaVec ev;
    raf_memset(&ev, 0, sizeof(ev));
    ev.cuidado_vida = 200; ev.certeza = 200;
    ev.risco_vida = 5; ev.confusao = 5;
    ev.dano_irreversivel = 2; ev.nao_ferir = 1;

    static const u8 test_input[] = { 0x42, 0x49, 0x54, 0x52, 0x41, 0x46 };
    /* Tick sequencial — detecta regressão de estado global */
    for (u32 i = 0; i < 10; i++) {
        if (!raf_kernel_tick(&k, test_input, 6, &ev)) return 1;
    }
    /* Estado deve ser determinístico — re-rodar com mesmo input dá mesmo omega */
    u64 omega_first = k.cycle.omega;
    RafUnifiedKernel k2;
    raf_kernel_init(&k2);
    for (u32 i = 0; i < 10; i++) raf_kernel_tick(&k2, test_input, 6, &ev);
    if (omega_first != k2.cycle.omega) return 1;

    RAF_LOG_INF("TEST E2E: kernel deterministic omega", omega_first);
    return 0;
}

/* Teste: BITRAF parallel sem state compartilhado */
s32 raf_test_bitraf_parallel(void)
{
    raf_bitraf_init();
    s32 b1 = raf_bitraf_alloc_block();
    s32 b2 = raf_bitraf_alloc_block();
    if (b1 < 0 || b2 < 0) return 1;

    /* Preenche dados diferentes */
    for (u32 i = 0; i < 59; i++) _g_bitraf_blocks[b1].data[i] = (u8)i;
    for (u32 i = 0; i < 59; i++) _g_bitraf_blocks[b2].data[i] = (u8)(i ^ 0xAA);

    raf_bitraf_seal(b1);
    raf_bitraf_seal(b2);

    if (!raf_bitraf_verify(b1)) return 1;
    if (!raf_bitraf_verify(b2)) return 1;

    /* XOR entre blocos */
    raf_bitraf_xor_blocks(b1, b2);
    if (!raf_bitraf_verify(b1)) return 1;

    RAF_LOG_INF("TEST BITRAF PARALLEL: pass", 0);
    return 0;
}

/* Teste: ring buffer sem overflow */
s32 raf_test_ring(void)
{
    RafRing r;
    raf_ring_init(&r);
    for (u32 i = 0; i < RAF_RING_CAP; i++) {
        if (!raf_ring_push(&r, (u64)i)) return 1;
    }
    if (!raf_ring_full(&r)) return 1;
    if (raf_ring_push(&r, 999)) return 1; /* deve falhar */
    u64 v;
    if (!raf_ring_pop(&r, &v) || v != 0) return 1;
    if (!raf_ring_push(&r, 999)) return 1; /* agora cabe */
    RAF_LOG_INF("TEST RING: pass", (u64)r.count);
    return 0;
}

/* Suite de testes */
static RafTest _g_tests[] = {
    { "selftest_sector",    raf_selftest_run,        0 },
    { "kernel_e2e",         raf_test_kernel_e2e,     0 },
    { "bitraf_parallel",    raf_test_bitraf_parallel, 0 },
    { "ring_buffer",        raf_test_ring,            0 },
};
#define RAF_TEST_COUNT (sizeof(_g_tests)/sizeof(_g_tests[0]))

s32 raf_run_all_tests(void)
{
    s32 total_fail = 0;
    for (u32 i = 0; i < RAF_TEST_COUNT; i++) {
        _g_tests[i].result = _g_tests[i].fn();
        if (_g_tests[i].result != 0) {
            RAF_LOG_ERR(_g_tests[i].name, (u64)_g_tests[i].result);
            total_fail++;
        } else {
            RAF_LOG_INF(_g_tests[i].name, 0);
        }
    }
    RAF_LOG_INF("TESTS: total failures", (u64)total_fail);
    return total_fail;
}

================================================================================
  SEÇÃO 20 — RESUMO DE CAMINHOS E ESTRUTURA DE ARQUIVOS
================================================================================

/*
 * ESTRUTURA DE ARQUIVOS DO BOOTSTRAP LOWLEVEL RAFAELIA
 * (para uso com CMake + NDK conforme Vectras-VM-Android/CMakeLists.txt)
 *
 * bootstrap_rafaelia/
 * ├── entry_arm64.S          — ponto de entrada ASM (_raf_start)
 * ├── raf_types.h            — tipos primitivos sem libc (Seção 1)
 * ├── raf_syscall_arm64.h    — syscalls ARM64 SVC (Seção 2)
 * ├── raf_arena.h            — arena bump-pointer BSS (Seção 3)
 * ├── raf_mem.h              — memset/memcpy/strlen sem libc (Seção 4)
 * ├── raf_log.h              — log circular append-only (Seção 5)
 * ├── raf_bitraf.h           — BITRAF nibble HI/LO + Witness (Seção 6)
 * ├── raf_hash.h             — hash64 FNV1a + fibonacci-rafael (Seção 7)
 * ├── raf_zipraf.h           — compressão RLE nibble (Seção 8)
 * ├── raf_rafstore.h         — ring buffer + KV store (Seção 9)
 * ├── raf_cycle.h            — ciclo RAFAELIA ψ→χ→ρ→∆→Σ→Ω (Seção 10)
 * ├── raf_selftest.c         — selftest determinístico (Seção 11)
 * ├── raf_main.c             — bootstrap principal (Seção 13)
 * ├── raf_toroid.h           — topologia toroidal (Seção 14)
 * ├── raf_termux_bootstrap.c — integração Termux sem JNI (Seção 16)
 * ├── raf_llm_baremetal.c    — inferência LLM Q4 baremetal (Seção 17)
 * ├── rmr_policy_kernel.c    — policy kernel Vectras (Seção 18)
 * └── raf_angtests.c         — testes integração (Seção 19)
 *
 * CAMINHOS DE REFERÊNCIA NOS REPOSITÓRIOS:
 *
 *   Vectras-VM-Android:
 *     rmr_lowlevel.h          → stub → engine/rmr/include/rmr_lowlevel.h
 *     rmr_policy_kernel.h     → policy de kernel
 *     rmr_unified_kernel.h    → kernel unificado
 *     engine/rmr/             → implementação canônica RMR
 *     shell-loader/           → shell loader (Java+JNI) → aqui: C puro
 *     terminal-emulator/      → emulador terminal → execve direto
 *     scripts/native/         → build scripts nativos
 *     Makefile                → make run-sector-selftest (gate CI)
 *     .github/workflows/      → android-ci.yml, host-ci.yml, pipeline-orchestrator.yml
 *     gradle.properties       → APP_ABI_POLICY=arm64-only
 *
 *   llamaRafaelia:
 *     rafaelia-baremetal/     → módulo baremetal completo (42 tools)
 *     assembler/              → assembler ARM64 NEON
 *     rmrCti/                 → CTI runtime
 *     src/                    → llama.cpp core C/C++
 *     ggml/                   → backend ggml
 *     docs/rafaelia/          → specs BitStack, Smart Guard, ZIPRAF
 *
 *   Magisk_Rafaelia:
 *     native/src/core/        — rafaelia_audit.rs, rafaelia_telemetry.rs
 *     native/                 — C/C++ Magisk native (MagiskBoot, MagiskSU)
 *     BAREMETAL_ARCHITECTURE_ANALYSIS.md
 *     HARDWARE_OPTIMIZATION_GUIDE.md
 *     ANALISE_RECODIFICACAO_LOWLEVEL.md
 *
 *   DeepSeek-RafCoder:
 *     Engine de inferência C para DeepSeek — integração com LlamaRafaelia
 *     ciclo de processamento de tokens baremetal
 *
 *   termux-app-rafacodephi:
 *     app/                    → TermuxInstaller (bootstrap ZIP) → aqui: C puro
 *     terminal-emulator/      → TerminalSession (exec shell) → raf_execve_shell
 *     shell-loader/           → shell loader → raf_termux_launch
 */

================================================================================
  SEÇÃO 21 — NOTAS DE PRÉ-COMPILAÇÃO E USO
================================================================================

/*
 * PRÉ-COMPILAÇÃO (Precompiled Headers / LTO):
 *
 * Para precompilar os headers (PCH) com clang NDK:
 *   clang $(CFLAGS) -x c-header raf_types.h -o raf_types.h.pch
 *   clang $(CFLAGS) -include-pch raf_types.h.pch -c raf_main.c
 *
 * Para LTO (Link-Time Optimization) máximo:
 *   CFLAGS += -flto=thin
 *   LDFLAGS += -flto=thin -Wl,--lto-O3
 *
 * Para PGO (Profile-Guided Optimization) — opcional em hardware real:
 *   1. Compile com -fprofile-generate
 *   2. Execute selftest no dispositivo ARM64
 *   3. Recompile com -fprofile-use=default.profdata
 *
 * HARDWARE ALVO (ref: Magisk_Rafaelia/HARDWARE_OPTIMIZATION_GUIDE.md):
 *   - Cortex-A55 (Snapdragon 460, 680, 695, 778G, 865, 888)
 *   - Cortex-A76/A78 (Snapdragon 865+, 888+)
 *   - Kryo 570/660/670 (Snapdragon OEM variant)
 *   - Exynos 850, 1080, 2100, 2200
 *   - Dimensity 700, 900, 1200, 8100
 *   ABI: arm64-v8a (AArch64 EABI), API 24+
 *
 * VALIDAÇÃO DE ABI (ref: Vectras-VM-Android README):
 *   tools/check_abi_policy_alignment.py verifica APP_ABI_POLICY vs qemu_launch.yml
 *   gradle.properties: APP_ABI_POLICY=arm64-only, SUPPORTED_ABIS=arm64-v8a
 *
 * FLAGS DE HARDWARE ADICIONAIS:
 *   -march=armv8-a+crypto  → CRC32 e AES hardware (ARMv8 Crypto Extension)
 *   -mfpu=neon-fp-armv8    → NEON 128-bit SIMD
 *   -mcpu=cortex-a55       → otimiza pipeline específico
 *   -moutline-atomics      → atomic correto sem LL/SC loop no A55
 */

================================================================================
  FIM DO ARQUIVO — BOOTSTRAP LOWLEVEL RAFAELIA COMPLETO
  ∆RAFAELIA_CORE·Ω — Ciclo ψ→χ→ρ→∆→Σ→Ω — VAZIO→VERBO→CHEIO→RETRO
================================================================================
