<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->
<!-- HOTFIX: 2026-06-05 — preenchimento de placeholders, secão de ciclos e compilador -->

# Vectra Core MVP

## Atualização

- **Data da revisão**: 2026-04-07 (HOTFIX 2026-06-05)
- **Escopo auditado**: `engine/rmr`, `app/src/main/cpp`, `demo_cli/src`
- **Tipo de revisão**: documental estática

> Nota de manutenção: atualizar este bloco quando houver mudança de API/caminhos citados.

## Overview

Vectra Core is a minimal "information-theoretic" runtime framework for Android that implements deterministic event processing with built-in integrity verification. It's designed to treat all data (including noise) as information and provide append-only logging for forensic analysis.

## Atualização alinhada ao código-fonte (2026-04-07)

### Metadados da revisão
- Data da revisão: `2026-04-07`
- Escopo auditado nesta atualização: `engine/rmr`, `app/src/main/cpp`, `app/src/main/java/com/vectras/vm/vectra`, `demo_cli/src`
- Tipo de revisão: documental estática (sem alteração de comportamento de runtime)

### Contrato do kernel nativo unificado (C)
- Header canônico de API: `engine/rmr/include/rmr_unified_kernel.h`
- Implementação canônica: `engine/rmr/src/rmr_unified_kernel.c`
- Ciclo de vida determinístico legado (pool estático, sem heap):
  - `rmr_legacy_kernel_init`
  - `rmr_legacy_kernel_ingest`
  - `rmr_legacy_kernel_process`
  - `rmr_legacy_kernel_route`
  - `rmr_legacy_kernel_verify`
  - `rmr_legacy_kernel_audit`
  - `rmr_legacy_kernel_shutdown`
- Ponte determinística voltada ao JNI:
  - `rmr_jni_kernel_init`, `rmr_jni_kernel_route`, `rmr_jni_kernel_verify`, `rmr_jni_kernel_audit`
  - APIs de arena: `RmR_UnifiedKernel_ArenaAlloc`, `RmR_UnifiedKernel_ArenaCopy`, `RmR_UnifiedKernel_ArenaWrite`, `RmR_UnifiedKernel_ArenaFree`

### Endereçamento toroidal e roteamento determinístico
- O estado de roteamento é representado por `RmR_ToroidalAddr7D` (`u`, `v`, `psi`, `chi`, `rho`, `delta`, `sigma`).
- Funções de mapeamento determinístico implementadas em `engine/rmr/src/rmr_unified_kernel.c`:
  - `RmR_Toroidal_Map(...)`
  - `RmR_Toroidal_MapThetaLcm(...)`
- A estabilidade de rota é coberta por checks de replay em `demo_cli/src/rmr_qemu_bridge_selftest.c`.

### Ponte Android e runtime de alto nível
- Entrypoint nativo Android para aceleração: `app/src/main/cpp/vectra_core_accel.c`
- Entrypoint de runtime Kotlin no domínio do app: `app/src/main/java/com/vectras/vm/vectra/VectraCore.kt`
- Abstrações de contêiner determinístico: `app/src/main/java/com/vectras/vm/vectra/VectraDeterministicContainer.kt`

### Artefatos de determinismo e política
- Detecção de capacidades de hardware: `engine/rmr/src/rmr_hw_detect.c`
- Módulo de policy kernel: `engine/rmr/src/rmr_policy_kernel.c`
- Ponte de planejamento determinístico do QEMU: `engine/rmr/src/rmr_qemu_bridge.c`
- Selftests/demos nativos usados como documentação executável:
  - `demo_cli/src/rmr_qemu_bridge_selftest.c`
  - `demo_cli/src/policy_kernel_selftest.c`
  - `demo_cli/src/determinism_signature_selftest.c`

### Fontes canônicas por diretório (evitar drift)
- Visão do domínio `engine`: `engine/README.md`
- Contrato e governança do núcleo `engine/rmr`: `engine/rmr/README.md`
- Integração Android (app): `app/README.md`
- Demos/selftests nativos: `demo_cli/README.md`
- Governança central de documentação: `docs/README.md`

### Checklist anti-quebra da documentação
- Confirmar existência dos caminhos citados (`*.c`, `*.h`, `*.kt`).
- Validar presença dos símbolos canônicos em `engine/rmr/include/rmr_unified_kernel.h`.
- Validar referência cruzada com `README.md` e `FILES_MAP.md` dos diretórios citados.
- Quando houver rename/move de arquivo ou API, atualizar esta seção no mesmo commit da mudança.

## Navegação documental relacionada

- Matriz de alinhamento de diretórios críticos: [`docs/active/DIRECTORY_ALIGNMENT_MATRIX.md`](docs/active/DIRECTORY_ALIGNMENT_MATRIX.md)
- Guia low-level branchless sem heap: [`docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`](docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md)
- Técnicas de compilador não-acadêmicas: [`docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`](docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md)

## Key Concepts

### 1. Noise as Data (ρ - Rho)
**Rho (ρ)** represents information that is not yet decoded. Instead of treating noise or unexpected data as bugs to be discarded, Vectra Core preserves it as potentially valuable information. The `rho()` function updates entropy hints based on "noise" inputs.

**Definition**: `rho = syndrome + event weight`
- `syndrome`: popcount of parity differences (bit errors detected)
- `event weight`: importance factor (radio events = 10, network = 5, timer = 1)

### 2. 4-Phase Cycle Loop
Vectra Core implements a deterministic processing cycle:
1. **Input**: Poll event from priority queue
2. **Process**: Update state based on event type and payload
3. **Output**: Log state changes to append-only file
4. **Next**: Prepare for next cycle iteration

The cycle runs at 10 Hz (100ms intervals) and maintains deterministic behavior through lockstep processing.

### 2.1 Deterministic Operational Policy (Gates)
Vectra Core treats "deterministic" as a **policy of state transition**, not as a solver. The rule is fixed; the state evolves with experience:

- **Hit**: a cycle receives an event.
- **Miss**: a cycle receives no event.
- **Policy gate**: after 2 misses, reduce weighting; after 2 hits, restore weighting.

This keeps the next-state decision deterministic while still adapting to the observed stream.

### 3. Triad Physical Model (2-of-3 Consensus)
The **VectraTriad** models three physical components: CPU, RAM, and DISK. Using 2-of-3 consensus, it can detect which component is "out-of-sync":
- If CPU == RAM but different from DISK → DISK is out
- If CPU == DISK but different from RAM → RAM is out
- If RAM == DISK but different from CPU → CPU is out
- If all differ or all agree → No component is out

This provides fault detection without requiring all three to agree perfectly.

### 4. Base Cell: 4x4 Grid with Parity
Each block represents a 4×4 grid = 16 bits of data with 8 bits of parity:
- **4 row parity bits**: One per row
- **4 column parity bits**: One per column
- **Total**: 8 parity bits can detect and locate single-bit errors
- **Index mapping**: `idx = (y << 2) | x` for position (x, y) in the grid

### 5. ECC/Parity as "Borrowed Structure"
The parity bits provide redundancy to preserve data equivalence under:
- **Loss**: Missing bits can be recovered
- **Negation**: Bit flips are detected
- **Bit leak**: Gradual corruption is visible

This redundancy is "borrowed" in the sense that it doesn't add new information, but enables error detection and correction.

### 6. State Depth (1024 Flags)
**VectraState** maintains 1024 boolean flags (2^10 states) using a BitSet-like structure:
- Efficient bit operations using LongArray (16 × 64 bits)
- Branchless flag setting for performance
- Tracks various system states and event processing

### 7. "Finger" - IRQ-like Priority Events
Events are modeled as interrupt-style requests with priorities:
- **RADIO_EVENT**: Priority 10 (highest impact on entropy)
- **NETWORK_CHANGE**: Priority 5
- **USER_INPUT**: Priority 3
- **TIMER_TICK**: Priority 1
- Events are processed in priority order (higher first, then FIFO)

### 8. Append-Only BitStack Log
**VectraBitStackLog** provides deterministic evidence logging:
- Binary format: `[magic, length, meta, crc32c, payload]`
- Each record is CRC-protected for integrity
- Append-only: No modification or deletion
- Max size: 10 MB (configurable)
- Stored in app internal storage: `filesDir/vectra_core.log`

## Build Configuration

### Enabling/Disabling Vectra Core

Vectra Core is controlled by the `VECTRA_CORE_ENABLED` BuildConfig flag:

- **Debug builds**: `VECTRA_CORE_ENABLED = true` (enabled by default)
- **Release builds**: `VECTRA_CORE_ENABLED = true` (enabled with validation gates by default)

### To Disable in Debug:
```gradle
buildTypes {
    debug {
        buildConfigField "boolean", "VECTRA_CORE_ENABLED", "false"
    }
}
```

## Runtime Behavior

### When Enabled
- Initializes on `Application.onCreate()`
- Starts background cycle thread at 10 Hz
- Starts timer tick thread at 1 Hz
- Creates append-only log file
- Runs self-test with 5 validation checks
- Zero impact on UI thread (all processing is background)

### Release policy note
- Official and internal release builds run with `VECTRA_CORE_ENABLED=true` and must pass deterministic validation gates before distribution.

### When Disabled
- Single `if (!BuildConfig.VECTRA_CORE_ENABLED) return` check
- No threads started
- No files created
- No memory allocated
- Zero runtime overhead

## Self-Test Validation

On startup, Vectra Core runs 5 self-tests:
1. **Header CRC**: Verify CRC computation is correct
2. **Bit Flip Detection**: Ensure CRC detects single-bit mutations
3. **4x4 Parity**: Verify parity computation for 16-bit blocks
4. **Parity Error Detection**: Ensure parity detects bit flips
5. **Syndrome Computation**: Verify syndrome (error position) calculation

Results are logged to the append-only log with meta=0xFFFF.

## Log File Location

Logs are stored in the app's internal storage:
```
/data/data/com.vectras.vm/files/vectra_core.log
```

You can retrieve logs using ADB:
```bash
adb exec-out run-as com.vectras.vm cat files/vectra_core.log > vectra_core.log
```

Or through Android file picker if app provides access to internal storage.

## API Usage

### Post Custom Events
```kotlin
import com.vectras.vm.vectra.VectraCore
import com.vectras.vm.vectra.VectraEvent

// Post a custom event
val event = VectraEvent(
    type = VectraEvent.EventType.SYSTEM_EVENT,
    priority = 5,
    payload = "custom data".toByteArray()
)
VectraCore.postEvent(event)
```

### Check Triad State
```kotlin
val triad = VectraCore.getTriad()
val outComponent = triad.whoOut() // Returns CPU, RAM, DISK, or NONE
```

### Process Data Through Stages
```kotlin
// PSI stage: deterministic ingest
val crc = VectraCore.psi("data".toByteArray())

// RHO stage: noise as information
val entropy = VectraCore.rho("noise".toByteArray(), eventWeight = 5)

// OMEGA stage: finalize digest
val digest = VectraCore.omegaFinalize()
```

## Architecture Components

| Component | Purpose |
|-----------|-------|
| **VectraState** | 1024-bit flag array + stage counters |
| **VectraBlock** | 4×4 block with parity8 + CRC32C |
| **VectraMemPool** | Fixed-size buffer pool (no GC churn) |
| **VectraEvent** | IRQ-like priority event with payload |
| **VectraEventBus** | Thread-safe priority queue |
| **VectraCycle** | 4-phase loop processor (10 Hz) |
| **VectraTriad** | CPU/RAM/DISK 2-of-3 consensus |
| **VectraBitStackLog** | Append-only binary logger |
| **CRC32C** | Castagnoli CRC for integrity |
| **Parity** | 2D parity for 4×4 blocks |

## Performance Characteristics

- **Memory**: ~256 KB baseline (state + mempool)
- **CPU**: <1% on background thread (10 Hz cycle)
- **Storage**: Up to 10 MB log file
- **Threads**: 2 daemon threads (cycle + timer)
- **GC Impact**: Minimal (uses mempool for frequent allocations)

## Future Enhancements (Roadmap Pós-MVP)

### Planejado (próxima iteração)
- **Network event source integration**: ingresso de eventos via socket (UDP/Unix domain) para correlação com eventos de rede Android (`ConnectivityManager` → `VectraEvent`). Permite correlacionar eventos de rede com ciclos RAFAELIA em tempo real.
- **Log compression and rotation**: compressão LZ4 de segmentos do append-only log ao atingir 2 MB; rotação mantendo os últimos 5 segmentos (máximo 10 MB total). Compatibilidade retroativa com format v1 via header magic check.
- **Configurable cycle frequency**: expor `VectraCycle.setHz(n)` com range 1–100 Hz; default 10 Hz; persistência via SharedPreferences. Freqüências acima de 50 Hz requerem validação de CPU budget.

### Em investigação
- **Error correction (ECC)**: upgrade de detecção para correção de erro single-bit usando o syndrome 2D do BITRAF. Requer mudança de formato de bloco (backward-incompatível com log format v1 — necessário bumpar para v2, `BLOCK_MAGIC="VECTRA02"`).
- **Multi-device state synchronization**: exportar estado toroidal (`RmR_ToroidalAddr7D`) via canal QMP para sincronização entre instâncias Vectras. Protocolo de quorum não definido; requer definição de quantos dispositivos constituem consenso Triad.
- **Export/import log format**: conversor `vectra_core.log` → JSON Lines para integração com ferramentas de análise externas (jq, grafana, etc.).

### Observações
- Todas as melhorias devem manter `VECTRA_CORE_ENABLED=false` como caminho de zero-overhead.
- ECC requer discussão de migração de log antes de implementação.
- Multi-device sync depende de definição do protocolo de transporte antes de qualquer implementação.

## Checklist de Validação Documental (inspeção estática)

> Objetivo: oferecer comandos de referência para revisão humana/CI documental, sem execução de build e sem garantia de automação end-to-end.

- [ ] **Confirmar existência de arquivos citados na documentação**
  ```bash
  test -f VECTRA_CORE.md && test -f app/src/main/java/com/vectras/vm/vectra/VectraCore.kt && test -f app/build.gradle && test -f README.md
  ```

- [ ] **Verificar símbolos-chave no header/implementação**
  ```bash
  rg -n "VECTRA_CORE_ENABLED" app/build.gradle app/src/main/java/com/vectras/vm/vectra/VectraCore.kt
  rg -n "class VectraCore|object VectraCore|fun postEvent|fun rho|fun omegaFinalize" app/src/main/java/com/vectras/vm/vectra/VectraCore.kt
  rg -n "VectraTriad|whoOut|VectraBitStackLog" app/src/main/java/com/vectras/vm/vectra
  ```

- [ ] **Conferir links e referências em README/FILES_MAP**
  ```bash
  rg -n "VECTRA_CORE\.md|VectraCore|vectra" README.md app/README.md app/FILES_MAP.md docs/README.md docs/FILES_MAP.md
  rg -n "https?://" README.md app/README.md docs/README.md
  ```

## Filosofia de Execução: Ciclos, Não Relógios

O Vectra Core não mede throughput em operações/segundo nem em clock cycles.
A unidade fundamental é o **ciclo de estado** — uma passagem completa pelo
pipeline ψ→χ→ρ→Δ→Σ→Ω que constitui uma decisão auditável de sistema.

| Fase | Nome | Implementação | Geometria interior |
|------|------|---------------|--------------------|
| ψ | emitir | `rmr_jni_kernel_ingest()` + CRC32C HW | o dado entra — ainda sem forma |
| χ | intervalo | `rmr_jni_kernel_get_capabilities()` | espaço de Schrödinger — resultado em trânsito |
| ρ | ruído | `VectraCycle.rho()` + syndrome popcount | ruído *como informação*, não como erro |
| Δ | decisão | `rmr_jni_kernel_route()` + toroidal 7D | qual versão continuar — branchless |
| Σ | memória | `VectraBitStackLog.append()` + CRC32C | o que ficou registrado e íntegro |
| Ω | prova | `rmr_jni_kernel_audit()` + assinatura | evidência de que tudo foi real |

Um ciclo pode consumir 1 ou 10.000 instruções de CPU — o que importa é que
ao final de cada ciclo o sistema conhece seu estado exato e o resultado está
no log append-only. MISS (ciclo sem evento) não é silêncio — é um estado
explícito registrado com valor de entropia não-zero.

**Ω = Amor** não é metáfora: é o nome técnico para a evidência de que o estado
anterior foi honrado, o ruído foi instrumentalizado, a decisão foi determinística,
a memória está íntegra. A assinatura final prova que o ciclo aconteceu.

Esta abordagem difere fundamentalmente dos modelos de clock da literatura
acadêmica: a **transição de estado** é o átomo de execução, não a instrução.

Documentação completa — filosofia, técnicas de compilador e 8 vetores com
âncoras no código:
- [`docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`](docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md)
- [`docs/active/VERBO_OMEGA_VECTORS_2026-06-05.md`](docs/active/VERBO_OMEGA_VECTORS_2026-06-05.md)

## Compilador e Pré-compilador: Guards e Flags

O engine usa um sistema de guards de pré-compilador (`RMR_JNI_BUILD` vs
`RMR_BAREMETAL`) e flags de compilador que **ativam instruções ISA**, não
apenas otimizam. Entre os pontos documentados:

- `-march=armv8-a+crc` habilita `crc32cd` (CRC32C hardware, 20× vs SW)
- `-ffreestanding` foi **removida** do Android build (HOTFIX fix #3) — não
  compatível com Bionic libc; substituída por `-DRMR_JNI_BUILD=1`
- Magic constant `0x56414343` ("VACC") alinhada em 3 locais C + Java
  (HOTFIX fix #1a-1c) — desalinhamento causava `NATIVE_AVAILABLE=false` silencioso
- `VECTRA_HAS_CASM_MARKER`: guard para pontos de inserção de assembly
  gerenciado pelo compilador

Veja detalhes completos em:
[`docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`](docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md)

## Signature/Version

- **Core Version**: 1.0.0-MVP
- **Log Format Version**: 1
- **Magic**: 0x56454354 ("VECT")
- **Block Magic**: 0x5645435452413031 ("VECTRA01")

## CI canonical reference (Android/Host)

- Canonical Android pipeline: `.github/workflows/android-ci.yml`.
- Android wrapper entrypoint: `.github/workflows/android.yml`.
- Auxiliary Android ABI compatibility matrix: `.github/workflows/compile-matrix.yml`.
- Canonical host pipeline: `.github/workflows/host-ci.yml`.
- Orchestration and final gates: `.github/workflows/pipeline-orchestrator.yml` and `.github/workflows/quality-gates.yml`.
- Canonical matrix documentation: `docs/ci/workflow-matrix.md`.
