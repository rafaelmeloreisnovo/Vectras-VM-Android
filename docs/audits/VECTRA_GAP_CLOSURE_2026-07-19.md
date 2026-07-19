# Vectra gap closure — 2026-07-19

## Escopo

Este registro acompanha o trabalho da branch `agent/vectra-gap-closure-zipraf-20260719` e do PR draft `#1049`.

Por decisão operacional, GitHub Actions/YAML ficam temporariamente fora do caminho crítico. O projeto continua avançando por código, compilação standalone, KAT local, testes unitários adicionados e harness instrumentado Android.

## Bloco fechado por implementação

### ZIPRAF direct runtime

- parser de local-file header clássico;
- parser de EOCD;
- parser de central directory clássico;
- validação single-disk;
- seleção de entrada pelo nome;
- rejeição de nome duplicado;
- cross-check local header ↔ central directory;
- exigência de ZIP `STORE`;
- rejeição de criptografia;
- rejeição de data descriptor;
- rejeição explícita de ZIP64;
- validação de CRC, tamanhos, offsets e limites;
- validação de nome contra NUL, caminho absoluto, drive e `..`;
- derivação auditável do extent;
- abertura segura por `openValidated`;
- CRC-32 obrigatório por padrão na abertura validada;
- mmap por janela, sem mapear arquivo ou payload inteiro;
- offsets `Long`, eliminando o limite anterior do payload inteiro em um `ByteBuffer`;
- fechamento do descritor em falha;
- estágios `BUFFER`, `L1_HOT`, `L2_SHARED`;
- lanes determinísticas `0..7`;
- invariante de bits fixos.

## Prova executada fora de Gradle

Comando:

```bash
bash tools/zipraf/run_zipraf_host_kat.sh
```

Ambiente observado:

```text
host_arch = x86_64
kotlinc   = 1.9.0
java      = OpenJDK 21.0.10
```

Resultado:

```json
{
  "schema": "zipraf.kat.v1",
  "status": "PASS",
  "checks": {
    "central_directory": true,
    "payload_size": true,
    "window_length": true,
    "lane": true,
    "window_content": true,
    "crc": true,
    "missing_entry_rejected": true
  }
}
```

Portanto:

```text
host_kotlin_compilation = PASS
standalone_kat          = PASS_7_OF_7
android_runtime         = TOKEN_VAZIO
```

## Testes adicionados

### Unidade JVM/JUnit

A suíte agora cobre 17 fronteiras:

1. três estágios e oito lanes;
2. cross-check do arquivo completo;
3. abertura `openValidated`;
4. parser local explicitamente baixo nível;
5. mutação de payload;
6. CRC central divergente;
7. tamanho central divergente;
8. nome duplicado;
9. múltiplas entradas sem seleção;
10. entrada ausente;
11. arquivo multi-disk;
12. data descriptor;
13. path traversal;
14. payload truncado;
15. central directory ausente;
16. extent vazio e método não-STORE;
17. bits fixos.

O código da suíte foi compilado sintaticamente no host com stubs mínimos de JUnit. Execução JUnit real permanece pendente.

### Android instrumentado

Foi adicionado:

```text
app/src/androidTest/java/com/vectras/vm/vectra/ZiprafDirectRuntimeInstrumentedTest.kt
```

O harness prepara:

- arquivo ZIP real em `cacheDir`;
- round-trip validado;
- janelas no início, meio e fim;
- oito lanes;
- CRC-32 no dispositivo;
- benchmark-harness de 256 janelas sobre 2 MiB;
- relatório JSON com ABI, SDK e tempo;
- ausência explícita de claim de ganho.

## Estado epistemológico

```text
code_added                    = true
central_directory_check       = IMPLEMENTED
windowed_mmap                 = IMPLEMENTED
standalone_host_compile       = PASS
standalone_host_kat           = PASS_7_OF_7
unit_tests_added              = true
unit_test_source_compile      = PASS_WITH_LOCAL_JUNIT_STUBS
real_junit_execution          = TOKEN_VAZIO
android_instrumented_added    = true
android_instrumented_executed = TOKEN_VAZIO
arm32_device                  = TOKEN_VAZIO
arm64_device                  = TOKEN_VAZIO
zip64                         = BLOCKED_BY_DESIGN
performance_harness           = ADDED_NOT_EXECUTED
performance_claim             = PROHIBITED
claim_allowed                 = false
Actions/YAML                  = DEFERRED_BY_OWNER
```

## TAIL do fragmento

```yaml
tail:
  traceability:
    repository: rafaelmeloreisnovo/Vectras-VM-Android
    branch: agent/vectra-gap-closure-zipraf-20260719
    pull_request: 1049
    base: master
    date: 2026-07-19
  authorship:
    upstream_lineage: Vectras VM Android
    modification_author: Rafael Melo Reis project workflow
  intent:
    purpose: close direct-memory ZIPRAF integrity and runtime gaps
    distribution: draft review only
  license:
    inherited_scope: repository license map applies
    new_code_spdx: TOKEN_VAZIO pending directory-wide legal decision
  evidence:
    source_review: PRESENT
    host_compile: PASS
    standalone_kat: PASS_7_OF_7
    unit_tests_present: PRESENT
    unit_junit_executed: TOKEN_VAZIO
    android_harness_present: PRESENT
    device_runtime: TOKEN_VAZIO
```

## Gaps prioritários remanescentes

### Programação imediata — sem depender de CI

1. integrar `openValidated` ao ponto real que consome ZIPRAF;
2. adicionar política de seleção por manifesto/entry name;
3. registrar telemetria de janelas e bytes;
4. acrescentar comparação `mmap × FileChannel.read × stream` no harness;
5. preparar comando local de teste JUnit direcionado;
6. executar no Termux/JVM local quando o checkout estiver disponível;
7. executar o teste Android em ARM32;
8. executar o teste Android em ARM64;
9. ligar o resultado ao `RELEASE_EVIDENCE_LEDGER.md`.

### P0 global do Vectra

- APK/AAB com SHA-256 e relatório ABI;
- boot mínimo de VM;
- proveniência de `libXlorie.so`, rootfs, BIOS e OVMF;
- decisão `NAOCOMERCIAL × GPLv2`;
- SPDX final de `engine/rmr/**`.

### P1 global

- ZIP64, caso realmente necessário;
- telemetria de page faults/RSS;
- migração QEMU string → `argv` estruturado;
- prova JNI versus fallback Java.

## Regra de promoção

```text
IMPLEMENTED
→ HOST_KAT_PASS
→ REAL_JUNIT_PASS
→ ANDROID_DEVICE_PASS
→ ARM32_ARM64_PASS
→ RELEASE_EVIDENCE_RECORDED
```

Nenhuma etapa posterior é inferida pela anterior.

## Retroalimentação

- `F_ok`: central directory, cross-check, janela mmap, abertura segura e KAT standalone foram concluídos.
- `F_gap`: JUnit real, Android ARM32/ARM64 e benchmark comparativo continuam abertos.
- `F_next`: integrar `openValidated` ao consumidor real e adicionar telemetria/benchmark comparativo, mantendo Actions fora do caminho crítico.
