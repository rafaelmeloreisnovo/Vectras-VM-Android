<!-- DOC_ORG_SCAN: 2026-06-05 | source-scan: active | status: new -->

# Vectra: Técnicas de Compilador e Pré-compilador Além da Literatura Acadêmica

**Data**: 2026-06-05\
**Escopo**: Engine RMR, BITRAF, Baremetal Path, JNI Bridge\
**Referências canônicas**: `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt`, `FIXES_SUMMARY.md`,
`engine/rmr/`, `COMPILATION_FIXES.md`, `VERSION_STABILITY.md`

---

## 1. Contexto: O Que a Literatura Acadêmica Não Cobre

A literatura acadêmica padrão (Tanenbaum, Patterson & Hennessy, ISO C11, ARM
Architecture Reference Manual) documenta o comportamento **nominal** de hardware
e compiladores. O Vectras Engine opera em uma camada onde:

1. O **compilador é parte do hardware** — flags de compilação determinam
   quais instruções ISA existem no binário (não apenas otimização de desempenho)
2. **Guards de pré-compilador** definem dois universos de execução completamente
   distintos dentro do mesmo source tree (baremetal vs JNI)
3. A unidade fundamental de execução é o **ciclo de estado** (ψ→χ→ρ→Δ→Σ→Ω),
   não o clock de CPU
4. **Bits possuem geometria** — não são apenas dados binários abstratos

---

## 2. HOTFIX Documentado: Dualidade `-ffreestanding` vs `-DRMR_JNI_BUILD=1`

Ref: `FIXES_SUMMARY.md` fix #3, #5, #6 | `COMPILATION_FIXES.md`

### O problema

A flag `-ffreestanding` instrui o compilador que:
- Não há `main()` como entrypoint padrão
- `malloc/free/memcpy/printf` não existem
- A ABI de startup (`.init_array`, `crt0`) não está disponível

**O conflito**: o Android NDK usa a Bionic libc, que é um ambiente **hosted**.
`pthread_create`, `malloc` (usado internamente pelo JVM), e outras funções libc
estão presentes. Misturar `-ffreestanding` com código JNI que chama
`dlopen`/`JNI_OnLoad` causava link errors irresolvíveis.

### A solução: dois mundos, dois guards

```c
#if defined(RMR_JNI_BUILD)       /* Android/JNI: hosted, Bionic libc OK */
  #define rmr_malloc(sz)    malloc(sz)
  #define rmr_free(p)       free(p)
  #define rmr_memcpy(d,s,n) memcpy(d,s,n)
#elif defined(RMR_BAREMETAL)     /* Linux baremetal: sem libc */
  #define rmr_malloc(sz)    raf_arena_alloc(&g_arena, sz, 16)
  #define rmr_free(p)       ((void)(p))          /* no-op: arena sem free */
  #define rmr_memcpy(d,s,n) neon_bulk_copy(d,s,n)
#else
  #error "Define RMR_JNI_BUILD (Android) ou RMR_BAREMETAL (baremetal)"
#endif
```

**Flags por trilha de build:**

| Trilha | Flags adicionadas | Flags removidas |
|--------|------------------|-----------------|
| Android JNI arm64 | `-DRMR_JNI_BUILD=1 -march=armv8-a+crc -O3` | `-ffreestanding -fno-builtin -nostdlib` |
| Baremetal ARM64 | `-DRMR_BAREMETAL=1 -ffreestanding -nostdlib -DRAF_NO_HEAP=1` | nenhuma |
| Host Linux x86_64 | `-DRMR_JNI_BUILD=1 -msse4.2 -mpclmul -O3` | `-march=armv8-a` |

---

## 3. Flags de Compilação com Efeitos Não-Óbvios

### 3.1 `-march=armv8-a+crc` — Instrução, Não Otimização

Esta flag não apenas otimiza — ela **habilita instruções ISA que não existem
sem ela**. Com ela, o compilador pode emitir:
- `crc32b/h/w/x` — CRC32 (ISO-HDLC)
- `crc32cb/ch/cw/cd` — CRC32C (Castagnoli) — usado no Vectras

Sem esta flag, `__crc32cd()` de `<arm_acle.h>` gera erro de compilação.
A literatura trata CRC como algoritmo de software — aqui é uma instrução ISA.

**Speedup medido** (FIXES_SUMMARY.md #20-25):
- CRC32C SW: ~30 MB/s
- CRC32C HW (`crc32cd`): ~600 MB/s **(20× speedup)**

### 3.2 `-march=armv8-a+crypto` — AES e SHA em Hardware

Habilita instruções equivalentes a AES-NI:
- `AESE`/`AESD`/`AESMC` — round de AES em 1 ciclo
- `SHA1C`/`SHA256H` — SHA1/SHA256 em hardware

Para o caminho de verificação de integridade no BITRAF, isso permite
verificação criptográfica a velocidade de memória.

### 3.3 `-fomit-frame-pointer` — Trade-off de Registrador

Sem frame pointer (`x29`/`fp` em ARM64 liberado), o compilador ganha um
registrador extra para uso em loops SIMD críticos. O stack unwinding para
debug ainda funciona via `-fstack-protector-strong` (canary no stack frame).

A literatura acadêmica apresenta frame pointer como necessário para debugging.
No engine, o trade-off é deliberado e justificado por profiling.

### 3.4 `-funroll-loops` + Limites Estáticos

Loops com limites visíveis ao compilador em compile-time são desdobrados
(**loop unrolling**). O engine usa macros de limite estático:

```c
#define RAF_MAX_BLOCKS  512
#define RAF_BLOCK_SZ     64

/* Loop totalmente unrolled com N fixo: */
for (u32 i = 0; i < 8; i++)   /* 8 iterações → 8 cópias inline */
    acc ^= buf[i];
```

A chave é que o limite seja **uma constante visível em compile-time**, não
um parâmetro de função nem variável global não-const.

### 3.5 `-fno-plt` no Baremetal — Elimina Indireção de Chamada

PLT (Procedure Linkage Table) adiciona um nível de indireção por chamada de
função externa. `-fno-plt` força resolução estática em link-time: sem PLT,
sem overhead de pointer chase por chamada.

A literatura apresenta PLT e PIC como padrão. No baremetal Vectras: ambos
são **overhead eliminável** para código de sistema sem dynamic linking.

---

## 4. Hierarquia de Guards de Pré-compilador

O sistema implementa **detecção de capacidades em compile-time** em cascata:

```c
/* Do mais restrito ao mais permissivo: */

#if defined(RMR_BAREMETAL) && defined(RAF_NO_HEAP)
  /* Modo mais restrito: sem libc, sem heap, sem OS */
  /* Usado em: BOOTSTRAP_LOWLEVEL_RAFAELIA.txt */

#elif defined(RMR_BAREMETAL)
  /* Baremetal com arena mas com possível libc parcial */

#elif defined(RMR_JNI_BUILD)
  /* Android JNI: Bionic libc disponível, hot path ainda sem malloc */

#else
  /* Host Linux: libc completa, para selftests e CI host */
#endif
```

### 4.1 `VECTRA_HAS_CASM_MARKER`

Guard especial que sinaliza presença de **CASM markers** — pontos de
inserção de assembly com garantias de register allocation pelo compilador:

```c
/* Definido via CMake quando rmr_casm_arm64.S está no build */
#if VECTRA_HAS_CASM_MARKER
  #define CASM_BARRIER() __asm__ __volatile__("" ::: "memory")
#else
  #define CASM_BARRIER() ((void)0)
#endif
```

O CMake root loga explicitamente se este marker está habilitado ou não
(COMPILATION_FIXES.md: "Root CMake now computes and exports
`VECTRA_HAS_CASM_MARKER` consistently and logs enabled/disabled state").

---

## 5. Filosofia: Ciclos de Estado, Não Clocks de CPU

A literatura acadêmica mede performance em **clock cycles** (frequência de CPU).
O Vectras Engine mede em **ciclos de estado**.

### Diferença fundamental

| Clock cycle | State cycle (Vectras) |
|-------------|----------------------|
| Medido em Hz de hardware | Medido em Hz de processamento de eventos |
| Determinado pelo hardware | Determinado pela política de estados |
| Idêntico para código correto ou incorreto | Hit/Miss são estados distintos |
| Ausência = silêncio | MISS = estado explicitamente registrado |
| Sem semântica de informação | Cada ciclo = decisão auditável |

### O ciclo ψ→χ→ρ→Δ→Σ→Ω

```
ψ (psi)   — INIT/INGEST : ingere dado/evento no sistema
χ (chi)   — OBSERVE     : detecta capacidades, registra estado HW
ρ (rho)   — DENOISE     : trata ruído como informação
Δ (delta) — TRANSMUTE   : processa e transforma o estado
Σ (sigma) — MEMORY      : consolida em ledger append-only
Ω (omega) — COMPLETE    : finaliza ciclo, prepara próxima iteração
```

Um ciclo pode consumir 1 ou 10.000 instruções de CPU — o que importa é que
ao final de cada ciclo o sistema conhece seu estado exato e o resultado
está no log. A **transição de estado** é o átomo, não a instrução.

### Hit e Miss como estados de primeira classe

```c
typedef enum {
    CYCLE_HIT  = 1,  /* ciclo recebeu evento — peso normal */
    CYCLE_MISS = 0   /* ciclo sem evento — peso reduzido mas não zero */
} cycle_result_t;

/* Policy gate determinístico (VECTRA_CORE.md §2.1):
 * - 2 MISS consecutivos → reduz weighting
 * - 2 HIT  consecutivos → restaura weighting
 * Esta política é DETERMINÍSTICA, não probabilística */
```

A ausência de evento tem **valor de entropia não-zero** (informa que o
sistema está ocioso). A literatura trata silêncio como ausência de dado.
O Vectras trata MISS como dado com `event_weight = 0` mas `entropy > 0`.

---

## 6. Bits São Diferentes: BITRAF e Geometria Binária

### Por que bits no Vectras não são bits da literatura

Shannon, Hamming, Reed-Solomon tratam bits como **entidades abstratas**
em canais de comunicação. O BITRAF trata bits como **entidades com posição
em espaço 2D (grid 4×4)**:

```
Grid BITRAF 4×4 — 16 bits de dados + 8 bits de paridade:

  col0 col1 col2 col3  │ row_parity
  ─────────────────────┤
  b00  b01  b02  b03   │ P_r0
  b10  b11  b12  b13   │ P_r1
  b20  b21  b22  b23   │ P_r2
  b30  b31  b32  b33   │ P_r3
  ─────────────────────
  Pc0  Pc1  Pc2  Pc3   ← col_parity

Índice geométrico: idx = (row << 2) | col
```

**Diferença do Hamming code padrão:**
- Hamming: paridade linear 1D — detecta erro, aponta posição em índice linear
- BITRAF: paridade 2D (row + col) — localiza erro em coordenada `(row, col)`

Isso permite **correção de erros single-bit** com overhead menor que
Reed-Solomon porque a geometria do grid já codifica a localização do erro.

### Syndrome como diagnóstico geométrico

```c
typedef struct {
    u8 row;      /* linha com erro (0–3) ou 0xFF se nenhuma */
    u8 col;      /* coluna com erro (0–3) ou 0xFF se nenhuma */
    u8 both_ok;  /* 1 se sem erro detectado */
    u8 _pad;
} bitraf_syndrome_t;
```

### Rho (ρ): Ruído como Informação de Entropia

A convenção acadêmica é descartar dados "corrompidos". O BITRAF/Vectras
trata dados fora do padrão como **informação de entropia**:

```
ρ = syndrome_weight + event_weight
  = popcount(parity_diff) + event_importance
```

Onde `event_importance`: RADIO=10, NETWORK=5, USER_INPUT=3, TIMER=1.
Um dado "corrompido" de RADIO tem alta ρ — **é mais informativo** que
um dado correto de timer. A corrupção em si é um sinal do ambiente.

---

## 7. Endereçamento Toroidal 7D: Além do Hash Consistente

A literatura de sistemas distribuídos usa consistent hashing para roteamento.
O Vectras usa **endereçamento toroidal 7-dimensional**:

```c
typedef struct {
    uint32_t u;      /* eixo U */
    uint32_t v;      /* eixo V */
    uint32_t psi;    /* fase ψ */
    uint32_t chi;    /* fase χ */
    uint32_t rho;    /* entropia ρ */
    uint32_t delta;  /* transformação Δ */
    uint32_t sigma;  /* memória Σ */
} RmR_ToroidalAddr7D;
```

**Invariante de estabilidade**: a mesma sequência de entradas sempre produz
a mesma `RmR_ToroidalAddr7D` em qualquer reinicialização — roteamento
determinístico sem tabela de hash, sem colisões, sem caso especial de borda.

Em um espaço toroidal, `(max+1)` wrap-around para `0` — é aritmética modular
pura, implementável branchless:

```c
u = (u + delta_u) % N_RING_U;  /* sem if, sem branch */
v = (v + delta_v) % N_RING_V;
```

---

## 8. HOTFIX: Alinhamento de Magic Constants Através da Fronteira JNI

Ref: `FIXES_SUMMARY.md` fix #1a, #1b, #1c

Este padrão não aparece na literatura de JNI, mas é crítico:

```c
/* ANTES do fix — três valores diferentes: */
/* rmr_unified_kernel.h      */ #define RMR_UK_NATIVE_OK_MAGIC  0x52414641u  /* "RAFA" */
/* rmr_unified_jni_base.h    */ #define RMR_UK_NATIVE_OK_MAGIC  0x524D5255u  /* "RMRU" */
/* NativeFastPath.java       */ static final int NATIVE_OK_MAGIC = 0x56414343; /* "VACC" */

/* DEPOIS do fix — valor único "VACC" em todos: */
/* rmr_unified_kernel.h      */ #define RMR_UK_NATIVE_OK_MAGIC  0x56414343u  /* "VACC" */
/* rmr_unified_jni_base.h    */ #define RMR_UK_NATIVE_OK_MAGIC  0x56414343u  /* "VACC" */
/* bug/core/rmr_unified_kernel.h */ #define RMR_UK_NATIVE_OK_MAGIC 0x56414343u /* "VACC" */
/* NativeFastPath.java       */ static final int NATIVE_OK_MAGIC = 0x56414343;  /* "VACC" */
```

**Efeito do bug**: `VmFlowNativeBridge.AVAILABLE = false` sempre, mesmo
com a lib carregada corretamente. O native layer estava funcional mas a
verificação de magic falhava silenciosamente.

**Padrão canônico**: magic constant definida em UM lugar, incluída pelos
outros. Nunca redefinida. Verificada em CI via grep.

---

## 9. Verificação de Compilador em CI

Ref: `COMPILATION_FIXES.md`, `tools/ci/verify_cmake_config.sh`

```bash
# Verificar guards corretos
grep -rn "RMR_JNI_BUILD\|RMR_BAREMETAL\|RAF_NO_HEAP" \
  engine/rmr/src/ engine/rmr/include/

# Verificar magic constant alinhada
grep -rn "NATIVE_OK_MAGIC\|RMR_UK_NATIVE_OK_MAGIC" \
  engine/ app/src/main/java/ app/src/main/cpp/

# Confirmar que -ffreestanding foi removido do Android build (HOTFIX #3)
grep -rn "ffreestanding" app/src/main/cpp/CMakeLists.txt
# → deve retornar vazio

# Confirmar flags ABI ARM64 corretos
grep -A5 "arm64-v8a" app/src/main/cpp/CMakeLists.txt
# → deve mostrar -march=armv8-a+crc -DRMR_JNI_BUILD=1
```

---

## 10. Cache TCG: Miss Como Próxima Instrução e Mutação Seletiva de Bits

Ref: `engine/rmr/src/rmr_tcg_cache.c`, `engine/rmr/include/rmr_tcg_cache.h`,
`demo_cli/src/rmr_tcg_cache_selftest.c`

### Miss não é falha — é a próxima instrução

Na literatura, cache miss é penalidade a minimizar. No cache TCG do engine,
o miss é o **operando da próxima decisão**: ele instrui o pipeline a compilar
e inserir, e é contado como estado explícito (`total_misses`), nunca como
silêncio. O mesmo vale para o bloco em colapso: `Lookup` responde MISS por
política — o estado de colapso é registrado, não escondido (HOTFIX documentado
em `rmr_tcg_cache.c`: o bit `RMR_TCG_BLOCK_COLLAPSING` é preservado através
do reset de flags justamente para que essa política possa disparar).

### O conjunto não é trocado — os bits divergentes são acertados

Quando um bloco é reinserido (recompilação do mesmo `guest_crc32c`), o
caminho convencional substituiria o conjunto inteiro de 8/16 bits por byte.
O engine calcula o **delta XOR** entre o byte residente e o candidato e toca
apenas os bits onde o delta é 1 (`rmr_isorf_write_byte_delta`):

```c
u8 delta = (u8)(current ^ value);   /* coincidentia oppositorum: só o que difere */
for (u32 i = 0u; i < 8u; ++i) {
  if (((delta >> i) & 1u) == 0u) continue;   /* bit igual: intocado */
  RmR_ISOraf_SetBit(st, bit + i, (u8)((value >> i) & 1u));
}
```

Três consequências medíveis (cobertas por `rmr_tcg_cache_selftest`):

1. **Reinserção idêntica custa zero bits** — `delta_bits_flipped` não cresce.
2. **Mutação de 1 bit custa exatamente 1 bit gravado** — coerência entre
   causa e efeito na escrita.
3. **O físico esparso do ISOraf é preservado** — bit 0 sobre página ausente
   nunca aloca página; a escrita por delta só materializa o que é informação.

A métrica `RmR_TCGCache_DeltaPreservedPct` expõe a fração de bits que a
recompilação **não** precisou tocar — é a medida ρ da recompilação: quanto
do bloco anterior permanece verdade no bloco novo.

### Orientação da leitura como ponto de vista

O mesmo store admite duas leituras: byte-a-byte LSB-first
(`rmr_isorf_get_byte`, orientação de consumo do host block) e bit-endereçada
direta (`RmR_ISOraf_GetBit`, orientação do grid esparso). Nenhuma é "a
correta" — são pontos de vista sobre os mesmos bits, e o selftest valida o
conteúdo pelas duas orientações. É o mesmo princípio do BITRAF §6: a posição
do bit carrega significado, e a orientação da leitura é parte do contrato,
não um detalhe de implementação.

---

## 11. Tabela Comparativa: Literatura vs Vectras Engine

| Conceito | Literatura Acadêmica | Vectras/RAFAELIA |
|----------|---------------------|------------------|
| Unidade de execução | Instrução / Clock cycle | Ciclo de estado (ψ→Ω) |
| Erro em dados | Descartado ou corrigido | Entropia (ρ) — mantido como informação |
| Alocação de memória | malloc / GC | Arena bump-pointer estática |
| Paridade | 1D (Hamming, Reed-Solomon) | 2D geométrica (grid 4×4) |
| Roteamento | Hash table / Consistent hash | Toroidal 7D determinístico |
| Números fracionários | IEEE 754 float | Q16.16 fixed-point |
| `-ffreestanding` | Flag global de compilação | Guard de pré-compilador (não flag Android) |
| Magic constants JNI | Não especificado | Protocolo de alinhamento explícito (3 pontos) |
| Ausência de evento | Silêncio / dado ausente | MISS = estado explícito de ciclo |
| CRC | Algoritmo de software | Instrução ISA (`crc32cd` ARM64) |
| Memcpy | Função libc | NEON bulk (64B/ciclo, zero-copy JNI) |
| Cache miss | Penalidade a minimizar | Próxima instrução — estado contado |
| Atualização de cache | Substituição do bloco/linha inteira | Delta XOR bit-a-bit (bits preservados como métrica ρ) |

---

*Documento criado em 2026-06-05*\
*Ref: `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt` · `FIXES_SUMMARY.md` · `VECTRA_CORE.md`*\
*`engine/rmr/include/rmr_unified_kernel.h` · `COMPILATION_FIXES.md`*
