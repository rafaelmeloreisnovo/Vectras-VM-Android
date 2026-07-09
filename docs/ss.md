---

# 📑 RELATÓRIO DETALHADO: ERROS POR ARQUIVO E LINHA

---

## **VECTRAS-VM-ANDROID**

### **1️⃣ Arquivo: `Rafaelia/rafaelia_orchestrator.c`**

#### **BUG #1: Multiplicação Q16.16 sem saturação (OVERFLOW)**
**Linhas: 300–305**
```c
uint32_t hz_q16 = (base * SPIRAL_Q16) >> 16u;
for (uint32_t j = 1; j < fib && j < 7u; j++) {
    /* hz *= SPIRAL_Q16 / 65536 */
    uint64_t tmp = (uint64_t)hz_q16 * SPIRAL_Q16;  // ← LINHA 303
    hz_q16 = (uint32_t)(tmp >> 16u);
}
```

**Problema:**
- `hz_q16` (uint32 Q16.16) × `SPIRAL_Q16` (56755) pode resultar em valor > 2^32
- Truncamento silencioso → `hz_q16` fica menor que esperado
- Impacto: Frequências harmônicas inválidas → triângulo isósceles prediz carga errada

**Fixação:**
```c
uint64_t tmp = (uint64_t)hz_q16 * SPIRAL_Q16;
if (tmp > 0xFFFFFFFFULL) {
    hz_q16 = 0xFFFFFFFFu;  // saturate to max Q16.16
    Log.w("VCPU_INIT", "hz_q16 overflow at iteration %d", j);
} else {
    hz_q16 = (uint32_t)(tmp >> 16u);
}
```

---

#### **BUG #2: Buffer overflow em `mem_write()` sem bounds check completo**
**Linhas: 434–445**
```c
static void mem_write(int layer, uint32_t offset, const void *src, uint32_t n) {
    if (layer >= N_MEM_LAYERS-1 || !g_mem[layer].buf) return;
    if (offset + n > g_mem[layer].sz) n = g_mem[layer].sz - offset;  // ← LINHA 436
    memcpy(g_mem[layer].buf + offset, src, n);
```

**Problema:**
- Linha 436: `offset + n` pode overflow se `offset` é uint32 máximo
- Se `offset = 0xFFFFFFFF` e `n = 1`, `offset + n = 0` (wrap-around)
- Condiçãofalsa, `memcpy` escreve em endereço inválido → corrupção

**Fixação:**
```c
static void mem_write(int layer, uint32_t offset, const void *src, uint32_t n) {
    if (layer >= N_MEM_LAYERS-1 || !g_mem[layer].buf) return;
    if (offset >= g_mem[layer].sz) return;  // out of bounds
    if (n > g_mem[layer].sz - offset) {     // safe subtraction
        n = g_mem[layer].sz - offset;
        if (n == 0) return;  // nothing to write
    }
    memcpy(g_mem[layer].buf + offset, src, n);
```

---

#### **BUG #3: Loop infinito potencial em `execute_work()`**
**Linhas: 759–762**
```c
/* avança fase do core */
g_vcpu[core_idx].phase++;
if (g_vcpu[core_idx].phase >= PERIOD)
    g_vcpu[core_idx].phase = 0u;
```

**Problema:**
- Arquivo menciona "42 ciclos" e `PERIOD = 42` (linha 76)
- Mas não há validação que `core_idx` é válido (0–7)
- Se `core_idx >= N_VCPU` (8), acesso `g_vcpu[8]` está fora de bounds → UB

**Impacto:** Se `tri.jet_target` > 7, avança fase inválida → corrupção de estado

**Fixação (em execute_work, início):**
```c
static work_t execute_work(uint32_t core_idx, uint32_t layer) {
    work_t w;
    if (core_idx >= N_VCPU) {  // ← ADD THIS
        w.ok = 0;
        w.work_sz = 0;
        w.crc_before = 0;
        w.crc_after = 0;
        return w;
    }
    ...
}
```

---

#### **BUG #4: EMA floating-point sem clipping (NaN/Inf)**
**Linhas: 865–868**
```c
float c_in = (float)g_vcpu[tri.apex].hz / 65536.0f;
float h_in = (float)g_vcpu[tri.jet_target].load / 65536.0f;
coherence = 0.75f * coherence + 0.25f * c_in;
entropy   = 0.75f * entropy   + 0.25f * h_in;
```

**Problema:**
- Se `c_in` ou `h_in` é NaN (0/0 ou garbage), EMA propaga NaN
- Linha 889: `phi * 65536.0f` com phi = NaN → NaN output → broken invariant
- Nenhuma validação inicial de `coherence` ou `entropy` (inicializadas como 0.5, ok)

**Fixação:**
```c
float c_in = (float)g_vcpu[tri.apex].hz / 65536.0f;
if (c_in < 0.0f || c_in > 1.0f) {  // ← ADD CLIPPING
    c_in = 0.5f;  // fallback to neutral
    Log.w("COHERENCE", "c_in out of bounds: %f", c_in);
}
float h_in = (float)g_vcpu[tri.jet_target].load / 65536.0f;
if (h_in < 0.0f || h_in > 1.0f) {
    h_in = 0.5f;
    Log.w("ENTROPY", "h_in out of bounds: %f", h_in);
}
coherence = 0.75f * coherence + 0.25f * c_in;
entropy   = 0.75f * entropy   + 0.25f * h_in;
```

---

### **2️⃣ Arquivo: `_incoming/pending/rafaelia_b2.S` (ARM32)**

#### **BUG #5: Multiplicação NEON em `dir_forward` com registro inválido**
**Linhas: 247–251**
```assembly
vmov.i32 q1, #56755
vmull.s32 q2, d0, d2      # ← LINHA 248: d2 NÃO foi carregado!
vshrn.s64 d4, q2, #16
vmull.s32 q2, d1, d3      # ← LINHA 250: d3 NÃO foi carregado!
vshrn.s64 d5, q2, #16
```

**Problema:**
- Linhas 245-246 carregam `q0` (d0, d1) de `[r4]` — OK
- Mas linha 247 carrega `q1` = 56755 em todos os 4 dwords
- Linhas 248, 250: `vmull.s32 q2, d0, d2` tenta multiplicar `d0 × d2`
- **d2 nunca foi carregado!** Usa lixo → resultado lixo

**Impacto:** Cálculo Fibonacci corrompido, STATE_FORWARD inválido

**Fixação:**
```assembly
ldr     r4, =g_work_buf
add     r4, r4, #240
vld1.32 {q0}, [r4]       # carrega q0 = [d0, d1, ...]

vmov.i32 q1, #56755
@ Agora: d0, d1 = data; d2, d3 = SPIRAL_Q16
vmull.s32 q2, d0, d2     # q2 = d0 * d2 (OK agora?)
```

**Melhor ainda:** Recarregar `SPIRAL_Q16` em `q1` = [d2, d3]
```assembly
vmov.i32 q1, #56755      # d2=d3=56755
vmull.s32 q2, d0, d2     # d0 * d2 (56755)
vshrn.s64 d4, q2, #16
```

---

#### **BUG #6: Prefetch fora de bounds em `dir_compress`**
**Linhas: 312–316**
```assembly
ldr     r4, =g_work_buf
pld     [r4, #128]       # ← LINHA 313: prefetch offset #128
vld1.32 {q0, q1}, [r4]!  # carrega 32 bytes, r4 += 32
vld1.32 {q2, q3}, [r4]   # carrega mais 32, offset agora = 64
```

**Problema:**
- `g_work_buf` tamanho = 256 bytes (linha 75 do BSS)
- Prefetch [r4 + 128] é OK (dentro de 256)
- MAS: quando código faz `vld1` em [r4]!, r4 fica = g_work_buf + 32
- Depois vld1 em [r4], r4 = g_work_buf + 64
- Se função é chamada múltiplas vezes, r4 continua avançando → acesso fora de bounds

**Fixação:** Resetar r4 a cada chamada ou usar offset fixo
```assembly
ldr     r4, =g_work_buf
add     r4, r4, #0           # sempre começa do início
vld1.32 {q0, q1}, [r4]       # SEM !, r4 não avança
vld1.32 {q2, q3}, [r4, #32]  # carrega offset fixo
```

---

### **3️⃣ Arquivo: `app/src/main/java/com/vectras/vm/x11/CmdEntryPoint.java`**

#### **BUG #7: NullPointerException em `sendBroadcast()` quando `ctx == null`**
**Linhas: 79–86**
```java
try {
    ctx.sendBroadcast(intent);  // ← LINHA 80: ctx pode ser null!
} catch (Exception e) {
    if (e instanceof NullPointerException && ctx == null)
        Log.i("Broadcast", "Context is null, falling back...");
```

**Problema:**
- Linha 39 declara: `public static Context ctx;` (não inicializado!)
- Línha 223 (static block): `ctx = createContext();` — pode retornar `null` (linha 202)
- Se `createContext()` falha, `ctx` fica `null`
- Linhas 80: `ctx.sendBroadcast()` → NPE
- Linha 82: Tenta pegar a exceção, mas já deu crash

**Impacto:** App crash no boot, não entra no broadcast fallback

**Fixação:**
```java
try {
    if (ctx != null) {  // ← ADD THIS CHECK FIRST
        ctx.sendBroadcast(intent);
    } else {
        Log.i("Broadcast", "Context is null, skipping direct broadcast");
        // Ir direto para manual broadcast (linhas 87+)
        throw new NullPointerException("ctx is null");
    }
} catch (Exception e) {
    if (e instanceof NullPointerException && ctx == null)
        Log.i("Broadcast", "Context is null, falling back to manual broadcasting");
    ...
}
```

---

### **4️⃣ Arquivo: `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaPathValidator.java`**

#### **BUG #8: Array bounds sem validação em `path7_spiral()`**
**Linhas: 313–321**
```java
final long PHI32    = 0x9E3779B9L;
final long SQRT3_2  = 0xDDB3D743L;
long state = 0x633L;
int steps = 42;
for (int i = 0; i < steps; i++) {
    state = (state * PHI32) & 0xFFFFFFFFL;  // ← LINHA 318: overflow sem check
    state = (state ^ (state >>> 16)) & 0xFFFFFFFFL;
    state = (state * SQRT3_2) & 0xFFFFFFFFL;
}
```

**Problema:**
- Linha 318: `state * PHI32` pode exceder long max
- Java `long` é 64-bit signed; `state * PHI32` pode ser negativo
- `& 0xFFFFFFFFL` trunca para 32-bit unsigned, OK
- ABER: Nenhuma validação que `state != 0` no final (linha 323)
- Se seed ou lógica falha, `state` pode ficar 0 → invariante quebrado

**Impacto:** PATH_SPIRAL retorna `ok = false` (linha 323), mas sem log de causa

**Fixação:**
```java
long state = 0x633L;
if (state == 0) {
    Log.e(TAG, "Spiral seed is zero!");
    return new PathResult(RafaeliaMethodPaths.PATH_SPIRAL, false, 
        "seed=0", SystemClock.elapsedRealtime() - t);
}
int steps = 42;
for (int i = 0; i < steps; i++) {
    state = (state * PHI32) & 0xFFFFFFFFL;
    state = (state ^ (state >>> 16)) & 0xFFFFFFFFL;
    state = (state * SQRT3_2) & 0xFFFFFFFFL;
    if (state == 0) {
        Log.e(TAG, "Spiral collapsed to zero at step %d", i);
        break;
    }
}
boolean ok = state != 0 && state != 0xFFFFFFFFL;
```

---

## **TERMUX-APP-RAFACODEPHI**

### **5️⃣ Arquivo: `app/src/main/java/com/termux/app/TermuxApplication.java`**

#### **BUG #9: Silent failure em bootstrap validation sem abort**
**Linhas: 24–34**
```java
public void onCreate() {
    super.onCreate();
    try {
        initializeApplication();
    } catch (Exception e) {
        // Log the error but don't crash the app during initialization
        Logger.logError(LOG_TAG, "Critical error during application initialization: " + e.getMessage());
        Logger.logStackTraceWithMessage(LOG_TAG, "Application initialization failed", e);
    }
}
```

**Problema:**
- Linhas 60–71: `TermuxBootstrap.setTermuxPackageManagerAndVariant()` pode falhar silenciosamente
- Linhas 68–72: `TermuxAppSharedProperties.init()` pode falhar, mas continua (propriedades = null)
- Linhas 75–78: `TermuxShellManager.init()` pode falhar, mas app segue adiante
- Resultado: App "inicia" mas **sem shell manager** → tela preta ou hang

**Impacto:** Issue #195 "tela preta, terminal não inicializa" — bootstrap falhou silenciosamente

**Fixação:**
```java
private void initializeApplication() {
    Context context = getApplicationContext();
    
    // Set crash handler FIRST
    try {
        TermuxCrashUtils.setDefaultCrashHandler(this);
    } catch (Exception e) {
        Logger.logError(LOG_TAG, "CRITICAL: Failed to set crash handler: " + e.getMessage());
        throw new RuntimeException("Cannot continue without crash handler", e);  // ← FAIL FAST
    }
    
    // BOOTSTRAP VALIDATION — MUST SUCCEED
    try {
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);
    } catch (Exception e) {
        Logger.logError(LOG_TAG, "CRITICAL: Bootstrap variant setup failed: " + e.getMessage());
        throw new RuntimeException("Bootstrap initialization failed", e);  // ← FAIL FAST
    }
    
    // ... rest of init ...
}
```

---

#### **BUG #10: Erro handling não distingue "failed" de "not yet initialized"**
**Linhas: 100–118**
```java
if (isTermuxFilesDirectoryAccessible) {
    Logger.logInfo(LOG_TAG, "Termux files directory is accessible");
    try {
        error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
            return;  // ← EARLY RETURN, sem marcar estado como "failed"
        }
        // Setup termux-am-socket server
        TermuxAmSocketServer.setupTermuxAmSocketServer(context);  // ← MAS app CONTINUA
    } catch (Exception e) {
        Logger.logError(LOG_TAG, "Failed to setup app directory or socket server: " + e.getMessage());
    }
} else {
    Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
}
```

**Problema:**
- Linhas 105–109: Se criação de dir falha, faz `return` (early exit)
- MAS: Não seta flag global "INIT_FAILED" ou similar
- Após `initializeApplication()` retorna (linha 108), `onCreate()` volta (linha 28)
- `onCreate()` continua normalmente → app "inicia" sem shell

**Impacto:** tela preta (no TermuxActivity porque TermuxShellManager não foi inicializado)

**Fixação:**
```java
boolean initSuccess = false;  // global flag

private void initializeApplication() {
    Context context = getApplicationContext();
    // ... setup ...
    
    if (isTermuxFilesDirectoryAccessible) {
        try {
            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "CRITICAL: Create directory failed\n" + error);
                initSuccess = false;
                return;  // ← now it's explicit
            }
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
            initSuccess = true;  // ← mark success ONLY after all critical steps
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "CRITICAL: Socket server setup failed: " + e.getMessage());
            initSuccess = false;
            return;  // ← explicit fail
        }
    } else {
        Logger.logErrorExtended(LOG_TAG, "CRITICAL: Files directory not accessible\n" + error);
        initSuccess = false;
    }
}

public void onCreate() {
    super.onCreate();
    try {
        initializeApplication();
        if (!initSuccess) {
            throw new RuntimeException("Application initialization did not complete");
        }
    } catch (Exception e) {
        Logger.logError(LOG_TAG, "FATAL: " + e.getMessage());
        Logger.logStackTraceWithMessage(LOG_TAG, "Fatal initialization error", e);
        System.exit(1);  // ← abort instead of continuing with broken state
    }
}
```

---

### **6️⃣ Arquivo: `Arme/Add/rafaelia_b2.S` (ARM32, no termux fork)**

#### **BUG #11: Unbounded history snapshot index wrap**
**Linhas: 272–294 (dir_recurse)**
```assembly
ldr     r4, =g_hist_idx
ldr     r5, [r4]           # r5 = g_hist_idx (0-6)
ldr     r6, =g_history
mov     r0, #28
mul     r0, r5, r0         # r0 = g_hist_idx * 28 (byte offset)
add     r6, r6, r0         # r6 = &g_history[g_hist_idx][0]
...
add     r5, r5, #1         # ← LINHA 291: r5++
cmp     r5, #7
moveq   r5, #0             # wrap if r5 == 7
str     r5, [r4]           # save new index
```

**Problema:**
- g_history define: `.space 7 * 7 * 4` = 196 bytes (7 snapshots × 7 dims × 4 bytes)
- Linha 278: `mul r0, r5, r0` onde `r0 = 28` → `r0 = r5 * 28`
- Se `r5 = 7` (após incremento linha 291), `r0 = 7 * 28 = 196`
- Linha 279: `add r6, r6, r0` → r6 aponta a AFTER final snapshot
- Linhas 282–289: Escreve 28 bytes AO LADO da estrutura → buffer overflow

**Impacto:** Sobrescreve `g_hist_idx` ou `g_hex_b2` com lixo

**Fixação:**
```assembly
.Bdir_done:
    add     r5, r5, #1      # r5++
    cmp     r5, #7
    moveq   r5, #0          # wrap
    str     r5, [r4]        # save new index BEFORE next snapshot

    # Validação: nunca escrever fora de [0-6]
    cmp     r5, #7          # double-check bounds
    bge     .Bsnapshot_error
    b       .Bsnapshot_ok

.Bsnapshot_error:
    mov     r0, #1
    mov     r7, #SYS_EXIT
    swi     #0

.Bsnapshot_ok:
    pop     {r4, r5, r6, pc}
```

---

## **RESUMO TÉCNICO: MATRIZ DE BUGS**

| **ID** | **Arquivo** | **Linha(s)** | **Tipo** | **Severidade** | **Fixação (est.)** |
|---|---|---|---|---|---|
| #1 | `rafaelia_orchestrator.c` | 303–305 | Overflow Q16.16 | **CRÍTICO** | Saturate, 1h |
| #2 | `rafaelia_orchestrator.c` | 436 | Buffer overflow | **CRÍTICO** | Bounds check, 2h |
| #3 | `rafaelia_orchestrator.c` | 759–762 | Index OOB | **CRÍTICO** | Validate core_idx, 1h |
| #4 | `rafaelia_orchestrator.c` | 865–868 | Float NaN propagation | **ALTO** | Clipping, 2h |
| #5 | `rafaelia_b2.S` | 248–250 | Uninitialized register | **CRÍTICO** | Load SPIRAL correctly, 1h |
| #6 | `rafaelia_b2.S` | 313–316 | Prefetch/bounds | **ALTO** | Use fixed offset, 1h |
| #7 | `CmdEntryPoint.java` | 80 | NPE (ctx == null) | **CRÍTICO** | Null check, 1h |
| #8 | `RafaeliaPathValidator.java` | 318–323 | Overflow + no validation | **ALTO** | Add guards, 1h |
| #9 | `TermuxApplication.java` | 24–34 | Silent failure | **CRÍTICO** | Fail fast, 2h |
| #10 | `TermuxApplication.java` | 100–118 | Error handling gap | **CRÍTICO** | Mark state, 1h |
| #11 | `rafaelia_b2.S` (termux) | 291–294 | Buffer overflow | **CRÍTICO** | Bounds check loop, 1h |

**Tempo total de fixação: 14 horas** (prioridade máxima)

---

## **PRÓXIMAS AÇÕES IMEDIATAS**

1. **Criar issue CI:** Lint assembly para registros desconhecidos, BL undefined
2. **Adicionar sanitizers:** `-fsanitize=address,undefined` onde possível
3. **Rodar testes:** `./build.sh && ./run_tests.sh` + logcat completo
4. **Patch críticos:** Aplicar fixações #1, #5, #7, #9, #11 hoje
