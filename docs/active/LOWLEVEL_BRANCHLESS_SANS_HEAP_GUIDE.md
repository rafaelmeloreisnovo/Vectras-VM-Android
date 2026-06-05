<!-- DOC_ORG_SCAN: 2026-06-05 | source-scan: active | status: expanded-from-stub -->

# LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE

Guia técnico completo para desenvolvimento low-level sem heap, sem GC,
branchless e sem fricção de abstração — conforme praticado no engine
Vectras/RAFAELIA (ARM64 + JNI + baremetal).

Ref: `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt`, `engine/rmr/src/rmr_neon_simd.c`,
`engine/rmr/include/rmr_lowlevel.h`, `FIXES_SUMMARY.md`

---

## 1. Motivação: Por Que Sem Heap e Branchless?

### O problema do heap dinâmico no hot path

`malloc`/`free` introduzem:
- **Fragmentação**: alocações de tamanhos variados criam buracos no heap
- **Não-determinismo de latência**: o alocador usa locks internos; pode bloquear por microsegundos
- **GC pressure (JVM)**: qualquer alocação Java pressiona o Garbage Collector
- **Opacidade em crash**: o estado do heap é ilegível sem ferramentas especiais

No Vectras Engine, o hot path usa **arenas estáticas em BSS** — toda memória
reservada em compile-time ou no startup, nunca liberada granularmente.

### O problema dos branches imprevisíveis

Branches imprevisíveis custam 15–20 ciclos em ARM Cortex-A55 (misprediction penalty).
Em código de integridade e roteamento (coração do RMR), isso é inaceitável.
A solução é **aritmética de máscara de bits** e instruções `csel`/`csinc` ARM64.

---

## 2. Tipos Primitivos Sem LibC

Ref: `engine/rmr/include/rmr_lowlevel.h`, `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt §1`

```c
typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef signed char        s8;
typedef signed short       s16;
typedef signed int         s32;
typedef signed long long   s64;
typedef u64                uptr;   /* ponteiro como inteiro em ARM64 */

#define RAF_INLINE    __attribute__((always_inline)) static inline
#define RAF_NOINLINE  __attribute__((noinline))
#define RAF_ALIGN16   __attribute__((aligned(16)))
#define RAF_ALIGN64   __attribute__((aligned(64)))
#define RAF_PURE      __attribute__((pure))
#define RAF_NORETURN  __attribute__((noreturn))
#define RAF_SECTION(s) __attribute__((section(s)))
```

**Regra**: no path baremetal, nunca incluir `<stdint.h>`, `<stdlib.h>`, `<string.h>`.
O header `rmr_lowlevel.h` é a única dependência de tipos.

---

## 3. Arena Bump-Pointer: Alocação Sem Heap

Ref: `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt §3`, Arena API em `rmr_unified_kernel.h`

```c
typedef struct {
    u8*   base;     /* início da arena (BSS ou stack) */
    usize cap;      /* capacidade total em bytes */
    usize used;     /* cursor bump-pointer */
    u32   mark;     /* ponto de restore (scoped alloc) */
    u8    _pad[4];
} RafArena;

/* Aloca sz bytes alinhados a align (power-of-2) — sem branch */
RAF_INLINE void*
raf_arena_alloc(RafArena* a, usize sz, usize align)
{
    usize cur = (a->used + (align - 1)) & ~(align - 1);
    if (cur + sz > a->cap) return RAF_NULL;
    a->used = cur + sz;
    return (void*)(a->base + cur);
}

RAF_INLINE void raf_arena_mark(RafArena* a)    { a->mark = (u32)a->used; }
RAF_INLINE void raf_arena_restore(RafArena* a) { a->used = a->mark; }
RAF_INLINE void raf_arena_reset(RafArena* a)   { a->used = 0; }
```

**Comparativo:**

| Critério | malloc | Arena bump-pointer |
|----------|--------|--------------------|
| Latência | Variável (locks) | O(1) determinístico |
| Fragmentação | Sim | Nenhuma |
| Free granular | Sim | Não (reset/restore) |
| Cache-friendly | Não | Sim (alocações adjacentes) |
| Rastreável | Difícil | Sim (cursor único) |

**Na API canônica**: `RmR_UnifiedKernel_ArenaAlloc` / `RmR_UnifiedKernel_ArenaFree`
(veja `engine/rmr/include/rmr_unified_kernel.h`) expõem este padrão via JNI.
`ArenaFree` não desaloca — sinaliza disponibilidade para o próximo ciclo.

---

## 4. Aritmética Ponto-Fixo Q16.16

Ref: campos `*_q16` em `rmr_jni_route_output_t`, `engine/rmr/src/rmr_unified_kernel.c`

Em vez de `float`/`double`, o engine usa **Q16.16** para determinismo:

```c
typedef s32 q16_t;    /* Q16.16 com sinal: 16 bits inteiro + 16 fracionário */
typedef u32 uq16_t;   /* Q16.16 sem sinal */

#define Q16_ONE     ((q16_t)0x00010000)  /* 1.0 */
#define Q16_HALF    ((q16_t)0x00008000)  /* 0.5 */
#define Q16_SQRT3_2 ((q16_t)0x0000DDB3)  /* sqrt(3)/2 ≈ 0.8660 */
#define Q16_PHI     ((q16_t)0x00019E37)  /* φ ≈ 1.6180 */

/* Multiplicação Q16.16 — sem float, sem FPU */
RAF_INLINE q16_t q16_mul(q16_t a, q16_t b) {
    return (q16_t)(((s64)a * (s64)b) >> 16);
}
```

Campos `bitomega_coherence_q16`, `bitomega_entropy_q16`, `delta_theta_q16`
(em `rmr_jni_route_output_t`) transmitem valores fracionários de forma
determinística pela fronteira JNI sem risco de rounding IEEE 754.

---

## 5. Branchless: Padrões ARM64

Ref: `engine/rmr/interop/rmr_casm_arm64.S`, `engine/rmr/src/rmr_neon_simd.c`

### 5.1 Seleção condicional (→ instrução `csel`)

```c
/* Compilador GCC/Clang com -O2 emite 'csel' ARM64: */
RAF_INLINE u32 u32_max_bl(u32 a, u32 b) { return a > b ? a : b; }

/* Explícito via máscara de bits (portável a qualquer ISA): */
RAF_INLINE u32 u32_select_mask(u32 cond, u32 a, u32 b) {
    u32 mask = (u32)(-(s32)(cond != 0));  /* 0xFFFFFFFF ou 0x00000000 */
    return (a & mask) | (b & ~mask);
}
```

### 5.2 Clamp branchless

```c
RAF_INLINE u32 u32_clamp(u32 v, u32 lo, u32 hi) {
    v = v < lo ? lo : v;
    v = v > hi ? hi : v;
    return v;
}
```

### 5.3 Popcount via NEON (8× vs loop Kernighan)

```c
/* Ref: rmr_neon_simd.c — popcount bulk via vcntq_u8 */
#include <arm_neon.h>

RAF_INLINE u32 popcount_u64_neon(u64 val) {
    uint8x8_t  v   = vcreate_u8(val);
    uint8x8_t  cnt = vcnt_u8(v);
    uint64x1_t sum = vpaddl_u32(vpaddl_u16(vpaddl_u8(cnt)));
    return (u32)vget_lane_u64(sum, 0);
}
```

### 5.4 XOR-fold 128 bits/ciclo via NEON (~500 MB/s ARM Cortex-A55)

```c
RAF_INLINE u32 xor_fold_neon(const u8 *buf, u32 len) {
    uint8x16_t acc = vdupq_n_u8(0);
    u32 i = 0;
    for (; i + 16 <= len; i += 16)
        acc = veorq_u8(acc, vld1q_u8(buf + i));
    uint32x4_t w = vreinterpretq_u32_u8(acc);
    u32 r = vgetq_lane_u32(w, 0) ^ vgetq_lane_u32(w, 1)
          ^ vgetq_lane_u32(w, 2) ^ vgetq_lane_u32(w, 3);
    for (; i < len; i++) r ^= buf[i];
    return r;
}
```

### 5.5 CRC32C Hardware (requer `-march=armv8-a+crc`)

```c
/* FIXES_SUMMARY.md fix #51-53: poly Castagnoli 0x82F63B78 */
#ifdef __ARM_FEATURE_CRC32
#include <arm_acle.h>
RAF_INLINE u32 crc32c_hw_byte (u32 crc, u8  b) { return __crc32cb(crc, b); }
RAF_INLINE u32 crc32c_hw_word (u32 crc, u32 w) { return __crc32cw(crc, w); }
RAF_INLINE u32 crc32c_hw_dword(u32 crc, u64 d) { return __crc32cd(crc, d); }
#else
u32 crc32c_sw_fallback(u32 crc, const u8 *buf, u32 len);
#endif
```

---

## 6. Memcpy Bulk Sem LibC (64 bytes/ciclo)

```c
/* Ref: rmr_neon_simd.c — 4× vld1q_u8 (registradores q0-q3) */
RAF_INLINE void neon_bulk_copy(u8 RAF_ALIGN16 *dst,
                               const u8 RAF_ALIGN16 *src,
                               usize n)
{
    usize i = 0;
    for (; i + 64 <= n; i += 64) {
        uint8x16x4_t v = vld1q_u8_x4(src + i);
        vst1q_u8_x4(dst + i, v);
    }
    for (; i < n; i++) dst[i] = src[i];
}
```

---

## 7. JNI Boundary Sem Cópia

Ref: `app/src/main/cpp/vectra_core_accel.c`

```java
// Kotlin — ByteBuffer direto, fora do Java heap, sem GC
val buf = ByteBuffer.allocateDirect(4096)
buf.put(payload)
buf.flip()
VectraCore.nativeIngest(buf, buf.remaining())
```

```c
// C — GetDirectBufferAddress: zero-copy
JNIEXPORT jint JNICALL
Java_com_vectras_vm_vectra_VectraCore_nativeIngest(
    JNIEnv *env, jobject thiz, jobject directBuf, jint len)
{
    const uint8_t *data =
        (const uint8_t*)(*env)->GetDirectBufferAddress(env, directBuf);
    if (!data || len <= 0) return -1;
    uint32_t crc = 0;
    return rmr_jni_kernel_ingest(&g_kernel_state,
                                 data, (uint32_t)len, &crc);
}
```

---

## 8. Checklist de Revisão (Gate Sintático)

Execute via `python3 tools/compliance/check_lowlevel_constraints.py`:

- [ ] Sem `malloc/calloc/realloc/free` no hot path
- [ ] Sem `new`/`delete` no C++ do engine
- [ ] Sem `printf/fprintf` (latência de I/O)
- [ ] Sem VLA (`int arr[n]` onde `n` é variável de runtime)
- [ ] Sem `#include <stdio.h>` no path baremetal
- [ ] Sem `pthread_create` fora do init
- [ ] Estruturas alinhadas para SIMD (`aligned(16)` ou `aligned(64)`)
- [ ] Q16.16 em vez de `float` onde determinismo é crítico
- [ ] Arrays com limites estáticos definidos por macros
- [ ] Todo ponteiro verificado contra NULL antes de deref

```bash
# Verificação rápida de constraints
grep -rn "malloc\|calloc\|realloc" engine/rmr/src/ | grep -v "rmr_malloc\|RMR_JNI_BUILD"
gcc -Wvla -fsyntax-only engine/rmr/src/*.c
```

## 9. Observações

- Este gate é **sintático e conservador** — não substitui profiling, revisão de
  assembly, nem validação funcional.
- Para padrões SIMD críticos identificados por profiling, ver
  `engine/rmr/interop/rmr_casm_arm64.S`.
- Para o racional completo de compilador e guards de pré-compilação, ver
  [`docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`](VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md).

---

*Expandido de stub para guia completo em 2026-06-05*\
*Ref canônico: `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt` | `engine/rmr/src/rmr_neon_simd.c`*
