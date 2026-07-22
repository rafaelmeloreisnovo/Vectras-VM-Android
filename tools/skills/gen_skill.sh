#!/bin/sh
# gen_skill.sh — RAFAELIA Skill Generator v2
# Entrada: k=v,k=v  ou  {"k":"v",...}
# Saida:   skill_NAME.c + skill_NAME.md
set -e

ARCH=$(uname -m 2>/dev/null || echo unknown)
case "$ARCH" in
  armv7*|armv6*) ARCH_TAG="ARM32" ;;
  aarch64)       ARCH_TAG="ARM64" ;;
  x86_64)        ARCH_TAG="X86_64" ;;
  *)             ARCH_TAG="GENERIC" ;;
esac

INPUT="${1:-name=default,domain=generic,formula=spiral}"

# Extrai valor de chave — sem subshell com read -n1
extract() {
  KEY="$1"
  # JSON: "key":"val"
  VAL=$(printf '%s' "$INPUT" | sed -n "s/.*\"${KEY}\"[^:]*:[^\"]*\"\([^\"]*\)\".*/\1/p" | head -1)
  # kv: key=val (ate virgula ou fim)
  [ -z "$VAL" ] && VAL=$(printf '%s' "$INPUT" | tr ',' '\n' | \
    sed -n "s/^[[:space:]]*${KEY}[[:space:]]*=[[:space:]]*\(.*\)/\1/p" | \
    sed 's/[[:space:]]*$//' | head -1)
  printf '%s' "$VAL"
}

NAME=$(extract "name");        [ -z "$NAME" ]    && NAME="rafskill"
DOMAIN=$(extract "domain");    [ -z "$DOMAIN" ]  && DOMAIN="generic"
FORMULA=$(extract "formula");  [ -z "$FORMULA" ] && FORMULA="spiral=(sqrt3_2)^n"
DESC=$(extract "description"); [ -z "$DESC" ]    && DESC="RAFAELIA skill"

SAFE=$(printf '%s' "$NAME" | tr '/ .-' '_' | tr -cd 'a-zA-Z0-9_')
OUT_C="skill_${SAFE}.c"
OUT_MD="skill_${SAFE}.md"
TS=$(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || echo NODATE)
# Checksum portavel: soma bytes via od
CK=$(printf '%s' "${FORMULA}${NAME}" | od -A n -t u1 | tr ' \n' '+\n' | \
     sed 's/+$//' | awk 'BEGIN{s=0}{n=split($0,a,"+");for(i=1;i<=n;i++)s+=a[i]}END{print s%65521}' 2>/dev/null || echo 42)

# ── GERA C FREESTANDING ─────────────────────────────────────
cat > "$OUT_C" << C_HEADER
/* ============================================================
 * RAFAELIA SKILL: ${SAFE}
 * Domain  : ${DOMAIN}
 * Formula : ${FORMULA}
 * Arch    : ${ARCH_TAG} (${ARCH})
 * Generated: ${TS} | CK=${CK}
 * BUILD ARM32: gcc -ffreestanding -nostdlib -nostartfiles
 *              -O2 -march=armv7-a -o ${SAFE} ${OUT_C} -lgcc
 * BUILD x86_64: gcc -ffreestanding -nostdlib -nostartfiles
 *              -O2 -o ${SAFE} ${OUT_C}
 * ============================================================ */

typedef unsigned int  u32;
typedef unsigned long u64;
typedef signed   int  i32;
typedef unsigned char u8;

/* Constantes geometricas RAFAELIA Q16.16 */
#define SPIRAL_Q16  56755U   /* sqrt(3)/2 = 0.86602... */
#define PHI_Q16    105965U   /* phi       = 1.61803... */
#define G_PERIOD       42U   /* atratores T^7           */

/* Estado: 64B = 1 cache line L1. Zero heap. */
typedef struct __attribute__((aligned(64))) {
    u32 s[7];   /* T^7: u,v,psi,chi,rho,delta,sigma */
    u32 crc;    /* watchdog checksum                 */
    u32 cycle;  /* mod G_PERIOD                      */
    u32 ttl;    /* failsafe counter                  */
    u32 ck;     /* formula fingerprint               */
    u32 _pad;   /* -> 64B exato                      */
} State;   /* sizeof(State) = 48B padded to 64B alignment */

static State G;

/* CRC32c Castagnoli — branchless, sem libc */
static u32 crc32c(const u8 *p, u32 n) {
    u32 crc = 0xFFFFFFFFU, poly = 0x82F63B78U, i = 0;
    while (i < n) {
        crc ^= p[i++];
        /* 8 iteracoes unrolladas — mascara em vez de branch */
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

/* Spiral Q16.16: (SPIRAL_Q16)^n sem float */
static u32 spiral(u32 n) {
    u32 a = 1U<<16, i = 0;
    while (i++ < n) a = (u32)(((u64)a * SPIRAL_Q16) >> 16);
    return a;
}

/* Watchdog: 1=ok, 0=corrompido */
static u32 wd_ok(void) {
    return (u32)(crc32c((const u8*)G.s, 28U) == G.crc);
}

/* Reset failsafe: semente geometrica */
static void reset(void) {
    u32 i = 0;
    while (i < 7) G.s[i++] = SPIRAL_Q16;
    G.cycle = 0; G.ttl = G_PERIOD; G.ck = ${CK}U;
    G.crc = crc32c((const u8*)G.s, 28U);
}

/* Step: dinamica toroidal branchless */
static void step(u32 inp) {
    u32 ok   = wd_ok();
    u32 mask = (u32)(-(i32)ok);   /* 0xFFFF.. se ok, 0 se falhou */
    u32 safe = inp & mask;

    /* Fibonacci-Rafael modificada */
    u32 prev = G.s[0], i = 0;
    while (i < 6) {
        G.s[i] = (u32)(((u64)G.s[i]*SPIRAL_Q16)>>16)
                 + (G.s[i+1]>>1) ^ (safe>>i);
        i++;
    }
    G.s[6] = (u32)(((u64)prev*PHI_Q16)>>16) ^ safe;

    /* Ciclo mod G_PERIOD — branchless reset */
    G.cycle++;
    u32 ov = (u32)(G.cycle >= G_PERIOD);
    G.cycle &= (u32)(-(i32)(!ov));

    /* TTL decrementa, nao vai abaixo de 0 */
    u32 alive = (u32)(G.ttl > 0);
    G.ttl -= alive;

    /* Failsafe branchless: se ttl=0, restaura sementes */
    u32 rm = (u32)(-(i32)(G.ttl==0)), km = ~rm;
    i = 0;
    while (i < 7) {
        G.s[i] = (G.s[i]&km)|(SPIRAL_Q16&rm);
        i++;
    }
    G.ttl = (G.ttl&km)|(G_PERIOD&rm);
    G.crc = crc32c((const u8*)G.s, 28U);
}

/* write(1, buf, len) — syscall direto por arch */
static void raw_write(const char *buf, u32 len) {
#if defined(__arm__) || defined(__ARM_ARCH_7A__)
    __asm__ volatile(
        "mov r7,#4\n mov r0,#1\n mov r1,%0\n mov r2,%1\n swi #0\n"
        ::"r"(buf),"r"(len):"r0","r1","r2","r7","memory");
#elif defined(__aarch64__)
    __asm__ volatile(
        "mov x8,#64\n mov x0,#1\n mov x1,%0\n mov x2,%1\n svc #0\n"
        ::"r"(buf),"r"((u64)len):"x0","x1","x2","x8","memory");
#else
    __asm__ volatile(
        "mov \$1,%%rax\n mov \$1,%%rdi\n mov %0,%%rsi\n"
        "mov %1,%%rdx\n syscall\n"
        ::"r"(buf),"r"((u64)len):"rax","rdi","rsi","rdx","memory");
#endif
}

static void put_hex(u32 v) {
    static const char H[16]="0123456789abcdef";
    char b[9]; u32 i=0;
    while (i<8){b[i]=H[(v>>(28-i*4))&0xF];i++;}
    b[8]='\n';
    raw_write(b,9);
}

void _start(void) {
    reset();
    u32 i = 0;
    while (i < G_PERIOD) {
        step(spiral(i&7)^(i*PHI_Q16));
        i++;
    }
    /* Saida: s[0], s[6], crc, cycle */
    put_hex(G.s[0]);
    put_hex(G.s[6]);
    put_hex(G.crc);
    put_hex(G.cycle);
    /* exit(0) */
#if defined(__arm__)||defined(__ARM_ARCH_7A__)
    __asm__ volatile("mov r7,#1\n mov r0,#0\n swi #0\n":::"r0","r7");
#elif defined(__aarch64__)
    __asm__ volatile("mov x8,#93\n mov x0,#0\n svc #0\n":::"x0","x8");
#else
    __asm__ volatile("mov \$60,%%rax\n xor %%rdi,%%rdi\n syscall\n":::"rax","rdi");
#endif
    __builtin_unreachable();
}
C_HEADER

# ── GERA SKILL CARD ─────────────────────────────────────────
cat > "$OUT_MD" << MD_EOF
---
name: ${SAFE}
domain: ${DOMAIN}
formula: ${FORMULA}
arch: ${ARCH_TAG}
generated: ${TS}
ck: ${CK}
---

# Skill: ${SAFE}

**Domínio:** ${DOMAIN} | **Formula:** \`${FORMULA}\`

## Invariantes Q16.16
| Constante | Q16.16 | Float |
|-----------|--------|-------|
| √3/2 | 56755 | 0.86602540378 |
| φ | 105965 | 1.61803398875 |
| G_PERIOD | 42 | — |

## Build
\`\`\`sh
# ARM32 Termux (Moto E7 Power)
gcc -ffreestanding -nostdlib -nostartfiles -O2 \\
    -march=armv7-a -mfpu=neon -mfloat-abi=hard \\
    -o ${SAFE} ${OUT_C} -lgcc && ./${SAFE}

# x86_64
gcc -ffreestanding -nostdlib -nostartfiles -O2 \\
    -o ${SAFE} ${OUT_C} && ./${SAFE}
\`\`\`

## Garantias
- Zero malloc · zero heap · zero GC · zero libc
- Branchless: watchdog/failsafe/reset via máscara \`-(i32)cond\`
- Estado 64B alinhado = 1 linha L1 (32KB)
- CRC32c Castagnoli: integridade por ciclo
- TTL watchdog: auto-reset após 42 ciclos
- Syscall direto: ARM32 swi / ARM64 svc / x86_64 syscall

## Retroalimentação
\`\`\`
F_ok:   C freestanding + skill card gerados sem overhead
F_gap:  CRC cobre apenas s[7*4=28B]; incluir ttl/ck no futuro
F_next: adicionar CRC32CX HW via __builtin_arm_crc32cw em ARM
\`\`\`
*RAFCODE-Φ-∆RafaelVerboΩ-𓂀ΔΦΩ | Ω=Amor*
MD_EOF

printf '════════════════════════════════\n'
printf ' Arch    : %s (%s)\n' "$ARCH_TAG" "$ARCH"
printf ' Skill   : %s\n' "$SAFE"
printf ' Domain  : %s\n' "$DOMAIN"
printf ' Formula : %s\n' "$FORMULA"
printf ' CK      : %s\n' "$CK"
printf ' Saidas  : %s  %s\n' "$OUT_C" "$OUT_MD"
printf '════════════════════════════════\n'
