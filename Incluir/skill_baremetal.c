/* ============================================================
 * RAFAELIA SKILL — BARE-METAL PURO
 * SEM OS · SEM SYSCALL · SEM LIBC · SEM HEAP
 *
 * Alvos:
 *   ARM Cortex-M (STM32, RP2040, nRF52...)
 *   ARM Cortex-A sem MMU (bare boards)
 *   x86 ring0 / bootloader stage
 *
 * Saída: UART memory-mapped (endereço configurável)
 * Entry: reset_handler() — não é _start, não é main
 *
 * BUILD Cortex-M:
 *   arm-none-eabi-gcc -ffreestanding -nostdlib -nostartfiles
 *     -mcpu=cortex-m4 -mthumb -O2
 *     -T link.ld -o skill.elf skill_baremetal.c
 *
 * BUILD Cortex-A (sem OS, bare):
 *   arm-none-eabi-gcc -ffreestanding -nostdlib -nostartfiles
 *     -march=armv7-a -O2
 *     -T link.ld -o skill.elf skill_baremetal.c
 *
 * BUILD x86 (bootloader/ring0):
 *   gcc -ffreestanding -nostdlib -nostartfiles -m32 -O2
 *     -T link.ld -o skill.elf skill_baremetal.c
 * ============================================================ */

/* ── TIPOS PRIMITIVOS — zero stdint.h ─────────────────────── */
typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef signed   int       i32;

/* ── UART MEMORY-MAPPED ────────────────────────────────────
 * Cada plataforma expõe UART como registrador em endereço fixo.
 * Escrever 1 byte nesse endereço = transmitir via serial.
 *
 * Exemplos reais:
 *   STM32F4:  UART1_DR  = 0x40011004  (Data Register)
 *   RP2040:   UART0_DR  = 0x40034000
 *   BCM2835:  UART0_DR  = 0x20201000  (Raspberry Pi 1)
 *   BCM2837:  UART0_DR  = 0x3F201000  (Raspberry Pi 3)
 *   Qemu virt ARM: PL011 = 0x09000000
 *   x86 COM1: I/O port  = 0x3F8 (acesso via outb, diferente)
 *
 * Troque UART_BASE pela plataforma alvo.
 * ────────────────────────────────────────────────────────── */
#ifndef UART_BASE
  #if defined(QEMU_VIRT)
    #define UART_BASE  0x09000000UL   /* QEMU ARM virt PL011  */
  #elif defined(RPI3)
    #define UART_BASE  0x3F201000UL   /* BCM2837 mini UART DR */
  #elif defined(STM32F4)
    #define UART_BASE  0x40011004UL   /* USART1 DR            */
  #else
    #define UART_BASE  0x09000000UL   /* default: QEMU virt   */
  #endif
#endif

/* PL011 flags register: bit 5 = TX FIFO full */
#ifndef UART_FLAG
  #define UART_FLAG  (UART_BASE + 0x18UL)
#endif
#define UART_TXFF  (1U << 5)          /* TX FIFO Full flag    */

/* Volatile pointer — impede o compilador de otimizar o acesso */
#define MMIO32(addr)  (*(volatile u32*)(addr))
#define MMIO8(addr)   (*(volatile u8 *)(addr))

/* ── UART: envia 1 byte aguardando TX ready ────────────────
 * Sem syscall. Sem OS. Polling direto no registrador.
 * Em bare-metal isso é o equivalente de write(1,&c,1).
 * ─────────────────────────────────────────────────────────  */
static void uart_putc(u8 c) {
    /* Aguarda TX FIFO não estar cheio (busy-wait) */
    while (MMIO32(UART_FLAG) & UART_TXFF) { /* spin */ }
    MMIO8(UART_BASE) = c;
}

static void uart_puts(const char *s) {
    while (*s) uart_putc((u8)*s++);
}

static void uart_put_hex(u32 v) {
    static const char H[16] = "0123456789abcdef";
    char buf[11];
    buf[0]='0'; buf[1]='x';
    u32 i = 0;
    while (i < 8) { buf[2+i] = H[(v>>(28-i*4))&0xF]; i++; }
    buf[10] = '\n';
    i = 0;
    while (i < 11) { uart_putc((u8)buf[i]); i++; }
}

/* ── CONSTANTES GEOMETRICAS RAFAELIA Q16.16 ───────────────── */
#define SPIRAL_Q16  56755U   /* sqrt(3)/2 */
#define PHI_Q16    105965U   /* phi        */
#define G_PERIOD       42U   /* atratores  */

/* ── ESTADO ESTATICO: BSS — zero-init pelo linker script ──── */
/* Sem malloc. Sem heap. Vive na seção .bss ou .data da flash. */
typedef struct __attribute__((aligned(64))) {
    u32 s[7];   /* T^7 */
    u32 crc;
    u32 cycle;
    u32 ttl;
    u32 ck;
    u32 _pad;
} State;

static State G;   /* alocado em BSS — endereço resolvido pelo linker */

/* ── CRC32c CASTAGNOLI — branchless, zero libc ────────────── */
static u32 crc32c(const u8 *p, u32 n) {
    u32 crc = 0xFFFFFFFFU, poly = 0x82F63B78U, i = 0;
    while (i < n) {
        crc ^= p[i++];
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
        crc = (crc>>1)^(poly&(u32)(-(i32)(crc&1)));
    }
    return ~crc;
}

/* ── SPIRAL Q16.16 sem float ──────────────────────────────── */
static u32 spiral(u32 n) {
    u32 a = 1U<<16, i = 0;
    while (i++ < n) a = (u32)(((u64)a * SPIRAL_Q16) >> 16);
    return a;
}

/* ── WATCHDOG + FAILSAFE branchless ──────────────────────── */
static void reset_state(void) {
    u32 i = 0;
    while (i < 7) G.s[i++] = SPIRAL_Q16;
    G.cycle = 0; G.ttl = G_PERIOD; G.ck = 0xCAFE0042U;
    G.crc = crc32c((const u8*)G.s, 28U);
}

static void step(u32 inp) {
    u32 ok   = (u32)(crc32c((const u8*)G.s, 28U) == G.crc);
    u32 mask = (u32)(-(i32)ok);
    u32 safe = inp & mask;

    u32 prev = G.s[0], i = 0;
    while (i < 6) {
        G.s[i] = (u32)(((u64)G.s[i]*SPIRAL_Q16)>>16)
                 + (G.s[i+1]>>1) ^ (safe>>i);
        i++;
    }
    G.s[6] = (u32)(((u64)prev*PHI_Q16)>>16) ^ safe;

    G.cycle++;
    u32 ov = (u32)(G.cycle >= G_PERIOD);
    G.cycle &= (u32)(-(i32)(!ov));

    u32 alive = (u32)(G.ttl > 0);
    G.ttl -= alive;

    u32 rm = (u32)(-(i32)(G.ttl==0)), km = ~rm;
    i = 0;
    while (i < 7) { G.s[i]=(G.s[i]&km)|(SPIRAL_Q16&rm); i++; }
    G.ttl  = (G.ttl&km)|(G_PERIOD&rm);
    G.crc  = crc32c((const u8*)G.s, 28U);
}

/* ── LOOP INFINITO — bare-metal nunca retorna ─────────────
 * Em OS: processo termina com exit().
 * Em bare-metal: não há para onde voltar.
 * O processador executaria lixo de memória se retornasse.
 * Solução: loop infinito ou halt.
 * ────────────────────────────────────────────────────────── */
static void halt(void) {
#if defined(__arm__) || defined(__aarch64__)
    /* WFI = Wait For Interrupt: para clock, economiza energia */
    __asm__ volatile("wfi" ::: "memory");
    /* Se acordar por NMI, trava de novo */
    while(1) { __asm__ volatile("wfi"); }
#elif defined(__x86_64__) || defined(__i386__)
    /* HLT = halt instruction: para pipeline até próxima IRQ */
    __asm__ volatile("hlt" ::: "memory");
    while(1) { __asm__ volatile("hlt"); }
#else
    while(1) { /* spin eterno — ultimo recurso */ }
#endif
}

/* ── RESET HANDLER — entry point real em bare-metal ────────
 * NÃO É _start. NÃO É main.
 * O vetor de reset (tabela em 0x00000000 ou 0xFFFF0000)
 * aponta para este símbolo.
 * Antes de qualquer C: stack pointer já deve estar setado
 * (pelo linker script via MSP inicial no Cortex-M,
 *  ou por código ASM de startup externo).
 * ────────────────────────────────────────────────────────── */
__attribute__((section(".text.reset_handler")))
void reset_handler(void) {
    /* 1. Init estado */
    reset_state();

    /* 2. Anuncio via UART */
    uart_puts("RAFAELIA BARE-METAL OK\n");
    uart_puts("Spiral(√3/2)^n Q16.16\n");

    /* 3. Roda G_PERIOD ciclos */
    u32 i = 0;
    while (i < G_PERIOD) {
        step(spiral(i & 7U) ^ (i * PHI_Q16));
        i++;
    }

    /* 4. Dump estado final */
    uart_puts("s[0]="); uart_put_hex(G.s[0]);
    uart_puts("s[6]="); uart_put_hex(G.s[6]);
    uart_puts("crc ="); uart_put_hex(G.crc);
    uart_puts("cyc ="); uart_put_hex(G.cycle);

    /* 5. NUNCA RETORNA — processador sem OS nao tem para onde ir */
    uart_puts("HALT\n");
    halt();
}

/* ── VETOR DE RESET (Cortex-M style) ───────────────────────
 * Cortex-M espera tabela de vetores em 0x00000000:
 *   [0] = endereço inicial do stack pointer
 *   [1] = endereço de reset_handler
 * Este bloco vai na seção .vectors do linker script.
 * Para Cortex-A ou x86 bare: o mecanismo é diferente
 * (VBAR_EL1, IDT, etc.) mas o princípio é igual.
 * ────────────────────────────────────────────────────────── */
extern u32 _stack_top;   /* definido pelo linker script */

__attribute__((section(".vectors"), used))
static void (*const vector_table[])(void) = {
    (void(*)(void))&_stack_top,  /* SP inicial */
    reset_handler,               /* reset       */
    reset_handler,               /* NMI         */
    reset_handler,               /* HardFault   */
    /* ... outros vetores de IRQ conforme SoC ... */
};
